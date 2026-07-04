import Foundation

enum LockCarResult: Equatable {
    case dryRun
    case success
    case confirmedFailure
    case sentUnconfirmed
    case skippedNoCredentials
    case failed(String)
}

/// Orchestrates a single lock attempt: dry-run short-circuit, credential
/// loading, session reuse/refresh/login, sending the command, and a short
/// best-effort confirmation poll. This is the single entry point both the
/// in-app "Test Lock" button and the (future) background App Intent call,
/// so the two paths can never drift.
enum LockCarService {
    static func lockCar(dryRun: Bool) async -> LockCarResult {
        guard !dryRun else {
            let username = (try? CredentialsStore.load())?.username
            LogStore.shared.append(
                event: "dry_run_lock",
                detail: "would have called lock() for account \(maskedUsername(username))"
            )
            return .dryRun
        }

        guard let credentials = try? CredentialsStore.load() else {
            LogStore.shared.append(event: "lock_skipped", detail: "no credentials configured")
            return .skippedNoCredentials
        }

        do {
            var session = try await validSession(credentials: credentials)

            if session.vehicle == nil {
                let vehicles = try await BluelinkAPI.getVehicles(credentials: credentials, accessToken: session.accessToken)
                guard let vehicle = vehicles.first else {
                    throw BluelinkError.noVehicleFound
                }
                session.vehicle = vehicle
                try? BluelinkSessionStore.save(session)
            }

            guard let vehicle = session.vehicle else {
                throw BluelinkError.noVehicleFound
            }

            let transactionId = try await BluelinkAPI.sendLockAction(
                .lock,
                credentials: credentials,
                accessToken: session.accessToken,
                vehicle: vehicle
            )

            LogStore.shared.append(
                event: "lock_command_sent",
                detail: transactionId.map { "transactionId=\($0)" } ?? "no transaction id returned"
            )

            guard let transactionId else {
                return .sentUnconfirmed
            }

            let status = await pollForConfirmation(
                transactionId: transactionId,
                credentials: credentials,
                accessToken: session.accessToken,
                vehicle: vehicle
            )

            switch status {
            case .success:
                LogStore.shared.append(event: "lock_confirmed_success")
                return .success
            case .failed:
                LogStore.shared.append(event: "lock_confirmed_failed")
                return .confirmedFailure
            case .pending, .unknown:
                LogStore.shared.append(event: "lock_unconfirmed", detail: "status still \(status) after polling window")
                return .sentUnconfirmed
            }
        } catch {
            let message = String(describing: error)
            LogStore.shared.append(event: "lock_error", detail: message)
            return .failed(message)
        }
    }

    /// Returns a session with a not-yet-expired access token, refreshing or
    /// logging in as needed, and persists whatever session results.
    private static func validSession(credentials: BluelinkCredentials) async throws -> BluelinkSession {
        if let cached = try? BluelinkSessionStore.load(), cached.isAccessTokenValid() {
            return cached
        }

        if let cached = try? BluelinkSessionStore.load() {
            do {
                var refreshed = try await BluelinkAPI.refresh(
                    username: credentials.username,
                    password: credentials.password,
                    refreshToken: cached.refreshToken
                )
                refreshed.vehicle = cached.vehicle
                try? BluelinkSessionStore.save(refreshed)
                LogStore.shared.append(event: "token_refreshed")
                return refreshed
            } catch {
                LogStore.shared.append(event: "token_refresh_failed", detail: "falling back to full login")
            }
        }

        let fresh = try await BluelinkAPI.login(username: credentials.username, password: credentials.password)
        try? BluelinkSessionStore.save(fresh)
        LogStore.shared.append(event: "login_succeeded")
        return fresh
    }

    /// Polls checkActionStatus a handful of times with a short delay. Lock
    /// commands are near-instant physically, but the API's own confirmation
    /// can lag a couple seconds behind the HTTP 200 that accepted the
    /// command, so treat "still pending after the window" as inconclusive
    /// rather than a failure.
    private static func pollForConfirmation(transactionId: String, credentials: BluelinkCredentials, accessToken: String, vehicle: BluelinkVehicleInfo) async -> BluelinkOrderStatus {
        let maxAttempts = 6
        for attempt in 0..<maxAttempts {
            do {
                let status = try await BluelinkAPI.checkActionStatus(
                    transactionId: transactionId,
                    credentials: credentials,
                    accessToken: accessToken,
                    vehicle: vehicle
                )
                if status == .success || status == .failed {
                    return status
                }
            } catch {
                // Treat a failed status check as inconclusive, not a lock
                // failure -- the command itself already returned HTTP 200.
            }
            if attempt < maxAttempts - 1 {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
            }
        }
        return .unknown
    }

    private static func maskedUsername(_ username: String?) -> String {
        guard let username, !username.isEmpty else { return "(none configured)" }
        guard let atIndex = username.firstIndex(of: "@") else {
            return String(username.prefix(2)) + "***"
        }
        let local = username[username.startIndex..<atIndex]
        return String(local.prefix(2)) + "***" + username[atIndex...]
    }
}

import Foundation

/// Ported from the Hyundai US Bluelink flow used by bluelinky (Node.js) and
/// hyundai_kia_connect_api (Python) -- there is no official public API, so
/// these endpoints, headers, and payload shapes are reverse-engineered.
/// hyundai_kia_connect_api's HyundaiBlueLinkApiUSA.py is treated as the
/// primary source of truth here since it documents a live-confirmed fix
/// (refresh requires username+password alongside the refresh token, not the
/// refresh token alone) more recent than bluelinky's implementation.
enum BluelinkAPI {
    private static let host = "api.telematics.hyundaiusa.com"
    private static let loginBase = "https://\(host)/v2/ac/"
    private static let apiBase = "https://\(host)/ac/v2/"

    private static let clientId = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
    private static let clientSecret = "v558o935-6nne-423i-baa8"

    private static var utcOffsetHours: String {
        let seconds = TimeZone.current.secondsFromGMT()
        return String(seconds / 3600)
    }

    /// Shared headers sent on every request. Mirrors the header set
    /// HyundaiBlueLinkApiUSA.py sends (User-Agent, brandIndicator, etc.) --
    /// deviating from this reverse-engineered shape risks the request being
    /// rejected as coming from an unrecognized client.
    private static func baseHeaders() -> [String: String] {
        [
            "content-type": "application/json;charset=UTF-8",
            "accept": "application/json, text/plain, */*",
            "accept-language": "en-US,en;q=0.9",
            "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.142 Safari/537.36",
            "origin": "https://\(host)",
            "referer": "https://\(host)/login",
            "from": "SPA",
            "to": "ISS",
            "language": "0",
            "offset": utcOffsetHours,
            "sec-fetch-dest": "empty",
            "sec-fetch-mode": "cors",
            "sec-fetch-site": "same-origin",
            "refresh": "false",
            "encryptFlag": "false",
            "brandIndicator": "H",
            "client_id": clientId,
            "clientSecret": clientSecret
        ]
    }

    private static func authenticatedHeaders(credentials: BluelinkCredentials, accessToken: String) -> [String: String] {
        var headers = baseHeaders()
        headers["username"] = credentials.username
        headers["accessToken"] = accessToken
        headers["blueLinkServicePin"] = credentials.pin
        return headers
    }

    private static func vehicleHeaders(credentials: BluelinkCredentials, accessToken: String, vehicle: BluelinkVehicleInfo) -> [String: String] {
        var headers = authenticatedHeaders(credentials: credentials, accessToken: accessToken)
        headers["registrationId"] = vehicle.regId
        headers["gen"] = String(vehicle.generation)
        headers["vin"] = vehicle.vin
        return headers
    }

    private static func makeRequest(url: URL, method: String, headers: [String: String], jsonBody: [String: Any]? = nil) throws -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }
        if let jsonBody {
            request.httpBody = try JSONSerialization.data(withJSONObject: jsonBody)
        }
        return request
    }

    // MARK: - Auth

    static func login(username: String, password: String) async throws -> BluelinkSession {
        let url = URL(string: loginBase + "oauth/token")!
        let request = try makeRequest(
            url: url,
            method: "POST",
            headers: baseHeaders(),
            jsonBody: ["username": username, "password": password]
        )
        let response = try await send(request)
        return try session(from: response)
    }

    static func refresh(username: String, password: String, refreshToken: String) async throws -> BluelinkSession {
        let url = URL(string: loginBase + "oauth/token")!
        let request = try makeRequest(
            url: url,
            method: "POST",
            headers: baseHeaders(),
            jsonBody: [
                "username": username,
                "password": password,
                "grant_type": "refresh_token",
                "refresh_token": refreshToken
            ]
        )
        let response = try await send(request)
        return try session(from: response)
    }

    private static func session(from data: Data) throws -> BluelinkSession {
        let decoded = try JSONDecoder().decode(BluelinkTokenResponse.self, from: data)
        if let errorCode = decoded.errorCode {
            throw BluelinkError.authenticationFailed("\(errorCode): \(decoded.errorMessage ?? "unknown error")")
        }
        guard !decoded.accessToken.isEmpty else {
            throw BluelinkError.authenticationFailed("no access_token in response")
        }
        return BluelinkSession(
            accessToken: decoded.accessToken,
            refreshToken: decoded.refreshToken,
            tokenExpiry: Date().addingTimeInterval(decoded.expiresIn),
            vehicle: nil
        )
    }

    // MARK: - Vehicles

    static func getVehicles(credentials: BluelinkCredentials, accessToken: String) async throws -> [BluelinkVehicleInfo] {
        let url = URL(string: apiBase + "enrollment/details/\(credentials.username)")!
        let request = try makeRequest(
            url: url,
            method: "GET",
            headers: authenticatedHeaders(credentials: credentials, accessToken: accessToken)
        )
        let data = try await send(request)
        let decoded = try JSONDecoder().decode(BluelinkEnrolledVehiclesResponse.self, from: data)
        if let errorCode = decoded.errorCode {
            throw BluelinkError.authenticationFailed("\(errorCode): \(decoded.errorMessage ?? "unknown error")")
        }
        guard let entries = decoded.enrolledVehicleDetails else {
            throw BluelinkError.noVehicleFound
        }
        return entries
            .filter { $0.vehicleDetails.enrollmentStatus != "CANCELLED" }
            .map {
                BluelinkVehicleInfo(
                    regId: $0.vehicleDetails.regid,
                    vin: $0.vehicleDetails.vin,
                    generation: Int($0.vehicleDetails.vehicleGeneration ?? "2") ?? 2,
                    nickname: $0.vehicleDetails.nickName
                )
            }
    }

    // MARK: - Commands

    /// Sends the lock/unlock command and returns the transaction ID (from
    /// the tmsTid/transactionId/Xid response header) if one was present, so
    /// the caller can poll checkActionStatus for confirmation. Control
    /// commands can return HTTP 200 with an empty body on success, so an
    /// empty body is not treated as an error.
    static func sendLockAction(_ action: BluelinkLockAction, credentials: BluelinkCredentials, accessToken: String, vehicle: BluelinkVehicleInfo) async throws -> String? {
        let url = URL(string: apiBase + action.endpointPath)!
        var headers = vehicleHeaders(credentials: credentials, accessToken: accessToken, vehicle: vehicle)
        headers["APPCLOUD-VIN"] = vehicle.vin

        let request = try makeRequest(
            url: url,
            method: "POST",
            headers: headers,
            jsonBody: ["userName": credentials.username, "vin": vehicle.vin]
        )

        let (data, httpResponse) = try await sendWithResponse(request)

        if !data.isEmpty, let decoded = try? JSONDecoder().decode(BluelinkActionStatusResponse.self, from: data),
           let errorCode = decoded.errorCode {
            throw BluelinkError.commandFailed("\(errorCode): \(decoded.errorMessage ?? "unknown error")")
        }

        for key in ["tmsTid", "transactionId", "Xid"] {
            if let value = httpResponse.value(forHTTPHeaderField: key) {
                return value
            }
        }
        return nil
    }

    static func checkActionStatus(transactionId: String, credentials: BluelinkCredentials, accessToken: String, vehicle: BluelinkVehicleInfo) async throws -> BluelinkOrderStatus {
        let url = URL(string: apiBase + "rmt/getRunningStatus")!
        var headers = vehicleHeaders(credentials: credentials, accessToken: accessToken, vehicle: vehicle)
        headers["tid"] = transactionId
        headers["login_id"] = credentials.username
        headers["service_type"] = "REMOTE_POLL"

        let request = try makeRequest(url: url, method: "GET", headers: headers)
        let data = try await send(request)

        guard !data.isEmpty else { return .unknown }
        let decoded = try JSONDecoder().decode(BluelinkActionStatusResponse.self, from: data)
        switch decoded.status {
        case "SUCCESS": return .success
        case "ERROR": return .failed
        case "PENDING": return .pending
        default: return .unknown
        }
    }

    // MARK: - Transport

    private static func send(_ request: URLRequest) async throws -> Data {
        try await sendWithResponse(request).0
    }

    private static func sendWithResponse(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw BluelinkError.network(error)
        }
        guard let httpResponse = response as? HTTPURLResponse else {
            throw BluelinkError.unexpectedResponse("non-HTTP response")
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw BluelinkError.unexpectedResponse("HTTP \(httpResponse.statusCode)")
        }
        return (data, httpResponse)
    }
}

import AppIntents

/// Exposed to Shortcuts so a personal automation (e.g. Wi-Fi disconnect from
/// the car's hotspot, Bluetooth disconnect, or CarPlay disconnect, "Ask
/// Before Running" off) can run this silently.
///
/// `openAppWhenRun = false` is what keeps this from bringing the app to the
/// foreground -- unlike a URL-scheme-based action, App Intents execute
/// in-process via the AppIntents framework's own background path, so there
/// is no browser/app-launch confirmation step to worry about.
struct LockCarIntent: AppIntent {
    static let title: LocalizedStringResource = "Lock Car"
    static let description = IntentDescription(
        "Locks your Hyundai via Bluelink, respecting the Dry Run, cooldown, and pre-lock delay settings configured in Walkaway Lock."
    )

    static let openAppWhenRun: Bool = false

    @MainActor
    func perform() async throws -> some IntentResult {
        let settings = AppSettings.shared

        guard settings.shouldProceedPastDebounce() else {
            LogStore.shared.append(
                event: "trigger_debounced",
                detail: "ignored -- within \(Int(settings.debounceSeconds))s cooldown"
            )
            return .result()
        }

        LogStore.shared.append(event: "trigger_received")

        let delaySeconds = settings.preLockDelaySeconds
        if delaySeconds > 0 {
            LogStore.shared.append(
                event: "pre_lock_delay_started",
                detail: "waiting \(Int(delaySeconds))s before sending lock command"
            )
            try? await Task.sleep(nanoseconds: UInt64(delaySeconds * 1_000_000_000))
        }

        _ = await LockCarService.lockCar(dryRun: settings.dryRunEnabled)
        return .result()
    }
}

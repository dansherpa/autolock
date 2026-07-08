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
        "Locks your Hyundai via Bluelink, respecting the Dry Run and cooldown settings configured in Walkaway Lock."
    )

    static let openAppWhenRun: Bool = false

    // Deliberately does NOT sleep/delay before locking. Silent automations
    // (openAppWhenRun = false, "Ask Before Running" off) run under a strict
    // background execution budget (commonly ~30s) -- blocking here for a
    // user-configurable delay ate that budget before the Bluelink network
    // calls even started, and Shortcuts reported "unknown error occurred"
    // when the intent got killed mid-flight. Any pre-lock wait needs to
    // happen in the Shortcuts automation itself (a native "Wait" action
    // before this one), not inside perform().
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
        _ = await LockCarService.lockCar(dryRun: settings.dryRunEnabled)
        return .result()
    }
}

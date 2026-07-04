import AppIntents

/// Exposed to Shortcuts so a personal automation (Wi-Fi disconnect from the
/// car's hotspot, "Ask Before Running" off) can run this silently.
///
/// `openAppWhenRun = false` is what keeps this from bringing the app to the
/// foreground -- unlike a URL-scheme-based action, App Intents execute
/// in-process via the AppIntents framework's own background path, so there
/// is no browser/app-launch confirmation step to worry about.
struct LockCarIntent: AppIntent {
    static let title: LocalizedStringResource = "Lock Car"
    static let description = IntentDescription(
        "Locks your Hyundai via Bluelink, respecting the Dry Run and cooldown settings configured in Autolock."
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
        _ = await LockCarService.lockCar(dryRun: settings.dryRunEnabled)
        return .result()
    }
}

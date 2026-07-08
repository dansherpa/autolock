import Foundation
import Combine

/// App-wide settings backed by UserDefaults. Centralized (rather than
/// scattered @AppStorage) so the App Intent added in a later step can read
/// the same values the Settings screen writes.
@MainActor
final class AppSettings: ObservableObject {
    static let shared = AppSettings()

    private enum Keys {
        static let dryRunEnabled = "dryRunEnabled"
        static let debounceSeconds = "debounceSeconds"
        static let lastTriggerDate = "lastTriggerDate"
    }

    private let defaults: UserDefaults

    @Published var dryRunEnabled: Bool {
        didSet { defaults.set(dryRunEnabled, forKey: Keys.dryRunEnabled) }
    }

    @Published var debounceSeconds: Double {
        didSet { defaults.set(debounceSeconds, forKey: Keys.debounceSeconds) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        // Dry run defaults to true (opt-in to live commands) even on first
        // launch, since UserDefaults.bool(forKey:) already returns false for
        // an unset key -- registerDefaults makes that explicit either way.
        defaults.register(defaults: [
            Keys.dryRunEnabled: true,
            Keys.debounceSeconds: 90.0
        ])
        self.dryRunEnabled = defaults.bool(forKey: Keys.dryRunEnabled)
        self.debounceSeconds = defaults.double(forKey: Keys.debounceSeconds)
    }

    var lastTriggerDate: Date? {
        get { defaults.object(forKey: Keys.lastTriggerDate) as? Date }
        set { defaults.set(newValue, forKey: Keys.lastTriggerDate) }
    }

    /// Returns true if enough time has passed since the last trigger to
    /// allow another lock attempt, and records `now` as the new last-trigger
    /// time as a side effect when it does.
    func shouldProceedPastDebounce(now: Date = Date()) -> Bool {
        if let last = lastTriggerDate, now.timeIntervalSince(last) < debounceSeconds {
            return false
        }
        lastTriggerDate = now
        return true
    }
}

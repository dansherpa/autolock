import XCTest
@testable import Autolock

@MainActor
final class LockCarIntentTests: XCTestCase {

    override func setUpWithError() throws {
        try CredentialsStore.clear()
        try BluelinkSessionStore.clear()
        LogStore.shared.clear()
        AppSettings.shared.dryRunEnabled = true
        AppSettings.shared.debounceSeconds = 90
        AppSettings.shared.lastTriggerDate = nil
    }

    override func tearDownWithError() throws {
        try CredentialsStore.clear()
        try BluelinkSessionStore.clear()
        AppSettings.shared.lastTriggerDate = nil
    }

    func testFirstTriggerProceedsAndLogsReceivedThenDryRun() async throws {
        _ = try await LockCarIntent().perform()

        let events = LogStore.shared.all().map(\.event).reversed()
        XCTAssertEqual(Array(events), ["trigger_received", "dry_run_lock"])
    }

    func testSecondTriggerWithinCooldownIsDebounced() async throws {
        _ = try await LockCarIntent().perform()
        LogStore.shared.clear()

        _ = try await LockCarIntent().perform()

        let events = LogStore.shared.all()
        XCTAssertEqual(events.count, 1)
        XCTAssertEqual(events[0].event, "trigger_debounced")
    }

    func testTriggerAfterCooldownWindowProceedsAgain() async throws {
        AppSettings.shared.debounceSeconds = 1
        _ = try await LockCarIntent().perform()
        LogStore.shared.clear()

        try await Task.sleep(nanoseconds: 1_200_000_000)
        _ = try await LockCarIntent().perform()

        let events = LogStore.shared.all().map(\.event)
        XCTAssertTrue(events.contains("trigger_received"))
    }
}

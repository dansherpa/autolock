import XCTest
@testable import Autolock

final class LockCarServiceTests: XCTestCase {

    override func setUpWithError() throws {
        try CredentialsStore.clear()
        try BluelinkSessionStore.clear()
        LogStore.shared.clear()
    }

    override func tearDownWithError() throws {
        try CredentialsStore.clear()
        try BluelinkSessionStore.clear()
    }

    func testDryRunNeverTouchesNetworkAndLogsWithoutCredentials() async {
        let result = await LockCarService.lockCar(dryRun: true)
        XCTAssertEqual(result, .dryRun)

        let entries = LogStore.shared.all()
        XCTAssertEqual(entries.count, 1)
        XCTAssertEqual(entries[0].event, "dry_run_lock")
        XCTAssertTrue(entries[0].detail.contains("(none configured)"))
    }

    func testDryRunMasksUsernameAndNeverLogsPasswordOrPin() async throws {
        try CredentialsStore.save(BluelinkCredentials(username: "driver@example.com", password: "supersecret", pin: "1234"))

        let result = await LockCarService.lockCar(dryRun: true)
        XCTAssertEqual(result, .dryRun)

        let entries = LogStore.shared.all()
        XCTAssertEqual(entries.count, 1)
        let detail = entries[0].detail
        XCTAssertTrue(detail.contains("dr***@example.com"), "expected masked username, got: \(detail)")
        XCTAssertFalse(detail.contains("supersecret"))
        XCTAssertFalse(detail.contains("1234"))
    }

    func testLiveLockSkippedWhenNoCredentialsConfigured() async {
        let result = await LockCarService.lockCar(dryRun: false)
        XCTAssertEqual(result, .skippedNoCredentials)

        let entries = LogStore.shared.all()
        XCTAssertEqual(entries.count, 1)
        XCTAssertEqual(entries[0].event, "lock_skipped")
    }

    func testBluelinkSessionAccessTokenValidity() {
        let stillValid = BluelinkSession(
            accessToken: "tok",
            refreshToken: "refresh",
            tokenExpiry: Date().addingTimeInterval(300),
            vehicle: nil
        )
        XCTAssertTrue(stillValid.isAccessTokenValid())

        let expired = BluelinkSession(
            accessToken: "tok",
            refreshToken: "refresh",
            tokenExpiry: Date().addingTimeInterval(-5),
            vehicle: nil
        )
        XCTAssertFalse(expired.isAccessTokenValid())

        // Within the ~10s safety margin should count as needing refresh.
        let almostExpired = BluelinkSession(
            accessToken: "tok",
            refreshToken: "refresh",
            tokenExpiry: Date().addingTimeInterval(5),
            vehicle: nil
        )
        XCTAssertFalse(almostExpired.isAccessTokenValid())
    }

    func testBluelinkSessionStoreRoundTrip() throws {
        let session = BluelinkSession(
            accessToken: "access-123",
            refreshToken: "refresh-456",
            tokenExpiry: Date().addingTimeInterval(1800),
            vehicle: BluelinkVehicleInfo(regId: "reg-1", vin: "VIN12345", generation: 3, nickname: "Ioniq 5")
        )
        try BluelinkSessionStore.save(session)
        let loaded = try BluelinkSessionStore.load()
        XCTAssertEqual(loaded, session)

        try BluelinkSessionStore.clear()
        XCTAssertNil(try BluelinkSessionStore.load())
    }
}

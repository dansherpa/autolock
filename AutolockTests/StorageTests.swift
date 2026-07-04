import XCTest
@testable import Autolock

final class StorageTests: XCTestCase {

    // Uses a dedicated Keychain account distinct from CredentialsStore's
    // "bluelink-primary" so running tests can never clobber real saved
    // credentials.
    private let testAccount = "test-keychain-account"

    override func tearDownWithError() throws {
        try KeychainStore.delete(account: testAccount)
        try super.tearDownWithError()
    }

    func testKeychainRoundTrip() throws {
        let credentials = BluelinkCredentials(username: "driver@example.com", password: "hunter2", pin: "1234")

        try KeychainStore.save(credentials, account: testAccount)
        let loaded = try KeychainStore.load(BluelinkCredentials.self, account: testAccount)
        XCTAssertEqual(loaded, credentials)

        try KeychainStore.delete(account: testAccount)
        let afterDelete = try KeychainStore.load(BluelinkCredentials.self, account: testAccount)
        XCTAssertNil(afterDelete)
    }

    func testKeychainSaveOverwritesExistingValue() throws {
        let first = BluelinkCredentials(username: "a@example.com", password: "pw1", pin: "1111")
        let second = BluelinkCredentials(username: "b@example.com", password: "pw2", pin: "2222")

        try KeychainStore.save(first, account: testAccount)
        try KeychainStore.save(second, account: testAccount)

        let loaded = try KeychainStore.load(BluelinkCredentials.self, account: testAccount)
        XCTAssertEqual(loaded, second)
    }

    func testLogStoreAppendOrderAndClear() {
        let store = LogStore.shared
        store.clear()

        store.append(event: "first_event", detail: "one")
        store.append(event: "second_event", detail: "two")

        let entries = store.all()
        XCTAssertEqual(entries.count, 2)
        // Newest first.
        XCTAssertEqual(entries[0].event, "second_event")
        XCTAssertEqual(entries[1].event, "first_event")

        store.clear()
        XCTAssertEqual(store.all().count, 0)
    }

    @MainActor
    func testDebounceBlocksWithinCooldownWindowAndAllowsAfter() {
        let suiteName = "test-suite-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let settings = AppSettings(defaults: defaults)
        settings.debounceSeconds = 60

        let start = Date()
        XCTAssertTrue(settings.shouldProceedPastDebounce(now: start), "first trigger should always proceed")
        XCTAssertFalse(
            settings.shouldProceedPastDebounce(now: start.addingTimeInterval(10)),
            "trigger within cooldown window should be suppressed"
        )
        XCTAssertTrue(
            settings.shouldProceedPastDebounce(now: start.addingTimeInterval(61)),
            "trigger after cooldown window should proceed"
        )
    }
}

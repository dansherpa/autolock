import Foundation

/// Loads and saves the single Bluelink account's credentials.
/// One phone, one Bluelink account -- no account switching, so a fixed key.
enum CredentialsStore {
    private static let account = "bluelink-primary"

    static func save(_ credentials: BluelinkCredentials) throws {
        try KeychainStore.save(credentials, account: account)
    }

    static func load() throws -> BluelinkCredentials? {
        try KeychainStore.load(BluelinkCredentials.self, account: account)
    }

    static func clear() throws {
        try KeychainStore.delete(account: account)
    }
}

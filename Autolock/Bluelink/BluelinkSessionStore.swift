import Foundation

/// Persists the Bluelink session (tokens + vehicle info) separately from
/// CredentialsStore's account/password/PIN, so clearing a stale session
/// never requires re-entering credentials.
enum BluelinkSessionStore {
    private static let account = "bluelink-session"

    static func save(_ session: BluelinkSession) throws {
        try KeychainStore.save(session, account: account)
    }

    static func load() throws -> BluelinkSession? {
        try KeychainStore.load(BluelinkSession.self, account: account)
    }

    static func clear() throws {
        try KeychainStore.delete(account: account)
    }
}

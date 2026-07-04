import Foundation
import Security

/// Minimal wrapper around Keychain Services for storing a single Codable
/// value as a generic password item.
///
/// Uses `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` rather than the
/// more common "WhenUnlocked" class: the App Intent that reads these
/// credentials runs in the background, often while the phone is locked in a
/// pocket right after leaving the car. "WhenUnlocked" would make the item
/// unreadable at exactly that moment. "ThisDeviceOnly" keeps the item out of
/// iCloud Keychain sync and out of restores to different hardware.
enum KeychainStore {
    enum KeychainError: Error {
        case encodingFailed
        case decodingFailed
        case unhandled(OSStatus)
    }

    private static let service = "com.sendprobe.autolock.credentials"

    static func save<T: Codable>(_ value: T, account: String) throws {
        guard let data = try? JSONEncoder().encode(value) else {
            throw KeychainError.encodingFailed
        }

        let baseQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]

        var attributesToSet: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]

        let updateStatus = SecItemUpdate(baseQuery as CFDictionary, attributesToSet as CFDictionary)

        if updateStatus == errSecItemNotFound {
            attributesToSet.merge(baseQuery) { current, _ in current }
            let addStatus = SecItemAdd(attributesToSet as CFDictionary, nil)
            guard addStatus == errSecSuccess else {
                throw KeychainError.unhandled(addStatus)
            }
        } else if updateStatus != errSecSuccess {
            throw KeychainError.unhandled(updateStatus)
        }
    }

    static func load<T: Codable>(_ type: T.Type, account: String) throws -> T? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        if status == errSecItemNotFound {
            return nil
        }
        guard status == errSecSuccess, let data = result as? Data else {
            throw KeychainError.unhandled(status)
        }
        guard let value = try? JSONDecoder().decode(T.self, from: data) else {
            throw KeychainError.decodingFailed
        }
        return value
    }

    static func delete(account: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.unhandled(status)
        }
    }
}

import Foundation

struct LogEntry: Codable, Identifiable {
    let id: UUID
    let timestamp: Date
    let event: String
    let detail: String

    init(event: String, detail: String = "", timestamp: Date = Date()) {
        self.id = UUID()
        self.timestamp = timestamp
        self.event = event
        self.detail = detail
    }
}

/// Append-only structured log, persisted to a JSON file in the app's
/// Application Support directory so it survives app relaunches and is
/// readable from the Settings screen without Xcode attached.
///
/// Deliberately never logs raw credential values -- callers pass in
/// human-readable summaries (e.g. "auth succeeded", "lock command sent")
/// rather than request/response bodies.
final class LogStore: @unchecked Sendable {
    static let shared = LogStore()

    private let maxEntries = 500
    private let fileURL: URL
    private let queue = DispatchQueue(label: "com.sendprobe.autolock.logstore")

    init() {
        let supportDir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: supportDir, withIntermediateDirectories: true)
        self.fileURL = supportDir.appendingPathComponent("autolock-log.json")
    }

    func append(event: String, detail: String = "") {
        queue.sync {
            var entries = readAllLocked()
            entries.append(LogEntry(event: event, detail: detail))
            if entries.count > maxEntries {
                entries.removeFirst(entries.count - maxEntries)
            }
            writeLocked(entries)
        }
    }

    func all() -> [LogEntry] {
        queue.sync { readAllLocked().sorted { $0.timestamp > $1.timestamp } }
    }

    func clear() {
        queue.sync { writeLocked([]) }
    }

    private func readAllLocked() -> [LogEntry] {
        guard let data = try? Data(contentsOf: fileURL) else { return [] }
        return (try? JSONDecoder().decode([LogEntry].self, from: data)) ?? []
    }

    private func writeLocked(_ entries: [LogEntry]) {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
}

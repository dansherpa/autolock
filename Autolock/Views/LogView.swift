import SwiftUI

struct LogView: View {
    @State private var entries: [LogEntry] = []

    private static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .medium
        return formatter
    }()

    var body: some View {
        List {
            if entries.isEmpty {
                Text("No log entries yet.")
                    .foregroundStyle(.secondary)
            }
            ForEach(entries) { entry in
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.event)
                        .font(.body.weight(.medium))
                    if !entry.detail.isEmpty {
                        Text(entry.detail)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Text(Self.formatter.string(from: entry.timestamp))
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
            }
        }
        .navigationTitle("Logs")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Clear") {
                    LogStore.shared.clear()
                    entries = []
                }
                .disabled(entries.isEmpty)
            }
        }
        .onAppear {
            entries = LogStore.shared.all()
        }
    }
}

#Preview {
    NavigationStack {
        LogView()
    }
}

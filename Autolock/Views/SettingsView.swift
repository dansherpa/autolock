import SwiftUI

struct SettingsView: View {
    @StateObject private var settings = AppSettings.shared

    @State private var username: String = ""
    @State private var password: String = ""
    @State private var pin: String = ""
    @State private var credentialsStored = false
    @State private var statusMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Username / Email", text: $username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("Password", text: $password)
                    SecureField("PIN", text: $pin)
                        .keyboardType(.numberPad)
                } header: {
                    Text("Bluelink Account")
                } footer: {
                    Text(credentialsStored ? "Credentials are saved in the Keychain." : "No credentials saved yet.")
                }

                Section {
                    Button("Save to Keychain") {
                        saveCredentials()
                    }
                    .disabled(username.isEmpty || password.isEmpty || pin.isEmpty)

                    if credentialsStored {
                        Button("Clear Saved Credentials", role: .destructive) {
                            clearCredentials()
                        }
                    }
                }

                Section {
                    Toggle("Dry Run", isOn: $settings.dryRunEnabled)
                } header: {
                    Text("Automation")
                } footer: {
                    Text(settings.dryRunEnabled
                         ? "Dry run is ON: the lock intent will log what it would do instead of calling Bluelink."
                         : "Dry run is OFF: the lock intent will send real commands to your car.")
                }

                Section {
                    Stepper(value: $settings.debounceSeconds, in: 30...600, step: 15) {
                        Text("Cooldown: \(Int(settings.debounceSeconds))s")
                    }
                } footer: {
                    Text("Repeated triggers within this window after a trigger are ignored, so a flaky Wi-Fi blip doesn't spam lock commands.")
                }

                Section {
                    NavigationLink("View Logs") {
                        LogView()
                    }
                }

                if let statusMessage {
                    Section {
                        Text(statusMessage)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Autolock")
            .onAppear(perform: loadCredentials)
        }
    }

    private func loadCredentials() {
        do {
            if let stored = try CredentialsStore.load() {
                username = stored.username
                password = stored.password
                pin = stored.pin
                credentialsStored = true
            } else {
                credentialsStored = false
            }
        } catch {
            statusMessage = "Failed to load credentials: \(error.localizedDescription)"
        }
    }

    private func saveCredentials() {
        let credentials = BluelinkCredentials(username: username, password: password, pin: pin)
        do {
            try CredentialsStore.save(credentials)
            credentialsStored = true
            statusMessage = "Saved."
            LogStore.shared.append(event: "credentials_saved")
        } catch {
            statusMessage = "Failed to save credentials: \(error.localizedDescription)"
        }
    }

    private func clearCredentials() {
        do {
            try CredentialsStore.clear()
            username = ""
            password = ""
            pin = ""
            credentialsStored = false
            statusMessage = "Cleared."
            LogStore.shared.append(event: "credentials_cleared")
        } catch {
            statusMessage = "Failed to clear credentials: \(error.localizedDescription)"
        }
    }
}

#Preview {
    SettingsView()
}

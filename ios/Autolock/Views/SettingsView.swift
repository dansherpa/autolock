import SwiftUI

struct SettingsView: View {
    @StateObject private var settings = AppSettings.shared

    @State private var username: String = ""
    @State private var password: String = ""
    @State private var pin: String = ""
    @State private var credentialsStored = false
    @State private var statusMessage: String?
    @State private var isTestingLock = false
    @State private var testLockResult: String?

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
                    Stepper(value: $settings.preLockDelaySeconds, in: 0...300, step: 15) {
                        Text("Pre-Lock Delay: \(Int(settings.preLockDelaySeconds))s")
                    }
                } footer: {
                    Text("This isn't enforced automatically -- add a matching \"Wait\" action before \"Lock Car\" in your Shortcuts automation. See Set Up Automation below. (A delay inside the app itself gets killed by iOS before it can finish -- that's not something this setting can fix.)")
                }

                Section {
                    Button {
                        runTestLock()
                    } label: {
                        HStack {
                            Text("Test Lock")
                            if isTestingLock {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .disabled(isTestingLock || !credentialsStored)
                } header: {
                    Text("Manual Test")
                } footer: {
                    if let testLockResult {
                        Text(testLockResult)
                    } else {
                        Text(settings.dryRunEnabled
                             ? "Dry run is on, so this will log a simulated attempt rather than calling Bluelink."
                             : "Dry run is off -- this sends a real lock command to your car.")
                    }
                }

                Section {
                    NavigationLink("Set Up Automation") {
                        AutomationSetupView()
                    }
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
            .navigationTitle("Walkaway Lock")
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

    private func runTestLock() {
        isTestingLock = true
        testLockResult = nil
        let dryRun = settings.dryRunEnabled
        Task {
            let result = await LockCarService.lockCar(dryRun: dryRun)
            await MainActor.run {
                testLockResult = description(for: result)
                isTestingLock = false
            }
        }
    }

    private func description(for result: LockCarResult) -> String {
        switch result {
        case .dryRun:
            return "Dry run: logged what would have happened. See View Logs for details."
        case .success:
            return "Lock confirmed successful."
        case .confirmedFailure:
            return "Bluelink reported the lock command failed."
        case .sentUnconfirmed:
            return "Lock command sent, but confirmation was inconclusive. Check the car and View Logs."
        case .skippedNoCredentials:
            return "No credentials saved -- add them above first."
        case .failed(let message):
            return "Failed: \(message)"
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

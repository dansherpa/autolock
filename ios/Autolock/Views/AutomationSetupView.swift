import SwiftUI
import UIKit

private struct SetupStep: Identifiable {
    let id = UUID()
    let title: String
    let detail: String
}

struct AutomationSetupView: View {
    private let steps: [SetupStep] = [
        SetupStep(
            title: "Connect your phone to your car's Wi-Fi (if using Wi-Fi as the trigger)",
            detail: "In iPhone Settings > Wi-Fi, join your car's network at least once. Being connected to CarPlay is not the same thing -- your phone won't join the car's Wi-Fi automatically just because CarPlay is connected. The network name is usually something like \"Hyundai_XXXX\" (check your car's infotainment Wi-Fi Hotspot settings if you're not sure). Skip this step if you're using Bluetooth or CarPlay disconnect instead."
        ),
        SetupStep(
            title: "Open the Shortcuts app",
            detail: "It's a built-in Apple app. If you don't see it, search for \"Shortcuts\" in Spotlight."
        ),
        SetupStep(
            title: "Go to the Automation tab",
            detail: "Tap \"Automation\" at the bottom, then tap the + button in the top corner and choose \"Create Personal Automation\"."
        ),
        SetupStep(
            title: "Choose a trigger for \"leaving the car\"",
            detail: "Wi-Fi disconnect is just one example -- pick whatever signal is most reliable for your car. Other options in Shortcuts include Bluetooth disconnect (if your car pairs as a Bluetooth device) or CarPlay disconnect. Scroll to find the one that matches."
        ),
        SetupStep(
            title: "Configure the trigger to fire on disconnect",
            detail: "For Wi-Fi: tap \"Choose\", pick your car's Wi-Fi network name from the list, then set the trigger to \"Disconnect\" (not Connect). For Bluetooth or CarPlay: choose your car from the device list and set it to \"Disconnect\" the same way. Tap Next."
        ),
        SetupStep(
            title: "Add a \"Wait\" action (recommended)",
            detail: "Tap \"Add Action\", search for \"Wait\" (it's under Scripting), and set it to however many seconds you want -- match the Pre-Lock Delay value in Settings. This gives you time to get out of the car if your trigger fires the instant it's turned off. This has to be a native Shortcuts action; the app can't safely do this delay itself (a silent automation only gets a short window to finish before iOS kills it, and blocking inside the app ate that whole window)."
        ),
        SetupStep(
            title: "Add the \"Lock Car\" action",
            detail: "Tap \"Add Action\" again, search for \"Lock Car\", and select it under Walkaway Lock. Make sure it comes after the Wait action, if you added one. Tap Next."
        ),
        SetupStep(
            title: "Turn off \"Ask Before Running\"",
            detail: "This is the important part -- if it's left on, iOS will ask you to confirm every time, instead of running silently in the background. Turn off \"Ask Before Running\" (and \"Notify When Run\" if shown), then tap Done."
        ),
        SetupStep(
            title: "Test it with Dry Run still on",
            detail: "Leave Dry Run enabled in Settings, then trigger your automation (e.g. walk out of your car's Wi-Fi range). Check View Logs afterward for a \"dry_run_lock\" entry to confirm the automation actually fired. If you get a notification that the automation failed, see the note below."
        ),
        SetupStep(
            title: "Turn off Dry Run when you're confident",
            detail: "Once you've confirmed the automation triggers reliably over a few real park-and-leave cycles, turn off Dry Run in Settings so it sends real lock commands."
        )
    ]

    var body: some View {
        List {
            Section {
                Text("Walkaway Lock can't create this automation for you -- Apple requires it to be set up manually in the Shortcuts app. It only takes a minute.")
                    .foregroundStyle(.secondary)
            }

            Section {
                ForEach(Array(steps.enumerated()), id: \.element.id) { index, step in
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(index + 1). \(step.title)")
                            .font(.headline)
                        Text(step.detail)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            }

            Section {
                Text("If Shortcuts shows a notification that \"Lock Car\" could not run because of an unknown error, try shortening or removing the Wait action. Very long silent automations can run into the same iOS background time limit the Wait step is meant to work around.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } header: {
                Text("Troubleshooting")
            }

            Section {
                Button("Open Shortcuts App") {
                    openShortcutsApp()
                }
            }
        }
        .navigationTitle("Set Up Automation")
    }

    private func openShortcutsApp() {
        guard let url = URL(string: "shortcuts://") else { return }
        UIApplication.shared.open(url)
    }
}

#Preview {
    NavigationStack {
        AutomationSetupView()
    }
}

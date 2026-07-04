import Foundation

enum BluelinkError: Error, CustomStringConvertible {
    case noCredentialsConfigured
    case authenticationFailed(String)
    case noVehicleFound
    case commandFailed(String)
    case unexpectedResponse(String)
    case network(Error)

    var description: String {
        switch self {
        case .noCredentialsConfigured:
            return "No Bluelink credentials configured"
        case .authenticationFailed(let message):
            return "Authentication failed: \(message)"
        case .noVehicleFound:
            return "No enrolled vehicle found on this Bluelink account"
        case .commandFailed(let message):
            return "Command failed: \(message)"
        case .unexpectedResponse(let message):
            return "Unexpected response: \(message)"
        case .network(let error):
            return "Network error: \(error.localizedDescription)"
        }
    }
}

import Foundation

/// Persisted Bluelink session: tokens plus the vehicle identifiers needed to
/// address commands at a specific car. Cached across app relaunches (the
/// background App Intent runs in a fresh process each time) so a normal lock
/// trigger costs a token refresh + one command call rather than a full
/// login + vehicle lookup every time -- both to be fast and to conserve
/// Bluelink's daily rate limit.
struct BluelinkSession: Codable, Equatable {
    var accessToken: String
    var refreshToken: String
    var tokenExpiry: Date
    var vehicle: BluelinkVehicleInfo?

    /// Matches bluelinky's ~10s safety margin before treating a token as
    /// needing refresh.
    func isAccessTokenValid(now: Date = Date()) -> Bool {
        now.addingTimeInterval(10) < tokenExpiry
    }
}

struct BluelinkVehicleInfo: Codable, Equatable {
    let regId: String
    let vin: String
    let generation: Int
    let nickname: String
}

enum BluelinkLockAction: String {
    case lock
    case unlock

    /// Path segment under the API base for this action.
    var endpointPath: String {
        switch self {
        case .lock: return "rcs/rdo/off"
        case .unlock: return "rcs/rdo/on"
        }
    }
}

enum BluelinkOrderStatus {
    case pending
    case success
    case failed
    case unknown
}

// MARK: - Wire response shapes

struct BluelinkTokenResponse: Decodable {
    let accessToken: String
    let refreshToken: String
    let expiresIn: Double
    let errorCode: String?
    let errorMessage: String?

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case expiresIn = "expires_in"
        case errorCode
        case errorMessage
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        accessToken = try container.decodeIfPresent(String.self, forKey: .accessToken) ?? ""
        refreshToken = try container.decodeIfPresent(String.self, forKey: .refreshToken) ?? ""
        errorCode = try container.decodeIfPresent(String.self, forKey: .errorCode)
        errorMessage = try container.decodeIfPresent(String.self, forKey: .errorMessage)

        // expires_in has been observed as both a JSON number and a numeric
        // string across API responses; accept either.
        if let asDouble = try? container.decode(Double.self, forKey: .expiresIn) {
            expiresIn = asDouble
        } else if let asString = try? container.decode(String.self, forKey: .expiresIn),
                  let parsed = Double(asString) {
            expiresIn = parsed
        } else {
            expiresIn = 0
        }
    }
}

struct BluelinkEnrolledVehiclesResponse: Decodable {
    struct Entry: Decodable {
        struct Details: Decodable {
            let regid: String
            let nickName: String
            let vin: String
            let vehicleGeneration: String?
            let enrollmentStatus: String?
        }
        let vehicleDetails: Details
    }
    let enrolledVehicleDetails: [Entry]?
    let errorCode: String?
    let errorMessage: String?
}

struct BluelinkActionStatusResponse: Decodable {
    let status: String?
    let errorCode: String?
    let errorMessage: String?
}

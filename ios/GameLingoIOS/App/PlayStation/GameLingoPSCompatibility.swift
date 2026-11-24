import Foundation

/// chiaki-ng's remote-connection model. Local PS4/PS5 connections are already
/// supported by the native core; PSN Internet hole-punch is an additional mode.
struct RemoteConsoleConnection: Sendable {
    let accessToken: String
    let consoleDUID: Data
    let isPS5: Bool
    let accountID: Data
}


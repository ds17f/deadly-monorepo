import Foundation
import OSLog

/// Pulls recent unified-log entries (filtered by subsystem) out of this process's
/// log store and returns them as plain text suitable for sharing in a bug report.
///
/// Notes:
/// - Only logs at level `.notice` and above are persisted to disk by Apple's unified
///   logging system. `.debug` and `.info` are dropped unless captured live.
/// - Interpolated values in `Logger` calls are redacted as `<private>` in release
///   builds unless explicitly annotated `, privacy: .public`. The playback layer
///   uses `[PB]` tags and `.public` annotations so exports are useful out of the box.
@available(iOS 15.0, macOS 12.0, *)
public enum LogExport {

    public enum Failure: Error {
        case storeUnavailable(String)
        case enumerationFailed(String)
    }

    /// Default subsystems to include. Covers the playback package and the app shell.
    public static let defaultSubsystems: [String] = [
        "SwiftAudioStreamEx",
        "com.grateful.deadly",
    ]

    /// Read the last `duration` seconds of logs for the given subsystems and return
    /// them formatted as plain text. Prefixed with a header describing the environment
    /// so a bug report is self-describing.
    public static func exportRecentLogs(
        subsystems: [String] = defaultSubsystems,
        duration: TimeInterval = 3600,
        filterContains: String? = nil
    ) throws -> String {
        let store: OSLogStore
        do {
            store = try OSLogStore(scope: .currentProcessIdentifier)
        } catch {
            throw Failure.storeUnavailable(error.localizedDescription)
        }

        let startDate = Date(timeIntervalSinceNow: -duration)
        let position = store.position(date: startDate)

        // Build a subsystem predicate. NSPredicate inside OSLogStore supports IN.
        let predicate = NSPredicate(format: "subsystem IN %@", subsystems)

        let entries: AnySequence<OSLogEntry>
        do {
            entries = try store.getEntries(at: position, matching: predicate)
        } catch {
            throw Failure.enumerationFailed(error.localizedDescription)
        }

        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        var lines: [String] = []
        lines.append(header(durationSeconds: duration, subsystems: subsystems))

        var count = 0
        for entry in entries {
            guard let logEntry = entry as? OSLogEntryLog else { continue }
            if let filter = filterContains, !logEntry.composedMessage.contains(filter) {
                continue
            }
            let ts = isoFormatter.string(from: logEntry.date)
            let level = levelString(logEntry.level)
            let category = logEntry.category
            let subsystem = logEntry.subsystem
            lines.append("\(ts) [\(level)] \(subsystem)/\(category) \(logEntry.composedMessage)")
            count += 1
        }
        lines.append("---")
        lines.append("Captured \(count) entries.")
        return lines.joined(separator: "\n")
    }

    // MARK: - Helpers

    private static func levelString(_ level: OSLogEntryLog.Level) -> String {
        switch level {
        case .undefined: return "?"
        case .debug:     return "DEBUG"
        case .info:      return "INFO"
        case .notice:    return "NOTICE"
        case .error:     return "ERROR"
        case .fault:     return "FAULT"
        @unknown default: return "?"
        }
    }

    private static func header(durationSeconds: TimeInterval, subsystems: [String]) -> String {
        let bundle = Bundle.main
        let appName = bundle.object(forInfoDictionaryKey: "CFBundleName") as? String ?? "deadly"
        let version = bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        let build = bundle.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
        let bundleID = bundle.bundleIdentifier ?? "?"
        let now = ISO8601DateFormatter().string(from: Date())

        #if os(iOS)
        let osVersion = "iOS \(ProcessInfo.processInfo.operatingSystemVersionString)"
        let device = deviceModel()
        #else
        let osVersion = ProcessInfo.processInfo.operatingSystemVersionString
        let device = "macOS"
        #endif

        return """
        === Deadly Bug Report ===
        Generated: \(now)
        App: \(appName) \(version) (\(build))
        Bundle: \(bundleID)
        OS: \(osVersion)
        Device: \(device)
        Window: last \(Int(durationSeconds))s
        Subsystems: \(subsystems.joined(separator: ", "))
        ---
        """
    }

    #if os(iOS)
    private static func deviceModel() -> String {
        var sysinfo = utsname()
        uname(&sysinfo)
        let machineMirror = Mirror(reflecting: sysinfo.machine)
        let identifier = machineMirror.children.reduce(into: "") { result, element in
            if let value = element.value as? Int8, value != 0 {
                result.append(Character(UnicodeScalar(UInt8(value))))
            }
        }
        return identifier.isEmpty ? "iOS" : identifier
    }
    #endif
}

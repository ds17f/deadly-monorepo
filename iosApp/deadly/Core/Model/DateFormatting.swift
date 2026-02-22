import Foundation

/// Shared date formatting for show dates in YYYY-MM-DD format.
enum DateFormatting {
    enum Style {
        case short  // "Jan 8, 1977"
        case long   // "January 8, 1977"
    }

    static func formatShowDate(_ date: String, style: Style = .long) -> String {
        let parts = date.split(separator: "-")
        guard parts.count == 3,
              let month = Int(parts[1]),
              let day = Int(parts[2]),
              month >= 1, month <= 12 else {
            return date
        }
        let year = String(parts[0])
        let monthName: String
        switch style {
        case .short:
            let names = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
            monthName = names[month - 1]
        case .long:
            let names = ["January","February","March","April","May","June",
                         "July","August","September","October","November","December"]
            monthName = names[month - 1]
        }
        return "\(monthName) \(day), \(year)"
    }
}

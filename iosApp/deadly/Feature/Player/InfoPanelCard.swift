import SwiftUI

/// Generic text panel card used for lyrics and venue info.
struct InfoPanelCard: View {
    let title: String
    let content: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
                .foregroundStyle(.primary)

            Text(content)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
    }
}

/// Panel card listing lineup members.
struct CreditsPanelCard: View {
    let members: [LineupMember]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Band")
                .font(.headline)
                .foregroundStyle(.primary)

            VStack(alignment: .leading, spacing: 6) {
                ForEach(members, id: \.name) { member in
                    HStack(alignment: .top, spacing: 4) {
                        Text(member.name)
                            .fontWeight(.medium)
                        if !member.instruments.isEmpty {
                            Text("—")
                            Text(member.instruments)
                        }
                    }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
    }
}

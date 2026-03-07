import SwiftUI

struct ShowReviewSheet: View {
    let showDate: String
    let venue: String
    let location: String
    let review: ShowReview
    let lineupMembers: [String]
    let currentRecordingId: String?
    let bestRecordingId: String?
    let onSave: (_ notes: String?, _ rating: Double?, _ recordingQuality: Int?, _ playingQuality: Int?, _ standoutPlayers: [String]) -> Void
    var onDelete: (() -> Void)? = nil

    @Environment(\.dismiss) private var dismiss

    @State private var notes: String
    @State private var overallRating: Int
    @State private var recordingQuality: Int
    @State private var playingQuality: Int
    @State private var standoutPlayers: Set<String>
    @State private var showDeleteConfirmation = false

    init(showDate: String, venue: String, location: String, review: ShowReview,
         lineupMembers: [String],
         currentRecordingId: String? = nil,
         bestRecordingId: String? = nil,
         onSave: @escaping (_ notes: String?, _ rating: Double?, _ recordingQuality: Int?, _ playingQuality: Int?, _ standoutPlayers: [String]) -> Void,
         onDelete: (() -> Void)? = nil) {
        self.showDate = showDate
        self.venue = venue
        self.location = location
        self.review = review
        self.lineupMembers = lineupMembers
        self.currentRecordingId = currentRecordingId
        self.bestRecordingId = bestRecordingId
        self.onSave = onSave
        self.onDelete = onDelete
        _notes = State(initialValue: review.notes ?? "")
        _overallRating = State(initialValue: review.overallRating.map { Int($0) } ?? 0)
        _recordingQuality = State(initialValue: review.recordingQuality ?? 0)
        _playingQuality = State(initialValue: review.playingQuality ?? 0)
        _standoutPlayers = State(initialValue: Set(review.playerTags.filter(\.isStandout).map(\.playerName)))
    }

    var body: some View {
        NavigationStack {
            Form {
                // Header
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(showDate).font(.headline)
                        Text(venue).font(.subheadline).foregroundStyle(.secondary)
                        Text(location).font(.caption).foregroundStyle(.secondary)
                    }
                }

                // Ratings
                Section("Ratings") {
                    StarRatingRow(label: "Overall", rating: $overallRating)
                    StarRatingRow(label: "Recording", rating: $recordingQuality)
                    if let displayId = currentRecordingId ?? review.reviewedRecordingId {
                        HStack {
                            Text("Recording: \(String(displayId.suffix(12)))")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                            if let best = bestRecordingId, displayId != best {
                                Text("(non-default)")
                                    .font(.caption2)
                                    .foregroundStyle(.tertiary)
                            }
                        }
                    }
                    StarRatingRow(label: "Playing", rating: $playingQuality)
                }

                // Standout players
                if !lineupMembers.isEmpty {
                    Section("Standout Players") {
                        FlowLayout(spacing: 8) {
                            ForEach(lineupMembers, id: \.self) { member in
                                let selected = standoutPlayers.contains(member)
                                Button {
                                    if selected {
                                        standoutPlayers.remove(member)
                                    } else {
                                        standoutPlayers.insert(member)
                                    }
                                } label: {
                                    Text(member)
                                        .font(.callout)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(selected ? DeadlyColors.primary.opacity(0.2) : Color.secondary.opacity(0.1))
                                        .foregroundStyle(selected ? DeadlyColors.primary : .primary)
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }

                // Notes
                Section("Notes") {
                    TextEditor(text: $notes)
                        .frame(minHeight: 100)
                }

                // Delete
                if onDelete != nil && review.hasContent {
                    Section {
                        Button(role: .destructive) {
                            showDeleteConfirmation = true
                        } label: {
                            Label("Delete Review", systemImage: "trash")
                        }
                    }
                }
            }
            .navigationTitle("Review")
            .alert("Delete Review", isPresented: $showDeleteConfirmation) {
                Button("Cancel", role: .cancel) { }
                Button("Delete", role: .destructive) {
                    onDelete?()
                    dismiss()
                }
            } message: {
                Text("This will permanently delete your review for this show.")
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(
                            notes.isEmpty ? nil : notes,
                            overallRating > 0 ? Double(overallRating) : nil,
                            recordingQuality > 0 ? recordingQuality : nil,
                            playingQuality > 0 ? playingQuality : nil,
                            Array(standoutPlayers)
                        )
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - Star Rating Row

private struct StarRatingRow: View {
    let label: String
    @Binding var rating: Int

    var body: some View {
        HStack {
            Text(label)
            Spacer()
            HStack(spacing: 4) {
                ForEach(1...5, id: \.self) { star in
                    Image(systemName: star <= rating ? "star.fill" : "star")
                        .foregroundStyle(star <= rating ? DeadlyColors.secondary : .secondary)
                        .onTapGesture {
                            rating = rating == star ? 0 : star
                        }
                }
            }
        }
    }
}

// MARK: - Flow Layout

private struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = arrange(proposal: proposal, subviews: subviews)
        return result.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(proposal: ProposedViewSize(width: bounds.width, height: bounds.height), subviews: subviews)
        for (index, position) in result.positions.enumerated() {
            subviews[index].place(at: CGPoint(x: bounds.minX + position.x, y: bounds.minY + position.y), proposal: .unspecified)
        }
    }

    private func arrange(proposal: ProposedViewSize, subviews: Subviews) -> (size: CGSize, positions: [CGPoint]) {
        let maxWidth = proposal.width ?? .infinity
        var positions: [CGPoint] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var maxX: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > maxWidth && x > 0 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            positions.append(CGPoint(x: x, y: y))
            rowHeight = max(rowHeight, size.height)
            x += size.width + spacing
            maxX = max(maxX, x - spacing)
        }

        return (CGSize(width: maxX, height: y + rowHeight), positions)
    }
}

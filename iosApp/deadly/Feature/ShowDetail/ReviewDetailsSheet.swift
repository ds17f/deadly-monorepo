import SwiftUI

struct ReviewDetailsSheet: View {
    let show: Show
    let playlistService: PlaylistServiceImpl
    var userReview: ShowReview? = nil
    var onWriteReview: (() -> Void)? = nil

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    headerSection
                    ratingSummaryCard
                    if let review = userReview, review.hasContent {
                        userReviewSummary(review)
                    }
                    if let onWriteReview {
                        let hasReview = userReview?.hasContent == true
                        Button {
                            dismiss()
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                                onWriteReview()
                            }
                        } label: {
                            Text(hasReview ? "Edit Review" : "Write Review")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    reviewsSection
                }
                .padding()
            }
            .navigationTitle("Reviews")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(DateFormatting.formatShowDate(show.date))
                .font(.headline)
            Text("\(show.venue.name), \(show.location.displayText)")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Rating Summary Card

    private var ratingSummaryCard: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                VStack(spacing: 4) {
                    Text(show.hasRating
                         ? String(format: "%.1f", show.averageRating!)
                         : "N/A")
                        .font(.largeTitle).fontWeight(.bold)
                    CompactStarRating(rating: show.averageRating, starSize: 20)
                    Text("\(playlistService.reviews.count) ratings")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(minWidth: 100)

                VStack(spacing: 4) {
                    ForEach((1...5).reversed(), id: \.self) { star in
                        distributionRow(star: star)
                    }
                }
            }
        }
        .padding()
        .background(Color(.systemGray6).opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
    }

    private func distributionRow(star: Int) -> some View {
        let count = playlistService.reviews.filter { $0.rating == star }.count
        let total = playlistService.reviews.count
        let fraction = total > 0 ? Double(count) / Double(total) : 0

        return HStack(spacing: 4) {
            Text("\(star)")
                .font(.caption2)
                .frame(width: 12, alignment: .trailing)
            Image(systemName: "star.fill")
                .font(.caption2)
                .foregroundStyle(DeadlyColors.secondary)
            ProgressView(value: fraction)
                .tint(DeadlyColors.secondary)
            Text("\(count)")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .frame(width: 20, alignment: .trailing)
        }
    }

    // MARK: - User Review Summary

    private func userReviewSummary(_ review: ShowReview) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Your Review")
                .font(.subheadline)
                .fontWeight(.bold)

            if let rating = review.overallRating {
                HStack(spacing: 8) {
                    CompactStarRating(rating: Float(rating), starSize: 14)
                    Text(String(format: "%.1f", rating))
                        .font(.subheadline)
                        .fontWeight(.medium)
                }
            }

            if let notes = review.notes, !notes.isEmpty {
                Text(notes)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(3)
            }

            let qualityParts: [String] = [
                review.recordingQuality.map { "Rec: \($0)/5" },
                review.playingQuality.map { "Play: \($0)/5" }
            ].compactMap { $0 }
            if !qualityParts.isEmpty {
                Text(qualityParts.joined(separator: "  "))
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DeadlyColors.primary.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
    }

    // MARK: - Reviews Section

    @ViewBuilder
    private var reviewsSection: some View {
        Text("Individual Reviews")
            .font(.headline)
            .padding(.top, 4)

        if playlistService.isLoadingReviews {
            HStack {
                Spacer()
                ProgressView("Loading reviews…")
                Spacer()
            }
            .padding(.vertical, 24)
        } else if let error = playlistService.reviewsError {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.red)
                Text(error)
                    .font(.subheadline)
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.red.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
        } else if playlistService.reviews.isEmpty {
            Text("No reviews available for this recording yet.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .padding(.vertical, 8)
        } else {
            ForEach(Array(playlistService.reviews.enumerated()), id: \.offset) { _, review in
                ReviewItemCard(review: review)
            }
        }
    }
}

// MARK: - Review Item Card

private struct ReviewItemCard: View {
    let review: Review

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(review.reviewer ?? "Anonymous")
                    .font(.subheadline)
                    .fontWeight(.medium)

                Spacer()

                if let rating = review.rating {
                    CompactStarRating(rating: Float(rating), starSize: 12)
                }

                if let dateText = formattedDate {
                    Text(dateText)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            if let title = review.title, !title.isEmpty {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.semibold)
            }

            if let body = review.body, !body.isEmpty {
                Text(body)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .background(Color(.systemGray6).opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
    }

    private var formattedDate: String? {
        guard let dateString = review.reviewDate else { return nil }
        // Archive.org format: "2023-05-15 12:00:00" — take the date part
        let datePart = dateString.split(separator: " ").first.map(String.init) ?? dateString
        return DateFormatting.formatShowDate(datePart, style: .short)
    }
}

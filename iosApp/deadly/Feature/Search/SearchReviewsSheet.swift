import SwiftUI

struct SearchReviewsSheet: View {
    let show: Show
    let archiveClient: any ArchiveMetadataClient

    @Environment(\.dismiss) private var dismiss
    @State private var reviews: [Review] = []
    @State private var isLoading = true
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    headerSection
                    ratingSummaryCard
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
        .task {
            guard let recordingId = show.bestRecordingId else {
                isLoading = false
                errorMessage = "No recording available."
                return
            }
            do {
                reviews = try await archiveClient.fetchReviews(recordingId: recordingId)
                isLoading = false
            } catch {
                isLoading = false
                errorMessage = error.localizedDescription
            }
        }
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
                        .font(.system(size: 48, weight: .bold))
                    CompactStarRating(rating: show.averageRating, starSize: 20)
                    Text("\(reviews.count) ratings")
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
        let count = reviews.filter { $0.rating == star }.count
        let total = reviews.count
        let fraction = total > 0 ? Double(count) / Double(total) : 0

        return HStack(spacing: 4) {
            Text("\(star)")
                .font(.caption2)
                .frame(width: 12, alignment: .trailing)
            Image(systemName: "star.fill")
                .font(.system(size: 8))
                .foregroundStyle(DeadlyColors.secondary)
            ProgressView(value: fraction)
                .tint(DeadlyColors.secondary)
            Text("\(count)")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .frame(width: 20, alignment: .trailing)
        }
    }

    // MARK: - Reviews Section

    @ViewBuilder
    private var reviewsSection: some View {
        Text("Individual Reviews")
            .font(.headline)
            .padding(.top, 4)

        if isLoading {
            HStack {
                Spacer()
                ProgressView("Loading reviews…")
                Spacer()
            }
            .padding(.vertical, 24)
        } else if let error = errorMessage {
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
        } else if reviews.isEmpty {
            Text("No reviews available for this recording yet.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .padding(.vertical, 8)
        } else {
            ForEach(Array(reviews.enumerated()), id: \.offset) { _, review in
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
        let datePart = dateString.split(separator: " ").first.map(String.init) ?? dateString
        return DateFormatting.formatShowDate(datePart, style: .short)
    }
}

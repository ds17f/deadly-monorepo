import SwiftUI

// MARK: - Active Download Row

struct ActiveDownloadRow: View {
    let show: Show
    let progress: ShowDownloadProgress
    let onPause: () -> Void
    let onCancel: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            ShowArtwork(
                recordingId: show.bestRecordingId,
                imageUrl: show.coverImageUrl,
                size: 60,
                cornerRadius: 8
            )

            VStack(alignment: .leading, spacing: 4) {
                Text(DateFormatting.formatShowDate(show.date, style: .short))
                    .font(.subheadline)
                    .fontWeight(.medium)

                Text(show.venue.name)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                HStack(spacing: 8) {
                    ProgressView(value: progress.overallProgress)
                        .tint(DeadlyColors.primary)

                    Text("\(progress.tracksCompleted)/\(progress.tracksTotal)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            HStack(spacing: 8) {
                Button {
                    onPause()
                } label: {
                    Image(systemName: "pause.fill")
                        .font(.body)
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)

                Button {
                    onCancel()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.body)
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Paused Download Row

struct PausedDownloadRow: View {
    let show: Show
    let progress: ShowDownloadProgress
    let onResume: () -> Void
    let onCancel: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            ShowArtwork(
                recordingId: show.bestRecordingId,
                imageUrl: show.coverImageUrl,
                size: 60,
                cornerRadius: 8
            )

            VStack(alignment: .leading, spacing: 4) {
                Text(DateFormatting.formatShowDate(show.date, style: .short))
                    .font(.subheadline)
                    .fontWeight(.medium)

                Text(show.venue.name)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                HStack(spacing: 8) {
                    ProgressView(value: progress.overallProgress)
                        .tint(.gray)

                    Text("Paused")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            HStack(spacing: 8) {
                Button {
                    onResume()
                } label: {
                    Image(systemName: "play.fill")
                        .font(.body)
                        .foregroundStyle(DeadlyColors.primary)
                }
                .buttonStyle(.plain)

                Button {
                    onCancel()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.body)
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Completed Download Row

struct CompletedDownloadRow: View {
    let show: Show
    let storageUsed: Int64
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            ShowArtwork(
                recordingId: show.bestRecordingId,
                imageUrl: show.coverImageUrl,
                size: 60,
                cornerRadius: 8
            )

            VStack(alignment: .leading, spacing: 4) {
                Text(DateFormatting.formatShowDate(show.date, style: .short))
                    .font(.subheadline)
                    .fontWeight(.medium)

                Text(show.venue.name)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Text(ByteCountFormatter.string(fromByteCount: storageUsed, countStyle: .file))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Image(systemName: "checkmark.circle.fill")
                .font(.title3)
                .foregroundStyle(DeadlyColors.primary)

            Button {
                onRemove()
            } label: {
                Image(systemName: "trash")
                    .font(.body)
                    .foregroundStyle(.red)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 4)
    }
}

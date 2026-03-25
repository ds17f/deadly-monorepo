import SwiftUI

// MARK: - Era Section

struct EraSection: View {
    var onSelectEra: (String) -> Void

    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("By Decade")
                .font(.title3)
                .fontWeight(.bold)
                .padding(.horizontal, DeadlySpacing.screenPadding)

            LazyVGrid(columns: columns, spacing: DeadlySpacing.gridSpacing) {
                eraButton("60s", imageName: "decade_1960s")
                eraButton("70s", imageName: "decade_1970s")
                eraButton("80s", imageName: "decade_1980s")
                eraButton("90s", imageName: "decade_1990s")
            }
            .padding(.horizontal, DeadlySpacing.screenPadding)
        }
    }

    private func eraButton(_ decade: String, imageName: String) -> some View {
        Button {
            onSelectEra(decade)
        } label: {
            Image(imageName)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(maxWidth: .infinity)
                .frame(height: 80)
                .clipShape(RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Discover Section

struct DiscoverSection: View {
    var refreshCounter: Int = 0
    var onSelectShortcut: (String) -> Void

    private let gradients: [[Color]] = [
        [Color(hex: "1976D2"), Color(hex: "42A5F5")],
        [Color(hex: "388E3C"), Color(hex: "66BB6A")],
        [Color(hex: "D32F2F"), Color(hex: "EF5350")],
        [Color(hex: "7B1FA2"), Color(hex: "AB47BC")],
        [Color(hex: "E64A19"), Color(hex: "FF7043")],
        [Color(hex: "00796B"), Color(hex: "26A69A")],
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Discover")
                .font(.title3)
                .fontWeight(.bold)
                .padding(.horizontal, DeadlySpacing.screenPadding)

            HStack(spacing: DeadlySpacing.gridSpacing) {
                ForEach(Array(discoverShortcuts(refreshCounter: refreshCounter).enumerated()), id: \.element.id) { index, shortcut in
                    discoverCard(shortcut, gradientIndex: index)
                }
            }
            .padding(.horizontal, DeadlySpacing.screenPadding)
        }
    }

    private func discoverCard(_ shortcut: SearchShortcut, gradientIndex: Int) -> some View {
        let gradient = gradients[gradientIndex % gradients.count]

        return Button {
            onSelectShortcut(shortcut.searchQuery)
        } label: {
            ZStack(alignment: .topTrailing) {
                LinearGradient(
                    colors: gradient,
                    startPoint: .top,
                    endPoint: .bottom
                )

                Image("deadly_logo")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 40, height: 40)
                    .opacity(0.2)
                    .padding(8)

                VStack(alignment: .leading, spacing: 4) {
                    Spacer()
                    Text(shortcut.title)
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundStyle(.white)
                        .lineLimit(2)
                    Text(shortcut.subtitle)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.8))
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
            }
            .frame(height: 220)
            .clipShape(RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Browse All Section

struct BrowseAllSection: View {
    var refreshCounter: Int = 0
    var onSelectShortcut: (String) -> Void

    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    private let colors: [Color] = [
        Color(hex: "1976D2"),
        Color(hex: "388E3C"),
        Color(hex: "D32F2F"),
        Color(hex: "7B1FA2"),
        Color(hex: "E64A19"),
        Color(hex: "00796B"),
        Color(hex: "5D4037"),
        Color(hex: "455A64"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Browse All")
                .font(.title3)
                .fontWeight(.bold)
                .padding(.horizontal, DeadlySpacing.screenPadding)

            LazyVGrid(columns: columns, spacing: DeadlySpacing.gridSpacing) {
                ForEach(Array(browseShortcuts(refreshCounter: refreshCounter).enumerated()), id: \.element.id) { index, shortcut in
                    browseCard(shortcut, colorIndex: index)
                }
            }
            .padding(.horizontal, DeadlySpacing.screenPadding)
        }
    }

    private func browseCard(_ shortcut: SearchShortcut, colorIndex: Int) -> some View {
        let backgroundColor = colors[colorIndex % colors.count]

        return Button {
            onSelectShortcut(shortcut.searchQuery)
        } label: {
            VStack(spacing: 6) {
                Spacer()
                Text(shortcut.title)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)

                Text(shortcut.subtitle)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.7))
                    .lineLimit(1)
                Spacer()
            }
            .frame(maxWidth: .infinity)
            .frame(height: 120)
            .background(
                backgroundColor,
                in: RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius)
            )
        }
        .buttonStyle(.plain)
    }
}

import SwiftUI

// MARK: - Data structures

struct FilterNode: Equatable {
    let id: String
    let label: String
    let children: [FilterNode]

    var isLeaf: Bool { children.isEmpty }

    init(id: String, label: String, children: [FilterNode] = []) {
        self.id = id
        self.label = label
        self.children = children
    }

    static func decadeCascadeTree() -> [FilterNode] {
        [
            FilterNode(
                id: "60s", label: "60s",
                children: (1965...1969).map { FilterNode(id: "\($0)", label: "\($0)") }
            ),
            FilterNode(
                id: "70s", label: "70s",
                children: [
                    FilterNode(
                        id: "early_70s", label: "Early 70s",
                        children: (1970...1974).map { FilterNode(id: "\($0)", label: "\($0)") }
                    ),
                    FilterNode(
                        id: "late_70s", label: "Late 70s",
                        children: (1975...1979).map { FilterNode(id: "\($0)", label: "\($0)") }
                    ),
                ]
            ),
            FilterNode(
                id: "80s", label: "80s",
                children: [
                    FilterNode(
                        id: "early_80s", label: "Early 80s",
                        children: (1980...1984).map { FilterNode(id: "\($0)", label: "\($0)") }
                    ),
                    FilterNode(
                        id: "late_80s", label: "Late 80s",
                        children: (1985...1989).map { FilterNode(id: "\($0)", label: "\($0)") }
                    ),
                ]
            ),
            FilterNode(
                id: "90s", label: "90s",
                children: (1990...1995).map { FilterNode(id: "\($0)", label: "\($0)") }
            ),
        ]
    }
}

struct FilterPath: Equatable {
    var nodes: [FilterNode] = []

    var isNotEmpty: Bool { !nodes.isEmpty }
    var isEmpty: Bool { nodes.isEmpty }

    var displayText: String {
        switch nodes.count {
        case 0: return ""
        case 1: return nodes[0].label
        default:
            let rest = nodes.dropFirst().map(\.label).joined(separator: " | ")
            return "[\(nodes[0].label)] \(rest)"
        }
    }
}

// MARK: - View

struct HierarchicalFilterChips: View {
    let filterTree: [FilterNode]
    @Binding var selectedPath: FilterPath

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                // "All" chip
                chipButton(label: "All", isSelected: selectedPath.isEmpty) {
                    selectedPath = FilterPath()
                }

                if selectedPath.isEmpty {
                    // Root level
                    ForEach(filterTree, id: \.id) { node in
                        chipButton(label: node.label, isSelected: false) {
                            selectedPath = FilterPath(nodes: [node])
                        }
                    }
                } else if selectedPath.nodes.last!.isLeaf {
                    // Leaf — combined chip
                    combinedChip
                } else {
                    // Intermediate — show selected highlighted + children
                    let deepest = selectedPath.nodes.last!
                    chipButton(label: deepest.label, isSelected: true) {
                        selectedPath = FilterPath(nodes: Array(selectedPath.nodes.dropLast()))
                    }
                    ForEach(deepest.children, id: \.id) { child in
                        chipButton(label: child.label, isSelected: false) {
                            selectedPath = FilterPath(nodes: selectedPath.nodes + [child])
                        }
                    }
                }
            }
            .padding(.horizontal, DeadlySpacing.screenPadding)
        }
        .contentMargins(.bottom, 0, for: .scrollContent)
        .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - Chip views

    private func chipButton(label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.subheadline)
                .fontWeight(.medium)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(isSelected ? Color.red : Color(.systemGray5))
                .foregroundStyle(isSelected ? .white : .primary)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private var combinedChip: some View {
        Button {
            selectedPath = FilterPath(nodes: Array(selectedPath.nodes.dropLast()))
        } label: {
            HStack(spacing: 4) {
                ForEach(Array(selectedPath.nodes.enumerated()), id: \.element.id) { index, node in
                    Text(node.label)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundStyle(.white)
                    if index < selectedPath.nodes.count - 1 {
                        Text("|")
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.6))
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(Color.red)
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

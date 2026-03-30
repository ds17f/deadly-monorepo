import SwiftUI

struct MissionView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Deadly makes it easy to discover and enjoy live concert recordings from the Internet Archive.")

                Text("The goal is to make listening to live shows as easy and enjoyable as possible in a modern streaming experience — freely, non-commercially, and in community.")

                Text("This app is completely open source. Anyone can inspect the code, contribute improvements, or build upon it.")

                Text("No money is made from streaming music through this app.")
            }
            .font(.body)
            .lineSpacing(5)
            .foregroundStyle(.primary)
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
        }
        .navigationTitle("Our Mission")
        .navigationBarTitleDisplayMode(.large)
    }
}

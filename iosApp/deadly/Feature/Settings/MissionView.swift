import SwiftUI

struct MissionView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("We built this app for one simple reason: we want to make it easy to explore, enjoy, and share live music from the Internet Archive's vast collection of concert recordings.")

                Text("The goal is to make listening to live shows as easy and enjoyable as possible in a modern streaming experience. We have deep respect for the long-standing tradition of taping and sharing live music — freely, non-commercially, and in community.")

                Text("This app is completely open source. Anyone can inspect the code, contribute improvements, or build upon it.")

                Text("No money is made from streaming music through this app. It exists because one fan wanted a modern way to listen to the music he loves.")
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

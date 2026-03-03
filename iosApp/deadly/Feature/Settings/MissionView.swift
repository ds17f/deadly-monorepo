import SwiftUI

struct MissionView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("We built this app for one simple reason: we want to encourage Deadheads — old and new — to engage with, enjoy, and share the music of the Grateful Dead.")

                Text("The goal is to make listening to live shows as easy and enjoyable as possible in a modern streaming experience. We have deep respect for the spirit of the band and the long-standing belief that this music is meant to be shared — freely, non-commercially, and in community.")

                Text("This app is completely open source. Anyone can inspect the code, contribute improvements, or build upon it.")

                Text("No money is made from streaming music through this app. It exists because one Deadhead wanted a modern way to listen to his favorite band.")
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

import SwiftUI

struct MissionView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("We built this app to make the Internet Archive's Live Music Archive accessible to everyone. Browse and stream thousands of live concert recordings from artists who embrace the taping and sharing tradition — all freely available and non-commercial.")

                Text("The Live Music Archive is home to recordings from the Grateful Dead, their side projects, tribute bands, and many other artists who allow audience taping. This app provides a modern streaming experience for exploring this incredible collection.")

                Text("This app is completely open source. Anyone can inspect the code, contribute improvements, or build upon it.")

                Text("No money is made from streaming music through this app. All content is provided by and streamed directly from the Internet Archive (archive.org).")
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

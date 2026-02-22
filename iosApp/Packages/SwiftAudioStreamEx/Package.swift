// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "SwiftAudioStreamEx",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SwiftAudioStreamEx", targets: ["SwiftAudioStreamEx"]),
    ],
    dependencies: [
        .package(url: "https://github.com/dimitris-c/AudioStreaming.git", from: "1.4.4"),
    ],
    targets: [
        .target(
            name: "SwiftAudioStreamEx",
            dependencies: ["AudioStreaming"]
        ),
        .testTarget(
            name: "SwiftAudioStreamExTests",
            dependencies: ["SwiftAudioStreamEx"]
        ),
    ]
)

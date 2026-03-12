import SwiftUI

struct ShareChooserSheet: View {
    @Binding var attachImage: Bool
    let onMessageShare: () -> Void
    let onQrShare: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                List {
                    Section {
                        Button {
                            dismiss()
                            onMessageShare()
                        } label: {
                            Label {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Message")
                                        .foregroundStyle(.primary)
                                    Text("Text with link")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            } icon: {
                                Image(systemName: "message")
                                    .foregroundStyle(.primary)
                            }
                        }

                        Button {
                            dismiss()
                            onQrShare()
                        } label: {
                            Label {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("QR Code")
                                        .foregroundStyle(.primary)
                                    Text("Scannable poster")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            } icon: {
                                Image(systemName: "qrcode")
                                    .foregroundStyle(.primary)
                            }
                        }
                    }

                    Section {
                        Toggle(isOn: $attachImage) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Attach image")
                                Text("Included with message")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Share")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.height(320)])
    }
}

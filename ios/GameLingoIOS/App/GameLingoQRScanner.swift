import SwiftUI
import VisionKit
import Vision
import AVFoundation

/// Local live QR recognition; no camera image storage or upload.
struct GameLingoQRScanner: UIViewControllerRepresentable {
    var onCode: (PairingQRCode) -> Void
    var onFailure: () -> Void
    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }
    func makeUIViewController(context: Context) -> DataScannerViewController {
        let scanner = DataScannerViewController(recognizedDataTypes: [.barcode(symbologies: [.qr])],
            qualityLevel: .balanced, recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: false, isPinchToZoomEnabled: true,
            isGuidanceEnabled: true, isHighlightingEnabled: true)
        scanner.delegate = context.coordinator
        do { try scanner.startScanning() } catch {
            DispatchQueue.main.async { onFailure() }
        }
        return scanner
    }
    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {
        context.coordinator.parent = self
    }
    static func dismantleUIViewController(_ controller: DataScannerViewController, coordinator: Coordinator) {
        controller.stopScanning()
    }
    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        var parent: GameLingoQRScanner
        private var delivered = false
        init(parent: GameLingoQRScanner) { self.parent = parent }
        func dataScanner(_ scanner: DataScannerViewController, didAdd addedItems: [RecognizedItem],
                         allItems: [RecognizedItem]) {
            guard !delivered else { return }
            for item in addedItems {
                if case .barcode(let barcode) = item,
                   let payload = barcode.payloadStringValue,
                   let code = PairingQRCode.parse(payload) {
                    delivered = true
                    scanner.stopScanning()
                    parent.onCode(code)
                    return
                }
            }
        }
        func dataScanner(_ scanner: DataScannerViewController,
                         becameUnavailableWithError error: DataScannerViewController.ScanningUnavailable) {
            guard !delivered else { return }
            delivered = true
            parent.onFailure()
        }
    }
}

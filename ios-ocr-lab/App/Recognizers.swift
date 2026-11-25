import UIKit
import Vision
import MLKitVision
import MLKitTextRecognition

final class Recognizers {
    private let ml = TextRecognizer.textRecognizer(options: TextRecognizerOptions())
    func read(_ engine: String, _ image: UIImage) throws -> String {
        if engine == "mlkit" {
            let input = VisionImage(image: image)
            input.orientation = .up
            return try ml.results(in: input).text
        }
        guard let cg = image.cgImage else { throw NSError(domain: "OCRLab", code: 1) }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = ["en-US"]
        request.usesLanguageCorrection = true
        try VNImageRequestHandler(cgImage: cg, orientation: .up, options: [:]).perform([request])
        return (request.results ?? []).compactMap { $0.topCandidates(1).first?.string }.joined(separator: "\n")
    }
}

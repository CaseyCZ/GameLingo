import XCTest
import UIKit
@testable import IOSOCRLab

final class OCRTests: XCTestCase {
    struct Sample: Decodable {
        let id: String
        let dataset: String
        let scene: String
        let expected: String
    }
    struct Row: Encodable {
        let id: String
        let dataset: String
        let scene: String
        let expected: String
        let engine: String
        let config: String
        let actual: String
        let ocr_ms: Double
        let resize_ms: Double
    }
    func testBenchmark() throws {
        // XCTest executes this body on the main thread; ML Kit synchronous results
        // explicitly require a background thread. Run both engines serially there.
        let done = expectation(description: "Both actual OCR engines")
        DispatchQueue.global(qos: .userInitiated).async {
            defer { done.fulfill() }
            do { try self.runBenchmark() }
            catch { XCTFail("OCR benchmark failed: \(error)") }
        }
        wait(for: [done], timeout: 2400)
    }
    private func runBenchmark() throws {
        let bundle = Bundle(for: Self.self)
        let manifest = try XCTUnwrap(bundle.url(forResource: "manifest", withExtension: "json", subdirectory: "assets"))
        let all = try JSONDecoder().decode([Sample].self, from: Data(contentsOf: manifest))
        let samples = all.filter { $0.dataset == "public" }
        XCTAssertEqual(samples.count, 24)
        let engines = Recognizers()
        let encoder = JSONEncoder()
        let filters = ["raw","gray","contrast","binary","white","outline","adaptive","darkbox","sharpen"]
        let configurations = filters.map { ($0,100) } + [75,125,150,200].map { ("raw",$0) }
        for engine in ["vision","mlkit"] {
            let smokeURL = try XCTUnwrap(bundle.url(forResource: "synthetic_006_raw", withExtension: "png", subdirectory: "assets"))
            let smoke = try XCTUnwrap(UIImage(contentsOfFile: smokeURL.path))
            let recognized = try engines.read(engine, smoke)
            XCTAssertTrue(recognized.lowercased().contains("one chance"), "\(engine) smoke: \(recognized)")
            print("IOS_SMOKE \(engine) \(recognized)")
        }
        for sample in samples {
            for (filter,scale) in configurations {
                try autoreleasepool {
                    let url = try XCTUnwrap(bundle.url(forResource: "\(sample.id)_\(filter)", withExtension: "png", subdirectory: "assets"))
                    let original = try XCTUnwrap(UIImage(contentsOfFile: url.path))
                    let start = ProcessInfo.processInfo.systemUptime
                    let image: UIImage
                    if scale == 100 { image = original }
                    else {
                        let size = CGSize(width: max(1, (original.size.width * CGFloat(scale) / 100).rounded()),
                                          height: max(1, (original.size.height * CGFloat(scale) / 100).rounded()))
                        let format = UIGraphicsImageRendererFormat()
                        format.scale = 1
                        format.opaque = true
                        image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
                            original.draw(in: CGRect(origin: .zero, size: size))
                        }
                    }
                    let resize = (ProcessInfo.processInfo.systemUptime - start) * 1000
                    for engine in ["vision","mlkit"] {
                        let begin = ProcessInfo.processInfo.systemUptime
                        let result = try engines.read(engine,image)
                        let row = Row(id: sample.id, dataset: sample.dataset, scene: sample.scene,
                                      expected: sample.expected, engine: engine, config: "\(filter)_\(scale)",
                                      actual: result, ocr_ms: (ProcessInfo.processInfo.systemUptime-begin)*1000,
                                      resize_ms: resize)
                        print("IOS_OCR " + String(decoding: try encoder.encode(row), as: UTF8.self))
                    }
                }
            }
        }
    }
}

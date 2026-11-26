import XCTest
import UIKit
import Vision
import MLKitVision
import MLKitTextRecognition
@testable import IOSOCRLab

final class CombinationTests: XCTestCase {
    func testCombination() throws {
        let done=expectation(description:"native OCR combination")
        DispatchQueue.global(qos:.userInitiated).async {
            defer { done.fulfill() }
            do { try self.runCases() } catch { XCTFail("Combination: \(error)") }
        }
        wait(for:[done],timeout:2400)
    }
    private func runCases() throws {
        let bundle=Bundle(for:Self.self)
        let url=try XCTUnwrap(bundle.url(forResource:"combination",withExtension:"json",subdirectory:"assets"))
        let samples=try XCTUnwrap(JSONSerialization.jsonObject(with:Data(contentsOf:url)) as? [[String:Any]])
        XCTAssertEqual(samples.count,30)
        let ml=TextRecognizer.textRecognizer(options:TextRecognizerOptions())
        for (index,sample) in samples.enumerated() {
            let id=try XCTUnwrap(sample["id"] as? String)
            for mode in (index % 2 == 0 ? ["raw","outline"] : ["outline","raw"]) {
                let path=try XCTUnwrap(bundle.url(forResource:"\(id)_\(mode)",withExtension:"png",subdirectory:"assets"))
                let image=try XCTUnwrap(UIImage(contentsOfFile:path.path))
                let cg=try XCTUnwrap(image.cgImage)
                for engine in ["vision-ios","mlkit-ios"] {
                    var row=sample
                    row["engine"]=engine;row["mode"]=mode
                    var lines=[[String:Any]]()
                    let start=ProcessInfo.processInfo.systemUptime
                    do {
                        if engine=="mlkit-ios" {
                            let input=VisionImage(image:image);input.orientation = .up
                            let result=try ml.results(in:input)
                            for block in result.blocks { for line in block.lines {
                                let b=line.frame
                                lines.append(["text":line.text,"box":[Double(b.minX),Double(b.minY),Double(b.maxX),Double(b.maxY)]])
                            }}
                        } else {
                            let request=VNRecognizeTextRequest()
                            request.recognitionLevel = .accurate
                            request.recognitionLanguages=["en-US"]
                            request.usesLanguageCorrection=true
                            try VNImageRequestHandler(cgImage:cg,orientation:.up,options:[:]).perform([request])
                            for observation in request.results ?? [] {
                                guard let c=observation.topCandidates(1).first else {continue}
                                let b=observation.boundingBox
                                lines.append(["text":c.string,"box":[Double(b.minX)*Double(cg.width),Double(1-b.maxY)*Double(cg.height),Double(b.maxX)*Double(cg.width),Double(1-b.minY)*Double(cg.height)]])
                            }
                        }
                    } catch {row["error"]=String(describing:error)}
                    row["ocr_ms"]=(ProcessInfo.processInfo.systemUptime-start)*1000
                    row["lines"]=lines
                    let data=try JSONSerialization.data(withJSONObject:row)
                    print("COMBO "+String(decoding:data,as:UTF8.self))
                }
            }
        }
    }
}

import Foundation
@main
struct GameGlossaryTests {
    static func main() {
        let rules = GameGlossary.parse("Arthur | Artur | Arthur\nNew York | Nový York | New York\nGo! | | Běž!\nMorgan | Arthur | Morgan", source: "en", target: "cs")!
        precondition(rules.apply(original: "Go!", translated: "Jdi!", source: "en", target: "cs") == "Běž!")
        precondition(rules.apply(original: "Arthur and Morgan in New York", translated: "Artur v Nový York", source: "en", target: "cs") == "Arthur v New York")
        precondition(rules.apply(original: "Arthurian", translated: "Artur", source: "en", target: "cs") == "Artur")
        precondition(rules.apply(original: "Arthur", translated: "Artur", source: "en", target: "de") == "Artur")
        precondition(GameGlossary.parse("X | Y | Z\nX | Y | Q", source: "en", target: "cs") == nil)
        precondition(GameGlossary.parse("X | Y | ", source: "en", target: "cs") == nil)
        let other = GameGlossary.parse("Arthur | Artur | A.", source: "en", target: "cs")!
        precondition(other.apply(original: "Arthur", translated: "Artur", source: "en", target: "cs") == "A.")
        print("GameGlossary: exact, guarded literal, non-cascade, language/profile isolation passed")
    }
}

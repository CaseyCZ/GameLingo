import Foundation

@main
struct TranslationRevisionTests {
    static func main() {
        var dialogue = TranslationRevision()
        var options = TranslationRevision()
        let oldDialogue = dialogue.value
        let oldOptions = options.value
        dialogue.advance()
        precondition(!dialogue.accepts(oldDialogue))
        precondition(options.accepts(oldOptions))
        let languageB = dialogue.value
        dialogue.advance() // language A again
        precondition(!dialogue.accepts(oldDialogue) && !dialogue.accepts(languageB))
        let current = dialogue.value
        precondition(dialogue.accepts(current))
        options.advance()
        precondition(!options.accepts(oldOptions))
        precondition(dialogue.accepts(current))
        print("TranslationRevision: independent lanes and A-B-A invalidation passed")
    }
}

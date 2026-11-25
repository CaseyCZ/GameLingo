"""Opt-in conservative confirmation of similar OCR candidates."""
from core import normalize, similarity

class Stabilizer:
    def __init__(self):
        self.pending=""
        self.accepted=""
    def accept(self,raw):
        text=normalize(raw)
        if not text:
            self.pending=""
            # Dialogue owns the two-blank clear rule.
            return ""
        if text==self.accepted:
            self.pending=""
            return text
        # Accept immediately if two successive OCR observations agree exactly.
        # Near-matches alone never silently merge different short sentences.
        if text==self.pending:
            self.accepted=text
            self.pending=""
            return text
        self.pending=text
        return None
    def reset(self):
        self.pending=self.accepted=""

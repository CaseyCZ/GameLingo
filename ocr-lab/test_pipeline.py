import unittest
from stabilizer import Stabilizer
from latest_slot import LatestSlot

class PipelineTests(unittest.TestCase):
    def test_two_equal_samples(self):
        s=Stabilizer()
        self.assertIsNone(s.accept("Wait here"))
        self.assertEqual(s.accept("Wait   here"),"Wait here")
        self.assertEqual(s.accept("Wait here"),"Wait here")
    def test_changed_short_line_not_merged(self):
        s=Stabilizer()
        s.accept("Go now");s.accept("Go now")
        self.assertIsNone(s.accept("No now"))
        self.assertEqual(s.accept("No now"),"No now")
    def test_noisy_interruption(self):
        s=Stabilizer()
        s.accept("Hello");s.accept("Hello")
        self.assertIsNone(s.accept("He1lo"))
        self.assertEqual(s.accept("Hello"),"Hello")
    def test_blank_resets_candidate(self):
        s=Stabilizer()
        s.accept("Wait")
        self.assertEqual(s.accept(""),"")
        self.assertIsNone(s.accept("Wait"))
    def test_latest_slot_replaces(self):
        s=LatestSlot()
        self.assertFalse(s.put("first"))
        self.assertTrue(s.put("second"))
        self.assertEqual(s.take(),"second")
        s.close()
        self.assertIsNone(s.take())
    def test_clear(self):
        s=LatestSlot()
        s.put("old")
        self.assertTrue(s.clear())
        self.assertFalse(s.clear())
        s.put("new")
        self.assertEqual(s.take(),"new")

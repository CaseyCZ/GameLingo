import unittest
from core import Dialogue,rolling_delta,percentile,replay_indices,score_cues

class DialogueTests(unittest.TestCase):
    def test_repeat_and_one_blank(self):
        d=Dialogue()
        a=d.accept("Hello   world")
        self.assertEqual(a.kind,"new")
        self.assertEqual(d.accept("").text,"Hello world")
        self.assertEqual(d.accept("Hello world").kind,"repeat")
        self.assertEqual(d.generation,a.generation)
    def test_confirmed_gap_allows_same_line(self):
        d=Dialogue()
        a=d.accept("Hello")
        d.accept("")
        self.assertEqual(d.accept("").kind,"clear")
        b=d.accept("Hello")
        self.assertEqual(b.kind,"new")
        self.assertGreater(b.generation,a.generation)
    def test_a_b_a_distinct_generations(self):
        d=Dialogue()
        events=[d.accept(s) for s in ["Wait.","Go.","Wait."]]
        self.assertEqual([e.generation for e in events],[1,2,3])
    def test_rolling_exact_overlap(self):
        self.assertEqual(rolling_delta("I will meet you","meet you outside"),"outside")
        self.assertEqual(rolling_delta("I will go","go now"),"go now")
        self.assertEqual(rolling_delta("No way","No way"),"")
    def test_percentile(self):
        self.assertEqual(percentile([1,2,3,4],.95),4)
        self.assertEqual(percentile([], .95),0)
    def test_latest_frame_no_backlog(self):
        self.assertEqual([x[0] for x in replay_indices(10,250,[900,900,900,900])],[0,3,7])
    def test_ground_truth(self):
        score=score_cues(["Hello world","Go away"],[(0,"Hello world"),(0,"Hello world"),(1,"bad")])
        self.assertEqual(score,dict(total=2,recognized=1,missed=1,wrong=1,duplicates=1))
    def test_invalid_timing(self):
        with self.assertRaises(ValueError):list(replay_indices(1,0,[2]))

if __name__=="__main__":
    unittest.main()

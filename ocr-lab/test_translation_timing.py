import unittest
from translation_timing import Ticket,Result,eligible,timings,run_job

class TimingTests(unittest.TestCase):
    def setUp(self):
        self.ticket=Ticket(3,1,"Wait here.",1.0,1.2)
        self.result=Result(self.ticket,1.4,1.9,"Počkej tady.",False)
    def test_timings(self):
        self.assertEqual(timings(self.result,2.0),dict(generation=3,translation_queue_ms=200.0,
            translation_ms=500.0,frame_to_result_ms=900.0,frame_to_ui_ms=1000.0,cold_translation=0,first_seen_to_ui_ms=""))
    def test_eligibility(self):
        self.assertTrue(eligible(self.result,1,2,2.0))
        self.assertFalse(eligible(self.result,2,2,2.0))
        self.assertFalse(eligible(self.result,1,3,2.0))
        self.assertFalse(eligible(self.result,1,2,4.1))
        self.assertFalse(eligible(self.result,1,2,2.0,False))
    def test_job(self):
        clock=iter([1.4,1.9])
        result=run_job(self.ticket,lambda t:"Počkej.",True,lambda:next(clock))
        self.assertTrue(result.cold)
        self.assertEqual(timings(result)["translation_ms"],500)
    def test_empty(self):
        with self.assertRaises(RuntimeError):
            run_job(self.ticket,lambda t:" ",False)

if __name__=="__main__":
    unittest.main()

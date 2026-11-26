import unittest, json, copy
from validate import ROOT,validate
class ProfileTests(unittest.TestCase):
    def setUp(self):self.p=json.loads((ROOT/"gta-vi.json").read_text())
    def reject(self,change):
        p=copy.deepcopy(self.p);change(p)
        with self.assertRaises(ValueError):validate(p)
    def test_valid(self):validate(self.p)
    def test_bounds(self):self.reject(lambda p:p["zones"][0].update(rect=[0,.65,1,1.1]))
    def test_inverted(self):self.reject(lambda p:p["zones"][0].update(rect=[1,.65,0,1]))
    def test_duplicates(self):self.reject(lambda p:p["zones"].append(p["zones"][0]))
    def test_max(self):self.reject(lambda p:p.update(zones=[dict(self.p["zones"][0],id=str(i)) for i in range(11)]))
    def test_platform(self):self.reject(lambda p:p.update(platforms=["ps5"]))
    def test_release_guard(self):self.reject(lambda p:p.update(releaseReady=True))
    def test_no_filter(self):self.reject(lambda p:p["ocr"].update(preprocessing="outline"))
if __name__=="__main__":unittest.main()

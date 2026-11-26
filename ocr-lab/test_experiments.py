import unittest
from PIL import Image,ImageDraw
from experiments import FILTERS,Adaptive,ImageGate,Word,clean_words,clean_text,equivalent
from engines import preprocess

class Experiments(unittest.TestCase):
    def test_filters_size(self):
        img=Image.new("RGB",(240,70),(50,70,80))
        ImageDraw.Draw(img).rectangle((30,20,100,40),fill="white")
        for mode in FILTERS:
            self.assertEqual(preprocess(img,mode,100).size,img.size,mode)
    def test_outline_rejects_bright_flat_area(self):
        im=Image.new("RGB",(100,100),"white")
        self.assertEqual(preprocess(im,"outline",100).getpixel((50,50)),(255,255,255))
    def test_gate_refresh(self):
        g=ImageGate();im=Image.new("L",(10,10),0)
        self.assertTrue(g.allow(im,0))
        self.assertFalse(g.allow(im,.2))
        self.assertTrue(g.allow(im,.8))
        im.putpixel((1,1),255)
        self.assertTrue(g.allow(im,.9))
    def test_adaptive_deadline(self):
        a=Adaptive()
        self.assertIsNone(a.accept("First",0)[0])
        self.assertIsNone(a.accept("Second",.2)[0])
        self.assertEqual(a.accept("Third",.4),("Third",0))
    def test_adaptive_confirmation(self):
        a=Adaptive()
        self.assertIsNone(a.accept("Wait",1)[0])
        self.assertEqual(a.accept("Wait",1.2),("Wait",1))
    def test_short_meaning_preserved(self):
        self.assertFalse(equivalent("Go now","No now"))
        self.assertEqual(clean_text("= SS | I need 5."),"SS I need 5.")
        self.assertEqual(clean_text("No!"),"No!")
    def test_confidence_geometry(self):
        words=[[Word("Wait",0,0,40,20,99),Word("here",45,0,40,20,99),Word("X",90,0,8,6,80)]]
        self.assertEqual(clean_words(words,True),"Wait here")
        self.assertEqual(clean_words(words,False,90),"Wait here")

if __name__=="__main__":unittest.main()

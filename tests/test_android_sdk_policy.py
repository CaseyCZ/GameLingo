import importlib.util
import struct
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("sdk_policy", Path(__file__).resolve().parents[1] / "tools/android_sdk_policy.py")
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)

def elf(alignment, offset=0, address=0, bits=64):
    ident = b"\x7fELF" + bytes([2 if bits == 64 else 1, 1, 1]) + bytes(9)
    if bits == 64:
        header = struct.pack("<HHIQQQIHHHHHH", 3, 183, 1, 0, 64, 0, 0, 64, 56, 1, 0, 0, 0)
        ph = struct.pack("<IIQQQQQQ", 1, 5, offset, address, 0, 0, 0, alignment)
    else:
        header = struct.pack("<HHIIIIIHHHHHH", 3, 40, 1, 0, 52, 0, 0, 52, 32, 1, 0, 0, 0)
        ph = struct.pack("<IIIIIIII", 1, offset, address, 0, 0, 0, 5, alignment)
    return ident + header + ph

class SDKPolicyTests(unittest.TestCase):
    def test_64bit_16kb_alignment(self):
        self.assertTrue(policy.elf_load_segments(elf(16384))[0]["aligned_16kb"])
        self.assertFalse(policy.elf_load_segments(elf(4096))[0]["aligned_16kb"])
        self.assertFalse(policy.elf_load_segments(elf(16384, 4096, 0))[0]["aligned_16kb"])

    def test_32bit_and_bad_elf(self):
        self.assertTrue(policy.elf_load_segments(elf(16384, bits=32))[0]["aligned_16kb"])
        with self.assertRaises(ValueError):
            policy.elf_load_segments(b"not ELF")

    def test_platform_targets(self):
        self.assertEqual(policy.MIN_SDK, 26)
        self.assertEqual(policy.TARGETS, {"mobile": 36, "tv": 34})

if __name__ == "__main__":
    unittest.main()

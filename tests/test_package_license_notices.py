import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from package_license_notices import android_dependency_notices, archive_notices


class PackageNoticeTests(unittest.TestCase):
    def test_nested_jar_and_original_notice(self):
        jar = io.BytesIO()
        with zipfile.ZipFile(jar, "w") as z:
            z.writestr("META-INF/LICENSE.txt", "Original license\n")
            z.writestr("../NOTICE", "Do not read unsafe paths")
        aar = io.BytesIO()
        with zipfile.ZipFile(aar, "w") as z:
            z.writestr("classes.jar", jar.getvalue())
            z.writestr("META-INF/NOTICE", "Original notice")
        with zipfile.ZipFile(aar) as z:
            entries = list(archive_notices(z, "example"))
        self.assertEqual({n for n, _, _ in entries},
                         {"example/classes.jar/META-INF/LICENSE.txt", "example/META-INF/NOTICE"})

    def test_runtime_inventory_flags_missing_notice(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            jar = root / "demo.jar"
            with zipfile.ZipFile(jar, "w") as z:
                z.writestr("example.class", b"\0")
            data = root / "artifacts.json"
            data.write_text(json.dumps([{"coordinate": "org.example:demo:1.0",
                                        "name": jar.name, "file": str(jar)}]))
            result = android_dependency_notices(data, {"files": []}, [])
            self.assertEqual(result[0]["review"], "no_embedded_notice_found")
            self.assertEqual(result[0]["sha256"], hashlib.sha256(jar.read_bytes()).hexdigest())
            self.assertEqual(result[0]["notices"], [])


    def test_duplicate_runtime_artifacts_preserve_distinct_bytes(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            jars = []
            for index, notice in enumerate(("First license", "First license", "Second license")):
                folder = root / str(index)
                folder.mkdir()
                jar = folder / "demo.jar"
                if index == 1:
                    jar.write_bytes(jars[0].read_bytes())
                else:
                    with zipfile.ZipFile(jar, "w") as z:
                        z.writestr("META-INF/LICENSE", notice)
                jars.append(jar)
            data = root / "artifacts.json"
            data.write_text(json.dumps([{"coordinate": "org.example:demo:1.0",
                                        "name": jar.name, "file": str(jar)}
                                       for jar in jars]))
            manifest, parts = {"files": []}, []
            result = android_dependency_notices(data, manifest, parts)
            self.assertEqual(len(result), 2)
            self.assertEqual(len({item["sha256"] for item in result}), 2)
            self.assertEqual(len({item["path"] for item in manifest["files"]}), 2)
            self.assertIn("First license", parts)
            self.assertIn("Second license", parts)


if __name__ == "__main__":
    unittest.main()

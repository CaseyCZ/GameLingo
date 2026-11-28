import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from package_license_notices import android_dependency_notices, archive_notices, pom_declarations, inherited_pom_declarations


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


    def test_google_third_party_notice_filenames(self):
        aar = io.BytesIO()
        with zipfile.ZipFile(aar, "w") as z:
            z.writestr("third_party_licenses.txt", "License text")
            z.writestr("third_party_licenses.json", '{"example": "metadata"}')
            z.writestr("res/raw/third_party_license_metadata", "0:12 example")
        with zipfile.ZipFile(aar) as z:
            self.assertEqual(len(list(archive_notices(z, "google"))), 3)

    def test_pom_license_is_evidence_not_full_license(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "demo.pom"
            path.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0">'
                            '<licenses><license><name>Apache-2.0</name>'
                            '<url>https://www.apache.org/licenses/LICENSE-2.0</url>'
                            '</license></licenses></project>')
            result = pom_declarations(path)
            self.assertEqual(result["status"], "declared")
            self.assertEqual(result["licenses"][0]["name"], "Apache-2.0")
            self.assertEqual(pom_declarations(None)["status"], "pom_unavailable")
            path.write_text("<project><parent><groupId>example</groupId>"
                            "<artifactId>parent</artifactId><version>1</version>"
                            "</parent></project>")
            result = pom_declarations(path)
            self.assertEqual(result["status"], "no_direct_declaration")
            self.assertEqual(result["parent"]["artifactId"], "parent")


    def test_parent_license_matches_explicit_parent(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "parent.pom"
            path.write_text("<project><licenses><license><name>Apache-2.0</name>"
                            "</license></licenses></project>")
            child = {"licenses": [], "parent": {"groupId": "org.example",
                      "artifactId": "parent", "version": "1"}}
            record = {"coordinate": "org.example:parent:1", "file": str(path)}
            self.assertEqual(inherited_pom_declarations(child, record)["licenses"][0]["name"], "Apache-2.0")
            with self.assertRaises(RuntimeError):
                inherited_pom_declarations(child, {**record, "coordinate": "org.example:other:1"})
            self.assertIsNone(inherited_pom_declarations({"licenses": [{"name": "BSD"}]}, record))


if __name__ == "__main__":
    unittest.main()

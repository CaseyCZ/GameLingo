"""Reuse the TV updater in the mobile generated source; no divergent download implementation."""
from pathlib import Path

def one(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError("Mobile update anchor changed: " + old[:90])
    return text.replace(old, new, 1)

def patch_mobile_update(folder):
    source = Path(__file__).with_name("GameLingoTVUpdater.kt").read_text(encoding="utf8")
    for old, new in (
        ('cz.caseycz.gamelingo.tv', 'cz.caseycz.gamelingo.mobile'),
        ('GameLingo-TV-update.json', 'GameLingo-Android-update.json'),
        ('GameLingo-GoogleTV-v', 'GameLingo-Android-v'),
        ('RemotePlayService.aktivni || PrijemService.bezi || GameLingoScreenTranslationService.running', 'RemotePlayService.aktivni'),
        ('GameLingo-TV-Updater', 'GameLingo-Android-Updater'),
    ):
        if old not in source:
            raise RuntimeError("Shared updater platform anchor changed: " + old)
        source = source.replace(old, new)
    (folder / "GameLingoTVUpdater.kt").write_text(source, encoding="utf8")
    # TV updater lives inside the existing About page, not the dashboard grid.
    about = folder / "GameLingoAbout.kt"
    about_text = about.read_text(encoding="utf8")
    update_anchor = '        page.addView(label("Licence a původní autoři", 19f, true))'
    if about_text.count(update_anchor) != 1:
        raise RuntimeError("Mobile About update anchor changed")
    about_text = about_text.replace(update_anchor, '''        page.addView(GameLingoVisualKit.deviceCard(activity, "TV", "Zkontrolovat aktualizace", "Stáhnout novou verzi", false) {
            GameLingoTVUpdater.show(activity)
        })
''' + update_anchor, 1)
    about.write_text(about_text, encoding="utf8")

    main = folder / "MainActivity.kt"
    text = main.read_text(encoding="utf8")
    text = one(text, "        GameLingoTVPairing.initialize(this)", "        GameLingoTVPairing.initialize(this)\n        GameLingoTVUpdater.checkAtStartup(this)")
    main.write_text(text, encoding="utf8")
    manifest = folder.parents[3] / "AndroidManifest.xml"
    xml = manifest.read_text(encoding="utf8")
    if xml.count("</application>") != 1:
        raise RuntimeError("Mobile manifest application anchor changed")
    if 'android:name="android.permission.REQUEST_INSTALL_PACKAGES"' not in xml:
        xml = xml.replace("<application", '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>\n    <application', 1)
    xml = xml.replace("</application>", """    <provider android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.updates" android:exported="false"
            android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/gamelingo_update_paths"/>
        </provider>
    </application>""", 1)
    paths = manifest.parent / "res" / "xml" / "gamelingo_update_paths.xml"
    paths.parent.mkdir(parents=True, exist_ok=True)
    paths.write_text('<paths xmlns:android="http://schemas.android.com/apk/res/android"><cache-path name="updates" path="gamelingo-updates/"/></paths>', encoding="utf8")
    manifest.write_text(xml, encoding="utf8")

"""Protect inherited console credentials without changing console discovery or UI."""
from pathlib import Path

def replace_once(source, old, new):
    if source.count(old) != 1:
        raise RuntimeError("Console security anchor changed: " + old[:90])
    return source.replace(old, new, 1)

def install_console_security(folder):
    helper = Path(__file__).with_name("GameLingoCredentialStore.kt")
    (folder / helper.name).write_text(helper.read_text(encoding="utf-8"), encoding="utf-8")
    path = folder / "XboxUcet.kt"
    s = path.read_text(encoding="utf-8")
    s = s.replace('prefs(ctx).getString("refresh", null)', 'GameLingoCredentialStore.read(prefs(ctx), "xbox", "refresh")')
    s = replace_once(s, 'p.getString("pristupMs", null)', 'GameLingoCredentialStore.read(p, "xbox", "pristupMs")')
    s = replace_once(s, 'p.getString("refresh", null)', 'GameLingoCredentialStore.read(p, "xbox", "refresh")')
    s = replace_once(s, '''        prefs(ctx).edit()
            .putString("refresh", j.optString("refresh_token"))
            .putString("pristupMs", j.optString("access_token"))
            .putLong("pristupMsDo", System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000 - 60_000)
            .apply()''', '''        val refresh = j.optString("refresh_token").takeIf { it.isNotBlank() }
            ?: GameLingoCredentialStore.read(prefs(ctx), "xbox", "refresh")
        val access = j.optString("access_token").takeIf { it.isNotBlank() }
        check(refresh != null && access != null &&
            GameLingoCredentialStore.write(prefs(ctx), "xbox",
                mapOf("refresh" to refresh, "pristupMs" to access),
                System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000 - 60_000)) {
            "Xbox: secure storage unavailable"
        }''')
    s = replace_once(s, '                ulozTokeny(ctx, j)', '                if (zruseno() || runCatching { ulozTokeny(ctx, j) }.isFailure) return false')
    s = replace_once(s, 'val c = URL(adresa).openConnection() as HttpURLConnection', '''val url = URL(adresa)
        require(url.protocol == "https" && url.userInfo == null && url.ref == null &&
            (url.port == -1 || url.port == 443) &&
            (url.host in setOf("login.microsoftonline.com", "login.live.com",
                "user.auth.xboxlive.com", "xsts.auth.xboxlive.com", "xhome.gssv-play-prod.xboxlive.com") ||
                url.host.endsWith(".gssv-play-prodxhome.xboxlive.com") ||
                url.host.endsWith(".gssv-play-prod.xboxlive.com"))) { "Xbox endpoint rejected" }
        val c = url.openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.useCaches = false''')
    s = s.replace('devicecode $stav: ${telo.take(200)}', 'devicecode $stav').replace('xhome login $s3: ${t3.take(200)}', 'xhome login $s3')
    path.write_text(s, encoding="utf-8")

    path = folder / "RemotePlay.kt"
    s = path.read_text(encoding="utf-8")
    for field in ("account_id", "regist_key", "rp_key"):
        s = replace_once(s, f'prefs.getString("{field}", null)',
                         f'GameLingoCredentialStore.read(prefs, "ps", "{field}")')
    s = replace_once(s, 'prefs.edit().putString("account_id", v?.trim()).apply()',
        '{ check(GameLingoCredentialStore.write(prefs, "ps", mapOf("account_id" to v?.trim()))) }')
    s = replace_once(s, 'prefs.edit().putString("account_id", id).commit()',
        'GameLingoCredentialStore.write(prefs, "ps", mapOf("account_id" to id))')
    for field in ("regist_key", "rp_key"):
        s = replace_once(s, f'prefs.edit().putString("{field}", v?.let {{ Base64.encodeToString(it, Base64.NO_WRAP) }}).apply()',
            f'{{ check(GameLingoCredentialStore.write(prefs, "ps", mapOf("{field}" to v?.let {{ Base64.encodeToString(it, Base64.NO_WRAP) }}))) }}')
    # Handle storage failure rather than claiming registration succeeded.
    s = replace_once(s, '                        udaje.registKey = udalost.host.rpRegistKey\n                        udaje.rpKey = udalost.host.rpKey\n                        hotovo(true,',
        '                        val stored = runCatching {\n                            udaje.registKey = udalost.host.rpRegistKey\n                            udaje.rpKey = udalost.host.rpKey\n                        }.isSuccess\n                        if (!stored) hotovo(false, "PlayStation: secure storage unavailable")\n                        else hotovo(true,')
    path.write_text(s, encoding="utf-8")

    path = folder / "PsnPrihlaseni.kt"
    s = path.read_text(encoding="utf-8")
    start = s.index("        if (!adresa.startsWith(PRESMEROVANI))")
    end = s.index("\n    }", start)
    s = s[:start] + '''        return runCatching {
            val uri = android.net.Uri.parse(adresa.trim())
            if (uri.scheme != "https" || uri.host != "remoteplay.dl.playstation.net" ||
                uri.encodedPath != "/remoteplay/redirect" || uri.userInfo != null ||
                uri.port !in listOf(-1, 443) || uri.fragment != null ||
                uri.getQueryParameter("error") != null) return null
            uri.getQueryParameters("code").singleOrNull()?.takeIf { it.isNotBlank() && it.length <= 4096 }
        }.getOrNull()''' + s[end:]
    for name in ("s1", "s2"):
        anchor = f'        {name}.connectTimeout = 8000; {name}.readTimeout = 8000'
        s = replace_once(s, anchor, f'        {name}.instanceFollowRedirects = false\n        {name}.useCaches = false\n' + anchor)
    path.write_text(s, encoding="utf-8")

"""Restore regular TV navigation after the receiver-only diagnostic patch."""
def patch_navigation(source: str) -> str:
    def one(before, after):
        nonlocal source
        if source.count(before) != 1:
            raise RuntimeError("TV navigation anchor changed: " + before[:90])
        source = source.replace(before, after, 1)

    one('''        // This independent APK acts only as an iPhone subtitle receiver.
        if (Settings.canDrawOverlays(this)) hratSTelefonem()
        else ukazNavodPovoleni()''', '''        prevezmiAccountId(intent)
        ukazUvod()''')
    one('''        if (Settings.canDrawOverlays(this) && obrazovka != "prijem") {
            hratSTelefonem()
        }''', '''        if (obrazovka == "hlavni") ukazHlavni()''')
    one('            "prijem" -> { PrijemService.zastav(this); finish() }',
        '            "prijem" -> zpetNaVyber()')
    one('''    private fun zpetNaVyber() {
        PrijemService.zastav(this)
        prefsBeta.edit().putString("rezimHry", "tv").apply()
        obrazovka = "hlavni"
        ukazHlavni()
    }''', '''    private fun zpetNaVyber() {
        // Navigation is not a request to stop the background receiver.
        obrazovka = "hlavni"
        ukazHlavni()
    }

    private fun zastavPrijemANavrat() {
        PrijemService.zastav(this)
        prefsBeta.edit().putString("rezimHry", "tv").apply()
        zpetNaVyber()
    }''')
    one('''        val zastav = GameLingoTVUi.action(this, GameLingoLanguages.ui(this, "Zpět")) {
            zpetNaVyber()
        }''', '''        val zastav = GameLingoTVUi.action(this, GameLingoLanguages.ui(this, "Zpět")) {
            zpetNaVyber()
        }
        val zastavPrijem = GameLingoTVUi.action(this, GameLingoLanguages.ui(this, "Zastavit příjem")) {
            zastavPrijemANavrat()
        }''')
    one('        // TV_RECEIVER_STOP_ACTION\n','        paticka.addView(zastavPrijem)\n')
    return source

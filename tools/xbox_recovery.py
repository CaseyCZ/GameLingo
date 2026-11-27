"""Keep Android Remote Play callbacks scoped to the live session.

A console-initiated stop is terminal, unlike a transport error.
Only patch the pinned Xbox start function; leave PlayStation and capture settings intact.
"""

def protect_xbox_recovery(source):
    start = source.index("    private fun spustXbox(id: String) {")
    end = source.index("    /** Snímek z Xboxu", start)
    block = source[start:end]
    patches = [
        ("        var spojeno = false\n        val r = XboxSpojeni.Relace(this, id, priStavu = { st ->",
         "        // Capture the existing service generation. Stop and console changes\n        // invalidate it before the old WebRTC session is released.\n        val xboxGeneration = relaceCteni.get()\n        var spojeno = false\n        val r = XboxSpojeni.Relace(this, id, priStavu = { st ->"),
        ("                if (xbox == null) return@post\n                when {",
         "                if (xbox == null || xboxGeneration != relaceCteni.get() || !aktivni || !automat) return@post\n                when {"),
        ("                        ui.postDelayed({ if (xbox != null) runCatching { AutoStart.uspech(this) } }, 60_000)",
         "                        ui.postDelayed({\n                            if (xbox != null && xboxGeneration == relaceCteni.get() && aktivni && automat)\n                                runCatching { AutoStart.uspech(this) }\n                        }, 60_000)"),
        ("                        ui.postDelayed({ zahrejCtecku() }, 1500)\n                    }\n                    st.startsWith(\"✘\") || st.startsWith(\"Xbox ukončil\") -> {",
         "                        ui.postDelayed({\n                            if (xbox != null && xboxGeneration == relaceCteni.get() && aktivni && automat) zahrejCtecku()\n                        }, 1500)\n                    }\n                    // Console/server termination is not a transport failure.\n                    // Never compete for a session the console deliberately ended.\n                    st.startsWith(\"Xbox ukončil\") -> {\n                        automat = false\n                        aktivni = false\n                        ui.removeCallbacks(hlidac)\n                        ui.removeCallbacks(hlidacSpojeni)\n                        zastav(st)\n                        stopSelf()\n                    }\n                    st.startsWith(\"✘\") -> {"),
        ("        }, priSnimku = { f -> snimekXbox(f) }, bezGpu = telefon)",
         "        }, priSnimku = { f ->\n            if (xbox != null && xboxGeneration == relaceCteni.get() && aktivni && automat) snimekXbox(f)\n        }, bezGpu = telefon)"),
    ]
    for old, new in patches:
        if block.count(old) != 1:
            raise RuntimeError("Xbox recovery anchor changed: " + old[:90])
        block = block.replace(old, new, 1)
    return source[:start] + block + source[end:]

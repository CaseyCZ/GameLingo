package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Original GameLingo central device manager, not PlayCaption's TV-only menu.
 * Xbox and PS5 registration are delegated to the existing working engine.
 */
object GameLingoDeviceCenter {
    private val navy = Color.parseColor("#070B19")
    private val surface = Color.parseColor("#111A33")
    private val border = Color.parseColor("#554078")
    private val cyan = Color.parseColor("#02D6FF")
    private val muted = Color.parseColor("#92A4C7")
    private val success = Color.parseColor("#35E57D")

    private fun background(fill: Int = surface, stroke: Int = border): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 21f
            setStroke(2, stroke)
        }

    private fun line(c: Activity, title: String, sub: String = "",
                     checked: Boolean = false,
                     onClick: () -> Unit): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val tenFoot = (c.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
                android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            val h = GameLingoVisualKit.dp(c, if (tenFoot) 10 else 13)
            val v = GameLingoVisualKit.dp(c, if (tenFoot) 8 else 12)
            setPadding(h, v, h, v)
            minimumHeight = GameLingoVisualKit.dp(c, if (tenFoot) 52 else 62)
            background = GameLingoVisualKit.card(c, checked)
            isClickable = true
            isFocusable = true
            setOnFocusChangeListener { view, focused ->
                view.background = if (tenFoot && focused)
                    GameLingoTVUi.panel(c, focus = true)
                else GameLingoVisualKit.card(c, checked)
            }
            setOnClickListener { onClick() }
            val family = when {
                sub.contains("PlayStation", ignoreCase = true) ||
                    title.contains("PlayStation", ignoreCase = true) -> "PlayStation"
                sub.contains("Xbox", ignoreCase = true) ||
                    title.contains("Xbox", ignoreCase = true) -> "Xbox"
                title.contains("TV", ignoreCase = true) ||
                    sub.contains("TV", ignoreCase = true) ||
                    title.contains("televize", ignoreCase = true) -> "TV"
                else -> null
            }
            val railSize = GameLingoVisualKit.dp(c, if (tenFoot) 38 else 54)
            addView(android.widget.FrameLayout(c).apply {
                if (family != null) addView(GameLingoVisualKit.platformEmblem(c, family),
                    android.widget.FrameLayout.LayoutParams(
                        GameLingoVisualKit.dp(c, if (tenFoot) 30 else 46),
                        GameLingoVisualKit.dp(c, if (tenFoot) 30 else 46),
                        Gravity.CENTER))
            }, LinearLayout.LayoutParams(railSize, railSize))
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(TextView(c).apply {
                    text = title
                    gravity = Gravity.CENTER
                    textSize = GameLingoVisualKit.sp(c, 17f)
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
                if (sub.isNotEmpty()) {
                    addView(TextView(c).apply {
                        text = sub
                        gravity = Gravity.CENTER
                        textSize = GameLingoVisualKit.sp(c, 13f)
                        setTextColor(muted)
                        setPadding(0, GameLingoVisualKit.dp(c, 5), 0, 0)
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            }, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(c).apply {
                text = if (checked) "✓" else ""
                textSize = GameLingoVisualKit.sp(c, 26f)
                setTextColor(success)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(railSize, railSize))
        }

    private fun label(c: Activity, value: String, size: Float = 20f,
                      color: Int = Color.WHITE): TextView =
        TextView(c).apply {
            text = value
            gravity = Gravity.CENTER
            textSize = GameLingoVisualKit.sp(c, size)
            setTextColor(color)
            typeface = Typeface.DEFAULT_BOLD
            if (GameLingoTVUi.isTV(c)) setPadding(GameLingoVisualKit.dp(c,4), GameLingoVisualKit.dp(c,9), GameLingoVisualKit.dp(c,4), GameLingoVisualKit.dp(c,9)) else setPadding(4, 9, 4, 9)
        }

    private fun add(root: LinearLayout, view: LinearLayout) {
        root.addView(view, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = if (GameLingoTVUi.isTV(root.context)) GameLingoVisualKit.dp(root.context,13) else 13 })
    }

    // Local aliases never change console credentials or Microsoft account data.
    fun consoleName(c: Activity, family:String, id:String, original:String): String =
        c.getSharedPreferences("gamelingo_devices",Activity.MODE_PRIVATE)
            .getString("alias:"+family+":"+id,null)?.takeIf{it.isNotBlank()} ?: original
    private fun rename(c:Activity,family:String,id:String,original:String,onSaved:()->Unit) {
        val input=android.widget.EditText(c).apply {setSingleLine(true);setText(consoleName(c,family,id,original));selectAll()}
        AlertDialog.Builder(c).setTitle(GameLingoLanguages.ui(c, "Přejmenovat konzoli")).setView(input).setNegativeButton(GameLingoLanguages.ui(c, "Zrušit"),null)
            .setPositiveButton(GameLingoLanguages.ui(c, "Uložit")){_,_->
                if(!RemotePlayService.aktivni) {
                    val value=input.text.toString().trim().take(64)
                    val e=c.getSharedPreferences("gamelingo_devices",Activity.MODE_PRIVATE).edit()
                    if(value.isBlank())e.remove("alias:"+family+":"+id) else e.putString("alias:"+family+":"+id,value)
                    e.apply();onSaved()
                }
            }.show()
    }

    /** TV uses the branded D-pad picker. Preserve the existing mobile presentation. */
    private fun chooseDevice(c: Activity, heading: String, options: Array<String>,
                             onSelect: (Int) -> Unit) {
        if (GameLingoTVUi.isTV(c)) {
            GameLingoTVUi.select(c, heading, options, onSelect = onSelect)
        } else {
            AlertDialog.Builder(c).setTitle(heading).setItems(options) { _, i -> onSelect(i) }
                .setNegativeButton(GameLingoLanguages.ui(c, "Zpět"), null).show()
        }
    }

    /** Send only to an authenticated receiver, never during gameplay. */
    private fun showTestCaption(c: Activity, tv: TvProfil) {
        fun tr(key: String) = GameLingoLanguages.ui(c, key)
        val input = android.widget.EditText(c).apply {
            hint = tr("Zkušební text")
            setText(tr("Zkušební titulek GameLingo"))
            filters = arrayOf(android.text.InputFilter.LengthFilter(300))
        }
        val dialog = AlertDialog.Builder(c)
            .setTitle(tr("Zkušební titulek"))
            .setView(input)
            .setPositiveButton(tr("Zobrazit na TV"), null)
            .setNegativeButton(tr("Zrušit"), null).create()
        dialog.show()
        val send = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        send.setOnClickListener {
            val sample = input.text.toString().trim()
            if (sample.isBlank()) {
                input.error = tr("Zkušební text")
                return@setOnClickListener
            }
            if (RemotePlayService.aktivni) {
                input.error = tr("Nejdřív zastav překlad.")
                return@setOnClickListener
            }
            send.isEnabled = false
            GameLingoTVPairing.verify(c, tv) { verified ->
                if (!dialog.isShowing || c.isFinishing || c.isDestroyed) return@verify
                send.isEnabled = true
                if (!verified || !GameLingoTVPairing.paired(c, tv.ip)) {
                    input.error = tr("TV neodpovídá. Ověř IP, připojení a případně zadej nový kód.")
                } else if (RemotePlayService.aktivni) {
                    input.error = tr("Nejdřív zastav překlad.")
                } else {
                    // Explicitly selects the target for the preview, using the signed sender.
                    val store = TvProfily(c)
                    if (store.najdi(tv.id)?.ip != tv.ip) return@verify
                    store.vyber(tv.id)
                    Propojeni.Vysilac.nastavCil(tv.ip, tv.jmeno)
                    Propojeni.Vysilac.ukazka(sample)
                    dialog.dismiss()
                }
            }
        }
    }

    /** Shared content for the native mobile device tab and the TV dialog. */
    fun content(c: Activity,
             onXbox: () -> Unit,
             onPlayStation: () -> Unit,
             onTV: () -> Unit,
             onUpdated: () -> Unit = {},
             onDismiss: () -> Unit = {},
             showClose: Boolean = false, showHeader: Boolean = true): LinearLayout {
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GameLingoVisualKit.pageBackground(c)
            val tv = GameLingoTVUi.isTV(c)
            setPadding(GameLingoVisualKit.dp(c, if (tv) 20 else 16),
                       GameLingoVisualKit.dp(c, if (tv) 20 else 12),
                       GameLingoVisualKit.dp(c, if (tv) 20 else 16),
                       GameLingoVisualKit.dp(c, if (tv) 26 else 16))
        }
        val tenFoot = GameLingoTVUi.isTV(c)
        if (showHeader) {
            if (!tenFoot) {
                root.addView(GameLingoVisualKit.neonTitle(c,
                    "Konzole a zařízení", "Správa konzolí, TV a výstupu titulků", "display.2"))
            } else {
                val header = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
                header.addView(GameLingoVisualKit.platformEmblem(c, "TV"),
                    LinearLayout.LayoutParams(GameLingoVisualKit.dp(c, 48),
                        GameLingoVisualKit.dp(c, 48)))
                header.addView(label(c, GameLingoLanguages.ui(c, "Konzole a zařízení"), 25f),
                    LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                header.addView(android.widget.FrameLayout(c).apply {
                    addView(GameLingoHelp.button(c, "console"),
                        android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                }, LinearLayout.LayoutParams(GameLingoVisualKit.dp(c, 48),
                    GameLingoVisualKit.dp(c, 48)))
                root.addView(header)
            }
        }
        if (!tenFoot) {
            // Two native platform choices, matching the iPhone device manager.
            val platformChoices = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            listOf("Xbox" to onXbox, "PlayStation" to onPlayStation)
                .forEachIndexed { index, (family, action) ->
                    val item = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        background = GameLingoVisualKit.card(c)
                        setPadding(GameLingoVisualKit.dp(c, 8), GameLingoVisualKit.dp(c, 10),
                            GameLingoVisualKit.dp(c, 8), GameLingoVisualKit.dp(c, 10))
                        isClickable = true
                        isFocusable = true
                        addView(GameLingoVisualKit.platformEmblem(c, family),
                            LinearLayout.LayoutParams(GameLingoVisualKit.dp(c, 48),
                                GameLingoVisualKit.dp(c, 48)))
                        addView(TextView(c).apply {
                            text = family
                            gravity = Gravity.CENTER
                            textSize = GameLingoVisualKit.sp(c, 14f)
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                        })
                        setOnClickListener { onDismiss(); action() }
                    }
                    platformChoices.addView(item,
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            .apply {
                                if (index == 0) rightMargin = GameLingoVisualKit.dp(c, 9)
                            })
                }
            root.addView(platformChoices, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = GameLingoVisualKit.dp(c, 10) })
        }
        val addDeviceButton = line(c, "＋  " + GameLingoLanguages.ui(c, "Přidat zařízení")) {
            val options = if (tenFoot) arrayOf("Xbox", "PlayStation 5 / 4", GameLingoLanguages.ui(c, "Telefon · QR / PIN"))
                else arrayOf("Xbox", "PlayStation 5 / 4", "Google TV / Android TV")
            chooseDevice(c, GameLingoLanguages.ui(c, "Přidat zařízení"), options) { selected ->
                    onDismiss()
                    when (selected) {
                        0 -> onXbox()
                        1 -> onPlayStation()
                        else -> onTV()
                    }
                }
        }
        add(root, addDeviceButton)

        val consolePanel = if (tenFoot) LinearLayout(c).apply { orientation = LinearLayout.VERTICAL } else root
        val tvPanel = if (tenFoot) LinearLayout(c).apply { orientation = LinearLayout.VERTICAL } else root
        if (tenFoot) {
            val columns = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            columns.addView(consolePanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { rightMargin = GameLingoVisualKit.dp(c, 16) })
            columns.addView(tvPanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(columns)
        }
        consolePanel.addView(label(c, GameLingoLanguages.ui(c, "Konzole"), 16f))
        val consoles = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
        }
        consolePanel.addView(consoles)

        fun psRows() {
            val saved = GameLingoPlayStationConsoles.list(c)
            if (saved.isEmpty()) {
                add(consoles, line(c, "PlayStation", GameLingoLanguages.ui(c, "Přidat PS5 / PS4")) {
                    onDismiss()
                    onPlayStation()
                })
            } else {
                for (ps in saved) {
                    add(consoles, line(c, consoleName(c,"ps",ps.ip,ps.name), ps.type + " · " + ps.ip,
                                      ps.selected) {
                        chooseDevice(c, consoleName(c,"ps",ps.ip,ps.name), arrayOf(GameLingoLanguages.ui(c, "Použít"), GameLingoLanguages.ui(c, "Odebrat"), GameLingoLanguages.ui(c, "Přejmenovat"))) { option ->
                                if(option==2) { if(!RemotePlayService.aktivni)rename(c,"ps",ps.ip,ps.name){onDismiss();onUpdated()};return@chooseDevice }
                                if (option == 0) {
                                    if (RemotePlayService.aktivni) {
                                        AlertDialog.Builder(c)
                                            .setMessage(GameLingoLanguages.ui(c, "Nejdřív zastav probíhající překlad."))
                                            .setPositiveButton("OK", null).show()
                                    } else if (GameLingoPlayStationConsoles.select(c, ps.ip)) {
                                        RemotePlayService.prepni(c, false)
                                        c.getSharedPreferences("gamelingo_devices", android.content.Context.MODE_PRIVATE)
                                            .edit().putBoolean("cameraSourceSelected", false).apply()
                                        onDismiss()
                                        onUpdated()
                                    }
                                } else {
                                    if (RemotePlayService.aktivni) {
                                        AlertDialog.Builder(c)
                                            .setMessage(GameLingoLanguages.ui(c, "Nejdřív zastav probíhající překlad."))
                                            .setPositiveButton("OK", null).show()
                                        return@chooseDevice
                                    }
                                    AlertDialog.Builder(c)
                                        .setMessage(GameLingoLanguages.ui(c, "Odebrat") + " " + ps.name + "?")
                                        .setPositiveButton(GameLingoLanguages.ui(c, "Odebrat")) { _, _ ->
                                            GameLingoPlayStationConsoles.remove(c, ps.ip)
                                            onDismiss()
                                            onUpdated()
                                        }
                                        .setNegativeButton(GameLingoLanguages.ui(c, "Zrušit"), null).show()
                                }
                            }
                    })
                }
            }
        }
        psRows()

        // Fetch every Xbox owned by the Microsoft account, NOT firstOrNull.
        val xboxStatus = label(c, GameLingoLanguages.ui(c, "Xbox: vyhledávám…"), 14f, muted)
        consolePanel.addView(xboxStatus)
        if (XboxUcet.prihlasen(c)) {
            Thread {
                val found = runCatching { XboxSpojeni.konzole(c) }
                c.runOnUiThread {
                    if (c.isFinishing) return@runOnUiThread
                    val list = found.getOrNull().orEmpty()
                    xboxStatus.text = if (list.isEmpty()) GameLingoLanguages.ui(c, "Xbox: žádná konzole")
                        else "Xbox"
                    val prefs = c.getSharedPreferences("gamelingo_devices",
                        Activity.MODE_PRIVATE)
                    val selectedID = prefs.getString("selectedXboxId", null)
                    val hiddenXboxIds = prefs.getStringSet("hiddenXboxIds", emptySet())
                        .orEmpty().toSet()
                    list.filterNot { it.id in hiddenXboxIds }.forEach { xbox ->
                        add(consoles, line(c, consoleName(c,"xbox",xbox.id,xbox.jmeno), "Xbox Series / One",
                                          selectedID == xbox.id) {
                            chooseDevice(c, consoleName(c,"xbox",xbox.id,xbox.jmeno), arrayOf(GameLingoLanguages.ui(c, "Použít"), GameLingoLanguages.ui(c, "Připojit"), GameLingoLanguages.ui(c, "Odebrat"), GameLingoLanguages.ui(c, "Přejmenovat"))) { option ->
                                    if (RemotePlayService.aktivni) {
                                        AlertDialog.Builder(c)
                                            .setMessage(GameLingoLanguages.ui(c, "Nejdřív zastav probíhající překlad."))
                                            .setPositiveButton("OK", null).show()
                                        return@chooseDevice
                                    }
                                    if(option==3) { rename(c,"xbox",xbox.id,xbox.jmeno){onDismiss();onUpdated()};return@chooseDevice }
                                    if (option == 2) {
                                        AlertDialog.Builder(c)
                                            .setTitle(GameLingoLanguages.ui(c, "Odebrat Xbox?"))
                                            .setMessage(GameLingoLanguages.ui(c, "Konzole zmizí ze seznamu GameLingo, ale zůstane na tvém Microsoft účtu. Můžeš ji později znovu zobrazit."))
                                            .setPositiveButton(GameLingoLanguages.ui(c, "Odebrat")) { _, _ ->
                                                val hidden = prefs.getStringSet("hiddenXboxIds", emptySet())
                                                    .orEmpty().toMutableSet()
                                                hidden.add(xbox.id)
                                                val update = prefs.edit()
                                                    .putStringSet("hiddenXboxIds", hidden)
                                                if (prefs.getString("selectedXboxId", null) == xbox.id)
                                                    update.remove("selectedXboxId")
                                                update.apply()
                                                onDismiss()
                                                onUpdated()
                                            }
                                            .setNegativeButton(GameLingoLanguages.ui(c, "Zrušit"), null)
                                            .show()
                                    } else {
                                        XboxUcet.ulozKonzoli(c, xbox.id, xbox.jmeno)
                                        RemotePlayService.prepni(c, true)
                                        prefs.edit().putString("selectedXboxId", xbox.id).putBoolean("cameraSourceSelected", false).apply()
                                        onDismiss()
                                        if (option == 1) onXbox() else onUpdated()
                                    }
                                }
                        })
                    }
                    if (hiddenXboxIds.isNotEmpty()) {
                        add(consoles, line(c, GameLingoLanguages.ui(c, "Vrátit odebrané Xboxy"),
                            GameLingoLanguages.ui(c, "Znovu zobrazit konzole") + " (" + hiddenXboxIds.size + ")") {
                            if (RemotePlayService.aktivni) {
                                AlertDialog.Builder(c)
                                    .setMessage(GameLingoLanguages.ui(c, "Nejdřív zastav probíhající překlad."))
                                    .setPositiveButton("OK", null).show()
                            } else {
                                prefs.edit().remove("hiddenXboxIds").apply()
                                onDismiss()
                                onUpdated()
                            }
                        })
                    }
                }
            }.apply { name = "gamelingo-xbox-devices"; isDaemon = true }.start()
        } else {
            xboxStatus.text = GameLingoLanguages.ui(c, "Xbox: nepřihlášeno")
            add(consoles, line(c, GameLingoLanguages.ui(c, "Přidat Xbox"), GameLingoLanguages.ui(c, "Přihlásit Microsoft")) {
                onDismiss()
                onXbox()
            })
        }

        if (tenFoot) {
            tvPanel.addView(label(c, GameLingoLanguages.ui(c, "Telefony"), 16f))
            add(tvPanel, line(c, GameLingoLanguages.ui(c, "Přidat telefon"), GameLingoLanguages.ui(c, "Párování pomocí QR kódu nebo PINu")) {
                onDismiss()
                onTV()
            })
            // TV_RECEIVER_PHONE_ROWS
            if (showClose) add(root, line(c, GameLingoLanguages.ui(c, "Hotovo")) { onDismiss() })
            return root
        }
        tvPanel.addView(label(c, GameLingoLanguages.ui(c, "Televize"), 16f))
        val tvStore = TvProfily(c)
        val tvs = tvStore.seznam()
        if (tvs.isEmpty()) {
            add(tvPanel, line(c, "Google TV", GameLingoLanguages.ui(c, "Přidat televizor")) {
                onDismiss()
                onTV()
            })
        }
        val mobile = !tenFoot
        if (mobile) add(tvPanel, line(c, GameLingoLanguages.ui(c, "IP adresa televize"),
            GameLingoLanguages.ui(c, "Přidat televizor")) {
            if (RemotePlayService.aktivni) {
                AlertDialog.Builder(c).setMessage(GameLingoLanguages.ui(c, "Nejdřív zastav probíhající překlad."))
                    .setPositiveButton("OK", null).show()
            } else {
                val input = android.widget.EditText(c).apply {
                    hint = GameLingoLanguages.ui(c, "IP adresa televize")
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
                    setSingleLine()
                }
                val dialog = AlertDialog.Builder(c)
                    .setTitle(GameLingoLanguages.ui(c, "Přidat televizor"))
                    .setView(input)
                    .setPositiveButton(GameLingoLanguages.ui(c, "Pokračovat"), null)
                    .setNegativeButton(GameLingoLanguages.ui(c, "Zrušit"), null).create()
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val ip = input.text.toString().trim()
                    if (!android.util.Patterns.IP_ADDRESS.matcher(ip).matches()) {
                        input.error = GameLingoLanguages.ui(c, "IP adresa televize")
                    } else if (!RemotePlayService.aktivni) {
                        val existing = tvStore.seznam().firstOrNull { it.ip == ip && !it.platforma.jenZobrazovac }
                        val candidate = existing ?: TvProfil("manual:" + ip, "Google TV", ip, TvPlatforma.ANDROID_TV)
                        dialog.dismiss()
                        GameLingoTVPairing.show(c, candidate) {
                            if (existing == null) tvStore.zapamatuj(candidate.id, candidate.jmeno, candidate.ip, candidate.platforma)
                            onDismiss()
                            onUpdated()
                        }
                    }
                }
            }
        })
        if (mobile) add(root, line(c, GameLingoLanguages.ui(c, "Načíst QR kód z TV"), GameLingoLanguages.ui(c, "Párování v domácí síti")) {
            if (RemotePlayService.aktivni) {
                AlertDialog.Builder(c).setMessage(GameLingoLanguages.ui(c, "Nejdřív zastav probíhající překlad."))
                    .setPositiveButton("OK", null).show()
            } else GameLingoQRScanner.scan(c) { qr ->
                val existing = tvStore.seznam().firstOrNull { it.ip == qr.ip && !it.platforma.jenZobrazovac }
                val candidate = existing ?: TvProfil("qr:" + qr.ip, "Google TV", qr.ip, TvPlatforma.ANDROID_TV)
                GameLingoTVPairing.show(c, candidate, initialPin = qr.pin) {
                    if (existing == null) tvStore.zapamatuj(candidate.id, candidate.jmeno, candidate.ip, candidate.platforma)
                    onDismiss()
                    onUpdated()
                }
            }
        })
        tvs.forEach { tv ->
            // The authenticated GameLingo pairing protocol belongs to Android TV,
            // not legacy LG webOS or Samsung display-only receivers.
            val pinSupported = mobile && !tv.platforma.jenZobrazovac
            val savedPairing = pinSupported && GameLingoTVPairing.paired(c, tv.ip)
            val descriptor = tv.ip + when {
                !savedPairing -> ""
                GameLingoTVPairing.connected(tv.ip) -> " · " + GameLingoLanguages.ui(c, "TV odpověděla")
                else -> " · " + GameLingoLanguages.ui(c, "Kód uložen, spojení neověřeno")
            }
            add(tvPanel, line(c, tv.jmeno, descriptor,
                          tvStore.posledniId == tv.id) {
                chooseDevice(c, tv.jmeno, if (pinSupported)
                        arrayOf(GameLingoLanguages.ui(c, "Použít"), GameLingoLanguages.ui(c, "Spárovat kódem z TV"),
                            GameLingoLanguages.ui(c, "Ověřit spojení"), GameLingoLanguages.ui(c, "Odebrat"),
                            GameLingoLanguages.ui(c, "Zrušit párování"), GameLingoLanguages.ui(c, "Zkušební titulek"))
                        else arrayOf(GameLingoLanguages.ui(c, "Použít"), GameLingoLanguages.ui(c, "Odebrat"))) { option ->
                        if (RemotePlayService.aktivni) {
                            AlertDialog.Builder(c)
                                .setMessage(GameLingoLanguages.ui(c, "Nejdřív zastav probíhající překlad."))
                                .setPositiveButton("OK", null).show()
                        } else if (option == 0) {
                            tvStore.vyber(tv.id)
                            onDismiss()
                            onUpdated()
                        } else if (pinSupported && option == 1) {
                            GameLingoTVPairing.show(c, tv) {
                                onDismiss()
                                onUpdated()
                            }
                        } else if (pinSupported && option == 2) {
                            GameLingoTVPairing.verify(c, tv) { reachable ->
                                onDismiss()
                                onUpdated()
                                AlertDialog.Builder(c)
                                    .setMessage(if (reachable)
                                        GameLingoLanguages.ui(c, "TV odpověděla na ověření. Příjem titulků ještě ověř ve hře.")
                                        else GameLingoLanguages.ui(c, "TV neodpovídá. Ověř IP, připojení a případně zadej nový kód."))
                                    .setPositiveButton("OK", null).show()
                            }
                        } else if (pinSupported && option == 5) {
                            showTestCaption(c, tv)
                        } else if (pinSupported && option == 4) {
                            AlertDialog.Builder(c)
                                .setTitle(GameLingoLanguages.ui(c, "Zrušit párování TV?"))
                                .setMessage(GameLingoLanguages.ui(c, "Přístup telefonu odvolej také na TV. Místní odebrání neodvolá klíč v přijímači."))
                                .setPositiveButton(GameLingoLanguages.ui(c, "Zrušit párování")) { _, _ ->
                                    if (!RemotePlayService.aktivni) {
                                        GameLingoTVPairing.forget(c, tv.ip)
                                        onDismiss()
                                        onUpdated()
                                    }
                                }
                                .setNegativeButton(GameLingoLanguages.ui(c, "Zrušit"), null).show()
                        } else {
                            AlertDialog.Builder(c)
                                .setMessage(GameLingoLanguages.ui(c, "Odebrat") + " " + tv.jmeno + " " + GameLingoLanguages.ui(c, "z tohoto zařízení?") +
                                    if (pinSupported) "\n\n" + GameLingoLanguages.ui(c, "Přístup telefonu odvolej také na TV. Místní odebrání neodvolá klíč v přijímači.") else "")
                                .setPositiveButton(GameLingoLanguages.ui(c, "Odebrat")) { _, _ ->
                                    tvStore.odeber(tv.id)
                                    if (mobile) GameLingoTVPairing.forget(c, tv.ip)
                                    onDismiss()
                                    onUpdated()
                                }
                                .setNegativeButton(GameLingoLanguages.ui(c, "Zrušit"), null).show()
                        }
                    }
            })
        }

        if (showClose) add(root, line(c, GameLingoLanguages.ui(c, "Hotovo")) { onDismiss() })
        return root
    }

    fun show(c: Activity,
             onXbox: () -> Unit,
             onPlayStation: () -> Unit,
             onTV: () -> Unit,
             onUpdated: () -> Unit = {}) {
        if (c.isFinishing) return
        val dialog = Dialog(c, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val root = content(c, onXbox, onPlayStation, onTV, onUpdated,
            onDismiss = { dialog.dismiss() }, showClose = false, showHeader = false)
        GameLingoTVUi.mountPage(c, dialog, GameLingoLanguages.ui(c, "Konzole a zařízení"), root, helpTopic = "console")
    }
}

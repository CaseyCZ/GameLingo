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
            val h = GameLingoVisualKit.dp(c, if (tenFoot) 17 else 13)
            val v = GameLingoVisualKit.dp(c, if (tenFoot) 17 else 12)
            setPadding(h, v, h, v)
            minimumHeight = GameLingoVisualKit.dp(c, if (tenFoot) 75 else 62)
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
            if (family != null) addView(GameLingoVisualKit.platformEmblem(c, family),
                LinearLayout.LayoutParams(GameLingoVisualKit.dp(c, 46),
                    GameLingoVisualKit.dp(c, 46)).apply {
                    rightMargin = GameLingoVisualKit.dp(c, 12)
                })
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(c).apply {
                    text = title
                    textSize = if (tenFoot) 20f else 17f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                })
                if (sub.isNotEmpty()) {
                    addView(TextView(c).apply {
                        text = sub
                        textSize = if (tenFoot) 15f else 13f
                        setTextColor(muted)
                        setPadding(0, 6, 0, 0)
                    })
                }
            }, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (checked) {
                addView(TextView(c).apply {
                    text = "✓"
                    textSize = 26f
                    setTextColor(success)
                })
            }
        }

    private fun label(c: Activity, value: String, size: Float = 20f,
                      color: Int = Color.WHITE): TextView =
        TextView(c).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(4, 9, 4, 9)
        }

    private fun add(root: LinearLayout, view: LinearLayout) {
        root.addView(view, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 13 })
    }

    /** Shared content for the native mobile device tab and the TV dialog. */
    fun content(c: Activity,
             onXbox: () -> Unit,
             onPlayStation: () -> Unit,
             onTV: () -> Unit,
             onUpdated: () -> Unit = {},
             onDismiss: () -> Unit = {},
             showClose: Boolean = false): LinearLayout {
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GameLingoVisualKit.pageBackground(c)
            setPadding(GameLingoVisualKit.dp(c, 20),
                       GameLingoVisualKit.dp(c, 20),
                       GameLingoVisualKit.dp(c, 20),
                       GameLingoVisualKit.dp(c, 26))
        }
        val header = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(GameLingoVisualKit.platformEmblem(c, "TV"),
            LinearLayout.LayoutParams(GameLingoVisualKit.dp(c, 48),
                GameLingoVisualKit.dp(c, 48)).apply {
                rightMargin = GameLingoVisualKit.dp(c, 12)
            })
        header.addView(label(c, "Konzole a zařízení", 25f),
            LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(GameLingoHelp.button(c, "console"))
        root.addView(header)

        val addDeviceButton = line(c, "＋  Přidat zařízení") {
            AlertDialog.Builder(c)
                .setTitle("Přidat zařízení")
                .setItems(arrayOf("Xbox", "PlayStation 5 / 4",
                                  "Google TV / Android TV")) { _, selected ->
                    onDismiss()
                    when (selected) {
                        0 -> onXbox()
                        1 -> onPlayStation()
                        else -> onTV()
                    }
                }
                .show()
        }
        add(root, addDeviceButton)

        root.addView(label(c, "Konzole", 19f))
        val consoles = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(consoles)

        fun psRows() {
            val saved = GameLingoPlayStationConsoles.list(c)
            if (saved.isEmpty()) {
                add(consoles, line(c, "PlayStation", "Přidat PS5 / PS4") {
                    onDismiss()
                    onPlayStation()
                })
            } else {
                for (ps in saved) {
                    add(consoles, line(c, ps.name, ps.type + " · " + ps.ip,
                                      ps.selected) {
                        AlertDialog.Builder(c)
                            .setTitle(ps.name)
                            .setItems(arrayOf("Použít", "Odebrat")) { _, option ->
                                if (option == 0) {
                                    if (RemotePlayService.aktivni) {
                                        AlertDialog.Builder(c)
                                            .setMessage("Nejdřív zastav probíhající překlad.")
                                            .setPositiveButton("OK", null).show()
                                    } else if (GameLingoPlayStationConsoles.select(c, ps.ip)) {
                                        onDismiss()
                                        onUpdated()
                                    }
                                } else {
                                    if (RemotePlayService.aktivni) {
                                        AlertDialog.Builder(c)
                                            .setMessage("Nejdřív zastav probíhající překlad.")
                                            .setPositiveButton("OK", null).show()
                                        return@setItems
                                    }
                                    AlertDialog.Builder(c)
                                        .setMessage("Odebrat " + ps.name + "?")
                                        .setPositiveButton("Odebrat") { _, _ ->
                                            GameLingoPlayStationConsoles.remove(c, ps.ip)
                                            onDismiss()
                                            onUpdated()
                                        }
                                        .setNegativeButton("Zrušit", null).show()
                                }
                            }.show()
                    })
                }
            }
        }
        psRows()

        // Fetch every Xbox owned by the Microsoft account, NOT firstOrNull.
        val xboxStatus = label(c, "Xbox: vyhledávám…", 14f, muted)
        root.addView(xboxStatus)
        if (XboxUcet.prihlasen(c)) {
            Thread {
                val found = runCatching { XboxSpojeni.konzole(c) }
                c.runOnUiThread {
                    if (c.isFinishing) return@runOnUiThread
                    val list = found.getOrNull().orEmpty()
                    xboxStatus.text = if (list.isEmpty()) "Xbox: žádná konzole"
                        else "Xbox"
                    val prefs = c.getSharedPreferences("gamelingo_devices",
                        Activity.MODE_PRIVATE)
                    val selectedID = prefs.getString("selectedXboxId", null)
                    val hiddenXboxIds = prefs.getStringSet("hiddenXboxIds", emptySet())
                        .orEmpty().toSet()
                    list.filterNot { it.id in hiddenXboxIds }.forEach { xbox ->
                        add(consoles, line(c, xbox.jmeno, "Xbox Series / One",
                                          selectedID == xbox.id) {
                            AlertDialog.Builder(c)
                                .setTitle(xbox.jmeno)
                                .setItems(arrayOf("Použít", "Připojit", "Odebrat")) { _, option ->
                                    if (RemotePlayService.aktivni) {
                                        AlertDialog.Builder(c)
                                            .setMessage("Nejdřív zastav probíhající překlad.")
                                            .setPositiveButton("OK", null).show()
                                        return@setItems
                                    }
                                    if (option == 2) {
                                        AlertDialog.Builder(c)
                                            .setTitle("Odebrat Xbox?")
                                            .setMessage("Konzole zmizí ze seznamu GameLingo, ale zůstane na tvém Microsoft účtu. Můžeš ji později znovu zobrazit.")
                                            .setPositiveButton("Odebrat") { _, _ ->
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
                                            .setNegativeButton("Zrušit", null)
                                            .show()
                                    } else {
                                        prefs.edit().putString("selectedXboxId", xbox.id).apply()
                                        onDismiss()
                                        if (option == 1) onXbox() else onUpdated()
                                    }
                                }.show()
                        })
                    }
                    if (hiddenXboxIds.isNotEmpty()) {
                        add(consoles, line(c, "Vrátit odebrané Xboxy",
                            "Znovu zobrazit ${hiddenXboxIds.size} konzolí") {
                            if (RemotePlayService.aktivni) {
                                AlertDialog.Builder(c)
                                    .setMessage("Nejdřív zastav probíhající překlad.")
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
            xboxStatus.text = "Xbox: nepřihlášeno"
            add(consoles, line(c, "Přidat Xbox", "Přihlásit Microsoft") {
                onDismiss()
                onXbox()
            })
        }

        root.addView(label(c, "Televize", 19f))
        val tvStore = TvProfily(c)
        val tvs = tvStore.seznam()
        if (tvs.isEmpty()) {
            add(root, line(c, "Google TV", "Přidat televizor") {
                onDismiss()
                onTV()
            })
        }
        val mobile = (c.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_TYPE_MASK) !=
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        if (mobile) add(root, line(c, "Načíst QR kód z TV", "Párování v domácí síti") {
            if (RemotePlayService.aktivni) {
                AlertDialog.Builder(c).setMessage("Nejdřív zastav probíhající překlad.")
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
            // The six-digit GameLingo PIN protocol belongs to Android TV,
            // not legacy LG webOS or Samsung display-only receivers.
            val pinSupported = mobile && !tv.platforma.jenZobrazovac
            val savedPairing = pinSupported && GameLingoTVPairing.paired(c, tv.ip)
            val descriptor = tv.ip + when {
                !savedPairing -> ""
                GameLingoTVPairing.connected(tv.ip) -> " · TV odpověděla"
                else -> " · Kód uložen, spojení neověřeno"
            }
            add(root, line(c, tv.jmeno, descriptor,
                          tvStore.posledniId == tv.id) {
                AlertDialog.Builder(c)
                    .setTitle(tv.jmeno)
                    .setItems(if (pinSupported)
                        arrayOf("Použít", "Spárovat šest číslic z TV",
                            "Ověřit spojení", "Odebrat")
                        else arrayOf("Použít", "Odebrat")) { _, option ->
                        if (RemotePlayService.aktivni) {
                            AlertDialog.Builder(c)
                                .setMessage("Nejdřív zastav probíhající překlad.")
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
                                        "TV odpověděla na ověření. Příjem titulků ještě ověř ve hře."
                                        else "TV neodpovídá. Ověř IP, připojení a případně zadej nový kód.")
                                    .setPositiveButton("OK", null).show()
                            }
                        } else {
                            AlertDialog.Builder(c)
                                .setMessage("Odebrat " + tv.jmeno + "?")
                                .setPositiveButton("Odebrat") { _, _ ->
                                    tvStore.odeber(tv.id)
                                    if (mobile) GameLingoTVPairing.forget(c, tv.ip)
                                    onDismiss()
                                    onUpdated()
                                }
                                .setNegativeButton("Zrušit", null).show()
                        }
                    }
                    .show()
            })
        }

        if (showClose) add(root, line(c, "Hotovo") { onDismiss() })
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
            onDismiss = { dialog.dismiss() }, showClose = true)
        val scroll = ScrollView(c).apply { isFillViewport = true }
        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        scroll.post {
            root.findFocus()?.requestFocus()
        }
    }
}

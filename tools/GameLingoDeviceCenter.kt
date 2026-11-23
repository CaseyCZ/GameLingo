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
    private val navy = Color.parseColor("#070B14")
    private val surface = Color.parseColor("#111827")
    private val border = Color.parseColor("#2A3850")
    private val cyan = Color.parseColor("#38BDF8")
    private val muted = Color.parseColor("#94A3B8")
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
            setPadding(18, 17, 18, 17)
            background = background()
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(c).apply {
                    text = title
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                })
                if (sub.isNotEmpty()) {
                    addView(TextView(c).apply {
                        text = sub
                        textSize = 13f
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

    fun show(c: Activity,
             onXbox: () -> Unit,
             onPlayStation: () -> Unit,
             onTV: () -> Unit,
             onUpdated: () -> Unit = {}) {
        if (c.isFinishing) return
        val dialog = Dialog(c)
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = background(navy, navy)
            setPadding(24, 16, 24, 24)
        }
        val header = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label(c, "Moje zařízení", 26f),
            LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(GameLingoHelp.button(c, "console"))
        root.addView(header)

        add(root, line(c, "＋  Přidat zařízení") {
            AlertDialog.Builder(c)
                .setTitle("Přidat zařízení")
                .setItems(arrayOf("Xbox", "PlayStation 5 / 4",
                                  "Google TV / Android TV")) { _, selected ->
                    dialog.dismiss()
                    when (selected) {
                        0 -> onXbox()
                        1 -> onPlayStation()
                        else -> onTV()
                    }
                }
                .show()
        })

        root.addView(label(c, "Konzole", 19f))
        val consoles = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(consoles)

        fun psRows() {
            val saved = GameLingoPlayStationConsoles.list(c)
            if (saved.isEmpty()) {
                add(consoles, line(c, "PlayStation", "Přidat PS5 / PS4") {
                    dialog.dismiss()
                    onPlayStation()
                })
            } else {
                for (ps in saved) {
                    add(consoles, line(c, ps.name, "PlayStation · " + ps.ip,
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
                                        dialog.dismiss()
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
                                            dialog.dismiss()
                                            show(c, onXbox, onPlayStation, onTV, onUpdated)
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
                    val selectedID = c.getSharedPreferences("gamelingo_devices",
                        Activity.MODE_PRIVATE).getString("selectedXboxId", null)
                    list.forEach { xbox ->
                        add(consoles, line(c, xbox.jmeno, "Xbox Series / One",
                                          selectedID == xbox.id) {
                            AlertDialog.Builder(c)
                                .setTitle(xbox.jmeno)
                                .setItems(arrayOf("Použít", "Připojit")) { _, option ->
                                    if (RemotePlayService.aktivni) {
                                        AlertDialog.Builder(c)
                                            .setMessage("Nejdřív zastav probíhající překlad.")
                                            .setPositiveButton("OK", null).show()
                                        return@setItems
                                    }
                                    c.getSharedPreferences("gamelingo_devices",
                                        Activity.MODE_PRIVATE).edit()
                                        .putString("selectedXboxId", xbox.id).apply()
                                    dialog.dismiss()
                                    if (option == 1) onXbox() else onUpdated()
                                }.show()
                        })
                    }
                }
            }.apply { name = "gamelingo-xbox-devices"; isDaemon = true }.start()
        } else {
            xboxStatus.text = "Xbox: nepřihlášeno"
            add(consoles, line(c, "Přidat Xbox", "Přihlásit Microsoft") {
                dialog.dismiss()
                onXbox()
            })
        }

        root.addView(label(c, "Televize", 19f))
        val tvStore = TvProfily(c)
        val tvs = tvStore.seznam()
        if (tvs.isEmpty()) {
            add(root, line(c, "Google TV", "Přidat televizor") {
                dialog.dismiss()
                onTV()
            })
        }
        tvs.forEach { tv ->
            add(root, line(c, tv.jmeno, tv.ip,
                          tvStore.posledniId == tv.id) {
                AlertDialog.Builder(c)
                    .setTitle(tv.jmeno)
                    .setItems(arrayOf("Použít", "Odebrat")) { _, option ->
                        if (RemotePlayService.aktivni) {
                            AlertDialog.Builder(c)
                                .setMessage("Nejdřív zastav probíhající překlad.")
                                .setPositiveButton("OK", null).show()
                        } else if (option == 0) {
                            tvStore.vyber(tv.id)
                            dialog.dismiss()
                            onUpdated()
                        } else {
                            AlertDialog.Builder(c)
                                .setMessage("Odebrat " + tv.jmeno + "?")
                                .setPositiveButton("Odebrat") { _, _ ->
                                    tvStore.odeber(tv.id)
                                    dialog.dismiss()
                                    show(c, onXbox, onPlayStation, onTV, onUpdated)
                                }
                                .setNegativeButton("Zrušit", null).show()
                        }
                    }
                    .show()
            })
        }

        add(root, line(c, "Hotovo") { dialog.dismiss() })
        val scroll = ScrollView(c).apply { isFillViewport = true }
        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (c.resources.displayMetrics.widthPixels * 0.93).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

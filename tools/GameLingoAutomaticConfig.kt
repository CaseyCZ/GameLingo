package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Separate automatic mode; no existing manual layout keys are overwritten. */
internal object GameLingoAutomaticConfig {
    private fun prefs(c: Context) = c.getSharedPreferences("gamelingo_automatic",Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled",false)
    private fun initial(c: Context) = listOf(AutoZone("dialogue",GameLingoLanguages.ui(c, "Dialogy"),true,AutoRect(0f,.65f,1f,1f),true))
    fun zones(c: Context): List<AutoZone> = prefs(c).getString("zones",null)?.let { decode(it) } ?: initial(c)
    private fun decode(raw: String): List<AutoZone>? = runCatching {
        val array=JSONArray(raw)
        val ids=mutableSetOf<String>()
        (0 until minOf(array.length(),10)).mapNotNull { i ->
            runCatching {
                val o=array.getJSONObject(i)
                val r=AutoRect(o.getDouble("left").toFloat(),o.getDouble("top").toFloat(),o.getDouble("right").toFloat(),o.getDouble("bottom").toFloat())
                val id=o.getString("id")
                require(id.isNotBlank() && id.length<=80 && r.valid() && ids.add(id))
                AutoZone(id,o.optString("name","Zóna").take(60),o.optBoolean("enabled",true),r,o.optBoolean("centered",false))
            }.getOrNull()
        }
    }.getOrNull()
    private fun encode(zones: List<AutoZone>)=JSONArray().apply {
        zones.filter { it.rect.valid() }.distinctBy { it.id }.take(10).forEach { z -> put(JSONObject().apply {
            put("id",z.id);put("name",z.name.take(60));put("enabled",z.enabled);put("centered",z.centered)
            put("left",z.rect.left.toDouble());put("top",z.rect.top.toDouble());put("right",z.rect.right.toDouble());put("bottom",z.rect.bottom.toDouble())
        }) }
    }
    private fun save(c: Context,zones: List<AutoZone>) { prefs(c).edit().putString("zones",encode(zones).toString()).apply() }
    fun profile(c: Context): JSONObject = JSONObject().put("enabled",enabled(c)).put("zones",encode(zones(c)))
    fun restore(c: Context,data: JSONObject?) {
        val editor=prefs(c).edit().putBoolean("enabled",data?.optBoolean("enabled",false)==true)
        data?.optJSONArray("zones")?.let { decode(it.toString()) }?.let { editor.putString("zones",encode(it).toString()) }
        editor.apply()
    }
    fun label(c: Context)=GameLingoLanguages.ui(c, if(enabled(c)) "Automatický" else "Standardní")
    private fun editable(a: Activity): Boolean {
        if (!GameLingoLanguages.translationSettingsBusy()) return true
        AlertDialog.Builder(a).setMessage(GameLingoLanguages.ui(a,"Nejdřív zastav překlad.")).setPositiveButton("OK",null).show()
        return false
    }
    private fun select(a:Activity,title:String,labels:Array<String>,selected:Int,action:(Int)->Unit) {
        if(GameLingoTVUi.isTV(a))GameLingoTVUi.select(a,title,labels,selected,action)
        else AlertDialog.Builder(a).setTitle(title).setSingleChoiceItems(labels,selected){dialog,index->dialog.dismiss();action(index)}
            .setNegativeButton(GameLingoLanguages.ui(a, "Zrušit"),null).show()
    }
    fun mode(a: Activity, settings: Nastaveni, onStandardAreas: () -> Unit,
             onLanguage: () -> Unit, onChanged: () -> Unit = {}) {
        if (!editable(a)) return
        fun tr(key: String) = GameLingoLanguages.ui(a, key)
        fun openAreas() {
            if (!editable(a)) return
            if (enabled(a)) showZones(a) else onStandardAreas()
        }
        fun areaDetail() = if (enabled(a)) "Automatické zóny" else "Standardní oblasti"
        val body = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val areas = GameLingoVisualKit.detailRow(a, "rectangle.dashed", tr("Oblasti obrazovky"), tr(areaDetail())) { openAreas() }
        body.addView(modePicker(a, onChanged = {
            areas.findViewWithTag<TextView>("gamelingo-detail")?.text = tr(areaDetail())
            onChanged()
        }, showTitle = false))
        fun addRow(row: View) {
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = GameLingoVisualKit.dp(a, 9)
            })
        }
        addRow(GameLingoVisualKit.detailRow(a, "slider.horizontal.3", tr("Pokročilé nastavení OCR"), tr("Citlivost, režim a filtrování textu")) {
            GameLingoOCRSettings.show(a, ::openAreas)
        })
        addRow(GameLingoVisualKit.detailRow(a, "character.book.closed.fill", tr("Překlad"), tr("Časování a aktuálnost titulků")) {
            GameLingoSettings.showTranslation(a, settings, ::openAreas, onLanguage)
        })
        addRow(areas)
        GameLingoTVUi.page(a, tr("Režim překladu"), body, helpTopic = "regions")
    }
    /** Inline two-option control matching the SwiftUI GLTranslationModePicker. */
    fun modePicker(a: Activity, onChanged: () -> Unit = {}, showTitle: Boolean = true): LinearLayout {
        val d = GameLingoVisualKit.dp(a, 1)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 6 * d, 0, 6 * d)
        }
        if (showTitle) root.addView(TextView(a).apply {
            text = GameLingoLanguages.ui(a, "Režim překladu")
            textSize = GameLingoVisualKit.sp(a, 13f)
            setTextColor(Color.parseColor(GameLingoUIContract.Palette.muted))
            gravity = android.view.Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6 * d })
        val choices = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        val buttons = listOf(false, true).mapIndexed { index, automatic ->
            TextView(a).apply {
                text = GameLingoLanguages.ui(a, if (automatic) "Automatický" else "Standardní")
                textSize = GameLingoVisualKit.sp(a, 14f)
                setTextColor(Color.WHITE)
                gravity = android.view.Gravity.CENTER
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                minHeight = 44 * d
                setPadding(7 * d, 10 * d, 7 * d, 10 * d)
                isFocusable = true
                isClickable = true
                choices.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (index == 0) rightMargin = 6 * d
                })
            }
        }
        fun render() {
            buttons.forEachIndexed { index, button ->
                val active = enabled(a) == (index == 1)
                button.isSelected = active
                button.background = if (active) {
                    android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(Color.parseColor(GameLingoUIContract.Palette.purple),
                            Color.parseColor(GameLingoUIContract.Palette.blue),
                            Color.parseColor(GameLingoUIContract.Palette.cyan))
                    ).apply { cornerRadius = (12 * d).toFloat() }
                } else {
                    GameLingoVisualKit.card(a)
                }
            }
        }
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                if (enabled(a) == (index == 1)) return@setOnClickListener
                if (!editable(a)) return@setOnClickListener
                prefs(a).edit().putBoolean("enabled", index == 1).apply()
                render()
                onChanged()
            }
        }
        root.addView(choices, LinearLayout.LayoutParams(-1, -2))
        render()
        return root
    }


    /**
     * One editor for Android phone and Google TV. The selected zone is edited in
     * place, like the four-region Standard editor, with one live 16:9 preview.
     * TV uses focusable buttons and seek bars; touch devices can drag a zone.
     */
    // Standalone TV preview is temporary, independent of capture/translation.
    private var localPreviewView: View? = null
    private var localPreviewManager: android.view.WindowManager? = null
    private val localPreviewHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val clearLocalPreview = Runnable {
        localPreviewView?.let { v -> runCatching { localPreviewManager?.removeViewImmediate(v) } }
        localPreviewView=null;localPreviewManager=null
    }
    private fun previewOnTV(a: Activity, zone: AutoZone) {
        if (!editable(a) || !zone.rect.valid()) return
        if(!GameLingoTVUi.isTV(a)) {
            GameLingoTVPairing.previewOCRRegion(a,zone.rect)
            return
        }
        if(!android.provider.Settings.canDrawOverlays(a)) {
            android.widget.Toast.makeText(a,GameLingoLanguages.ui(a,"Nejdříve povol překryv v nastavení TV."),android.widget.Toast.LENGTH_LONG).show()
            return
        }
        localPreviewHandler.removeCallbacks(clearLocalPreview)
        clearLocalPreview.run()
        val c=a.applicationContext
        val wm=c.getSystemService(android.view.WindowManager::class.java)
        val view=object: View(c) {
            private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                val r=zone.rect
                paint.color=0xFF4FB3FF.toInt();paint.style=Paint.Style.STROKE
                paint.strokeWidth=4*resources.displayMetrics.density
                canvas.drawRect(r.left*width,r.top*height,r.right*width,r.bottom*height,paint)
                paint.style=Paint.Style.FILL;paint.textSize=16*resources.displayMetrics.scaledDensity
                canvas.save()
                canvas.clipRect(r.left*width,r.top*height,r.right*width,r.bottom*height)
                canvas.drawText(zone.name,r.left*width+8,r.top*height+paint.textSize+4,paint)
                canvas.restore()
            }
        }
        val lp=android.view.WindowManager.LayoutParams(-1,-1,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT).apply { gravity=android.view.Gravity.TOP or android.view.Gravity.LEFT }
        try {
            wm.addView(view,lp);localPreviewView=view;localPreviewManager=wm
            localPreviewHandler.postDelayed(clearLocalPreview,30000L)
            a.moveTaskToBack(true)
        } catch (_: Exception) {
            clearLocalPreview.run()
            android.widget.Toast.makeText(a,GameLingoLanguages.ui(a,"Náhled nelze zobrazit"),android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun showZones(a: Activity) {
        if (!editable(a)) return
        val tr: (String) -> String = { GameLingoLanguages.ui(a, it) }
        val dp: (Int) -> Int = { GameLingoVisualKit.dp(a, it) }
        val tv = GameLingoTVUi.isTV(a)
        val entries = zones(a).toMutableList()
        var selected = if (entries.isEmpty()) -1 else 0
        var updating = false
        val dialog = android.app.Dialog(a, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val page = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            background = GameLingoVisualKit.pageBackground(a)
        }
        page.addView(GameLingoVisualKit.neonTitle(
            a, "Oblasti překladu", "Vyber oblast, potom uprav její polohu a velikost v náhledu.", "viewfinder"))

        val tabs = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        page.addView(android.widget.HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        })
        val editor = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GameLingoVisualKit.card(a)
        }
        val title = TextView(a).apply {
            setTextColor(Color.WHITE)
            textSize = GameLingoVisualKit.sp(a, 17f)
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        editor.addView(title)
        val name = EditText(a).apply {
            hint = tr("Název")
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF92A4C7.toInt())
            filters = arrayOf(android.text.InputFilter.LengthFilter(60))
        }
        editor.addView(name)
        val enabledRow = android.widget.Switch(a).apply {
            text = tr("Aktivní zóna")
            setTextColor(Color.WHITE)
            textSize = GameLingoVisualKit.sp(a, 15f)
        }
        editor.addView(enabledRow)
        val labels = mutableListOf<TextView>()
        val bars = mutableListOf<SeekBar>()
        for (axis in listOf("Vodorovná pozice", "Svislá pozice", "Šířka", "Výška")) {
            val label = TextView(a).apply {
                setTextColor(Color.WHITE)
                textSize = GameLingoVisualKit.sp(a, 14f)
                setPadding(0, dp(10), 0, 0)
            }
            editor.addView(label)
            labels.add(label)
            val seek = SeekBar(a).apply {
                max = 1000
                keyProgressIncrement = 10
                isFocusable = true
            }
            editor.addView(seek)
            bars.add(seek)
        }
        lateinit var refresh: () -> Unit
        lateinit var refreshTabs: () -> Unit
        lateinit var preview: View

        fun updateSelected(change: (AutoZone) -> AutoZone, rerender: Boolean = true) {
            if (selected !in entries.indices || GameLingoLanguages.translationSettingsBusy()) return
            val updated = change(entries[selected])
            if (!updated.rect.valid()) return
            entries[selected] = updated
            save(a, entries)
            preview.invalidate()
            if (rerender) refresh() else refreshTabs()
        }

        val blue = 0xFF4FB3FF.toInt()
        preview = object : View(a) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(dp(180))
                setMeasuredDimension(width, (width * 9f / 16f).toInt())
            }
            override fun onDraw(c: Canvas) {
                super.onDraw(c)
                c.drawColor(0xFF111A33.toInt())
                paint.style = Paint.Style.FILL
                paint.color = 0xFF1A3050.toInt()
                val mountains = android.graphics.Path().apply {
                    moveTo(0f, height * .91f); lineTo(width * .30f, height * .3f)
                    lineTo(width * .51f, height * .72f); lineTo(width * .7f, height * .19f)
                    lineTo(width.toFloat(), height * .91f); close()
                }
                c.drawPath(mountains, paint)
                entries.forEachIndexed { index, zone ->
                    val r = zone.rect
                    val left = width * r.left
                    val top = height * r.top
                    val right = width * r.right
                    val bottom = height * r.bottom
                    paint.style = Paint.Style.FILL
                    paint.color = if (index == selected) 0x604FB3FF else 0x184FB3FF
                    c.drawRect(left, top, right, bottom, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dp(if (index == selected) 3 else 2).toFloat()
                    paint.color = if (zone.enabled) blue else 0xFF64748B.toInt()
                    paint.pathEffect = if (index == selected) null else android.graphics.DashPathEffect(floatArrayOf(dp(7).toFloat(), dp(4).toFloat()), 0f)
                    c.drawRect(left, top, right, bottom, paint)
                    paint.pathEffect = null
                    paint.style = Paint.Style.FILL
                    paint.textSize = dp(12).toFloat()
                    paint.color = Color.WHITE
                    c.save()
                    c.clipRect(left, top, right, bottom)
                    c.drawText(zone.name, left + dp(5), (top + dp(15)).coerceAtMost(bottom), paint)
                    c.restore()
                }
            }
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (tv || selected !in entries.indices) return false
                if (event.actionMasked != android.view.MotionEvent.ACTION_DOWN &&
                    event.actionMasked != android.view.MotionEvent.ACTION_MOVE &&
                    event.actionMasked != android.view.MotionEvent.ACTION_UP) return true
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) return true
                val x = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                val y = (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                updateSelected({ z ->
                    val w = z.rect.width
                    val h = z.rect.height
                    val l = (x - w / 2f).coerceIn(0f, 1f - w)
                    val t = (y - h / 2f).coerceIn(0f, 1f - h)
                    z.copy(rect = AutoRect(l, t, l + w, t + h), centered = false)
                })
                return true
            }
        }
        val previewPanel = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GameLingoVisualKit.card(a, true)
            addView(TextView(a).apply {
                text = tr("Náhled obrazovky")
                gravity = android.view.Gravity.CENTER
                textSize = GameLingoVisualKit.sp(a, 17f)
                setTextColor(Color.WHITE)
            })
            addView(preview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(TextView(a).apply {
                text = tr(if (tv) "Šipkami uprav polohu a velikost zóny." else "Přetáhni vybraný rámeček.")
                gravity = android.view.Gravity.CENTER
                textSize = GameLingoVisualKit.sp(a, 12f)
                setTextColor(0xFF92A4C7.toInt())
            })
        }
        val content = LinearLayout(a).apply { orientation = if (tv) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        if (tv) {
            content.addView(previewPanel, LinearLayout.LayoutParams(0, -2, 1.25f).apply { rightMargin = dp(12) })
            content.addView(editor, LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            content.addView(previewPanel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            content.addView(editor, LinearLayout.LayoutParams(-1, -2))
        }
        page.addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        fun action(label: String, onClick: () -> Unit): TextView =
            TextView(a).apply {
                text = tr(label)
                textSize = GameLingoVisualKit.sp(a, 14f)
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(13), dp(8), dp(13), dp(8))
                minimumHeight = dp(if (tv) 48 else 44)
                background = GameLingoVisualKit.card(a)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
                GameLingoTVUi.controlFocus(a, this)
            }

        val remove = action("Smazat zónu") {
            if (selected !in entries.indices) return@action
            AlertDialog.Builder(a).setMessage(tr("Smazat zónu") + "?")
                .setNegativeButton(tr("Zrušit"), null)
                .setPositiveButton(tr("Smazat zónu")) { _, _ ->
                    if (GameLingoLanguages.translationSettingsBusy()) return@setPositiveButton
                    entries.removeAt(selected)
                    save(a, entries)
                    selected = if (entries.isEmpty()) -1 else selected.coerceAtMost(entries.lastIndex)
                    refresh()
                }.show()
        }
        editor.addView(action("Náhled na TV") {
            entries.getOrNull(selected)?.let { previewOnTV(a,it) }
        })
        editor.addView(action("Obnovit oblast") {
            updateSelected({ it.restoreArea() })
        })
        editor.addView(remove)

        refreshTabs = {
            tabs.removeAllViews()
            entries.forEachIndexed { index, zone ->
                val tab = action((if (zone.enabled) "▣ " else "□ ") + zone.name) {
                    selected = index
                    refresh()
                }
                tab.background = if (index == selected) GameLingoVisualKit.primary(a) else GameLingoVisualKit.card(a)
                tabs.addView(tab, LinearLayout.LayoutParams(-2, dp(52)).apply { rightMargin = dp(7) })
            }
            if (entries.size < 10) {
                val add = action("＋ " + tr("Přidat zónu")) {
                    if (GameLingoLanguages.translationSettingsBusy()) return@action
                    val next = AutoZone(UUID.randomUUID().toString(), tr("Zóna") + " " + (entries.size + 1),
                        true, AutoRect(.2f, .2f, .8f, .45f), false)
                    entries.add(next)
                    selected = entries.lastIndex
                    save(a, entries)
                    refresh()
                }
                tabs.addView(add, LinearLayout.LayoutParams(-2, dp(52)))
            }
        }
        refresh = {
            updating = true
            refreshTabs()
            val hasSelection = selected in entries.indices
            editor.visibility = if (hasSelection) View.VISIBLE else View.GONE
            if (hasSelection) {
                val zone = entries[selected]
                title.text = tr("Upravit zónu") + " · " + zone.name
                if (name.text.toString() != zone.name) name.setText(zone.name)
                enabledRow.isChecked = zone.enabled
                val values = floatArrayOf(zone.rect.left, zone.rect.top, zone.rect.width, zone.rect.height)
                listOf("Vodorovná pozice", "Svislá pozice", "Šířka", "Výška").forEachIndexed { axis, label ->
                    labels[axis].text = tr(label) + ": " + (values[axis] * 100f).toInt() + " %"
                    bars[axis].progress = (values[axis] * 1000f).toInt().coerceIn(0, 1000)
                }
            }
            preview.invalidate()
            updating = false
        }

        name.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (updating || selected !in entries.indices) return
                updateSelected({ it.copy(name = s.toString().take(60)) }, false)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        enabledRow.setOnCheckedChangeListener { _, value ->
            if (!updating) updateSelected({ it.copy(enabled = value) })
        }
        bars.forEachIndexed { axis, bar ->
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser || updating || selected !in entries.indices) return
                    updateSelected({ zone ->
                        val rect = zone.rect
                        val value = progress / 1000f
                        val left = when (axis) {
                            0 -> value.coerceIn(0f, 1f - rect.width)
                            2 -> rect.left.coerceAtMost(1f - value.coerceIn(.08f, 1f))
                            else -> rect.left
                        }
                        val top = when (axis) {
                            1 -> value.coerceIn(0f, 1f - rect.height)
                            3 -> rect.top.coerceAtMost(1f - value.coerceIn(.04f, 1f))
                            else -> rect.top
                        }
                        val w = if (axis == 2) value.coerceIn(.08f, 1f) else rect.width
                        val h = if (axis == 3) value.coerceIn(.04f, 1f) else rect.height
                        zone.copy(rect = AutoRect(left, top, left + w, top + h), centered = false)
                    })
                }
            })
        }
        refresh()
        GameLingoTVUi.mountPage(a, dialog, tr("Oblasti překladu"), page, helpTopic = "regions", onClose = {
            localPreviewHandler.removeCallbacks(clearLocalPreview)
            clearLocalPreview.run()
        })
    }
}

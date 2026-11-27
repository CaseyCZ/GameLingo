package cz.titulky.tv

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Actual Android GameLingo visual components, matching the native SwiftUI
 * design language. Transport, OCR and console pairing stay in their own code.
 */
object GameLingoVisualKit {
    private val cyan = Color.parseColor(GameLingoUIContract.Palette.cyan)
    private val blue = Color.parseColor(GameLingoUIContract.Palette.blue)
    private val purple = Color.parseColor(GameLingoUIContract.Palette.purple)
    private val ink = Color.parseColor(GameLingoUIContract.Palette.background)
    private val panel = Color.parseColor(GameLingoUIContract.Palette.panel)
    private val muted = Color.parseColor(GameLingoUIContract.Palette.muted)

    // Symbol identifiers follow GameLingoVisualSystem.swift and SettingsHomeView.swift.
    // Native vector equivalents; SF Symbols themselves are not Android assets.
    fun iconName(symbol: String, title: String = ""): String = when {
        title.startsWith("Jazyk") -> "globe"
        title.startsWith("OCR") -> "viewfinder"
        title.startsWith("Překladová") -> "internaldrive"
        title.startsWith("Překlad z telefonu") -> "smartphone"
        title.startsWith("Přímo na TV") -> "tv"
        title.startsWith("Překlad") || title.startsWith("Herní slovník") -> "character.book.closed.fill"
        title.startsWith("Oblasti") || title=="Rozložení" -> "rectangle.dashed"
        title.startsWith("Vzhled") -> "text.bubble"
        title.startsWith("Profily") || title=="Konzole" -> "gamecontroller"
        title.startsWith("Zařízení") -> "display.2"
        title.startsWith("Historie") -> "clock.arrow.circlepath"
        title.startsWith("Diagnostika") -> "waveform.path.ecg"
        title.startsWith("O aplikaci") -> "info.circle"
        title.startsWith("Nastavení") -> "slider.horizontal.3"
        else -> symbol
    }
    fun iconDrawable(name: String, tint: Int = Color.WHITE, size: Int = 24): Drawable =
        object : Drawable() {
            private val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color=tint; style=android.graphics.Paint.Style.STROKE
                strokeWidth=1.8f;strokeCap=android.graphics.Paint.Cap.ROUND;strokeJoin=android.graphics.Paint.Join.ROUND
            }
            override fun getIntrinsicWidth()=size
            override fun getIntrinsicHeight()=size
            override fun draw(canvas:android.graphics.Canvas) {
                canvas.save()
                canvas.translate(bounds.left.toFloat(),bounds.top.toFloat())
                canvas.scale(bounds.width()/24f,bounds.height()/24f)
                fun line(x:Float,y:Float,x2:Float,y2:Float)=canvas.drawLine(x,y,x2,y2,p)
                fun circle(x:Float,y:Float,r:Float)=canvas.drawCircle(x,y,r,p)
                fun rect(x:Float,y:Float,x2:Float,y2:Float,r:Float=2f)=canvas.drawRoundRect(x,y,x2,y2,r,r,p)
                fun path(vararg points:Float) {
                    val path=android.graphics.Path();path.moveTo(points[0],points[1])
                    for(i in 2 until points.size step 2)path.lineTo(points[i],points[i+1])
                    canvas.drawPath(path,p)
                }
                when(name) {
                    "house.fill" -> {path(3f,11f,12f,3f,21f,11f);path(5f,10f,5f,21f,10f,21f,10f,15f,14f,15f,14f,21f,19f,21f,19f,10f)}
                    "globe" -> {circle(12f,12f,9f);canvas.drawOval(8f,3f,16f,21f,p);line(3f,12f,21f,12f);line(5f,7f,19f,7f);line(5f,17f,19f,17f)}
                    "viewfinder" -> {path(3f,9f,3f,3f,9f,3f);path(15f,3f,21f,3f,21f,9f);path(21f,15f,21f,21f,15f,21f);path(9f,21f,3f,21f,3f,15f);circle(12f,12f,2f)}
                    "rectangle.dashed" -> {p.pathEffect=android.graphics.DashPathEffect(floatArrayOf(3f,2f),0f);rect(3f,5f,21f,19f);p.pathEffect=null}
                    "character.book.closed.fill" -> {rect(5f,3f,20f,21f);line(8f,3f,8f,21f);path(11f,15f,14f,8f,17f,15f);line(12f,12f,16f,12f)}
                    "text.bubble" -> {rect(3f,4f,21f,17f,4f);path(7f,17f,7f,21f,12f,17f);line(7f,8f,17f,8f);line(7f,12f,15f,12f)}
                    "gamecontroller","gamecontroller.fill" -> {path(7f,7f,17f,7f,20f,9f,22f,17f,20f,19f,16f,15f,8f,15f,4f,19f,2f,17f,4f,9f,7f,7f);line(6f,10f,10f,10f);line(8f,8f,8f,12f);circle(17f,10f,.8f);circle(19f,12f,.8f)}
                    "display.2","tv" -> {rect(3f,4f,21f,16f);line(12f,16f,12f,20f);line(7f,20f,17f,20f);if(name=="display.2")rect(16f,12f,22f,22f)}
                    "smartphone" -> {rect(6f,2f,18f,22f,3f);line(10f,19f,14f,19f)}
                    "clock.arrow.circlepath" -> {circle(12f,12f,9f);path(12f,6f,12f,12f,16f,14f);path(3f,4f,3f,9f,8f,9f)}
                    "internaldrive" -> {rect(3f,6f,21f,18f);line(3f,13f,21f,13f);circle(17f,15.5f,.7f)}
                    "waveform.path.ecg","waveform" -> path(2f,12f,6f,12f,9f,5f,12f,19f,15f,8f,18f,12f,22f,12f)
                    "slider.horizontal.3","gearshape.fill" -> {line(3f,6f,21f,6f);line(3f,12f,21f,12f);line(3f,18f,21f,18f);circle(8f,6f,2f);circle(16f,12f,2f);circle(10f,18f,2f)}
                    "info.circle" -> {circle(12f,12f,9f);line(12f,11f,12f,17f);circle(12f,7f,.6f)}
                    "questionmark.circle" -> {circle(12f,12f,9f);canvas.drawArc(8f,6f,16f,13f,180f,200f,false,p);path(15f,11f,12f,14f,12f,15f);circle(12f,18f,.55f)}
                    "chevron.right" -> path(9f,6f,15f,12f,9f,18f)
                    "arrow.right" -> {line(4f,12f,20f,12f);path(14f,6f,20f,12f,14f,18f)}
                    "download" -> {line(12f,3f,12f,15f);path(7f,10f,12f,15f,17f,10f);path(4f,16f,4f,21f,20f,21f,20f,16f)}
                    "play.fill" -> path(7f,4f,20f,12f,7f,20f,7f,4f)
                    "plus.circle" -> {circle(12f,12f,9f);line(7f,12f,17f,12f);line(12f,7f,12f,17f)}
                    else -> {circle(12f,12f,9f);line(7f,12f,17f,12f)}
                }
                canvas.restore()
            }
            override fun setAlpha(alpha:Int){p.alpha=alpha;invalidateSelf()}
            override fun setColorFilter(filter:android.graphics.ColorFilter?){p.colorFilter=filter;invalidateSelf()}
            @Suppress("DEPRECATION") override fun getOpacity()=android.graphics.PixelFormat.TRANSLUCENT
        }
    fun icon(activity: Activity, name:String, tint:Int=Color.WHITE): ImageView = ImageView(activity).apply {
        setImageDrawable(iconDrawable(name,tint,dp(activity,24)))
        scaleType=ImageView.ScaleType.FIT_CENTER
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun iconBadge(activity:Activity,name:String): View = FrameLayout(activity).apply {
        background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(
            Color.argb(184,2,214,255),Color.argb(199,163,41,250))).apply { cornerRadius=dp(activity,13).toFloat() }
        addView(icon(activity,name),FrameLayout.LayoutParams(dp(activity,21),dp(activity,21),Gravity.CENTER))
    }
    // Use a symmetrical 52dp icon / chevron rail so the central label truly
    // occupies the physical centre of the card on both phone and TV.
    // The colours, typography and corners match the shared iOS GLDetailRow.
    fun detailRow(activity: Activity, symbol: String, title: String, detail: String,
                  onClick: () -> Unit): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val tv = GameLingoTVUi.isTV(activity)
            background = if (tv) GameLingoTVUi.panel(activity) else card(activity)
            setPadding(dp(activity, 11), dp(activity, 11),
                dp(activity, 11), dp(activity, 11))
            isFocusable = true
            isClickable = true
            addView(FrameLayout(activity).apply {
                addView(iconBadge(activity, iconName(symbol, title)),
                    FrameLayout.LayoutParams(dp(activity, 42), dp(activity, 42), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 42)))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(TextView(activity).apply {
                    text = title
                    tag = "gamelingo-title"
                    gravity = Gravity.CENTER
                    textSize = sp(activity, 16f)
                    setTextColor(Color.WHITE)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(TextView(activity).apply {
                    text = detail
                    tag = "gamelingo-detail"
                    gravity = Gravity.CENTER
                    textSize = sp(activity, 12f)
                    setTextColor(muted)
                    setPadding(0, dp(activity, 4), 0, 0)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(FrameLayout(activity).apply {
                addView(icon(activity, "chevron.right", muted),
                    FrameLayout.LayoutParams(dp(activity, 16), dp(activity, 16),
                        Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 42)))
            setOnClickListener { onClick() }
            if (tv) GameLingoTVUi.controlFocus(activity, this)
        }

    // GLBrandWordmark: same existing application mark, white Game and purple Lingo.
    fun wordmark(activity:Activity,compact:Boolean=false):LinearLayout=LinearLayout(activity).apply {
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER
        val size=dp(activity,if(compact)39 else 50)
        addView(ImageView(activity).apply {
            setImageResource(R.drawable.gamelingo_ikona);adjustViewBounds=true
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(size,size).apply{marginEnd=dp(activity,if(compact)8 else 12)})
        addView(TextView(activity).apply {
            text=android.text.SpannableString("GameLingo").apply {
                setSpan(android.text.style.ForegroundColorSpan(purple),4,9,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            textSize=sp(activity, if(compact)25f else 31f);setTextColor(Color.WHITE)
            typeface=Typeface.create("sans-serif-black",Typeface.NORMAL);maxLines=1
        })
        contentDescription="GameLingo"
    }
    // The SwiftUI appearance preview uses a blue/violet scene and mountain symbol.
    fun captionPreview(activity:Activity,caption:TextView):FrameLayout=FrameLayout(activity).apply {
        background=card(activity,true)
        setPadding(dp(activity,14),dp(activity,14),dp(activity,14),dp(activity,14))
        val scene=FrameLayout(activity).apply {
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(
                Color.rgb(20,54,102),Color.rgb(41,28,89),ink)).apply{cornerRadius=dp(activity,16).toFloat()}
        }
        scene.addView(object:View(activity){
            private val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(56,2,214,255)}
            override fun onDraw(c:android.graphics.Canvas){
                val w=width.toFloat();val h=height.toFloat();val path=android.graphics.Path()
                path.moveTo(w*.12f,h*.85f);path.lineTo(w*.36f,h*.24f);path.lineTo(w*.52f,h*.63f)
                path.lineTo(w*.69f,h*.10f);path.lineTo(w*.90f,h*.85f);path.close();c.drawPath(path,p)
            }
        },FrameLayout.LayoutParams(-1,-1))
        (caption.parent as? ViewGroup)?.removeView(caption)
        scene.addView(caption,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin=dp(activity,13);leftMargin=dp(activity,12);rightMargin=dp(activity,12)
        })
        addView(scene,FrameLayout.LayoutParams(-1,dp(activity,176)))
    }
    /** Menu sizes only. Never use these helpers to transform video/OCR coordinates. */
    fun dp(c: Context, value: Int): Int =
        (c.resources.displayMetrics.density * value * GameLingoTVUi.menuScale(c) + 0.5f).toInt()

    fun sp(c: Context, value: Float): Float = value * GameLingoTVUi.menuScale(c)

    fun card(context: Context, selected: Boolean = false): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(panel, ink, if (selected)
                Color.parseColor("#291448") else Color.parseColor("#15142A"))
        ).apply {
            cornerRadius = dp(context, 20).toFloat()
            setStroke(dp(context, if (selected) 2 else 1),
                if (selected) purple else Color.parseColor("#435177"))
        }

    fun primary(context: Context): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(cyan, blue, purple)).apply {
            cornerRadius = dp(context, 18).toFloat()
            setStroke(dp(context, 1), Color.parseColor("#9DEAFF"))
        }

    /** Native Android equivalent of SwiftUI GLNeonTitle (centered, balanced rails). */
    fun neonTitle(activity: Activity, title: String, subtitle: String,
                  symbol: String): LinearLayout {
        val rail = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 4), 0, dp(activity, 7))
        }
        rail.addView(FrameLayout(activity).apply {
            addView(iconBadge(activity, symbol),
                FrameLayout.LayoutParams(dp(activity, 41), dp(activity, 41), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(activity, 41), dp(activity, 41)))
        rail.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(activity).apply {
                text = GameLingoLanguages.ui(activity, title)
                gravity = Gravity.CENTER
                textSize = sp(activity, 19f)
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(activity).apply {
                text = GameLingoLanguages.ui(activity, subtitle)
                gravity = Gravity.CENTER
                textSize = sp(activity, 12f)
                setTextColor(muted)
                setPadding(0, dp(activity, 3), 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // Fixed balancing rail keeps the title centered across the full screen.
        rail.addView(View(activity), LinearLayout.LayoutParams(dp(activity, 41), dp(activity, 41)))
        return rail
    }

    fun heading(context: Context, title: String): TextView =
        TextView(context).apply {
            text = title
            gravity = Gravity.CENTER
            textSize = sp(context, 20f)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(context, 2), dp(context, 8),
                dp(context, 2), dp(context, 10))
        }

    fun hero(activity: Activity, selectSource: ((String) -> Unit)? = null): View {
        fun caption(label: String, size: Float, color: Int,
                    strong: Boolean = false) = TextView(activity).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = sp(activity, size)
            setTextColor(color)
            if (strong) setTypeface(null, Typeface.BOLD)
        }
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 17), dp(activity, 16),
                dp(activity, 17), dp(activity, 17))
            background = card(activity, selected = true)
        }
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(View(activity), LinearLayout.LayoutParams(dp(activity,36),dp(activity,36)))
        top.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(caption(GameLingoLanguages.ui(activity, "HRAJ. ROZUMĚJ."), 11f, cyan, true).apply {
                letterSpacing = 0.14f
            })
            addView(caption(GameLingoLanguages.ui(activity, "Překlad přímo při hraní"), 18f, Color.WHITE, true).apply {
                setPadding(0, dp(activity, 5), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(icon(activity,"waveform",cyan),
            LinearLayout.LayoutParams(dp(activity,36),dp(activity,36)))
        panel.addView(top)

        val sources = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 12), 0, 0)
        }
        val families = if (selectSource == null) listOf("Xbox", "PlayStation") else listOf("Xbox", "PlayStation", "Camera")
        for (family in families) {
            val label = if (family == "Camera") GameLingoLanguages.ui(activity, "Kamera") else family
            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(platformEmblem(activity, family), LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)))
                addView(caption(label, 12f, Color.WHITE, true))
                if (selectSource != null) {
                    isFocusable = true
                    setOnClickListener { selectSource.invoke(family) }
                }
            }
            sources.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        panel.addView(sources)
        return panel
    }

    fun shortcut(activity: Activity, symbol: String, title: String,
                 onClick: () -> Unit): TextView = TextView(activity).apply {
        text = symbol + "\n" + title
        gravity = Gravity.CENTER
        textSize = sp(activity, 15f)
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        minHeight = dp(activity, 103)
        setPadding(dp(activity, 9), dp(activity, 12),
            dp(activity, 9), dp(activity, 12))
        background = card(activity)
        elevation = dp(activity, 3).toFloat()
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick() }
        if (GameLingoTVUi.isTV(activity)) GameLingoTVUi.controlFocus(activity, this)
    }
    fun brandHeader(activity: Activity, onSettings: () -> Unit): LinearLayout {
        val px = dp(activity, 13)
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 5), 0, dp(activity, 8))
        }
        bar.addView(LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER_HORIZONTAL
            addView(wordmark(activity,true))
            addView(TextView(activity).apply {
                text=GameLingoLanguages.ui(activity, "Titulky a překlady her v reálném čase");gravity=Gravity.CENTER;textSize = sp(activity, 12f);setTextColor(muted)
                maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
            })
        },LinearLayout.LayoutParams(0,-2,1f))
        bar.addView(GameLingoHelp.button(activity,"home"),GameLingoHelp.layout(activity,dp(activity,36),dp(activity,44)))
        bar.addView(TextView(activity).apply {
            text = ""
            setCompoundDrawablesWithIntrinsicBounds(iconDrawable("gearshape.fill",Color.WHITE,dp(activity,21)),null,null,null)
            setPadding(dp(activity,12),0,0,0)
            contentDescription = GameLingoLanguages.ui(activity, "Nastavení")
            gravity = Gravity.CENTER
            textSize = sp(activity, 26f)
            setTextColor(Color.WHITE)
            background = card(activity)
            isFocusable = true
            isClickable = true
            setOnClickListener { onSettings() }
            if (GameLingoTVUi.isTV(activity)) GameLingoTVUi.controlFocus(activity, this)
        }, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)))
        return bar
    }

    fun platformEmblem(activity: Activity, family: String): View {
        val xbox = family.equals("Xbox", ignoreCase = true)
        val playStation = family.equals("PlayStation", ignoreCase = true)
        val from = if (xbox) Color.parseColor("#1B7938")
                   else if (playStation) Color.parseColor("#285ACA") else cyan
        val to = if (xbox) Color.parseColor("#15283A")
                 else if (playStation) Color.parseColor("#1F1445") else blue
        return FrameLayout(activity).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(from, to)).apply {
                cornerRadius = dp(activity, 14).toFloat()
            }
            addView(ImageView(activity).apply {
                setImageResource(when {
                    xbox -> R.drawable.gamelingo_xbox
                    playStation -> R.drawable.gamelingo_playstation
                    family == "Camera" -> android.R.drawable.ic_menu_camera
                    else -> android.R.drawable.ic_menu_slideshow
                })
                setColorFilter(Color.WHITE)
                contentDescription = family
                adjustViewBounds = true
            }, FrameLayout.LayoutParams(dp(activity, 31),
                dp(activity, 31), Gravity.CENTER))
        }
    }

    // The emphasized GLPlatformBadge panel used on the iOS Xbox and PlayStation registration screens.
    fun platformPanel(activity:Activity,family:String,title:String,subtitle:String):LinearLayout =
        LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            background=card(activity,true)
            val pad=dp(activity,15);setPadding(pad,pad,pad,pad)
            addView(platformEmblem(activity,family),LinearLayout.LayoutParams(dp(activity,64),dp(activity,64)).apply{marginEnd=dp(activity,13)})
            addView(LinearLayout(activity).apply {
                orientation=LinearLayout.VERTICAL
                addView(TextView(activity).apply{text=title;gravity=Gravity.CENTER;textSize = sp(activity, 18f);setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD})
                addView(TextView(activity).apply{text=subtitle;gravity=Gravity.CENTER;textSize = sp(activity, 13f);setTextColor(muted);setPadding(0,dp(activity,4),0,0)})
            },LinearLayout.LayoutParams(0,-2,1f))
        }
    /** Mobile dashboard cards use the same balanced center column as iOS. */
    fun deviceCard(activity: Activity, family: String, title: String,
                   subtitle: String, selected: Boolean,
                   onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = card(activity, selected)
            setPadding(dp(activity, 13), dp(activity, 13),
                dp(activity, 13), dp(activity, 13))
            minimumHeight = dp(activity, 77)
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(FrameLayout(activity).apply {
            addView(platformEmblem(activity, family),
                FrameLayout.LayoutParams(dp(activity, 53), dp(activity, 53), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(activity, 61), dp(activity, 53)))
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(activity).apply {
                text = title
                gravity = Gravity.CENTER
                textSize = sp(activity, 17f)
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                maxLines = 2
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(activity).apply {
                text = subtitle
                gravity = Gravity.CENTER
                textSize = sp(activity, 12.5f)
                setTextColor(if (selected) Color.parseColor("#3BE781") else muted)
                maxLines = 3
                setPadding(0, dp(activity, 4), 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(activity).apply {
            text = "›"
            textSize = sp(activity, 27f)
            setTextColor(muted)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(activity, 61), dp(activity, 53)))
        if (GameLingoTVUi.isTV(activity)) GameLingoTVUi.controlFocus(activity, row)
        return row
    }

    /** iOS Home: a centered section title with a right-aligned Add action. */
    fun deviceSection(activity: Activity, onAdd: () -> Unit): LinearLayout {
        val rail = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 4), dp(activity, 1), dp(activity, 4), dp(activity, 1))
        }
        rail.addView(TextView(activity).apply {
            text = GameLingoLanguages.ui(activity, "Moje zařízení")
            textSize = sp(activity, 18f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rail.addView(TextView(activity).apply {
            text = "+  " + GameLingoLanguages.ui(activity, "Přidat")
            textSize = sp(activity, 14f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(cyan)
            gravity = Gravity.CENTER
            minHeight = dp(activity, 44)
            setPadding(dp(activity, 10), 0, dp(activity, 4), 0)
            isFocusable = true
            isClickable = true
            setOnClickListener { onAdd() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        return rail
    }

    fun section(activity: Activity, name: String): TextView = heading(activity, name)

    fun status(activity: Activity, title: String, message: String,
               ready: Boolean): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = card(activity, selected = ready)
            setPadding(dp(activity, 14), dp(activity, 14),
                dp(activity, 14), dp(activity, 14))
        }
        row.addView(TextView(activity).apply {
            text = if (ready) "✓" else "!"
            textSize = sp(activity, 23f)
            gravity = Gravity.CENTER
            setTextColor(if (ready) Color.parseColor("#3BE781") else muted)
        }, LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)))
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(activity).apply {
                text = title
                gravity = Gravity.CENTER
                textSize = sp(activity, 15.5f)
                setTextColor(if (ready) Color.parseColor("#3BE781") else Color.WHITE)
                setTypeface(null, Typeface.BOLD)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(activity).apply {
                text = message
                gravity = Gravity.CENTER
                textSize = sp(activity, 12f)
                setTextColor(muted)
                setPadding(0, dp(activity, 3), 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // Balance the left status glyph to keep labels centered in the full card.
        row.addView(android.view.View(activity),
            LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)))
        return row
    }

    /**
     * Same three violet/blue/cyan ambient light pools as GLNeonBackground on
     * iPhone, native Android radial drawables behind real interactive views.
     */
    fun pageBackground(activity: Activity): Drawable {
        val base = GradientDrawable().apply { setColor(ink) }
        fun glow(color: Int, centerX: Float, centerY: Float,
                 radiusDp: Int): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(color, Color.TRANSPARENT)
            gradientRadius = dp(activity, radiusDp).toFloat()
            setGradientCenter(centerX, centerY)
        }
        return LayerDrawable(arrayOf(
            base,
            glow(Color.argb(80, 53, 87, 255), 0.88f, 0.12f, 260),
            glow(Color.argb(74, 163, 41, 250), 0.86f, 0.75f, 260),
            glow(Color.argb(34, 2, 214, 255), 0.08f, 0.40f, 230)
        ))
    }

    fun navigationTile(activity: Activity, symbol: String, title: String,
                       onClick: () -> Unit): LinearLayout {
        val item = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = card(activity)
            minimumHeight = dp(activity, 99)
            isFocusable = true
            isClickable = true
            setPadding(dp(activity, 7), dp(activity, 11),
                dp(activity, 7), dp(activity, 11))
            setOnClickListener { onClick() }
        }
        item.addView(icon(activity,iconName(symbol,title),cyan),
            LinearLayout.LayoutParams(dp(activity,28),dp(activity,28)))
        item.addView(TextView(activity).apply {
            text = title
            gravity = Gravity.CENTER
            textSize = sp(activity, 13.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(activity, 6), 0, 0)
        })
        if (GameLingoTVUi.isTV(activity)) GameLingoTVUi.controlFocus(activity, item)
        return item
    }

    fun cta(activity: Activity, title: String,
            onClick: () -> Unit): TextView = TextView(activity).apply {
        text = title
        // iOS uses matching leading/trailing action rails; avoid a left-shifted label.
        setCompoundDrawablesWithIntrinsicBounds(
            iconDrawable("play.fill", Color.WHITE, dp(activity, 22)), null,
            iconDrawable("arrow.right", Color.WHITE, dp(activity, 22)), null)
        compoundDrawablePadding = dp(activity, 8)
        setPadding(dp(activity, 17), 0, dp(activity, 17), 0)
        textSize = sp(activity, 20f)
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        background = primary(activity)
        minHeight = dp(activity, 52)
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick() }
        if (GameLingoTVUi.isTV(activity)) GameLingoTVUi.controlFocus(activity, this)
    }

}

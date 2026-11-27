package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Optional contextual help shared by every native GameLingo Android / TV page. */
object GameLingoHelp {
    private const val NAVY = "#070B14"
    private const val CYAN = "#38BDF8"

    private fun info(screen: String): Pair<String, String> = when (screen) {
        "languages" -> "Jazyky" to
            "Jazyk aplikace mění nabídky. Jazyk hry určuje původní text a jazyk překladu určuje výstup. Nastavují se nezávisle."
        "about" -> "O aplikaci / Licence" to
            "Tady najdeš verzi aplikace, aktualizace, původní autory a úplné licenční texty."
        "settings" -> "Nastavení TV" to
            "Jazyky, OCR a Překlad určují, co a jak se překládá. Rozložení nastavuje čtyři oblasti. Vzhled upravuje titulky a Profily ukládají konfiguraci pro hry, filmy a aplikace."
        "ocr" -> "OCR a rozpoznávání" to
            "Zvol režim rozpoznávání, filtrování menu a případně rozšířené hledání. Ruční modrá oblast omezuje čtení na místo dialogů. Před změnou zastav překlad."
        "appearance" -> "Vzhled titulků" to
            "Vyber barvu a pozadí překladu. Polohu, šířku a velikost písma upravíš v Rozložení a uložíš do profilu."
        "diagnostics" -> "Diagnostika" to
            "Test překryvu ověří zobrazení pevného titulku. Test překladového jádra ověří online překlad. Test snímání vyžaduje souhlas Androidu. Náhled lze vymazat."
        "download" -> "Stáhnout aplikaci" to
            "Vyber iPhone nebo Android a naskenuj QR kód fotoaparátem telefonu. Odkaz vede na nejnovější dostupný mobilní balíček na GitHubu. IPA vyžaduje podpis; Android APK může vyžadovat povolení instalace z tohoto zdroje. Pokud vydání nelze ověřit, použij stránku všech vydání. Toto není párovací QR kód."
        "pairing" -> "Párování telefonu" to
            "Telefon a TV připoj ke stejné síti. V mobilu otevři Zařízení, vyber TV a naskenuj QR kód nebo zadej celý párovací kód. Po vypršení platnosti vytvoř nový kód."
        "history" -> "Historie překladů" to
            "Historie je volitelná a uchovává poslední překlady v paměti. Je oddělená od místní překladové paměti."
        "cache" -> "Překladová paměť" to
            "Místní paměť ukládá originály a překlady pro opakované použití. Můžeš ji vypnout nebo vymazat bez odstranění profilů."
        "glossary" -> "Herní slovník" to
            "Slovník opravuje vlastní výrazy a jména pro zvolenou jazykovou dvojici. Před úpravou zastav překlad."
        "tv" -> "GameLingo TV" to
            "1. Otevři GameLingo na telefonu.\n2. Zvol Google TV a vyber tuto televizi.\n3. Načti QR nebo opiš celý kód z obrazovky.\n\nTelefon a TV musí být ve stejné síti. Pokud používáš starý přijímač PlayCaption, zavři jej. Přijímač může zobrazit titulky přes HDMI obraz jen na podporovaném televizoru."
        "regions" -> "Oblasti" to
            "Modrá oblast čte původní titulky. Zelená určuje místo přeložených dialogů. Žlutá čte volby a herní texty, oranžová zobrazuje jejich překlad. Polohu a rozměry měň podle konkrétní hry. Oblasti se ukládají do profilů."
        "translation" -> "Překlad" to
            "OCR čte dialogy ve zvoleném zdrojovém jazyce. Samostatně lze zapnout dialogy, herní texty a volby. Při změně rychlosti nebo oblastí doporučujeme překlad nejdříve zastavit. Výchozí nastavení vychází z ověřeného základu PlayCaption."
        "profiles" -> "Profily her" to
            "Profil ukládá aktuální OCR oblasti, volby a nastavení překladu pro konkrétní hru, film nebo aplikaci. Údaje o účtech Sony a Microsoft se do profilů neukládají."
        "console" -> "Xbox a PlayStation" to
            "Pro Xbox se přihlašuješ svým účtem Microsoft a povolíš vzdálené funkce. Pro PS5/PS4 použij Remote Play a údaje svého účtu PlayStation. Párovací PIN z konzole se liší od hesla. Do hry ani do konzole se nic neinstaluje."
        "phone" -> "Překlad na telefonu" to
            "Standardní režim funguje bez dalšího nastavování. Automatický režim používá připravenou zónu a další můžeš přidat v Oblastech obrazovky. Pokud překládáš přes TV, telefon i TV musí být ve stejné síti."
        else -> "GameLingo" to
            "Vyber Xbox, PlayStation nebo samostatnou TV, připoj zařízení a spusť překlad. Kam se přeložené titulky zobrazí, nastavíš v Zařízení. Oblasti upravují místo čtení a zobrazení, Profily her ukládají vlastní konfiguraci."
    }

    /** Fixed TV geometry; mobile callers retain their existing dimensions. */
    fun layout(activity: Activity, mobileWidth: Int = -2, mobileHeight: Int = -2): LinearLayout.LayoutParams =
        if (GameLingoTVUi.isTV(activity)) LinearLayout.LayoutParams(
            GameLingoTVUi.dp(activity,64), GameLingoTVUi.dp(activity,64)).apply {
                marginStart=GameLingoTVUi.dp(activity,12)
            }
        else LinearLayout.LayoutParams(mobileWidth,mobileHeight)

    fun button(activity: Activity, topic: String = "home",
               size: Float = 24f, news: Boolean = false): TextView = TextView(activity).apply {
        text = "?"
        textSize = GameLingoVisualKit.sp(activity, size)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor(CYAN))
        setPadding(17, 9, 17, 9)
        minWidth = 52
        minHeight = 48
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#172033"))
            cornerRadius = 16f
            setStroke(2, Color.parseColor("#2A3850"))
        }
        isClickable = true
        isFocusable = true
        contentDescription = GameLingoLanguages.ui(activity, if(news) "Co je nového" else "Otevřít nápovědu")
        setOnClickListener { if(news) showNews(activity) else show(activity, topic) }
        if (!GameLingoTVUi.isTV(activity)) {
            text = ""
            background = null
            setPadding(0,0,0,0)
            setCompoundDrawablesWithIntrinsicBounds(GameLingoVisualKit.iconDrawable(if(news) "rocket" else "questionmark.circle",Color.parseColor("#02D6FF"),GameLingoVisualKit.dp(activity,23)),null,null,null)
            minWidth=0;minHeight=0
        }
        if (GameLingoTVUi.isTV(activity)) {
            textSize = GameLingoVisualKit.sp(activity, 23f)
            setPadding(0,0,0,0)
            minWidth=GameLingoTVUi.dp(activity,64)
            minHeight=GameLingoTVUi.dp(activity,64)
            layoutParams=layout(activity)
            id=android.view.View.generateViewId()
            fun paint(focused: Boolean) {
                background=GameLingoTVUi.panel(activity,focused).apply {
                    cornerRadius=GameLingoTVUi.dp(activity,32).toFloat()
                    if(!focused) {setColor(Color.TRANSPARENT);setStroke(0,Color.TRANSPARENT)}
                }
                text=""
                val color=if(focused)Color.WHITE else Color.parseColor("#02D6FF")
                val drawable=GameLingoVisualKit.iconDrawable(if(news) "rocket" else "questionmark.circle",color,GameLingoTVUi.dp(activity,38))
                setCompoundDrawablesWithIntrinsicBounds(drawable,null,null,null)
                setPadding(GameLingoTVUi.dp(activity,13),0,0,0)
                elevation=GameLingoTVUi.dp(activity,if(focused) 10 else 0).toFloat()
            }
            setOnFocusChangeListener { _, focused -> paint(focused) }
            paint(hasFocus())
        }
    }

    fun show(activity: Activity, topic: String) {
        val (titleKey, descriptionKey) = info(topic)
        val title = GameLingoLanguages.ui(activity, titleKey)
        val description = GameLingoLanguages.ui(activity, descriptionKey)
        if (GameLingoTVUi.isTV(activity)) {
            val body=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
            body.addView(TextView(activity).apply {
                text=description; gravity = android.view.Gravity.CENTER; textSize = GameLingoVisualKit.sp(activity, 19f)
                setTextColor(Color.WHITE); setLineSpacing(5f,1.18f)
                background=GameLingoTVUi.panel(activity)
                val padding=GameLingoTVUi.dp(activity,24)
                setPadding(padding,padding,padding,padding)
            },LinearLayout.LayoutParams(-1,-2))
            GameLingoTVUi.page(activity,GameLingoLanguages.ui(activity, "Nápověda") + " · " + title,body)
            return
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(NAVY))
                cornerRadius = 24f
            }
        }
        container.addView(TextView(activity).apply {
            text = title; gravity = android.view.Gravity.CENTER
            textSize = GameLingoVisualKit.sp(activity, 23f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(6, 0, 6, 14)
        })
        container.addView(TextView(activity).apply {
            text = description; gravity = android.view.Gravity.CENTER
            textSize = GameLingoVisualKit.sp(activity, 16f)
            setTextColor(Color.parseColor("#D8E2F0"))
            setLineSpacing(4f, 1.12f)
            setPadding(6, 0, 6, 12)
        })
        val scroll = ScrollView(activity)
        scroll.addView(container)
        AlertDialog.Builder(activity)
            .setView(scroll)
            .setPositiveButton(GameLingoLanguages.ui(activity, "Hotovo"), null)
            .show()
    }

    fun newsButton(activity: Activity): TextView = button(activity, news = true)
    private fun tr(a:Activity,key:String)=GameLingoLanguages.ui(a,key)
    private fun d(a:Activity,n:Int)=if(GameLingoTVUi.isTV(a))GameLingoTVUi.dp(a,n) else GameLingoVisualKit.dp(a,n)
    private fun column(a:Activity)=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
    private fun label(a:Activity,value:String,size:Float=14f)=TextView(a).apply {
        text=value;textSize=GameLingoVisualKit.sp(a,size);setTextColor(Color.WHITE);gravity=Gravity.START
        val p=d(a,5);setPadding(p,p,p,p)
    }
    private fun version(a:Activity)=a.packageManager.getPackageInfo(a.packageName,0).versionName ?: "?"
    private const val issues="https://github.com/CaseyCZ/GameLingo/issues/new"
    private fun open(a:Activity,url:String) {
        runCatching { a.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url))) }
            .onFailure { android.widget.Toast.makeText(a,tr(a,"Nelze otevřít GitHub."),android.widget.Toast.LENGTH_LONG).show() }
    }
    fun showNews(a:Activity) {
        val body=column(a)
        val v=version(a)
        body.addView(label(a,"GameLingo "+v,18f))
        val entries=if(v=="0.5.9") listOf("Hlášení problémů v diagnostice","Raketa v horní liště otevírá novinky.") else listOf("Přehled této verze je na GitHubu.")
        entries.forEach { body.addView(label(a,"✓  "+tr(a,it))) }
        body.addView(GameLingoTVUi.action(a,tr(a,"Historie vydání")) { open(a,"https://github.com/CaseyCZ/GameLingo/releases") })
        GameLingoTVUi.page(a,tr(a,"Co je nového"),body)
    }
    // Explicit safe metadata only, no raw logs, OCR content or authentication.
    fun report(a:Activity){
        val tv=GameLingoTVUi.isTV(a)
        val body=column(a)
        val metadata="GameLingo "+version(a)+" · "+(if(tv)"Google TV" else "Android")+" "+android.os.Build.VERSION.RELEASE
        val diagnostic="\nremotePlay="+RemotePlayService.aktivni+"\nreceiver="+PrijemService.bezi
        val categories=listOf("Překlad a titulky","Připojení konzole","Párování TV","Kamera a OCR","Pád aplikace","Vzhled a ovládání","Ostatní")
        body.addView(label(a,metadata,16f))
        body.addView(label(a,tr(a,"GitHub je veřejný. Před odesláním zkontroluj hlášení; potřebuješ účet."),12f))
        if(tv){
            body.addView(label(a,tr(a,"Naskenuj QR kód telefonem a popiš problém na GitHubu.")))
            val include=android.widget.CheckBox(a).apply{text=tr(a,"Přiložit základní diagnostiku");isChecked=true;setTextColor(Color.WHITE)}
            body.addView(include)
            val image=android.widget.ImageView(a).apply{scaleType=android.widget.ImageView.ScaleType.FIT_CENTER
                contentDescription=tr(a,"Naskenuj QR kód telefonem a popiš problém na GitHubu.")}
            fun render(){
                val url=issues+"?body="+android.net.Uri.encode(metadata+if(include.isChecked)diagnostic else "")
                val qr=runCatching{
                    val m=com.google.zxing.qrcode.QRCodeWriter().encode(url,com.google.zxing.BarcodeFormat.QR_CODE,320,320)
                    val pixels=IntArray(m.width*m.height){i->if(m[i%m.width,i/m.width])Color.BLACK else Color.WHITE}
                    android.graphics.Bitmap.createBitmap(pixels,m.width,m.height,android.graphics.Bitmap.Config.ARGB_8888)
                }.getOrNull()
                image.setImageBitmap(qr)
            }
            include.setOnCheckedChangeListener{_,_->render()};render()
            body.addView(image,LinearLayout.LayoutParams(-1,d(a,220)))
            body.addView(label(a,issues,12f))
        }else{
            body.addView(label(a,tr(a,"Typ problému"),12f))
            val type=android.widget.Spinner(a).apply{adapter=android.widget.ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,categories.map{tr(a,it)})}
            body.addView(type)
            val detail=android.widget.EditText(a).apply{
                hint=tr(a,"Popis problému");minLines=3;maxLines=5;setTextColor(Color.WHITE);filters=arrayOf(android.text.InputFilter.LengthFilter(2000))
            }
            body.addView(detail)
            val include=android.widget.CheckBox(a).apply{text=tr(a,"Přiložit základní diagnostiku");isChecked=true;setTextColor(Color.WHITE)}
            body.addView(include)
            fun reportText()=metadata+"\n"+tr(a,categories[type.selectedItemPosition.coerceIn(categories.indices)])+"\n\n"+detail.text.toString()+(if(include.isChecked)diagnostic else "")
            body.addView(GameLingoTVUi.action(a,tr(a,"Zkopírovat hlášení")){
                val clipboard=a.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GameLingo",reportText()))
                android.widget.Toast.makeText(a,tr(a,"Zkopírováno"),android.widget.Toast.LENGTH_SHORT).show()
            })
            body.addView(GameLingoTVUi.action(a,tr(a,"Otevřít GitHub")){open(a,issues)})
        }
        GameLingoTVUi.page(a,tr(a,"Nahlásit problém"),body)
    }
}

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
        "news" -> "Co je nového" to "Přehled této verze je na GitHubu."
        "report" -> "Nahlásit problém" to "GitHub je veřejný. Před odesláním zkontroluj hlášení; potřebuješ účet."
        "camera" -> "Překlad kamerou" to
            "Zvol celý obraz nebo nastav žlutý rámeček na původní titulky."
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
            "1. Otevři GameLingo na telefonu.\n2. Zvol Google TV a vyber tuto televizi.\n3. Načti QR nebo opiš celý kód z obrazovky.\n\nTelefon a TV musí být ve stejné síti. Přijímač může zobrazit titulky přes HDMI obraz jen na podporovaném televizoru."
        "regions" -> "Oblasti" to
            "Modrá oblast čte původní titulky. Zelená určuje místo přeložených dialogů. Žlutá čte volby a herní texty, oranžová zobrazuje jejich překlad. Polohu a rozměry měň podle konkrétní hry. Oblasti se ukládají do profilů."
        "translation" -> "Překlad" to
            "OCR čte dialogy ve zvoleném zdrojovém jazyce. Samostatně lze zapnout dialogy, herní texty a volby. Při změně rychlosti nebo oblastí doporučujeme překlad nejdříve zastavit."
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

    private fun guide(topic:String):List<Pair<String,String>> = when(topic) {
        "camera" -> listOf("camera" to "Zvol Kameru ve výběru zařízení.","tv" to "Namiř telefon na text nebo obrazovku.","viewfinder" to "Zvol celý obraz nebo nastav žlutý rámeček na původní titulky.","text.bubble" to "Překlad se zobrazí podle vybraného režimu.")
        "glossary" -> listOf("text.bubble" to "Opiš originální výraz přesně ze hry.","character.book.closed.fill" to "Doplň chybný překlad a požadovanou opravu oddělené svislítky.","checkmark.circle" to "Ulož slovník a použij správný profil před hraním.")
        "regions" -> listOf("viewfinder" to "Modrá čte dialogy, zelená určuje jejich výstup.","rectangle.dashed" to "Žlutá čte herní texty, oranžová určuje jejich výstup.","checkmark.circle" to "Uprav oblasti podle obrazu a ulož profil.")
        "console" -> listOf("gamecontroller" to "Povol vzdálené funkce na konzoli a přihlas se ke svému účtu.","display.2" to "Vyber uloženou konzoli nebo přidej novou.","play.fill" to "Vyber zdroj obrazu a výstup titulků.")
        "tv","pairing" -> listOf("wifi" to "Připoj telefon a televizi ke stejné domácí síti.","qrcode" to "Na televizi otevři párování a v telefonu načti QR kód nebo zadej celý kód.","checkmark.circle" to "Ověř spojení a spusť překlad.")
        "home" -> listOf("gamecontroller" to "Vyber zdroj obrazu podle zařízení.","globe" to "Připoj zařízení a vyber jazyk hry a překladu.","play.fill" to "Nastav výstup a spusť překlad.")
        else -> emptyList()
    }
    private fun picture(a:Activity,topic:String,index:Int,symbol:String):android.view.View =
        android.widget.FrameLayout(a).apply {
            background=GameLingoVisualKit.card(a)
            if(topic=="glossary") addView(label(a,if(index==0)"Save game" else "Save game\nZachránit hru\n→ Uložit hru",10f).apply {
                gravity=Gravity.CENTER;setTextColor(Color.parseColor("#02D6FF"))
            },android.widget.FrameLayout.LayoutParams(-1,-1))
            else if(topic=="regions"||(topic=="camera"&&index>=2)) addView(object:android.view.View(a) {
                private val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(c:android.graphics.Canvas) {
                    val w=width.toFloat();val h=height.toFloat()
                    p.style=android.graphics.Paint.Style.STROKE;p.strokeWidth=d(a,2).toFloat()
                    p.color=if(topic=="camera")Color.YELLOW else Color.BLUE
                    c.drawRect(w*.08f,h*.22f,w*.92f,h*.48f,p)
                    p.color=if(topic=="camera")Color.TRANSPARENT else Color.GREEN
                    c.drawRect(w*.08f,h*.60f,w*.92f,h*.86f,p)
                    if(topic=="regions"&&index==1) {
                        p.color=Color.YELLOW;c.drawRect(w*.58f,h*.02f,w*.92f,h*.20f,p)
                        p.color=Color.rgb(249,115,22);c.drawRect(w*.58f,h*.87f,w*.92f,h*.99f,p)
                    }
                    p.style=android.graphics.Paint.Style.FILL;p.color=Color.WHITE;p.textAlign=android.graphics.Paint.Align.CENTER;p.textSize=minOf(w*.07f,h*.18f)
                    c.drawText("Where are you going?",w/2,h*.40f,p);c.drawText("Kam jdeš?",w/2,h*.77f,p)
                }
            },android.widget.FrameLayout.LayoutParams(-1,-1))
            else addView(GameLingoVisualKit.icon(a,symbol,Color.parseColor("#02D6FF")),
                android.widget.FrameLayout.LayoutParams(d(a,34),d(a,34),Gravity.CENTER))
        }

    fun show(activity: Activity, topic: String) {
        val(titleKey,descriptionKey)=info(topic)
        val title=GameLingoLanguages.ui(activity,titleKey)
        val description=GameLingoLanguages.ui(activity, descriptionKey)
        val custom=guide(topic)
        val steps=if(custom.isNotEmpty())custom.map{it.first to tr(activity,it.second)}
            else description.split(". ").filter{it.isNotBlank()}.map{"info.circle" to it}
        val body=column(activity)
        var selected=0
        val details=column(activity).apply{visibility=android.view.View.GONE}
        val enlarged=android.widget.FrameLayout(activity)
        val explanation=label(activity,"")
        details.addView(enlarged,LinearLayout.LayoutParams(-1,d(activity,110)))
        details.addView(explanation)
        val nav=LinearLayout(activity)
        val previous=GameLingoTVUi.action(activity,tr(activity,"Předchozí krok")){}
        val next=GameLingoTVUi.action(activity,tr(activity,"Další krok")){}
        nav.addView(previous,LinearLayout.LayoutParams(0,-2,1f));nav.addView(next,LinearLayout.LayoutParams(0,-2,1f))
        details.addView(nav)
        fun select(index:Int) {
            if(index !in steps.indices)return
            selected=index;details.visibility=android.view.View.VISIBLE;enlarged.removeAllViews()
            enlarged.addView(picture(activity,topic,index,steps[index].first),android.widget.FrameLayout.LayoutParams(-1,-1))
            explanation.text=steps[index].second
            previous.isEnabled=index>0;next.isEnabled=index<steps.lastIndex
        }
        previous.setOnClickListener{select(selected-1)};next.setOnClickListener{select(selected+1)}
        steps.forEachIndexed{i,step->
            val row=LinearLayout(activity).apply {
                gravity=Gravity.CENTER_VERTICAL;setPadding(0,d(activity,3),0,d(activity,3));isClickable=true;isFocusable=true
                contentDescription=(i+1).toString()+". "+step.second
                setOnClickListener{select(i)}
            }
            row.addView(picture(activity,topic,i,step.first),LinearLayout.LayoutParams(d(activity,96),d(activity,62)))
            row.addView(label(activity,(i+1).toString()+". "+step.second),LinearLayout.LayoutParams(0,-2,1f))
            body.addView(row)
        }
        body.addView(details)
        details.addView(GameLingoTVUi.action(activity,tr(activity,"Hotovo")){details.visibility=android.view.View.GONE})
        if(custom.isNotEmpty())body.addView(label(activity,description,12f))
        if(topic in listOf("camera","regions","glossary")) {
            val practice=column(activity).apply{visibility=android.view.View.GONE}
            body.addView(GameLingoTVUi.action(activity,tr(activity,"Vyzkoušet")){
                practice.visibility=if(practice.visibility==android.view.View.VISIBLE)android.view.View.GONE else android.view.View.VISIBLE
            })
            practice.addView(label(activity,tr(activity,"Ukázka bez změny nastavení"),12f))
            if(topic=="glossary") {
                practice.addView(label(activity,tr(activity,"Příklad pravidla (angličtina → čeština)"),12f))
                practice.addView(label(activity,"Save game | Zachránit hru | Uložit hru",12f))
                val result=label(activity,"Zachránit hru")
                practice.addView(android.widget.Switch(activity).apply{
                    text=tr(activity,"Použít ukázkové pravidlo");setTextColor(Color.WHITE)
                    setOnCheckedChangeListener{_,checked->result.text=if(checked)"Uložit hru" else "Zachránit hru"}
                });practice.addView(result)
            } else {
                var position=25;var full=false
                val demo=object:android.view.View(activity) {
                    private val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    override fun onDraw(c:android.graphics.Canvas){
                        val w=width.toFloat();val h=height.toFloat()
                        p.color=Color.parseColor("#111A33");p.style=android.graphics.Paint.Style.FILL;c.drawRect(0f,0f,w,h,p)
                        p.color=Color.WHITE;p.textAlign=android.graphics.Paint.Align.CENTER;p.textSize=d(activity,13).toFloat()
                        c.drawText("Where are you going?",w/2,h*.60f,p)
                        val y=h*(.10f+.65f*position/100f)
                        if(!full){p.color=if(topic=="camera")Color.YELLOW else Color.BLUE;p.style=android.graphics.Paint.Style.STROKE;p.strokeWidth=d(activity,2).toFloat();c.drawRect(w*.08f,y-h*.10f,w*.92f,y+h*.10f,p)}
                        if(full||kotlin.math.abs(y-h*.60f)<h*.09f){p.style=android.graphics.Paint.Style.FILL;p.color=Color.WHITE;c.drawText("Kam jdeš?",w/2,h*.90f,p)}
                    }
                }
                practice.addView(demo,LinearLayout.LayoutParams(-1,d(activity,116)))
                val seek=android.widget.SeekBar(activity).apply {
                    max=100;progress=25;contentDescription=tr(activity,"Posuň oblast na původní titulek.")
                    setOnSeekBarChangeListener(object:android.widget.SeekBar.OnSeekBarChangeListener{
                        override fun onProgressChanged(s:android.widget.SeekBar?,value:Int,fromUser:Boolean){position=value;demo.invalidate()}
                        override fun onStartTrackingTouch(s:android.widget.SeekBar?){}
                        override fun onStopTrackingTouch(s:android.widget.SeekBar?){}
                    })
                }
                practice.addView(seek)
                if(topic=="camera")practice.addView(android.widget.Switch(activity).apply{
                    text=tr(activity,"Celý obraz");setTextColor(Color.WHITE);setOnCheckedChangeListener{_,checked->full=checked;seek.isEnabled=!checked;demo.invalidate()}
                })
            }
            body.addView(practice)
        }
        GameLingoTVUi.page(activity,GameLingoLanguages.ui(activity, "Nápověda") + " · " + title,body)
        // Hidden practice controls must also have focus styling when revealed.
        GameLingoTVUi.applyMenuFocus(activity,body)
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
        val entries=if(v.substringBefore('-')=="0.5.9") listOf("Obrázková a interaktivní nápověda","Hlášení problémů v diagnostice","Raketa v horní liště otevírá novinky.") else listOf("Přehled této verze je na GitHubu.")
        entries.forEach { body.addView(label(a,"✓  "+tr(a,it))) }
        body.addView(GameLingoTVUi.action(a,tr(a,"Historie vydání")) { open(a,"https://github.com/CaseyCZ/GameLingo/releases") })
        GameLingoTVUi.page(a,tr(a,"Co je nového"),body,helpTopic="news")
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
        GameLingoTVUi.page(a,tr(a,"Nahlásit problém"),body,helpTopic="report")
    }
}

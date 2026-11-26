package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

internal object GameLingoQRDisplay {
    fun bitmap(ip: String, pin: String): Bitmap? {
        val payload = GameLingoPairingQR.encode(ip, pin) ?: return null
        if (IPhoneTVPairing.activeCode() != pin) return null
        return runCatching {
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 360, 360)
            val pixels = IntArray(matrix.width * matrix.height) { i ->
                if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    fun show(activity: Activity, ip: String, pin: String) {
        val payload = GameLingoPairingQR.encode(ip, pin)
        if (payload == null || IPhoneTVPairing.activeCode() != pin) {
            AlertDialog.Builder(activity)
                .setMessage(GameLingoLanguages.ui(activity, "QR není dostupný. Zkontroluj místní IP a platnost PINu, nebo použij ruční párování."))
                .setPositiveButton("OK", null).show()
            return
        }
        val codeBitmap = bitmap(ip,pin)
        if (codeBitmap == null) {
            android.widget.Toast.makeText(activity,GameLingoLanguages.ui(activity, "QR kód není dostupný."),android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val television = GameLingoTVUi.isTV(activity)
        val image = ImageView(activity).apply {
            setImageBitmap(codeBitmap)
            (drawable as? android.graphics.drawable.BitmapDrawable)?.isFilterBitmap = false
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = GameLingoLanguages.ui(activity, "Dočasný párovací QR kód")
        }
        val label = TextView(activity).apply {
            gravity = Gravity.CENTER
            textSize = GameLingoVisualKit.sp(activity, if(television) 16f else 14f)
            setTextColor(Color.WHITE)
            setPadding(12,12,12,12)
        }
        val instructions = GameLingoTVUi.title(activity,
            GameLingoLanguages.ui(activity, "V mobilní aplikaci otevři Zařízení a naskenuj QR kód."),20f,muted=true).apply {
            gravity=Gravity.CENTER
        }
        val metrics=activity.resources.displayMetrics
        val size=if(television) minOf((metrics.widthPixels*.42f).toInt(),(metrics.heightPixels*.55f).toInt())
            else minOf(metrics.widthPixels/2,metrics.heightPixels/2,420)
        val content=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            setPadding(16,12,16,12)
            addView(instructions)
            addView(image,LinearLayout.LayoutParams(size,size).apply{topMargin=12;bottomMargin=12})
            addView(label)
        }
        val handler=Handler(Looper.getMainLooper())
        var dialog:android.app.Dialog?=null
        val tick=object:Runnable {
            override fun run() {
                if(dialog?.isShowing != true || activity.isFinishing || activity.isDestroyed) return
                if(IPhoneTVPairing.activeCode()!=pin) {
                    image.setImageDrawable(null)
                    image.visibility=View.GONE
                    label.text=GameLingoLanguages.ui(activity, "Kód již není platný. Vrať se zpět a vygeneruj nový.")
                    return
                }
                label.text=pin.chunked(8).joinToString(" ")+"  ·  "+GameLingoLanguages.ui(activity, "zbývá")+" "+IPhoneTVPairing.remainingSeconds()+" s"
                handler.postDelayed(this,500L)
            }
        }
        fun cleanup() {
            handler.removeCallbacks(tick)
            image.setImageDrawable(null)
        }
        content.background=GameLingoVisualKit.card(activity)
        dialog=GameLingoTVUi.page(activity,GameLingoLanguages.ui(activity, "Párování pomocí QR"),content,helpTopic="pairing",onClose=::cleanup)
        tick.run()
    }
}


/**
 * Public mobile installation links, separate from expiring private pairing QR codes.
 * The project's releases are prereleases, so /releases/latest cannot be used.
 * No credentials, device identifiers or local network details are transmitted.
 */
internal object GameLingoMobileDownloads {
    private const val RELEASES="https://github.com/CaseyCZ/GameLingo/releases"
    private const val API="https://api.github.com/repos/CaseyCZ/GameLingo/releases?per_page=20"
    private data class MobileRelease(val tag:String,val ios:String,val android:String)

    private fun fetch():MobileRelease? {
        val connection=(java.net.URL(API).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout=6000;readTimeout=8000
            setRequestProperty("Accept","application/vnd.github+json")
            setRequestProperty("User-Agent","GameLingo-TV")
            instanceFollowRedirects=false
        }
        return try {
            if(connection.responseCode!=200)return null
            val response=connection.inputStream.bufferedReader(Charsets.UTF_8).use{reader->
                val out=StringBuilder();val buf=CharArray(4096)
                while(true){val n=reader.read(buf);if(n<0)break
                    out.append(buf,0,n);if(out.length>1_000_000)return null
                }
                out.toString()
            }
            val releases=org.json.JSONArray(response)
            val candidates=mutableListOf<Pair<String,MobileRelease>>()
            for(i in 0 until releases.length()){
                val release=releases.optJSONObject(i)?:continue
                if(release.optBoolean("draft"))continue
                val tag=release.optString("tag_name")
                if(!tag.matches(Regex("[A-Za-z0-9._-]{1,150}")))continue
                val assets=release.optJSONArray("assets")?:continue
                var ipa:String?=null;var apk:String?=null
                for(j in 0 until assets.length()){
                    val asset=assets.optJSONObject(j)?:continue
                    val name=asset.optString("name")
                    val url=asset.optString("browser_download_url")
                    if(asset.optLong("size")<=0||asset.optString("state")!="uploaded")continue
                    if(url!=RELEASES+"/download/"+tag+"/"+name)continue
                    if(name.matches(Regex("GameLingo-iOS-[A-Za-z0-9._-]+[.]ipa")))ipa=url
                    if(name.matches(Regex("GameLingo-Android-[A-Za-z0-9._-]+[.]apk")))apk=url
                }
                val published=release.optString("published_at")
                if(ipa!=null&&apk!=null&&published.isNotBlank())candidates.add(published to MobileRelease(tag,ipa,apk))
            }
            candidates.maxByOrNull{it.first}?.second
        } finally {connection.disconnect()}
    }
    fun show(activity:Activity) {
        if(activity.isFinishing)return
        val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
        body.addView(GameLingoTVUi.title(activity,GameLingoLanguages.ui(activity, "Vyber svůj mobilní telefon."),20f,muted=true))
        val row=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;clipChildren=false;clipToPadding=false}
        var page:android.app.Dialog?=null
        val choices=listOf(Triple("iPhone",GameLingoLanguages.ui(activity, "IPA · instalace s podpisem"),true),Triple("Android",GameLingoLanguages.ui(activity, "APK · mobilní verze"),false))
        choices.forEachIndexed { i,item->
            val card=GameLingoTVUi.tile(activity,"smartphone",item.first,item.second){
                page?.dismiss();showPackage(activity,item.third)
            }
            row.addView(card,LinearLayout.LayoutParams(0,-2,1f).apply{if(i==0)marginEnd=GameLingoTVUi.dp(activity,16)})
        }
        body.addView(row,LinearLayout.LayoutParams(-1,-2).apply{topMargin=GameLingoTVUi.dp(activity,16)})
        page=GameLingoTVUi.page(activity,GameLingoLanguages.ui(activity, "Stáhnout GameLingo"),body,helpTopic="download")
    }
    private fun showPackage(activity:Activity,ios:Boolean) {
        val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL}
        val name=if(ios)"iPhone · IPA" else "Android · APK"
        val status=GameLingoTVUi.title(activity,GameLingoLanguages.ui(activity, "Ověřuji aktuální vydání…"),22f)
        val link=GameLingoTVUi.title(activity,"",14f,muted=true).apply{
            setTextIsSelectable(false);gravity=Gravity.CENTER
        }
        val image=ImageView(activity).apply{
            scaleType=ImageView.ScaleType.FIT_CENTER
            contentDescription=GameLingoLanguages.ui(activity, "QR kód ke stažení")+" "+name
        }
        val metrics=activity.resources.displayMetrics
        val size=minOf((metrics.widthPixels*.38f).toInt(),(metrics.heightPixels*.46f).toInt())
        body.addView(status)
        body.addView(image,LinearLayout.LayoutParams(size,size).apply{
            topMargin=GameLingoTVUi.dp(activity,12);bottomMargin=GameLingoTVUi.dp(activity,12)
        })
        body.addView(link,LinearLayout.LayoutParams(-1,-2))
        val instructions=if(ios)
            GameLingoLanguages.ui(activity, "Naskenuj QR kód fotoaparátem iPhonu. IPA je testovací balíček; před instalací vyžaduje podpis, například přes AltStore nebo Sideloadly.")
            else GameLingoLanguages.ui(activity, "Naskenuj QR kód telefonem s Androidem, stáhni mobilní APK a potvrď instalaci. Systém může vyžadovat povolení instalace z tohoto zdroje.")
        body.addView(GameLingoTVUi.title(activity,instructions,18f,muted=true).apply{
            setPadding(0,GameLingoTVUi.dp(activity,14),0,GameLingoTVUi.dp(activity,8))
        })
        var url=RELEASES
        val open=GameLingoTVUi.action(activity,GameLingoLanguages.ui(activity, "Otevřít odkaz")){
            runCatching{activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url)))}
                .onFailure{android.widget.Toast.makeText(activity,GameLingoLanguages.ui(activity, "Otevři QR kód v telefonu."),android.widget.Toast.LENGTH_SHORT).show()}
        }
        open.isEnabled=false
        body.addView(open)
        val again=GameLingoTVUi.action(activity,GameLingoLanguages.ui(activity, "Znovu ověřit vydání")){}
        again.isEnabled=false
        body.addView(again,LinearLayout.LayoutParams(-1,-2).apply{topMargin=GameLingoTVUi.dp(activity,8)})
        val dialog=GameLingoTVUi.page(activity,name,body,helpTopic="download")
        fun resolve(){
            status.text=GameLingoLanguages.ui(activity, "Ověřuji aktuální vydání…");image.setImageDrawable(null);link.text=""
            open.isEnabled=false;again.isEnabled=false
            Thread {
                val release=runCatching{fetch()}.getOrNull()
                val target=if(release==null)RELEASES else if(ios)release.ios else release.android
                val bitmap=runCatching{
                    val matrix=QRCodeWriter().encode(target,BarcodeFormat.QR_CODE,480,480)
                    Bitmap.createBitmap(IntArray(matrix.width*matrix.height){i->
                        if(matrix[i%matrix.width,i/matrix.width])Color.BLACK else Color.WHITE
                    },matrix.width,matrix.height,Bitmap.Config.ARGB_8888)
                }.getOrNull()
                activity.runOnUiThread{
                    if(!dialog.isShowing||activity.isFinishing||activity.isDestroyed)return@runOnUiThread
                    url=target
                    status.text=if(release!=null)GameLingoLanguages.ui(activity, "Aktuální vydání")+": "+release.tag else GameLingoLanguages.ui(activity, "Vydání se nepodařilo ověřit · stránka všech vydání")
                    image.setImageBitmap(bitmap)
                    (image.drawable as? android.graphics.drawable.BitmapDrawable)?.isFilterBitmap=false
                    link.text=target
                    open.isEnabled=true;again.isEnabled=true
                }
            }.apply{this.name="gamelingo-mobile-release";isDaemon=true}.start()
        }
        again.setOnClickListener{resolve()}
        resolve()
    }
}

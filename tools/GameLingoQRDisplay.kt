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
                .setMessage("QR není dostupný. Zkontroluj místní IP a platnost PINu, nebo použij ruční párování.")
                .setPositiveButton("OK", null).show()
            return
        }
        val codeBitmap = bitmap(ip,pin)
        if (codeBitmap == null) {
            android.widget.Toast.makeText(activity,"QR kód není dostupný.",android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val television = GameLingoTVUi.isTV(activity)
        val image = ImageView(activity).apply {
            setImageBitmap(codeBitmap)
            (drawable as? android.graphics.drawable.BitmapDrawable)?.isFilterBitmap = false
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Dočasný párovací QR kód"
        }
        val label = TextView(activity).apply {
            gravity = Gravity.CENTER
            textSize = if(television) 22f else 18f
            setTextColor(Color.WHITE)
            setPadding(12,12,12,12)
        }
        val instructions = GameLingoTVUi.title(activity,
            "V mobilní aplikaci otevři Zařízení a naskenuj QR kód.",20f,muted=true).apply {
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
                    label.text="Kód již není platný. Vrať se zpět a vygeneruj nový."
                    return
                }
                label.text="PIN: "+pin+"  ·  zbývá "+IPhoneTVPairing.remainingSeconds()+" s"
                handler.postDelayed(this,500L)
            }
        }
        fun cleanup() {
            handler.removeCallbacks(tick)
            image.setImageDrawable(null)
        }
        content.background=GameLingoVisualKit.card(activity)
        dialog=GameLingoTVUi.page(activity,"Párování pomocí QR",content,helpTopic="pairing",onClose=::cleanup)
        tick.run()
    }
}

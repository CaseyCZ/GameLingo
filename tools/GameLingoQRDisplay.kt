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
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 360, 360)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE
        }
        val bitmap = Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        val image = ImageView(activity).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Dočasný párovací QR kód"
        }
        val label = TextView(activity).apply {
            gravity = Gravity.CENTER
            textSize = 18f
            setPadding(12, 12, 12, 12)
        }
        val size = minOf(activity.resources.displayMetrics.widthPixels / 2,
            activity.resources.displayMetrics.heightPixels / 2, 420)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(image, LinearLayout.LayoutParams(size, size))
            addView(label)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Párování pomocí QR")
            .setView(content).setPositiveButton("Zavřít", null).create()
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                if (!dialog.isShowing || activity.isFinishing) return
                if (IPhoneTVPairing.activeCode() != pin) {
                    image.setImageDrawable(null)
                    image.visibility = View.GONE
                    label.text = "Kód již není platný. Vygeneruj nový."
                    return
                }
                label.text = "PIN: " + pin + " · zbývá " + IPhoneTVPairing.remainingSeconds() + " s"
                handler.postDelayed(this, 500)
            }
        }
        dialog.setOnDismissListener {
            handler.removeCallbacks(tick)
            image.setImageDrawable(null)
        }
        dialog.show()
        tick.run()
    }
}

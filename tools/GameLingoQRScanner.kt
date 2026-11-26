package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import com.google.zxing.integration.android.IntentIntegrator
import java.lang.ref.WeakReference

/** Camera UI only in the mobile APK. No automatic handshake. */
internal object GameLingoQRScanner {
    private const val REQUEST = 0x474c
    private var owner: WeakReference<Activity>? = null
    private var callback: ((GameLingoPairingQR) -> Unit)? = null

    fun scan(activity: Activity, onCode: (GameLingoPairingQR) -> Unit) {
        if (activity.isFinishing || RemotePlayService.aktivni) return
        owner = WeakReference(activity)
        callback = onCode
        try {
            IntentIntegrator(activity).setRequestCode(REQUEST)
                .setDesiredBarcodeFormats(listOf(IntentIntegrator.QR_CODE))
                .setPrompt(GameLingoLanguages.ui(activity, "Načti QR kód z GameLingo TV"))
                .setBeepEnabled(false).setOrientationLocked(false)
                .setBarcodeImageEnabled(false).initiateScan()
        } catch (_: Exception) {
            clear(activity)
            AlertDialog.Builder(activity).setMessage(GameLingoLanguages.ui(activity, "Fotoaparát není dostupný. Použij ruční PIN."))
                .setPositiveButton("OK", null).show()
        }
    }
    fun result(activity: Activity, request: Int, result: Int, data: Intent?): Boolean {
        if (request != REQUEST) return false
        if (owner?.get() !== activity) return true
        val deliver = callback
        clear(activity)
        if (result != Activity.RESULT_OK || activity.isFinishing || RemotePlayService.aktivni) return true
        val parsed = data?.takeIf { it.getStringExtra("SCAN_RESULT_FORMAT") == "QR_CODE" }
            ?.getStringExtra("SCAN_RESULT")?.let { GameLingoPairingQR.parse(it) }
        if (parsed == null) {
            AlertDialog.Builder(activity).setMessage(GameLingoLanguages.ui(activity, "Neplatný GameLingo QR kód. Použij nový kód z TV."))
                .setPositiveButton("OK", null).show()
        } else deliver?.invoke(parsed)
        return true
    }
    fun clear(activity: Activity) {
        if (owner?.get() === activity) { owner = null; callback = null }
    }
}

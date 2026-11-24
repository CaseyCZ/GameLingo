package cz.titulky.tv
import android.app.Activity
// Receiver APK has no camera scanner dependency.
internal object GameLingoQRScanner {
    fun scan(activity: Activity, onCode: (GameLingoPairingQR) -> Unit) = Unit
}

package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Main-thread-only, transient console login PIN; never persisted or logged. */
object GameLingoPSLoginPIN {
    private data class Request(val owner: Any, val submit: (String) -> Unit, val cancel: () -> Unit)
    private var pending: Request? = null
    private var activity: Activity? = null
    private var dialog: AlertDialog? = null
    private var field: EditText? = null

    fun request(owner: Any, submit: (String) -> Unit, cancel: () -> Unit) {
        close()
        pending = Request(owner, submit, cancel)
        show()
    }

    fun clear(owner: Any) {
        if (pending?.owner !== owner) return
        pending = null
        close()
    }

    fun resume(a: Activity) {
        if (activity !== a) close()
        activity = a
        show()
    }

    fun pause(a: Activity) {
        if (activity !== a) return
        activity = null
        close()
    }

    private fun close() {
        field?.text?.clear()
        field = null
        val old = dialog
        dialog = null
        old?.dismiss()
    }

    private fun show() {
        val a = activity ?: return
        val request = pending ?: return
        if (dialog != null || a.isFinishing || a.isDestroyed) return
        fun tr(key: String) = GameLingoLanguages.ui(a, key)
        val body = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (a.resources.displayMetrics.density * 20).toInt()
            setPadding(pad, pad, pad, pad)
        }
        body.addView(TextView(a).apply { text = tr("Zadej PIN uživatele konzole, ne osmimístný párovací kód.") })
        val input = EditText(a).apply {
            hint = tr("PIN")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
            isSaveEnabled = false
            setSingleLine()
        }
        body.addView(input)
        field = input
        val prompt = AlertDialog.Builder(a)
            .setTitle(tr("Přihlašovací PIN PlayStation"))
            .setView(body)
            .setPositiveButton(tr("Pokračovat"), null)
            .setNegativeButton(tr("Zrušit")) { _, _ ->
                if (pending === request) { pending = null; close(); request.cancel() }
            }
            .setCancelable(false)
            .create()
        dialog = prompt
        prompt.show()
        prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (pending !== request) { close(); return@setOnClickListener }
            val pin = input.text.toString()
            if (pin.length != 4 || pin.any { it !in '0'..'9' }) {
                input.error = tr("Zadej přihlašovací PIN PlayStation")
                return@setOnClickListener
            }
            pending = null
            close()
            request.submit(pin)
        }
    }
}

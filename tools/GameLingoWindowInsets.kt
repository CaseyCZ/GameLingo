package cz.titulky.tv

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager

/** Preserve existing centered content while Android 15/16 draw windows edge-to-edge. */
internal object GameLingoWindowInsets {
    private val installed = java.util.WeakHashMap<View, Boolean>()
    fun apply(window: Window) {
        if (Build.VERSION.SDK_INT < 35 ||
            window.context.applicationInfo.targetSdkVersion < 35) return
        val content = window.findViewById<View>(android.R.id.content) ?: return
        if (installed.put(content, true) != null) return
        window.setDecorFitsSystemWindows(false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val left = content.paddingLeft
        val top = content.paddingTop
        val right = content.paddingRight
        val bottom = content.paddingBottom
        content.setOnApplyWindowInsetsListener { v, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsets.Type.ime())
            v.setPadding(left + safe.left, top + safe.top, right + safe.right,
                bottom + maxOf(safe.bottom, keyboard.bottom))
            // The content container has handled these insets once. Avoid
            // applying the same padding again in nested legacy layouts.
            WindowInsets.CONSUMED
        }
        content.requestApplyInsets()
    }
}

class GameLingoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityPostCreated(activity: Activity, state: Bundle?) {
                GameLingoWindowInsets.apply(activity.window)
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

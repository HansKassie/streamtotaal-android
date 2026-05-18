package nl.streamfix

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager

/** Detecteert of de app op een tv (Android TV / Google TV) draait. */
object DeviceMode {
    fun isTelevision(context: Context): Boolean {
        val ui = context.getSystemService(Context.UI_MODE_SERVICE)
            as? UiModeManager
        if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("android.hardware.type.television")
    }
}

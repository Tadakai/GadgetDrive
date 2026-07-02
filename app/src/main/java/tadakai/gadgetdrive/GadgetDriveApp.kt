package tadakai.gadgetdrive

import android.app.Application
import com.google.android.material.color.DynamicColors

/**
 * Application entry point.
 * Applies Material You dynamic colours on Android 12+;
 * falls back to the static M3 palette on older devices.
 */
class GadgetDriveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
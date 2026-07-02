package tadakai.gadgetdrive

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG       = "MainActivity"
        private const val PREF_FILE = "gadgetdrive_prefs"
        private const val KEY_PATH  = "source_path"
        private const val KEY_RO    = "read_only"
    }

    private enum class MountState { IDLE, WORKING, MOUNTED }

    // Views
    private lateinit var progressIndicator : LinearProgressIndicator
    private lateinit var statusDot         : ImageView
    private lateinit var statusTitle       : TextView
    private lateinit var statusSubtitle    : TextView
    private lateinit var fileNameText      : TextView
    private lateinit var fileSizeText      : TextView
    private lateinit var readOnlySwitch    : MaterialSwitch
    private lateinit var logText           : TextView
    private lateinit var logScroll         : NestedScrollView
    private lateinit var mountButton       : MaterialButton
    private lateinit var unmountButton     : MaterialButton

    private var selectedFilePath: String? = null
    private val mainScope  = MainScope()
    private val logBuilder = StringBuilder()
    private val prefs by lazy { getSharedPreferences(PREF_FILE, MODE_PRIVATE) }
    private lateinit var filePicker: ActivityResultLauncher<String>

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) handlePickedUri(uri)
        }

        bindViews()
        restorePreferences()
        setStatus(MountState.IDLE)
        appendLog("GadgetDrive ready.")
        appendLog("Tap the file card to select a disk image, then tap Mount.")
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }

    // -------------------------------------------------------------------------
    // Menu
    // -------------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> { showAboutDialog(); true }
            else              -> super.onOptionsItemSelected(item)
        }
    }

    // -------------------------------------------------------------------------
    // View wiring
    // -------------------------------------------------------------------------

    private fun bindViews() {
        progressIndicator = findViewById(R.id.progress_indicator)
        statusDot         = findViewById(R.id.status_dot)
        statusTitle       = findViewById(R.id.status_title)
        statusSubtitle    = findViewById(R.id.status_subtitle)
        fileNameText      = findViewById(R.id.file_name_text)
        fileSizeText      = findViewById(R.id.file_size_text)
        readOnlySwitch    = findViewById(R.id.read_only_switch)
        logText           = findViewById(R.id.log_text)
        logScroll         = findViewById(R.id.log_scroll)
        mountButton       = findViewById(R.id.mount_button)
        unmountButton     = findViewById(R.id.unmount_button)

        findViewById<View>(R.id.file_card).setOnClickListener { filePicker.launch("*/*") }
        mountButton.setOnClickListener   { onMountClicked() }
        unmountButton.setOnClickListener { onUnmountClicked() }

        readOnlySwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_RO, checked).apply()
        }
    }

    private fun restorePreferences() {
        selectedFilePath         = prefs.getString(KEY_PATH, null)
        readOnlySwitch.isChecked = prefs.getBoolean(KEY_RO, true)
        updateFileCard()
    }

    // -------------------------------------------------------------------------
    // File picking
    // -------------------------------------------------------------------------

    private fun handlePickedUri(uri: Uri) {
        // Resolve content URI → real path, identical to FilePickerPreference in original
        val path = PathResolver.getPath(this, uri)
        Log.d(TAG, "handlePickedUri: uri=$uri → path=$path")
        if (path != null) {
            selectedFilePath = path
            prefs.edit().putString(KEY_PATH, path).apply()
            updateFileCard()
            appendLog("Selected: $path")
        } else {
            // PathResolver returned null — the URI cannot be resolved to a real path.
            // This happens on Android 10+ when the file is in a sandboxed location.
            showSnackbar(getString(R.string.error_path_resolve))
            appendLog("[!] Could not resolve file path from URI.")
            appendLog("    URI: $uri")
            appendLog("    Try selecting a file from internal storage (e.g. /sdcard/Downloads).")
        }
    }

    private fun updateFileCard() {
        val file = selectedFilePath?.let { File(it) }
        fileNameText.text = file?.name ?: getString(R.string.file_none_selected)
        if (file != null && file.exists()) {
            val mib = file.length().toDouble() / (1 shl 20)
            fileSizeText.text       = getString(R.string.file_size_mib, mib)
            fileSizeText.visibility = View.VISIBLE
        } else {
            fileSizeText.visibility = View.GONE
        }
    }


    private fun onMountClicked() {
        val path = selectedFilePath

        if (path.isNullOrBlank()) {
            showSnackbar(getString(R.string.error_no_file))
            appendLog("[!] No image file selected.")
            return
        }
        if (!File(path).exists()) {
            showSnackbar(getString(R.string.error_file_not_found))
            appendLog("[!] File does not exist: $path")
            return
        }

        val file = "(.)".toRegex().replace(path, "\\\\$1")
        val ro   = if (readOnlySwitch.isChecked) "1" else "0"

        clearLog()
        appendLog("\u25b6 Mount")
        appendLog("  path     : $path")
        appendLog("  escaped  : $file")
        appendLog("  read-only: $ro")
        appendLog("")
        appendLog("  Requesting root access\u2026")
        setStatus(MountState.WORKING)
        setUiEnabled(false)

        mainScope.launch {
            delay(80)
            appendLog("  Running shell script\u2026")

            val result = withContext(Dispatchers.IO) { MountManager.mount(file, ro) }

            appendLog("")
            if (result.output.isNotEmpty()) {
                appendLog("\u2500\u2500 shell output \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500")
                result.output.filter { it.isNotBlank() }.forEach { appendLog("  $it") }
                appendLog("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500")
                appendLog("")
            }

            if (result.success) {
                appendLog("\u2713 Device should now appear on your PC.")
                setStatus(MountState.MOUNTED)
            } else {
                appendLog("\u2717 Failed \u2014 could not get root access.")
                setStatus(MountState.IDLE)
            }
            setUiEnabled(true)
        }
    }


    private fun onUnmountClicked() {
        clearLog()
        appendLog("\u25b6 Unmount")
        appendLog("  Requesting root access\u2026")
        setStatus(MountState.WORKING)
        setUiEnabled(false)

        mainScope.launch {
            delay(80)
            appendLog("  Running shell script\u2026")

            val result = withContext(Dispatchers.IO) { MountManager.unmount() }

            appendLog("")
            if (result.output.isNotEmpty()) {
                appendLog("\u2500\u2500 shell output \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500")
                result.output.filter { it.isNotBlank() }.forEach { appendLog("  $it") }
                appendLog("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500")
                appendLog("")
            }

            if (result.success) {
                appendLog("\u2713 Device should now disappear from your PC.")
            } else {
                appendLog("\u2717 Failed \u2014 could not get root access.")
            }
            setStatus(MountState.IDLE)
            setUiEnabled(true)
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        mountButton.isEnabled   = enabled
        unmountButton.isEnabled = enabled
        progressIndicator.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun setStatus(state: MountState) {
        val (title, subtitle, colorRes) = when (state) {
            MountState.IDLE    -> Triple(getString(R.string.status_idle),    getString(R.string.status_idle_sub),    R.color.status_idle)
            MountState.WORKING -> Triple(getString(R.string.status_working), getString(R.string.status_working_sub), R.color.status_working)
            MountState.MOUNTED -> Triple(getString(R.string.status_mounted), getString(R.string.status_mounted_sub), R.color.status_mounted)
        }
        statusTitle.text    = title
        statusSubtitle.text = subtitle
        statusDot.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }

    private fun appendLog(line: String) {
        if (logBuilder.isNotEmpty()) logBuilder.append('\n')
        logBuilder.append(line)
        logText.text = logBuilder
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun clearLog() {
        logBuilder.clear()
        logText.text = ""
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(R.id.coordinator), message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
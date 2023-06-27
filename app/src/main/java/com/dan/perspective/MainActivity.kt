package com.dan.perspective

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    companion object {
        val PERMISSIONS = arrayOf<String>(
        )

        const val REQUEST_PERMISSIONS = 1
        const val SELECT_SAVE_FOLDER = 2
    }

    private val stack = mutableListOf<Pair<String, AppFragment>>()
    val settings: Settings by lazy { Settings(this) }
    var selectFolderCallback: (()->Unit)? = null

    fun startSelectFolder( callback: (()->Unit)? = null ) {
        this.selectFolderCallback = callback
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.putExtra(Intent.EXTRA_TITLE, "Select save folder")
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        intent.addFlags(
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        startActivityForResult(intent, SELECT_SAVE_FOLDER)
    }

    private fun popView(homeButton: Boolean = false): Boolean {
        if (stack.size <= 1) return false

        val prevFragment = stack.removeLast().second
        val item = stack.last()

        prevFragment.onBack(homeButton)

        val transaction = supportFragmentManager.beginTransaction()
        transaction.show(item.second)
        transaction.remove(prevFragment)
        transaction.commit()

        item.second.onActivate()

        supportActionBar?.setDisplayHomeAsUpEnabled(stack.size > 1)
        supportActionBar?.title = item.first

        return true
    }

    fun pushView(title: String, fragment: AppFragment) {
        val prevFragment: AppFragment? = if (stack.isEmpty()) null else stack.last().second

        stack.add( Pair(title, fragment) )

        val transaction = supportFragmentManager.beginTransaction()
        if (null != prevFragment) transaction.hide(prevFragment)
        transaction.add(R.id.app_fragment, fragment)
        transaction.commit()

        supportActionBar?.setDisplayHomeAsUpEnabled(stack.size > 1)
        supportActionBar?.title = title
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!askPermissions())
            onPermissionsAllowed()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (android.R.id.home == item.itemId) {
            popView(true)
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (!popView(false)) super.onBackPressed()
    }

    fun giveHapticFeedback(view: View) {
        if (settings.hapticFeedback) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_PERMISSIONS -> handleRequestPermissions(grantResults)
        }
    }

    fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun exitApp() {
        setResult(0)
        finish()
    }

    private fun fatalError(msg: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage(msg)
            .setIcon(android.R.drawable.stat_notify_error)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ -> exitApp() }
            .show()
    }

    private fun askPermissions(): Boolean {
        for (permission in PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSIONS)
                return true
            }
        }

        return false
    }

    private fun handleRequestPermissions(grantResults: IntArray) {
        var allowedAll = grantResults.size >= PERMISSIONS.size

        if (grantResults.size >= PERMISSIONS.size) {
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allowedAll = false
                    break
                }
            }
        }

        if (allowedAll) onPermissionsAllowed()
        else fatalError("You must allow permissions !")
    }

    fun runAsync( msg: String, callback: ()->Unit ) {
        BusyDialog.show(supportFragmentManager, msg)

        GlobalScope.launch(Dispatchers.Default) {
            callback.invoke()
            runOnUiThread {
                BusyDialog.dismiss()
            }
        }
    }

    private fun setBusyDialogTitleAsync(title: String) {
        runOnUiThread {
            BusyDialog.setTitle(title)
        }
    }

    private fun onPermissionsAllowed() {
        if (null == settings.saveFolder) {
            startSelectFolder()
        } else {
            startApp()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        if (SELECT_SAVE_FOLDER == requestCode && RESULT_OK == resultCode && null != intent) {
            val data = intent.data
            if (data is Uri) {
                try {
                    contentResolver.takePersistableUriPermission(data,  Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch(e: Exception) {
                    e.printStackTrace()
                }

                val callStartApp = null == settings.saveFolder
                settings.saveFolder = DocumentFile.fromTreeUri(applicationContext, data)
                settings.saveProperties()

                if (callStartApp) startApp()

                selectFolderCallback?.invoke()
            }
        }
    }


    private fun startApp() {
        try {
            System.loadLibrary("opencv_java4")
        } catch (e: Exception) {
            fatalError("Failed to initialize OpenCV")
        }

        setContentView(R.layout.activity_main)
        MainFragment.show(this)
    }
}

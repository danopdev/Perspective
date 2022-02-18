package com.dan.perspective

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.dan.perspective.databinding.ActivityMainBinding
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.core.CvType.*
import org.opencv.imgproc.Imgproc.*
import kotlin.concurrent.timer


class MainActivity : AppCompatActivity() {
    companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        const val REQUEST_PERMISSIONS = 1
        const val INTENT_OPEN_IMAGE = 2

        const val ALPHA_8_TO_16 = 256.0
        const val ALPHA_16_TO_8 = 1.0 / ALPHA_8_TO_16
    }

    val settings: Settings by lazy { Settings(this) }
    private var inputImage: Mat? = null
    private var outputImage: Mat? = null
    private var menuSave: MenuItem? = null

    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var outputName = Settings.DEFAULT_NAME

    init {
        BusyDialog.create(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!askPermissions())
            onPermissionsAllowed()
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menuSave = menu?.findItem(R.id.save)
        menuSave?.isEnabled = null != inputImage
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.open -> {
                startActivityToOpenImage()
                return true
            }

            R.id.save -> {
                return true
            }

            R.id.settings -> {
                SettingsDialog.show(this)
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == INTENT_OPEN_IMAGE) {
            outputName = Settings.DEFAULT_NAME
            BusyDialog.show(supportFragmentManager, "Loading image")

            runFakeAsync {
                data?.data?.let { uri ->
                    try {
                        DocumentFile.fromSingleUri(
                            applicationContext,
                            uri
                        )?.name?.let { name ->
                            if (name.length > 0) {
                                val fields = name.split('.')
                                outputName = fields[0]
                            }
                        }
                    } catch (e: Exception) {
                    }

                    setImage(uri)
                }

                BusyDialog.dismiss()
            }
        }
    }

    private fun runFakeAsync(l: () -> Unit) {
        timer(null, false, 500, 500) {
            this.cancel()
            runOnUiThread {
                l.invoke()
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private fun startActivityToOpenImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .putExtra("android.content.extra.SHOW_ADVANCED", true)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            .putExtra(Intent.EXTRA_TITLE, "Open image")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        startActivityForResult(intent, INTENT_OPEN_IMAGE)
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

    private fun convertToDepth( image: Mat, depth: Int ) : Mat {
        when( depth ) {
            Settings.DEPTH_8_BITS -> {
                if (CV_16UC3 == image.type()) {
                    val newImage = Mat()
                    image.convertTo(newImage, CV_8UC3, ALPHA_16_TO_8)
                    return newImage
                }
            }

            Settings.DEPTH_16_BITS -> {
                if (CV_8UC3 == image.type()) {
                    val newImage = Mat()
                    image.convertTo(newImage, CV_16UC3, ALPHA_8_TO_16)
                    return newImage
                }
            }
        }

        return image
    }

    private fun matToBitmap(image: Mat?): Bitmap? {
        if (null == image || image.empty()) return null

        val bitmap = Bitmap.createBitmap(
                image.cols(),
                image.rows(),
                Bitmap.Config.ARGB_8888
        )

        //make sure it's 8 bits per channel
        val image8Bits: Mat
        if (CV_16UC3 == image.type()) {
            image8Bits = Mat()
            image.convertTo(image8Bits, CV_8UC3, ALPHA_16_TO_8)
        } else {
            image8Bits = image
        }

        Utils.matToBitmap(image8Bits, bitmap)
        return bitmap
    }

    private fun setImage(uri: Uri) {
        inputImage = loadImage(uri)
        outputImage = null

        val enabled = null != inputImage
        binding.imageView.setBitmap(matToBitmap(inputImage))
        binding.buttonPreview.isEnabled = enabled
        binding.buttonReset.isEnabled = enabled
        menuSave?.isEnabled = enabled
    }

    private fun loadImage(uri: Uri) : Mat? {
        // Can't create MatOfByte from kotlin ByteArray, but works correctly from java byte[]
        val image = OpenCVLoadImageFromUri.load(uri, contentResolver)
        if (null == image || image.empty()) return null

        val imageRGB = Mat()

        when(image.type()) {
            CV_8UC3, CV_16UC3 -> cvtColor(image, imageRGB, COLOR_BGR2RGB)
            CV_8UC4, CV_16UC4 -> cvtColor(image, imageRGB, COLOR_BGRA2RGB)
            else -> return null
        }

        return convertToDepth(imageRGB, settings.engineDepth)
    }

    private fun onPermissionsAllowed() {
        try {
            System.loadLibrary("opencv_java4")
        } catch (e: Exception) {
            fatalError("Failed to initialize OpenCV")
        }

        setContentView(binding.root)
    }
}

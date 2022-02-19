package com.dan.perspective

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.dan.perspective.databinding.ActivityMainBinding
import org.opencv.android.Utils
import org.opencv.core.CvType.*
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Imgproc.*
import java.io.File
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
    private var inputImage = Mat()
    private var outputImage = Mat()
    private var menuSave: MenuItem? = null
    private var editMode = true

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

    private fun setEditMode(enabled: Boolean) {
        if (enabled == editMode) return
        editMode = enabled
        updateButtons()
        binding.imagePreview.visibility = if (!editMode) View.VISIBLE else View.GONE
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
        menuSave?.isEnabled = !inputImage.empty()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.open -> {
                startActivityToOpenImage()
                return true
            }

            R.id.save -> {
                warpImage()
                saveImage()
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

    private fun convertToDepth(image: Mat, depth: Int) : Mat {
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
        BusyDialog.show(supportFragmentManager, "Loading")
        inputImage = loadImage(uri)
        binding.imageEdit.setBitmap(matToBitmap(inputImage))
        clearOutputImage()
        updateButtons()
        BusyDialog.dismiss()
    }

    private fun updateButtons() {
        val enabled = !inputImage.empty()
        binding.buttonReset.isEnabled = enabled && editMode
        binding.buttonPreview.isEnabled = enabled && editMode
        binding.buttonEdit.isEnabled = enabled && !editMode
        menuSave?.isEnabled = enabled
    }

    private fun loadImage(uri: Uri) : Mat {
        // Can't create MatOfByte from kotlin ByteArray, but works correctly from java byte[]
        val image = OpenCVLoadImageFromUri.load(uri, contentResolver)
        if (null == image || image.empty()) return Mat()

        val imageRGB = Mat()

        when(image.type()) {
            CV_8UC3, CV_16UC3 -> cvtColor(image, imageRGB, COLOR_BGR2RGB)
            CV_8UC4, CV_16UC4 -> cvtColor(image, imageRGB, COLOR_BGRA2RGB)
            else -> return Mat()
        }

        return convertToDepth(imageRGB, settings.engineDepth)
    }

    private fun saveImage() {
        if (outputImage.empty()) return

        BusyDialog.show(supportFragmentManager, "Saving")

        val outputExtension = settings.outputExtension()

        try {
            var fileName = "${outputName}.${outputExtension}"
            var fileFullPath = Settings.SAVE_FOLDER + "/" + fileName
            var counter = 0
            while (File(fileFullPath).exists() && counter < 998) {
                counter++
                val counterStr = "%03d".format(counter)
                fileName = "${outputName}_${counterStr}.${outputExtension}"
                fileFullPath = Settings.SAVE_FOLDER + "/" + fileName
            }

            val outputRGB = Mat()
            cvtColor(outputImage, outputRGB, COLOR_BGR2RGB)

            var outputDepth = Settings.DEPTH_AUTO

            if ( Settings.OUTPUT_TYPE_JPEG == settings.outputType
                    || (Settings.OUTPUT_TYPE_PNG == settings.outputType && Settings.DEPTH_8_BITS == settings.pngDepth)
                    || (Settings.OUTPUT_TYPE_TIFF == settings.outputType && Settings.DEPTH_8_BITS == settings.tiffDepth)
            ) {
                outputDepth = Settings.DEPTH_8_BITS
            } else if ( (Settings.OUTPUT_TYPE_PNG == settings.outputType && Settings.DEPTH_16_BITS == settings.pngDepth)
                    || (Settings.OUTPUT_TYPE_TIFF == settings.outputType && Settings.DEPTH_16_BITS == settings.tiffDepth)
            ) {
                outputDepth = Settings.DEPTH_16_BITS
            }

            File(fileFullPath).parentFile?.mkdirs()

            val outputParams = MatOfInt()

            if (Settings.OUTPUT_TYPE_JPEG == settings.outputType) {
                outputParams.fromArray(Imgcodecs.IMWRITE_JPEG_QUALITY, settings.jpegQuality )
            }

            Imgcodecs.imwrite(fileFullPath, convertToDepth(outputRGB, outputDepth), outputParams)
            showToast("Saved to: ${fileName}")

            //Add it to gallery
            val values = ContentValues()
            @Suppress("DEPRECATION")
            values.put(MediaStore.Images.Media.DATA, fileFullPath)
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/${outputExtension}")
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            showToast("Failed to save")
        }

        BusyDialog.dismiss()
    }

    private fun warpImage() {
        if (inputImage.empty()) return
        if (!outputImage.empty()) return

        BusyDialog.show(supportFragmentManager, "Warping")

        val perspectivePoints = binding.imageEdit.perspectivePoints

        val srcMat = Mat(4, 1, CV_32FC2)
        srcMat.put(
                0, 0,
                perspectivePoints.leftTop.x.toDouble(), perspectivePoints.leftTop.y.toDouble(),
                perspectivePoints.rightTop.x.toDouble(), perspectivePoints.rightTop.y.toDouble(),
                perspectivePoints.rightBottom.x.toDouble(), perspectivePoints.rightBottom.y.toDouble(),
                perspectivePoints.leftBottom.x.toDouble(), perspectivePoints.leftBottom.y.toDouble(),
        )

        val destLeft = (perspectivePoints.leftTop.x + perspectivePoints.leftBottom.x) / 2.0
        val destRight = (perspectivePoints.rightTop.x + perspectivePoints.rightBottom.x) / 2.0
        val destTop = (perspectivePoints.leftTop.y + perspectivePoints.rightBottom.y) / 2.0
        val destBottom = (perspectivePoints.leftBottom.y + perspectivePoints.rightBottom.y) / 2.0

        val destMat = Mat(4, 1, CV_32FC2)
        destMat.put(
                0,0,
                destLeft, destTop,
                destRight, destTop,
                destRight, destBottom,
                destLeft, destBottom
        )

        val perspectiveMat = getPerspectiveTransform(srcMat, destMat)
        warpPerspective( inputImage, outputImage, perspectiveMat, inputImage.size(), INTER_LANCZOS4)

        binding.imagePreview.setBitmap( matToBitmap(outputImage) )

        BusyDialog.dismiss()
    }

    private fun clearOutputImage() {
        outputImage.release()
        binding.imagePreview.setBitmap(null)
    }

    private fun onPermissionsAllowed() {
        try {
            System.loadLibrary("opencv_java4")
        } catch (e: Exception) {
            fatalError("Failed to initialize OpenCV")
        }

        setContentView(binding.root)

        binding.buttonReset.setOnClickListener { binding.imageEdit.resetPoints() }
        binding.imageEdit.setOnPerspectiveChanged { clearOutputImage() }

        binding.buttonPreview.setOnClickListener {
            warpImage()
            setEditMode(false)
        }
    }
}

package com.dan.perspective

import android.Manifest
import android.animation.Animator
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.dan.perspective.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.opencv.android.Utils
import org.opencv.core.Core.mean
import org.opencv.core.Core.minMaxLoc
import org.opencv.core.CvType.*
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc.*
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


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

        const val AUTO_DETECT_WORK_SIZE = 1024
    }

    val settings: Settings by lazy { Settings(this) }
    private lateinit var inputImage: Mat
    private lateinit var outputImage: Mat
    private var menuSave: MenuItem? = null
    private var editMode = true

    private val firstAnimatorListener = object : Animator.AnimatorListener {
        override fun onAnimationStart(p0: Animator?) {
            if (!editMode) {
                binding.layoutPreview.visibility = View.VISIBLE
            } else {
                binding.layoutEdit.visibility = View.VISIBLE
            }
        }

        override fun onAnimationEnd(p0: Animator?) {
        }

        override fun onAnimationCancel(p0: Animator?) {
        }

        override fun onAnimationRepeat(p0: Animator?) {
        }
    }

    private val lastAnimatorListener = object : Animator.AnimatorListener {
        override fun onAnimationStart(p0: Animator?) {
        }

        override fun onAnimationEnd(p0: Animator?) {
            if (editMode) {
                binding.layoutPreview.visibility = View.GONE
            } else {
                binding.layoutEdit.visibility = View.GONE
            }
        }

        override fun onAnimationCancel(p0: Animator?) {
        }

        override fun onAnimationRepeat(p0: Animator?) {
        }
    }

    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var outputName = Settings.DEFAULT_NAME
    private var inputUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!askPermissions())
            onPermissionsAllowed()
    }

    private fun giveHapticFeedback(view: View) {
        if (settings.hapticFeedback) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }
    }

    private fun setEditMode(enabled: Boolean) {
        if (enabled == editMode) return
        editMode = enabled
        updateButtons()

        if (editMode) {
            val editAnimation = binding.layoutEdit.animate()
                    .translationX(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setListener(lastAnimatorListener)
                    .setInterpolator(AccelerateInterpolator())
                    .setStartDelay(100L)
                    .setDuration(200L)

            val previewAnimation = binding.layoutPreview.animate()
                    .translationX(EditPerspectiveImageView.dpToPixels(100))
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .alpha(0f)
                    .setListener(firstAnimatorListener)
                    .setInterpolator(AccelerateInterpolator())
                    .setDuration(200L)

            previewAnimation.start()
            editAnimation.start()
        } else {
            val editAnimation = binding.layoutEdit.animate()
                    .translationX(EditPerspectiveImageView.dpToPixels(-100))
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .alpha(0f)
                    .setListener(firstAnimatorListener)
                    .setInterpolator(AccelerateInterpolator())
                    .setDuration(200L)

            val previewAnimation = binding.layoutPreview.animate()
                    .translationX(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setListener(lastAnimatorListener)
                    .setInterpolator(AccelerateInterpolator())
                    .setStartDelay(100L)
                    .setDuration(200L)

            editAnimation.start()
            previewAnimation.start()
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
            data?.data?.let { uri -> setImage(uri) }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
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

    private fun matToBitmap(image: Mat): Bitmap? {
        if (image.empty()) return null

        val bitmap = Bitmap.createBitmap(
                image.cols(),
                image.rows(),
                Bitmap.Config.ARGB_8888
        )

        Utils.matToBitmap( convertToDepth( image, Settings.DEPTH_8_BITS ) , bitmap)
        return bitmap
    }

    private fun setImage(uri: Uri) {
        runAsync("Loading") {
            runOnUiThread {
                setEditMode(true)
            }

            inputImage = loadImage(uri)
            val bitmap = matToBitmap(inputImage)

            runOnUiThread {
                binding.imageEdit.setBitmap(bitmap)

                if (null == bitmap) {
                    showToast("Failed to load the image")
                } else {
                    outputName = Settings.DEFAULT_NAME

                    try {
                        DocumentFile.fromSingleUri(
                                applicationContext,
                                uri
                        )?.name?.let { name ->
                            if (name.isNotEmpty()) {
                                val fields = name.split('.')
                                outputName = fields[0]
                            }
                        }
                    } catch (e: Exception) {
                    }
                }

                clearOutputImage()
                updateButtons()
            }
        }
    }

    private fun updateButtons() {
        val enabled = !inputImage.empty()

        binding.buttonReset.isEnabled = enabled
        binding.buttonAuto.isEnabled = enabled
        binding.buttonPreview.isEnabled = enabled
        binding.radioButtonPointDirectionAll.isEnabled = enabled
        binding.radioButtonPointDirectionHorizontal.isEnabled = enabled
        binding.radioButtonPointDirectionVertical.isEnabled = enabled

        menuSave?.isEnabled = enabled
    }

    private fun loadImage(uri: Uri) : Mat {
        // Can't create MatOfByte from kotlin ByteArray, but works correctly from java byte[]
        val image = OpenCVLoadImageFromUri.load(uri, contentResolver)
        if (null == image || image.empty()) return Mat()

        inputUri = uri
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

        runAsync("Saving") {

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

                if (Settings.OUTPUT_TYPE_JPEG == settings.outputType
                        || (Settings.OUTPUT_TYPE_PNG == settings.outputType && Settings.DEPTH_8_BITS == settings.pngDepth)
                        || (Settings.OUTPUT_TYPE_TIFF == settings.outputType && Settings.DEPTH_8_BITS == settings.tiffDepth)
                ) {
                    outputDepth = Settings.DEPTH_8_BITS
                } else if ((Settings.OUTPUT_TYPE_PNG == settings.outputType && Settings.DEPTH_16_BITS == settings.pngDepth)
                        || (Settings.OUTPUT_TYPE_TIFF == settings.outputType && Settings.DEPTH_16_BITS == settings.tiffDepth)
                ) {
                    outputDepth = Settings.DEPTH_16_BITS
                }

                File(fileFullPath).parentFile?.mkdirs()

                val outputParams = MatOfInt()

                if (Settings.OUTPUT_TYPE_JPEG == settings.outputType) {
                    outputParams.fromArray(Imgcodecs.IMWRITE_JPEG_QUALITY, settings.jpegQuality)
                }

                Imgcodecs.imwrite(fileFullPath, convertToDepth(outputRGB, outputDepth), outputParams)

                inputUri?.let { uri ->
                    ExifTools.copyExif( contentResolver, uri, fileFullPath )
                }

                runOnUiThread {
                    //Add it to gallery
                    val values = ContentValues()
                    @Suppress("DEPRECATION")
                    values.put(MediaStore.Images.Media.DATA, fileFullPath)
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/${outputExtension}")
                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                }

                showToast("Saved to: $fileName")
            } catch (e: Exception) {
                showToast("Failed to save")
            }
        }
    }

    private fun runAsync( msg: String, callback: ()->Unit ) {
        BusyDialog.show(supportFragmentManager, msg)

        GlobalScope.launch(Dispatchers.Default) {
            callback.invoke()
            runOnUiThread {
                BusyDialog.dismiss()
            }
        }
    }

    private fun warpImage() {
        if (inputImage.empty()) return
        if (!outputImage.empty()) return

        runAsync( "Warping") {
            val perspectivePoints = binding.imageEdit.getPerspective()

            val srcMat = Mat(4, 1, CV_32FC2)
            srcMat.put(
                    0, 0,
                    perspectivePoints.pointLeftTop.x.toDouble(), perspectivePoints.pointLeftTop.y.toDouble(),
                    perspectivePoints.pointRightTop.x.toDouble(), perspectivePoints.pointRightTop.y.toDouble(),
                    perspectivePoints.pointRightBottom.x.toDouble(), perspectivePoints.pointRightBottom.y.toDouble(),
                    perspectivePoints.pointLeftBottom.x.toDouble(), perspectivePoints.pointLeftBottom.y.toDouble(),
            )

            val destLeft = (perspectivePoints.pointLeftTop.x + perspectivePoints.pointLeftBottom.x) / 2.0
            val destRight = (perspectivePoints.pointRightTop.x + perspectivePoints.pointRightBottom.x) / 2.0
            val destTop = (perspectivePoints.pointLeftTop.y + perspectivePoints.pointRightTop.y) / 2.0
            val destBottom = (perspectivePoints.pointLeftBottom.y + perspectivePoints.pointRightBottom.y) / 2.0

            val destMat = Mat(4, 1, CV_32FC2)
            destMat.put(
                    0, 0,
                    destLeft, destTop,
                    destRight, destTop,
                    destRight, destBottom,
                    destLeft, destBottom
            )

            val perspectiveMat = getPerspectiveTransform(srcMat, destMat)
            warpPerspective(inputImage, outputImage, perspectiveMat, inputImage.size(), INTER_LANCZOS4)

            runOnUiThread {
                binding.imagePreview.setBitmap(matToBitmap(outputImage))
            }
        }
    }

    private fun clearOutputImage() {
        outputImage.release()
        binding.imagePreview.setBitmap(null)
    }

    private fun lineIntersection( line1: Pair<PointF, PointF>, line2: Pair<PointF, PointF> ): PointF? {
        val delta1 = PointF( line1.second.x - line1.first.x, line1.second.y - line1.first.y )
        val delta2 = PointF( line2.second.x - line2.first.x, line2.second.y - line2.first.y )
        val cross = delta1.x * delta2.y - delta1.y * delta2.x
        if (abs(cross) < 0.00001) return null

        val ref = PointF( line2.first.x - line1.first.x, line2.first.y - line1.first.y )
        val t = ( ref.x * delta2.y - ref.y * delta2.x ) / cross
        return PointF( line1.first.x + delta1.x * t, line1.first.y + delta1.y * t )
    }

    private fun toPointF( point: Point, scaleX: Float, scaleY: Float ): PointF {
        return PointF(
                point.x * scaleX,
                point.y * scaleY
        )
    }

    private fun toLineF( line: Pair<Point, Point>, scaleX: Float, scaleY: Float ): Pair<PointF, PointF> {
        return Pair(
                toPointF( line.first, scaleX, scaleY ),
                toPointF( line.second, scaleX, scaleY ),
        )
    }

    private fun autoDetectPerspectiveAsync() {
        val minValue = AUTO_DETECT_WORK_SIZE * EditPerspectiveImageView.BORDER / 100
        val maxValue = AUTO_DETECT_WORK_SIZE - minValue

        val image = Mat()
        resize(
                convertToDepth( inputImage, Settings.DEPTH_8_BITS ),
                image,
                Size( AUTO_DETECT_WORK_SIZE.toDouble(), AUTO_DETECT_WORK_SIZE.toDouble()) ,
                0.0,
                0.0,
                INTER_AREA
        )

        cvtColor( image, image, COLOR_BGR2GRAY )

        //blur reduce number of edge detected
        blur( image, image, Size(3.0, 3.0) )

        val meanValue = mean(image).`val`[0].toInt()
        val minMax = minMaxLoc(image)
        val minVal = minMax.minVal
        val maxVal = minMax.maxVal
        val lowerThreshold = (minVal + meanValue) / 2
        val upperThreshold = (maxVal + meanValue) / 2

        val edges = Mat()
        Canny( image, edges, lowerThreshold, upperThreshold )

        val hLines = mutableListOf<Pair<Point, Point>>()
        val vLines = mutableListOf<Pair<Point, Point>>()

        for( threshold in 200 downTo 100 step 20 ) {
            hLines.clear()
            vLines.clear()

            val lines = Mat()
            HoughLinesP(edges, lines, 1.0, PI / 1024, threshold, 300.0, 100.0)
            if (lines.empty()) continue

            for (lineIndex in 0 until lines.rows()) {
                val startPoint = Point( lines.get(lineIndex, 0)[0].toInt(), lines.get(lineIndex, 0)[1].toInt() )
                val endPoint = Point( lines.get(lineIndex, 0)[2].toInt(), lines.get(lineIndex, 0)[3].toInt() )
                val delta = Point( abs(endPoint.x - startPoint.x), abs(endPoint.y - startPoint.y) )
                val ratio = min(delta.x, delta.y).toFloat() / max(delta.x, delta.y)

                // avoid lines that are too harsh
                if (ratio >= 0.3) continue

                if (delta.x > delta.y) {
                    if (startPoint.y >= minValue && endPoint.y >= minValue && startPoint.y <= maxValue && endPoint.y <= maxValue) {
                        hLines.add( Pair(startPoint, endPoint) )
                    }
                } else {
                    if (startPoint.x >= minValue && endPoint.x >= minValue && startPoint.x <= maxValue && endPoint.x <= maxValue) {
                        vLines.add( Pair(startPoint, endPoint) )
                    }
                }
            }

            if (hLines.size >= 2 && vLines.size >= 2) break
        }

        val hLineTop: Pair<Point, Point>
        val hLineBottom: Pair<Point, Point>
        val vLineLeft: Pair<Point, Point>
        val vLineRight: Pair<Point, Point>

        when {
            hLines.isEmpty() -> {
                hLineTop = Pair( Point( minValue, minValue ), Point( maxValue, minValue ) )
                hLineBottom = Pair( Point( minValue, maxValue ), Point( maxValue, maxValue ) )
            }

            1 == hLines.size -> {
                if ((hLines[0].first.y / 2) < (AUTO_DETECT_WORK_SIZE / 2)) {
                    hLineTop = hLines[0]
                    hLineBottom = Pair( Point( minValue, maxValue ), Point( maxValue, maxValue ) )
                } else {
                    hLineTop = Pair( Point( minValue, minValue ), Point( maxValue, minValue ) )
                    hLineBottom = hLines[0]
                }
            }

            else -> {
                hLines.sortBy { line -> line.first.y }
                hLineTop = hLines.first()
                hLineBottom = hLines.last()
            }
        }

        when {
            vLines.isEmpty() -> {
                vLineLeft = Pair( Point( minValue, minValue ), Point( minValue, maxValue ) )
                vLineRight = Pair( Point( maxValue, minValue ), Point( maxValue, maxValue ) )
            }

            1 == vLines.size -> {
                if ((vLines[0].first.x / 2) < (AUTO_DETECT_WORK_SIZE / 2)) {
                    vLineLeft = vLines[0]
                    vLineRight = Pair( Point( maxValue, minValue ), Point( maxValue, maxValue ) )
                } else {
                    vLineLeft = Pair( Point( minValue, minValue ), Point( minValue, maxValue ) )
                    vLineRight = vLines[0]
                }
            }

            else -> {
                vLines.sortBy { line -> line.first.x }
                vLineLeft = vLines.first()
                vLineRight = vLines.last()
            }
        }

        val scaleX = inputImage.cols().toFloat() / AUTO_DETECT_WORK_SIZE
        val scaleY = inputImage.rows().toFloat() / AUTO_DETECT_WORK_SIZE

        val hLineTopF = toLineF(hLineTop, scaleX, scaleY)
        val hLineBottomF = toLineF(hLineBottom, scaleX, scaleY)
        val vLineLeftF = toLineF(vLineLeft, scaleX, scaleY)
        val vLineRightF = toLineF(vLineRight, scaleX, scaleY)

        val leftTop = lineIntersection( vLineLeftF, hLineTopF ) ?: toPointF( Point(minValue, minValue), scaleX, scaleY )
        val leftBottom = lineIntersection( vLineLeftF, hLineBottomF ) ?: toPointF( Point(minValue, maxValue), scaleX, scaleY )
        val rightTop = lineIntersection( vLineRightF, hLineTopF ) ?: toPointF( Point(maxValue, minValue), scaleX, scaleY )
        val rightBottom = lineIntersection( vLineRightF, hLineBottomF ) ?: toPointF( Point(maxValue, maxValue), scaleX, scaleY )

        runOnUiThread {
            clearOutputImage()
            binding.imageEdit.setPerspective(leftTop, rightTop, leftBottom, rightBottom)
        }
    }

    private fun autoDetectPerspective() {
        if (inputImage.empty()) return

        runAsync("Auto detect") {
            autoDetectPerspectiveAsync()
        }
    }

    private fun onPermissionsAllowed() {
        try {
            System.loadLibrary("opencv_java4")
        } catch (e: Exception) {
            fatalError("Failed to initialize OpenCV")
        }

        inputImage = Mat()
        outputImage = Mat()

        setContentView(binding.root)
        updateButtons()

        binding.buttonReset.setOnClickListener {
            binding.imageEdit.resetPoints()
        }

        binding.buttonAuto.setOnClickListener {
            autoDetectPerspective()
        }

        binding.imageEdit.setOnPerspectiveChanged {
            clearOutputImage()
        }

        binding.imageEdit.setOnEditStart {
            giveHapticFeedback( binding.imageEdit )
        }

        binding.buttonEdit.setOnClickListener {
            setEditMode(true)
        }

        binding.buttonPreview.setOnClickListener {
            setEditMode(false)
            warpImage()
        }

        binding.radioGroupPointDirection.setOnCheckedChangeListener { _, id ->
            when(id) {
                binding.radioButtonPointDirectionHorizontal.id -> binding.imageEdit.pointEditDirection = EditPerspectiveImageView.POINT_EDIT_DIRECTION_HORIZONTAL
                binding.radioButtonPointDirectionVertical.id -> binding.imageEdit.pointEditDirection = EditPerspectiveImageView.POINT_EDIT_DIRECTION_VERTICAL
                else -> binding.imageEdit.pointEditDirection = EditPerspectiveImageView.POINT_EDIT_DIRECTION_ALL
            }
        }

        var initialUri: Uri? = null

        if (null != intent && null != intent.action) {
            if (Intent.ACTION_SEND == intent.action) {
                val extraStream = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM)
                if (null != extraStream) {
                    initialUri = extraStream as Uri
                }
            } else if(Intent.ACTION_VIEW == intent.action){
                initialUri = intent.data
            }
        }

        if (null != initialUri) setImage(initialUri)
    }
}

package com.dan.perspective

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.dan.perspective.databinding.MainFragmentBinding
import org.opencv.android.Utils
import org.opencv.core.CvType.*
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class MainFragment(activity: MainActivity) : AppFragment(activity) {
    companion object {
        const val MSG_AUTO_DETECT = "Auto detect"
        const val MSG_WARP = "Warping"
        const val MSG_SAVE = "Saving"

        const val INTENT_OPEN_IMAGE = 2
        const val INTENT_TAKE_PHOTO = 3

        const val AUTO_DETECT_WORK_SIZE = 1024

        fun show(activity: MainActivity) {
            activity.pushView( "Perspective", MainFragment(activity) )
        }
    }

    private var inputImage = Mat()
    private var outputImage = Mat()
    private var outputImageCropped = Mat()
    private var menuSave: MenuItem? = null
    private var menuPrevPerspective: MenuItem? = null
    private val sharedParams = SharedParams()
    private lateinit var tmpPhotoFile: File

    private lateinit var binding: MainFragmentBinding //: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var outputName = Settings.DEFAULT_NAME
    private var inputUri: Uri? = null

    private fun updateToSharedParams() {
        sharedParams.cropped = binding.switchCrop.isChecked
    }

    private fun updateFromSharedParams() {
        binding.switchCrop.isChecked = sharedParams.cropped
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_menu, menu)

        menuSave = menu.findItem(R.id.save)
        menuSave?.isEnabled = !inputImage.empty()

        menuPrevPerspective = menu.findItem(R.id.prevPerspective)
        menuPrevPerspective?.isEnabled = !inputImage.empty() && settings.prevHeight > 0
    }

    override fun onDestroyOptionsMenu() {
        menuSave = null
        menuPrevPerspective = null
        super.onDestroyOptionsMenu()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.open -> {
                startActivityToOpenImage()
                return true
            }

            R.id.takePhoto -> {
                startActivityToTakePhoto()
                return true
            }

            R.id.save -> {
                saveImage()
                return true
            }

            R.id.settings -> {
                SettingsFragment.show(activity)
                return true
            }

            R.id.prevPerspective -> {
                if (settings.prevHeight < 1) return true
                if (inputImage.empty()) return true

                binding.imageEdit.setPerspective(
                    PointF(settings.prevLeftTopX * inputImage.width() / settings.prevWidth,
                        settings.prevLeftTopY * inputImage.height() / settings.prevHeight
                    ),
                    PointF(
                        settings.prevRightTopX * inputImage.width() / settings.prevWidth,
                        settings.prevRightTopY * inputImage.height() / settings.prevHeight
                    ),
                    PointF(
                        settings.prevLeftBottomX * inputImage.width() / settings.prevWidth,
                        settings.prevLeftBottomY * inputImage.height() / settings.prevHeight
                    ),
                    PointF(
                        settings.prevRightBottomX * inputImage.width() / settings.prevWidth,
                        settings.prevRightBottomY * inputImage.height() / settings.prevHeight
                    )
                )

                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == AppCompatActivity.RESULT_OK && null != data) {
            when (requestCode) {
                INTENT_OPEN_IMAGE -> {
                    data.data?.let { uri -> setImage(uri) }
                }

                INTENT_TAKE_PHOTO -> {
                    if (tmpPhotoFile.exists()) {
                        val name = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(System.currentTimeMillis()))
                        setImage( Uri.fromFile(tmpPhotoFile), name )
                        tmpPhotoFile.delete()
                    }
                }
            }
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

    private fun startActivityToTakePhoto() {
        val tmpUri = FileProvider.getUriForFile(
            requireContext(),
            "com.dan.perspective.provider",
            tmpPhotoFile)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, tmpUri)
        
        startActivityForResult(intent, INTENT_TAKE_PHOTO)
    }

    private fun matToBitmap(image: Mat): Bitmap? {
        if (image.empty()) return null

        val bitmap = Bitmap.createBitmap(
                image.cols(),
                image.rows(),
                Bitmap.Config.ARGB_8888
        )

        Utils.matToBitmap( image, bitmap)
        return bitmap
    }

    private fun setImage(uri: Uri, suggestedName: String? = null) {
        val matAndBitmap = loadImage(uri)
        if (null == matAndBitmap) {
            inputUri = null
            showToast("Failed to load the image")
            return
        }

        inputImage = matAndBitmap.first
        binding.imageEdit.setBitmap(matAndBitmap.second)

        if (null != suggestedName) {
            outputName = suggestedName
        } else {
            outputName = Settings.DEFAULT_NAME

            try {
                DocumentFile.fromSingleUri(requireContext(), uri)?.name?.let { name ->
                    if (name.isNotEmpty()) {
                        val fields = name.split('.')
                        outputName = fields[0]
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        inputUri = uri
        clearOutputImage()
        updateButtons()

        if (settings.autoDetectOnOpen) {
            autoDetectPerspective()
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
        menuPrevPerspective?.isEnabled = enabled && settings.prevHeight > 0
    }

    private fun loadImage(uri: Uri) : Pair<Mat,Bitmap>? {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val image =  Mat()
            Utils.bitmapToMat(bitmap, image)
            inputStream.close()
            return Pair(image, bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun saveImageAsync() {
        if (inputImage.empty()) return

        warpImageAsync()
        if (outputImage.empty()) return

        val bitmap = matToBitmap(
            if (binding.switchCrop.isChecked)
                outputImageCropped
            else
                outputImage) ?: return

        setBusyDialogTitleAsync(MSG_SAVE)
        var success = false
        val fileName = "${outputName}.jpg"

        try {
            val saveFolder = settings.saveFolder
            if (null != saveFolder) {
                val newFile = saveFolder.createFile("image/jpeg", fileName)
                if (null != newFile) {
                    val outputStream = requireContext().contentResolver.openOutputStream(newFile.uri)
                    if (null != outputStream) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, settings.jpegQuality, outputStream)
                        outputStream.close()

                        inputUri?.let { uri ->
                            ExifTools.copyExif( activity.contentResolver, uri, newFile.uri )
                        }

                        success = true
                    }
                }
            }

            runOnUiThread {
                val perspectivePoints = binding.imageEdit.getPerspective()
                settings.prevWidth = outputImage.width()
                settings.prevHeight = outputImage.height()
                settings.prevLeftTopX = perspectivePoints.pointLeftTop.x
                settings.prevLeftTopY = perspectivePoints.pointLeftTop.y
                settings.prevRightTopX = perspectivePoints.pointRightTop.x
                settings.prevRightTopY = perspectivePoints.pointRightTop.y
                settings.prevLeftBottomX = perspectivePoints.pointLeftBottom.x
                settings.prevLeftBottomY = perspectivePoints.pointLeftBottom.y
                settings.prevRightBottomX = perspectivePoints.pointRightBottom.x
                settings.prevRightBottomY = perspectivePoints.pointRightBottom.y
                settings.saveProperties()

                menuPrevPerspective?.isEnabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (success)
            showToast("Saved to: $fileName")
        else
            showToast("Failed to save")
    }

    private fun saveImage() {
        if (inputImage.empty()) return

        runAsync(MSG_SAVE) {
            saveImageAsync()
        }
    }

    private fun setBusyDialogTitleAsync(title: String) {
        runOnUiThread {
            BusyDialog.setTitle(title)
        }
    }

    private fun warpImageAsync() {
        if (inputImage.empty()) return
        if (!outputImage.empty()) return

        setBusyDialogTitleAsync(MSG_WARP)

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

        val destLeftInt = destLeft.toInt() + 1
        val destRightInt = destRight.toInt() - 1
        val destTopInt = destTop.toInt() + 1
        val destBottomInt = destBottom.toInt() - 1

        outputImageCropped = Mat(
            outputImage,
            Rect(
                destLeftInt,
                destTopInt,
                destRightInt - destLeftInt - 1,
                destBottomInt - destTopInt - 1))
    }

    private fun showPreview() {
        if (inputImage.empty()) return
        if (outputImage.empty()) return
        val bitmap = matToBitmap( outputImage) ?: return
        val bitmapCropped = matToBitmap( outputImageCropped ) ?: return
        runOnUiThread {
            updateToSharedParams()
            PreviewFragment.show(activity, bitmap, bitmapCropped, sharedParams)
        }
    }

    override fun onActivate() {
        updateFromSharedParams()
        super.onActivate()
    }

    private fun warpImage() {
        if (inputImage.empty()) return
        if (!outputImage.empty()) {
            showPreview()
            return
        }

        runAsync(MSG_WARP) {
            warpImageAsync()
            showPreview()
        }
    }

    private fun clearOutputImage() {
        outputImage.release()
        outputImageCropped.release()
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
                inputImage,
                image,
                Size( AUTO_DETECT_WORK_SIZE.toDouble(), AUTO_DETECT_WORK_SIZE.toDouble()) ,
                0.0,
                0.0,
                INTER_NEAREST_EXACT
        )

        cvtColor( image, image, COLOR_BGR2GRAY )
        blur(image, image, Size(9.0,9.0))
        threshold( image, image, 0.0,255.0,THRESH_BINARY + THRESH_OTSU)

        val edges = Mat()
        Canny( image, edges, 100.0, 200.0 )

        val hLines = mutableListOf<Pair<Point, Point>>()
        val vLines = mutableListOf<Pair<Point, Point>>()

        val lines = Mat()
        HoughLinesP(edges, lines, 1.0, PI / 360, 10, 100.0, 100.0)
        if (!lines.empty()) {
            for (lineIndex in 0 until lines.rows()) {
                val line = lines.get(lineIndex, 0)
                val startPoint = Point( line[0].toInt(), line[1].toInt() )
                val endPoint = Point( line[2].toInt(), line[3].toInt() )
                val delta = Point( abs(endPoint.x - startPoint.x), abs(endPoint.y - startPoint.y) )
                val ratio = min(delta.x, delta.y).toFloat() / max(delta.x, delta.y)

                // avoid lines that are too close to 45°
                if (ratio >= 0.3) continue

                Log.i("ABCDEFG", "dx: ${delta.x}, dy: ${delta.y}")

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

        runAsync(MSG_AUTO_DETECT) {
            autoDetectPerspectiveAsync()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        tmpPhotoFile = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "tmp.jpg")

        binding = MainFragmentBinding.inflate(inflater)

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
            activity.giveHapticFeedback( binding.imageEdit )
        }

        binding.buttonPreview.setOnClickListener {
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

        if (null != activity.intent && null != activity.intent.action) {
            if (Intent.ACTION_SEND == activity.intent.action) {
                val extraStream = activity.intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM)
                if (null != extraStream) {
                    initialUri = extraStream as Uri
                }
            } else if(Intent.ACTION_VIEW == activity.intent.action){
                initialUri = activity.intent.data
            }
        }

        if (null != initialUri) setImage(initialUri)

        setHasOptionsMenu(true)

        return binding.root
    }
}

package com.dan.perspective

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.dan.perspective.databinding.SettingsFragmentBinding


class SettingsFragment(activity: MainActivity) : AppFragment(activity) {

    companion object {
        private const val JPEG_QUALITY_BASE = 60
        private const val JPEG_QUALITY_TICK = 5

        fun show(activity: MainActivity ) {
            activity.pushView("Settings", SettingsFragment(activity))
        }
    }

    private val settings = activity.settings
    private lateinit var binding: SettingsFragmentBinding

    override fun onBack(backButton: Boolean) {
        if (binding.radioPng.isChecked) activity.settings.outputType = Settings.OUTPUT_TYPE_PNG
        else if (binding.radioTiff.isChecked) activity.settings.outputType = Settings.OUTPUT_TYPE_TIFF
        else activity.settings.outputType = Settings.OUTPUT_TYPE_JPEG

        activity.settings.jpegQuality = JPEG_QUALITY_BASE + binding.seekBarJpegQuality.progress * JPEG_QUALITY_TICK
        activity.settings.pngDepth = binding.spinnerPngDepth.selectedItemPosition
        activity.settings.tiffDepth = binding.spinnerTiffDepth.selectedItemPosition
        activity.settings.engineDepth = binding.spinnerEngineDepth.selectedItemPosition
        activity.settings.hapticFeedback = binding.switchHapticFeedback.isChecked

        activity.settings.saveProperties()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = SettingsFragmentBinding.inflate( inflater )

        binding.radioPng.isChecked = Settings.OUTPUT_TYPE_PNG == settings.outputType
        binding.radioTiff.isChecked = Settings.OUTPUT_TYPE_TIFF == settings.outputType
        binding.radioJpeg.isChecked = ! (binding.radioPng.isChecked || binding.radioTiff.isChecked)

        val alignedJpegQuality = when {
            settings.jpegQuality > 100 -> 100
            settings.jpegQuality < JPEG_QUALITY_BASE -> JPEG_QUALITY_BASE
            else -> (settings.jpegQuality / JPEG_QUALITY_TICK) * JPEG_QUALITY_TICK //round the value to tick size
        }
        val jpegTick = (alignedJpegQuality - JPEG_QUALITY_BASE) / JPEG_QUALITY_TICK
        binding.seekBarJpegQuality.progress = jpegTick
        binding.txtJpegQuality.text = alignedJpegQuality.toString()

        binding.spinnerPngDepth.setSelection(settings.pngDepth)
        binding.spinnerTiffDepth.setSelection(settings.tiffDepth)
        binding.spinnerEngineDepth.setSelection(settings.engineDepth)
        binding.switchHapticFeedback.isChecked = settings.hapticFeedback

        binding.seekBarJpegQuality.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                val jpegQuality = JPEG_QUALITY_BASE + progress * JPEG_QUALITY_TICK
                binding.txtJpegQuality.text = jpegQuality.toString()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })

        return binding.root
    }
}
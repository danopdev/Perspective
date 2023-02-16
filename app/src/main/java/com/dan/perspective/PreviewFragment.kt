package com.dan.perspective

import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import com.dan.perspective.databinding.PreviewFragmentBinding

class PreviewFragment(
    activity: MainActivity,
    private val bitmap: Bitmap,
    private val bitmapCropped: Bitmap,
    private val sharedParams: SharedParams
    ) : AppFragment(activity) {

    companion object {
        fun show(activity: MainActivity, bitmap: Bitmap, bitmapCropped: Bitmap, sharedParams: SharedParams) {
            activity.pushView(
                "Perspective Preview",
                PreviewFragment(activity, bitmap, bitmapCropped, sharedParams)
            )
        }
    }

    private var _binding: PreviewFragmentBinding? = null

    private fun updateImage() {
        val binding = _binding ?: return

        binding.imagePreview.setBitmap(
            if (sharedParams.cropped)
                bitmapCropped
            else
                bitmap
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = PreviewFragmentBinding.inflate(inflater)
        binding.switchCropped.isChecked = sharedParams.cropped
        _binding = binding
        updateImage()

        binding.switchCropped.setOnCheckedChangeListener { _, isChecked ->
            sharedParams.cropped = isChecked
            updateImage()
        }

        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

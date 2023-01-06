package com.dan.perspective

import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import com.dan.perspective.databinding.PreviewFragmentBinding

class PreviewFragment(activity: MainActivity, private val bitmap: Bitmap) : AppFragment(activity) {
    companion object {
        fun show(activity: MainActivity, bitmap: Bitmap) {
            activity.pushView( "Perspective Preview", PreviewFragment(activity, bitmap) )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = PreviewFragmentBinding.inflate(inflater)
        binding.imagePreview.setBitmap(bitmap)
        return binding.root
    }
}

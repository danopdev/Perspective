package com.dan.perspective

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.dan.perspective.databinding.PreviewDialogBinding
import com.dan.perspective.databinding.SettingsDialogBinding


class PreviewDialog( val bitmap: Bitmap ) : DialogFragment() {

    companion object {
        private const val DIALOG_TAG = "PREVIEW_DIALOG"

        fun show( fragmentManager: FragmentManager, bitmap: Bitmap ) {
            with( PreviewDialog( bitmap ) ) {
                isCancelable = false
                show(fragmentManager, DIALOG_TAG)
            }
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = PreviewDialogBinding.inflate( inflater )

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.imageView.setBitmap(bitmap)

        return binding.root
    }
}
package com.dan.perspective

import androidx.fragment.app.Fragment

open class AppFragment(val activity: MainActivity) : Fragment() {
    val settings = activity.settings

    open fun onBack(homeButton: Boolean) {
    }

    open fun onActivate() {
    }

    fun showToast(message: String) {
        activity.showToast(message)
    }

    fun runOnUiThread(action: ()->Unit) {
        activity.runOnUiThread(action)
    }

    fun runAsync( msg: String, callback: ()->Unit ) {
        activity.runAsync(msg, callback)
    }
}
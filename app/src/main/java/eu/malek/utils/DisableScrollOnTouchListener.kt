package eu.malek.utils

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

class DisableScrollOnTouchListener(private val viewGroupDisablingScrollOnTouch: ViewGroup) :
    View.OnTouchListener {
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                viewGroupDisablingScrollOnTouch.requestDisallowInterceptTouchEvent(true)
                true
            }
            MotionEvent.ACTION_UP -> {
                viewGroupDisablingScrollOnTouch.requestDisallowInterceptTouchEvent(false)
                true
            }
            else -> {
                false
            }
        }
    }
}
package org.oelab.octopusengine.octolabapp.ui.rgb

import android.graphics.Color

data class RGB(val red: Int = 0, val green: Int = 0, val blue: Int = 0) {
    fun toIntColor(): Int {
        return Color.rgb(red, green, blue)
    }

}

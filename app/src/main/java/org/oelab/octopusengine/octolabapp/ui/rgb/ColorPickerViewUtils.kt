package org.oelab.octopusengine.octolabapp.ui.rgb

import android.graphics.Color
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.sliders.AlphaSlideBar
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import eu.malek.utils.DisableScrollOnTouchListener
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit


data class ColorPickerState(
    val rgb: RGB = RGB(),
    val selectorX: Int = CENTER_COLOR_PICKER_SELECTOR,
    val selectorY: Int = CENTER_COLOR_PICKER_SELECTOR,
    val pureColor: Int = Color.WHITE,
    val brightness: Float = 1.0f
) {
    companion object {
        const val CENTER_COLOR_PICKER_SELECTOR = -999
    }
}


fun setColorPickerState(
    cp: ColorPickerView,
    it: ColorPickerState
) {
    cp.brightnessSlider.brightness = it.brightness
    cp.pureColor = it.pureColor
    cp.setSelectorPoint(it.selectorX, it.selectorY)
    cp.setCoordinate(it.selectorX, it.selectorY)
    cp.fireColorListener(it.rgb.toIntColor(), false)
}

fun createRgbPickerSubject(
    bsb: BrightnessSlideBar,
    cp: ColorPickerView,
    _alphaSlideBar: AlphaSlideBar
): Observable<ColorPickerState> {
    bsb.setOnTouchListener(DisableScrollOnTouchListener(bsb))
    cp.setOnTouchListener(DisableScrollOnTouchListener(cp))
    cp.attachBrightnessSlider(bsb)
    cp.attachAlphaSlider(_alphaSlideBar) // not used but crashes without
    setColorPickerState(cp, ColorPickerState())

    val rgbPickerSubject = BehaviorSubject.create<ColorPickerState>()
    cp.setColorListener(
        ColorEnvelopeListener { envelope, fromUser ->
            rgbPickerSubject.onNext(
                ColorPickerState(
                    RGB(
                        envelope.argb[1],
                        envelope.argb[2],
                        envelope.argb[3]
                    ),
                    cp.selectedPoint.x,
                    cp.selectedPoint.y,
                    cp.pureColor,
                    cp.brightnessSlider.brightness
                )

            )
        })

    return rgbPickerSubject.debounce(2L, TimeUnit.MILLISECONDS, Schedulers.computation())

}
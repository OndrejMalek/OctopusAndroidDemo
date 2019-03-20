package org.oelab.octopusengine.octolabapp.ui.rgb

import androidx.lifecycle.ViewModel
import com.jakewharton.rxrelay2.ReplayRelay

class RGBViewModel: ViewModel() {
    lateinit var state: State

    val fabReplay: ReplayRelay<RgbDeviceState> = ReplayRelay.create()

}

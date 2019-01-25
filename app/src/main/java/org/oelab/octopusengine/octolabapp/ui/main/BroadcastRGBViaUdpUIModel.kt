package org.oelab.octopusengine.octolabapp.ui.main

data class BroadcastRGBViaUdpUIModel(
    val displayToUI: Boolean = true,
    val rgb: RGB? = RGB(),
    val message: String = "",
    val sendError: Boolean = false,
    val openSocket: Boolean = false,
    val OpenSocketError: Boolean = false
)

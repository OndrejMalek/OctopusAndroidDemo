package org.oelab.octopusengine.octolabapp.ui.main

data class BroadcastRGBViaUdpUIModel(
    val rgb: RGB?,
    val message: String = "",
    val sendError: Boolean = false,
    val OpenSocketError: Boolean = false
)

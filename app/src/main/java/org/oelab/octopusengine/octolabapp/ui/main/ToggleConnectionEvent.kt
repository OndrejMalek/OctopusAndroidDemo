package org.oelab.octopusengine.octolabapp.ui.main

data class ToggleConnectionEvent(
    val buttonOn: Boolean = false,
    val ipAddress: String = "",
    val udpPort: String = ""
)

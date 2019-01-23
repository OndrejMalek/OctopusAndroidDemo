package org.oelab.octopusengine.octolabapp.ui.main

data class CheckedUdpFieldsUIModel(
    val validIPAddress: Boolean,
    val validPort: Boolean,
    val event: ToggleConnectionEvent
) {

}

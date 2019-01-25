package org.oelab.octopusengine.octolabapp.ui.main

data class CheckedUdpFieldsUIModel(
    val validIPAddress: Boolean = false,
    val validPort: Boolean = false,
    val toggleEvent: ToggleConnectionEvent = ToggleConnectionEvent()
) {

}

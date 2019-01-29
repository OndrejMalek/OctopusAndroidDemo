package org.oelab.octopusengine.octolabapp.ui.main

data class SentRGBModel(
    val rgb: RGB? = RGB(),
    val message: String = "",
    val checkedModel: CheckedUdpFieldsUIModel? = null
):BroadcastModel

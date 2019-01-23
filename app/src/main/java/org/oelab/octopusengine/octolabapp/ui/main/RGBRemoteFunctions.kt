package org.oelab.octopusengine.octolabapp.ui.main

import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers

fun checkUdpFields(): (Observable<ToggleConnectionEvent>) -> Observable<CheckedUdpFieldsUIModel> =
    { from ->
        from.filter { event -> event.buttonOn }
            .map { event ->
                CheckedUdpFieldsUIModel(
                    isValidIPAddress(event.ipAddress), isValidPort(event.udpPort), event
                )
            }
    }

fun isValidIPAddress(text: String): Boolean {
    if (!text.matches("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""".toRegex())) return false
    text.split("""\.""".toRegex()).forEach { s: String -> if (s.toInt() !in 0..255) return false }
    return true
}

fun isValidPort(text: String): Boolean {
    return text.matches("""\d{1,5}""".toRegex()) && text.toInt() in 0..65535
}

fun broadcastRgbViaUdp(
    rgbEventSource: Observable<RGB>,
    socket: IUdpSocket
): (Observable<CheckedUdpFieldsUIModel>) -> Observable<BroadcastRGBViaUdpUIModel> =
    { from: Observable<CheckedUdpFieldsUIModel> ->
        from.filter { checkedModel -> checkedModel.validIPAddress && checkedModel.validPort }
            .flatMap { checkedModel ->
                Observable.fromCallable {
                    socket.open()
                    BroadcastRGBViaUdpUIModel(
                        rgb = null,
                        message = "",
                        sendError = false,
                        OpenSocketError = false
                    )

                }
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .onErrorReturn {
                        socket.close()
                        BroadcastRGBViaUdpUIModel(
                            rgb = null,
                            message = "",
                            sendError = false,
                            OpenSocketError = true
                        )

                    }
                    .flatMap { broadcastModel ->
                        if (broadcastModel.OpenSocketError) {
                            Observable.just(broadcastModel)
                        } else {
                            rgbEventSource
                                .observeOn(Schedulers.io())
                                .map { rgb: RGB ->
                                    val message = toRGBUdpMessage(rgb)
                                    socket.send(
                                        message = message,
                                        ipAddress = checkedModel.event.ipAddress,
                                        port = checkedModel.event.udpPort
                                    )
                                    BroadcastRGBViaUdpUIModel(
                                        rgb = rgb,
                                        message = message,
                                        sendError = false,
                                        OpenSocketError = false
                                    )
                                }
                                .onErrorReturn {
                                    BroadcastRGBViaUdpUIModel(
                                        rgb = null,
                                        message = "",
                                        sendError = true,
                                        OpenSocketError = false
                                    )
                                }

                        }

                    }

            }
            .filter { model: BroadcastRGBViaUdpUIModel -> model.OpenSocketError || model.sendError }
    }



package org.oelab.octopusengine.octolabapp.ui.main

import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler

fun checkUdpFields(): (Observable<ToggleConnectionEvent>) -> Observable<CheckedUdpFieldsUIModel> =
    { from ->
        from.map { event ->
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
    socket: IUdpSocket,
    scheduler: Scheduler
): (Observable<CheckedUdpFieldsUIModel>) -> Observable<BroadcastRGBViaUdpUIModel> =
    { from: Observable<CheckedUdpFieldsUIModel> ->
        from.filter { checkedModel -> checkedModel.validIPAddress && checkedModel.validPort }
            .flatMap { checkedModel ->
                Observable.fromCallable {
                    socket.open()
                    BroadcastRGBViaUdpUIModel(
                        openSocket = true
                    )

                }
                    .subscribeOn(scheduler)
                    .observeOn(scheduler)
                    .onErrorReturn {
                        socket.close()
                        BroadcastRGBViaUdpUIModel(
                            OpenSocketError = true
                        )

                    }
                    .switchMap{ broadcastModel ->
                        if (broadcastModel.OpenSocketError) {
                            Observable.just(broadcastModel)

                        } else {
                            rgbEventSource
                                .observeOn(scheduler)
                                .map { rgb: RGB ->
                                    val message = sendRGB(rgb, socket, checkedModel)
                                    BroadcastRGBViaUdpUIModel(
                                        rgb = rgb,
                                        message = message,
                                        displayToUI = false
                                    )
                                }
                                .onErrorReturn {
                                    BroadcastRGBViaUdpUIModel(
                                        sendError = true
                                    )
                                }.startWith(broadcastModel)

                        }

                    }

            }
            .filter { model: BroadcastRGBViaUdpUIModel -> model.displayToUI }
    }

fun sendRGB(
    rgb: RGB,
    socket: IUdpSocket,
    checkedModel: CheckedUdpFieldsUIModel
): String {
    val redMessage= "R${rgb.red}"
    socket.send(
        message = redMessage,
        ipAddress = checkedModel.toggleEvent.ipAddress,
        port = checkedModel.toggleEvent.udpPort,
        charset = Charsets.UTF_8
    )
    val greenMessage = "G${rgb.green}"
    socket.send(
        message = greenMessage,
        ipAddress = checkedModel.toggleEvent.ipAddress,
        port = checkedModel.toggleEvent.udpPort,
        charset = Charsets.UTF_8
    )
    val blueMessage = "B${rgb.blue}"
    socket.send(
        message = blueMessage,
        ipAddress = checkedModel.toggleEvent.ipAddress,
        port = checkedModel.toggleEvent.udpPort,
        charset = Charsets.UTF_8
    )

    return redMessage + greenMessage + blueMessage
}

fun closeSocket(
    socket: IUdpSocket,
    scheduler: Scheduler
): ObservableTransformer<CheckedUdpFieldsUIModel, CloseSocketUIModel> {
    return object : ObservableTransformer<CheckedUdpFieldsUIModel, CloseSocketUIModel> {
        override fun apply(upstream: Observable<CheckedUdpFieldsUIModel>): ObservableSource<CloseSocketUIModel> {
            return upstream
                .map {
                    socket.close()
                    CloseSocketUIModel()
                }
        }
    }
}


package org.oelab.octopusengine.octolabapp.ui.main

import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.cast

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

data class OpenSocketErrorModel(val value: Unit = Unit) : BroadcastModel
data class CloseSocketModel(val value: Unit = Unit) : BroadcastModel
data class SendErrorModel(val value: Unit = Unit) : BroadcastModel


fun broadcastRgbViaUdp(
    rgbEventSource: Observable<RGB>,
    socket: IUdpSocket,
    scheduler: Scheduler
): (Observable<CheckedUdpFieldsUIModel>) -> Observable<BroadcastModel> =
    { from: Observable<CheckedUdpFieldsUIModel> ->
        val checkedFieldsEvent = from.observeOn(scheduler)
            .share()

        val buttonOffCloseSocketEvent = checkedFieldsEvent
            .observeOn(scheduler)
            .filter { !it.toggleEvent.buttonOn }
            .map {
                socket.close()
                CloseSocketModel() as BroadcastModel
            }.share()


        val buttonOnOpenSocketEvent = checkedFieldsEvent
            .observeOn(scheduler)
            .filter { event -> event.toggleEvent.buttonOn }
            .filter { checkedModel -> checkedModel.validIPAddress && checkedModel.validPort }
            .map { checkedModel ->
                socket.open()
                OpenSocketModel(checkedModel = checkedModel) as BroadcastModel
            }
            .onErrorResumeNext(Observable.fromCallable {
                socket.close()
                OpenSocketErrorModel() as BroadcastModel
            }).share()


        val broadcastRgbViaUdp = Observable.merge(buttonOffCloseSocketEvent, buttonOnOpenSocketEvent)
            .observeOn(scheduler)
            .switchMap { broadcastModel ->
                when (broadcastModel) {
                    is OpenSocketModel -> {
                        rgbEventSource
                            .observeOn(scheduler)
                            .map { rgb: RGB ->
                                val message = sendRGB(rgb, socket, broadcastModel.checkedModel)
                                SentRGBModel(
                                    rgb = rgb,
                                    message = message,
                                    checkedModel = broadcastModel.checkedModel
                                ) as BroadcastModel
                            }
                            .retry(3)
                            .onErrorReturn {
                                socket.close()
                                SendErrorModel()
                            }
                    }
                    is CloseSocketModel, is OpenSocketErrorModel -> Observable.empty<BroadcastModel>()
                    else -> {
                        Observable.empty<BroadcastModel>()
                    }
                }

            }


        Observable.merge(buttonOnOpenSocketEvent, broadcastRgbViaUdp,buttonOffCloseSocketEvent)
            .filter { model -> model !is SentRGBModel }
            .cast()
    }

fun sendRGB(
    rgb: RGB,
    socket: IUdpSocket,
    checkedModel: CheckedUdpFieldsUIModel
): String {
    val redMessage = "R${rgb.red}"
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


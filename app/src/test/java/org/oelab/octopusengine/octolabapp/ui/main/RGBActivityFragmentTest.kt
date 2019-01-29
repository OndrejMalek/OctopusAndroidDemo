package org.oelab.octopusengine.octolabapp.ui.main

import io.mockk.*
import io.reactivex.Observable
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.junit.Test
import java.util.concurrent.TimeUnit

class RGBActivityFragmentTest {

    private val toggleOn = ToggleConnectionEvent(true, "1.1.1.1", "10")

    @Test
    fun checkUdpFields_correct() {
        Observable
            .just(toggleOn)
            .compose(checkUdpFields())
            .test()
            .assertValue(CheckedUdpFieldsUIModel(true, true, toggleOn))
    }

    @Test
    fun checkUdpFields_incorrectIp() {
        val toggleOnWrongIp = ToggleConnectionEvent(true, "1.1.1.", "10")
        Observable
            .just(toggleOnWrongIp)
            .compose(checkUdpFields())
            .test()
            .assertValue(CheckedUdpFieldsUIModel(false, true, toggleOnWrongIp))
    }

    @Test
    fun checkUdpFields_incorrectPort() {
        val toggleOnWrongIp = ToggleConnectionEvent(true, "1.1.1.1", "99999")
        Observable
            .just(toggleOnWrongIp)
            .compose(checkUdpFields())
            .test()
            .assertValue(CheckedUdpFieldsUIModel(true, false, toggleOnWrongIp))
    }


    private val red = RGB(255, 0, 0)
    private val green = RGB(0, 255, 0)

    @Test
    fun broadcastRgbViaUdp_noError() {
        val socket = mockk<IUdpSocket>(relaxed = true)
        val validCheckedModel = CheckedUdpFieldsUIModel(true, true, toggleOn)
        val rgbEventSource = BehaviorSubject.create<RGB>()
        val scheduler = Schedulers.trampoline()
//        scheduler.advanceTimeBy(1, TimeUnit.SECONDS)
        val subject = BehaviorSubject.create<CheckedUdpFieldsUIModel>()

        val test = subject
            .compose(broadcastRgbViaUdp(rgbEventSource, socket, scheduler))
            .test()


        test
            .assertValues(
                OpenSocketModel(
                    checkedModel = validCheckedModel
                )
            )
            .assertOf { verify(exactly = 1) { socket.open() } }
            .assertOf {
                verify(exactly = 2 * 3) {
                    socket.send(
                        any(),
                        any(),
                        any(),
                        any()
                    )
                }
            }
            .assertNoErrors()

        assert(rgbEventSource.hasObservers())
        rgbEventSource.onNext(red)
        rgbEventSource.onNext(blue)
        rgbEventSource.doOnSubscribe { print("rgbEventSource.doOnSubscribe") }
        rgbEventSource.onComplete()

        subject.onNext(validCheckedModel)
        subject.onComplete()
    }

    private val blue = RGB(0, 0, 255)

    @Test
    fun broadcastRgbViaUdp_withError_CannotOpenSocketException() {
        val socket = mockk<IUdpSocket>() {
            every { open() } throws UdpSocket.CannotOpenSocketException()
            every { close() } just Runs
        }

        Observable
            .just(CheckedUdpFieldsUIModel(true, true, toggleOn))
            .compose(broadcastRgbViaUdp(Observable.just(red, green, blue), socket, Schedulers.trampoline()))
            .test()
            .assertNoErrors()
            .assertValues(
                OpenSocketErrorModel()
            )
    }

    @Test
    fun broadcastRgbViaUdp_withError_ExceptionOnSend() {
        val socket = mockk<IUdpSocket>(relaxed = true) {
            every { send(any(), any(), any(), any()) } throws Exception()
        }

        val validCheckedModel = CheckedUdpFieldsUIModel(true, true, toggleOn)
        Observable
            .just(validCheckedModel)
            .compose(broadcastRgbViaUdp(Observable.just(red, green, blue), socket, Schedulers.trampoline()))
            .test()
            .assertNoErrors()
            .assertValues(
                OpenSocketModel(
                    checkedModel = validCheckedModel
                ),
                SendErrorModel()
            )
    }
}
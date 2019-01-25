package org.oelab.octopusengine.octolabapp.ui.main

import io.mockk.*
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import org.junit.Test

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

        Observable
            .just(CheckedUdpFieldsUIModel(true, true, toggleOn))
            .compose(broadcastRgbViaUdp(Observable.just(red, green), socket, Schedulers.trampoline()))
            .test()
            .assertNoErrors()
            .assertValues(BroadcastRGBViaUdpUIModel(openSocket = true))
            .assertOf { verify(exactly = 1) { socket.open() } }
            .assertOf {
                verify(exactly = 2 * 3) {
                    socket.send(
                        any(),
                        any(),
                        any(),
                        Charsets.UTF_8
                    )
                }
            }
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
                BroadcastRGBViaUdpUIModel(
                    OpenSocketError = true
                )
            )
    }

    @Test
    fun broadcastRgbViaUdp_withError_sendException() {
        val socket = mockk<IUdpSocket>(relaxed = true) {
            every { send(any(), any(), any(), any()) } throws Exception()
        }

        Observable
            .just(CheckedUdpFieldsUIModel(true, true, toggleOn))
            .compose(broadcastRgbViaUdp(Observable.just(red, green, blue), socket, Schedulers.trampoline()))
            .test()
            .assertNoErrors()
            .assertValues(
                BroadcastRGBViaUdpUIModel(
                    openSocket = true
                ),
                BroadcastRGBViaUdpUIModel(
                    sendError = true
                )
            )
    }
}
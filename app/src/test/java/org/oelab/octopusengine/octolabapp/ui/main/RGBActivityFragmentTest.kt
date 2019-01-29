package org.oelab.octopusengine.octolabapp.ui.main

import com.google.common.truth.Truth
import io.mockk.*
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import org.oelab.octopusengine.octolabapp.ui.rgb.*

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
        val socket = mockk<IUdpSocket>()
        every { socket.open() }.just(Runs)

        val validCheckedModel = CheckedUdpFieldsUIModel(true, true, toggleOn)
        val rgbEventSource = Observable.just(red, blue)
        val scheduler = Schedulers.trampoline()


        Observable.just(validCheckedModel)
            .compose(broadcastRgbViaUdp(rgbEventSource, socket, scheduler))
            .test()
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
            .compose(
                broadcastRgbViaUdp(
                    Observable.just(red, green, blue),
                    socket,
                    Schedulers.trampoline()
                )
            )
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
            .compose(
                broadcastRgbViaUdp(
                    Observable.just(red, green, blue),
                    socket,
                    Schedulers.trampoline()
                )
            )
            .test()
            .assertNoErrors()
            .assertValues(
                OpenSocketModel(
                    checkedModel = validCheckedModel
                ),
                SendErrorModel()
            )
    }

    @Test
    fun name() {
        val shared = Observable.just(1, 2, 3, 4).share()

        var a: Int = 0
        val share2 = shared.doOnNext {
            print("shared: $it")
            a = 10
        }.share()

        val end = share2.doOnNext {
            a = 2
        }

        share2.subscribe()
        Truth.assertThat(a).isEqualTo(2)
    }
}
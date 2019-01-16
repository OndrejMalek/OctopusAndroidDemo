package org.oelab.octopusengine.octolabapp.ui.main

import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.userChanges
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function3
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_rgb.*
import org.oelab.octopusengine.octolabapp.R
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import java.util.concurrent.TimeUnit


/**
 * A placeholder fragment containing a simple view.
 */
class RGBActivityFragment : androidx.fragment.app.Fragment() {


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_rgb, container, false)
    }

    private val disposables = CompositeDisposable()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delayMillis = 2L
        val rgbObservable: Observable<RGB> = Observable.combineLatest(
            redSeekBar.userChanges().debounce(delayMillis, TimeUnit.MILLISECONDS),
            greenSeekBar.userChanges().debounce(delayMillis, TimeUnit.MILLISECONDS),
            blueSeekBar.userChanges().debounce(delayMillis, TimeUnit.MILLISECONDS),
            Function3 { red: Int, green: Int, blue: Int -> RGB(red, green, blue) })


        disposables.add(
            toggleConnectionButton
                .clicks()
                .map {
                    ToggleConnectionEvent(
                        ipAddressEditText.text.toString().trim(),
                        udpPortEditText.text.toString().trim()
                    )
                }.subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .map { event ->
                    Log.d("map event", "${Thread.currentThread()}")
                    if (isValidIPAddress(event.ipAddress) && isValidPort(event.udpPort)) {
                        connect(rgbObservable, event)
                        Log.d(RGBActivityFragment::class.simpleName, "olee")
                    }
                    ToggleConnectionUIModel(
                        isValidIPAddress(event.ipAddress),
                        isValidPort(event.udpPort)
                    )
                }

                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { model: ToggleConnectionUIModel ->
                    if (!model.validIPAddress) ipAddressEditText.error =
                            "Invalid IP address format xxx.xxx.xxx.xxx" else ipAddressEditText.error = null
                    if (!model.validPort) udpPortEditText.error =
                            "Invalid port number 0-65535" else udpPortEditText.error = null

                    Log.d("subscribe model", "${Thread.currentThread()}")
                }
        )

        Log.d("MAIN_THREAD", "${Thread.currentThread()}")

        createClientUdp()

    }

    private fun createClientUdp() {

    }


    private fun isValidIPAddress(text: String): Boolean {
        if (!text.matches("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""".toRegex())) return false
        text.split("""\.""".toRegex()).forEach { s: String -> if (s.toInt() !in 0..255) return false }
        return true
    }

    private fun isValidPort(text: String): Boolean {
        return text.matches("""\d{1,5}""".toRegex()) && text.toInt() in 0..65535
    }

    fun connect(
        rgbObservable: Observable<RGB>,
        event: ToggleConnectionEvent
    ) {

        var serverSocket: DatagramSocket

        val socketAddress = InetSocketAddress(
            InetAddress.getByName(event.ipAddress),
            event.udpPort.toInt()
        )
        disposables.add(Observable.fromCallable {
            val channel = DatagramChannel.open()

            serverSocket = channel.socket()
            Log.d("Socket", "serveSocket: ${serverSocket}, ${Thread.currentThread()} , isMainThread: ${Thread.currentThread() == Looper.getMainLooper().thread}, Main: ${Looper.getMainLooper().thread}, reachable: ${InetAddress.getByName(event.ipAddress).isReachable(4000)}")
//            serverSocket.reuseAddress = true
            serverSocket.bind(null)


            serverSocket
        }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribeBy(
                onNext = { socket: DatagramSocket ->
                    disposables.add(
                        rgbObservable
                            .subscribeOn(AndroidSchedulers.mainThread())
                            .observeOn(Schedulers.io())
                            .subscribeBy(
                                onNext = { rgb: RGB ->
                                    val rgbUdpMessage = rgbUdpMessage(rgb)
                                    socket.send(
                                        DatagramPacket(
                                            rgbUdpMessage,
                                            rgbUdpMessage.size,
                                            socketAddress

                                        )

                                    )
                                },
                                onError = { t: Throwable? ->
                                    Log.d(
                                        "RGB ",
                                        "OnError Connection: ${t?.message
                                            ?: t} , Thread: ${Thread.currentThread()}"
                                    )
                                })
                    )

                },
                onError = { t: Throwable? ->
                    Log.d(
                        "DatagramSocket",
                        "OnError Connection: ${t?.message ?: t}, Thread: ${Thread.currentThread()}, event: $event ," +
                                " InetAddress: ${InetAddress.getByName(event.ipAddress)}, ${t?.printStackTrace()}"
                    )

                })
        )

    }

    private fun rgbUdpMessage(rgb: RGB) =
        "{R: ${rgb.red}, G:${rgb.green}, B:${rgb.blue}}\n".toByteArray()

    override fun onStop() {
        super.onStop()
        disposables.clear()
    }
}


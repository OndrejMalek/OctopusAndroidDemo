package org.oelab.octopusengine.octolabapp.ui.main

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.userChanges
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function3
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_rgb.*
import org.oelab.octopusengine.octolabapp.R
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
        val rgbEventSource: Observable<RGB> =
            Observable
                .combineLatest(
                    redSeekBar.userChanges().debounce(delayMillis, TimeUnit.MILLISECONDS),
                    greenSeekBar.userChanges().debounce(delayMillis, TimeUnit.MILLISECONDS),
                    blueSeekBar.userChanges().debounce(delayMillis, TimeUnit.MILLISECONDS),
                    Function3 { red: Int, green: Int, blue: Int -> RGB(red, green, blue) })
                .subscribeOn(AndroidSchedulers.mainThread())

        val toggleConnectionEventSource = toggleConnectionButton
            .clicks()
            .subscribeOn(AndroidSchedulers.mainThread())
            .map {
                ToggleConnectionEvent(
                    toggleConnectionButton.isChecked,
                    udpIpAddressEditText.text.toString().trim(),
                    udpPortEditText.text.toString().trim()
                )
            }
            .observeOn(Schedulers.io())
            .compose(checkUdpFields())
            .publish()

        toggleConnectionEventSource
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onNext = { model: CheckedUdpFieldsUIModel ->
                if (!model.validIPAddress) udpIpAddressEditText.error =
                        "Invalid IP address format xxx.xxx.xxx.xxx" else udpIpAddressEditText.error = null
                if (!model.validPort) udpPortEditText.error =
                        "Invalid port number 0-65535" else udpPortEditText.error = null

                Log.d("subscribe model", "${Thread.currentThread()}")
            }).addTo(disposables)


        toggleConnectionEventSource
            .observeOn(Schedulers.io())
            .compose(broadcastRgbViaUdp(rgbEventSource, UdpSocket()))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onNext = { model: BroadcastRGBViaUdpUIModel ->
                when {
                    model.OpenSocketError -> {
                        Toast.makeText(
                            this.context,
                            "Error! Connot open communication. Check permissions",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    model.sendError -> {
                        Toast.makeText(this.context, "Error cannot send data!", Toast.LENGTH_LONG).show()
                    }

                }

            })
            .addTo(disposables)


        toggleConnectionEventSource.connect().addTo(disposables)
        Log.d("MAIN_THREAD", "${Thread.currentThread()}")

    }


    override fun onStop() {
        super.onStop()
        disposables.clear()
    }
}


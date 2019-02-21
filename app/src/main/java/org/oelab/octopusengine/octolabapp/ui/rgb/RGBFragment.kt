package org.oelab.octopusengine.octolabapp.ui.rgb

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.changes
import com.jakewharton.rxbinding3.widget.checkedChanges
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.exceptions.OnErrorNotImplementedException
import io.reactivex.functions.Function3
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_rgb.*
import kotlinx.android.synthetic.main.rgb_help_dialog.view.*
import kotlinx.android.synthetic.main.rgb_remote_single_device.*
import org.oelab.octopusengine.octolabapp.R
import java.util.concurrent.TimeUnit


class RGBFragment : androidx.fragment.app.Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_rgb, container, false)
    }

    private val subscriptions = CompositeDisposable()

    override fun onResume() {
        super.onResume()

        subs(
            redSeekBar,
            greenSeekBar,
            blueSeekBar,
            toggleConnectionButton,
            udpIpAddressEditText,
            udpPortEditText
        )


        floating_action_button.clicks().subscribeBy(
            onNext = { },
            onError = {throwable -> throw OnErrorNotImplementedException(throwable) }
        ).addTo(subscriptions)

        this.setHasOptionsMenu(true)
    }

    private fun subs(
        redSeekBar: SeekBar,
        greeSeekBar: SeekBar,
        blueSeekBar: SeekBar,
        toggleConnectionButton: Switch,
        udpIpAddressEditText: EditText,
        udpPortEditText: EditText
    ) {
        val delayMillis = 2L
        val rgbEventSource: Observable<RGB> =
            Observable
                .combineLatest(
                    redSeekBar.changes().debounce(delayMillis, TimeUnit.MILLISECONDS),
                    greeSeekBar.changes().debounce(delayMillis, TimeUnit.MILLISECONDS),
                    blueSeekBar.changes().debounce(delayMillis, TimeUnit.MILLISECONDS),
                    Function3 { red: Int, green: Int, blue: Int ->
                        RGB(
                            red,
                            green,
                            blue
                        )
                    })
                .subscribeOn(AndroidSchedulers.mainThread())

        val checkFieldsAndToggleConnection = toggleConnectionButton
            .checkedChanges()
            .subscribeOn(AndroidSchedulers.mainThread())
            .map {
                ToggleConnectionEvent(
                    toggleConnectionButton.isChecked,
                    udpIpAddressEditText.text.toString().trim(),
                    udpPortEditText.text.toString().trim()
                )
            }
            .doOnNext { event: ToggleConnectionEvent -> enableUdpFields(!event.buttonOn) }
            .observeOn(Schedulers.io())
            .compose(checkUdpFields())
            .publish()

        checkFieldsAndToggleConnection
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { model: CheckedUdpFieldsUIModel ->
                    if (!model.validIPAddress) udpIpAddressEditText.error =
                        "Invalid IP address format xxx.xxx.xxx.xxx" else udpIpAddressEditText.error = null
                    if (!model.validPort) udpPortEditText.error =
                        "Invalid port number 0-65535" else udpPortEditText.error = null

                    if (model.toggleEvent.buttonOn && (!model.validIPAddress || !model.validPort)) toggleConnectionButton.isChecked =
                        false

                    Log.d("subscribe model", "${Thread.currentThread()}")
                },
                onError = { throwable -> throw OnErrorNotImplementedException(throwable) }
            ).addTo(subscriptions)


        val socket = UdpSocket()

        val broadcastRgbViaUdpEvent = checkFieldsAndToggleConnection
            .observeOn(Schedulers.io())
            .compose(broadcastRgbViaUdp(rgbEventSource, socket, Schedulers.io()))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { model: BroadcastModel ->
                    when (model) {
                        is OpenSocketErrorModel -> {
                            showMessage("Error! Connot open communication. Check permissions")
                            toggleConnectionButton.isChecked = false
                        }
                        is SendErrorModel -> {
                            showMessage("Error cannot send data!")
                        }
                        is OpenSocketModel -> {
                            showMessage("UDP Socket opened.")
                        }
                        is CloseSocketModel -> showMessage("UDP Socket closed.")
                    }
                },
                onError = { throwable -> throw OnErrorNotImplementedException(throwable) })
            .addTo(subscriptions)

        checkFieldsAndToggleConnection.connect().addTo(subscriptions)
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_rgb, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuHelp -> showHelp()
        }
        return true
    }

    private fun showHelp() {

        val view = this.layoutInflater.inflate(R.layout.rgb_help_dialog, null)
        view.htmlHelpText.setHtml(R.raw.rgb_guide)

        val alertDialog = AlertDialog.Builder(this.context!!)
            .setCancelable(true)
            .setTitle("Help")
            .setView(view)
            .create()

        view.okButton.setOnClickListener { alertDialog.dismiss() }

        alertDialog
            .show()
    }


    private fun showMessage(message: String) {
        Toast.makeText(
            this.context,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun enableUdpFields(enable: Boolean) {
        udpIpAddressEditText.isEnabled = enable
        udpPortEditText.isEnabled = enable
    }


    override fun onStop() {
        super.onStop()
        subscriptions.clear()
    }
}


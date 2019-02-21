package org.oelab.octopusengine.octolabapp.ui.rgb

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnLayout
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.checkedChanges
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.sliders.AlphaSlideBar
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import eu.malek.utils.DisableScrollOnTouchListener
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.exceptions.OnErrorNotImplementedException
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_rgb.*
import kotlinx.android.synthetic.main.rgb_help_dialog.view.*
import kotlinx.android.synthetic.main.rgb_remote_single_device.*
import kotlinx.android.synthetic.main.rgb_remote_single_device.view.*
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

        addRgbDeviceLayout(contentLinearLayout, this.context)

        floating_action_button.clicks().subscribeBy(
            onNext = {
                addRgbDeviceLayout(contentLinearLayout, this.context)
                    .doOnLayout {  scrollView.fullScroll(View.FOCUS_DOWN)}
            },
            onError = { throwable -> throw OnErrorNotImplementedException(throwable) }
        ).addTo(subscriptions)



        this.setHasOptionsMenu(true)
    }

    private fun addRgbDeviceLayout(linearLayout: LinearLayout, context: Context?): View {
        val view = View.inflate(context, R.layout.rgb_remote_single_device, null)
        linearLayout.addView(view)

        subscribeRGBDeviceLayout(
            view.toggleConnectionButton,
            view.udpIpAddressEditText,
            view.udpPortEditText,
            view.colorPickerView,
            view.brightnessSlide,
            view.alphaSlideBar
        )

        return view
    }

    private fun subscribeRGBDeviceLayout(
        toggleConnectionButton: ToggleButton,
        udpIpAddressEditText: EditText,
        udpPortEditText: EditText,
        colorPickerView: ColorPickerView,
        brightnessSlideBar: BrightnessSlideBar,
        alphaSlideBar: AlphaSlideBar
    ) {

//        colorPickerView.setFlagView(CustomFlag(this, R.layout.layout_flag))
        val rgbEventSubject = BehaviorSubject.create<RGB>()

        colorPickerView.setOnTouchListener(DisableScrollOnTouchListener(colorPickerView))
        colorPickerView.attachBrightnessSlider(brightnessSlideBar)
        brightnessSlideBar.setOnTouchListener(DisableScrollOnTouchListener(brightnessSlideBar))
        colorPickerView.attachAlphaSlider(alphaSlideBar) // not used but crashes without
        colorPickerView.setLifecycleOwner(this)
        colorPickerView.setColorListener(
            ColorEnvelopeListener { envelope, fromUser ->
                rgbEventSubject.onNext(
                    RGB(
                        envelope.argb[1],
                        envelope.argb[2],
                        envelope.argb[3]
                    )
                )
            })

        val rgbEventSource = rgbEventSubject.debounce(2L,TimeUnit.MILLISECONDS,Schedulers.computation())




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


package org.oelab.octopusengine.octolabapp.ui.rgb

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.lifecycle.ViewModelProviders
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.checkedChanges
import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.ReplayRelay
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.sliders.AlphaSlideBar
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import eu.malek.persistState
import eu.malek.attachToReplayStream
import eu.malek.utils.DisableScrollOnTouchListener
import eu.malek.utils.UtilsViewGroup
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.exceptions.OnErrorNotImplementedException
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.rgb_activity.*
import kotlinx.android.synthetic.main.rgb_help_dialog.view.*
import kotlinx.android.synthetic.main.rgb_remote_single_device.*
import kotlinx.android.synthetic.main.rgb_remote_single_device.view.*
import org.oelab.octopusengine.octolabapp.R
import org.oelab.octopusengine.octolabapp.ui.App
import java.util.*
import java.util.concurrent.TimeUnit


class RGBActivity : AppCompatActivity() {

    private val subscriptions = CompositeDisposable()

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        this.menuInflater.inflate(R.menu.menu_rgb, menu)
        return super.onCreateOptionsMenu(menu)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.rgb_activity)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val viewModel = ViewModelProviders.of(this).get(RGBViewModel::class.java)

//
//        viewModel.fabReplay
//            .take(1)
//            .subscribeBy { rgbDeviceState ->
//                subscribeRGBDeviceLayout(
//                    addRgbDeviceLayout(contentLinearLayout, this),
//                    rgbDeviceState
//                )
//
//            }.addTo(subscriptions)


        val appScopeMap = (this.application as App).scope

        floating_action_button
            .clicks()
            .skip(1)
            .compose(
                persistState(
                    appScopeMap,
                    "addRgbDevice"
                )
            )
            .scan(0, { akku, value -> akku + 1 })
            .map { RgbDeviceState(it) }
            .subscribeBy(
                onNext = { rgbDeviceState ->
                    val rgbDeviceLayout = addRgbDeviceLayout(contentLinearLayout, this)
                    rgbDeviceLayout.doOnLayout { view ->
                        UtilsViewGroup.smoothScrollToViewBottom(scrollView, view)
                    }

                    subscribeRGBDeviceLayout(rgbDeviceLayout, rgbDeviceState, appScopeMap)
                },
                onError = { throwable -> throw OnErrorNotImplementedException(throwable) }
            ).addTo(subscriptions)
    }

    private fun addRgbDeviceLayout(linearLayout: LinearLayout, context: Context?): View {
        val view = View.inflate(context, R.layout.rgb_remote_single_device, null)
        linearLayout.addView(view)
        return view
    }

    fun subscribeRGBDeviceLayout(
        view: View,
        rgbDeviceState: RgbDeviceState,
        appScopeMap: HashMap<String, Any>
    ) {

        val toggleConnectionButton = view.toggleConnectionButton
        val udpIpAddress = view.udpIpAddressEditText
        val udpPortEdit = view.udpPortEditText
        val brightnessSlideBar = view.brightnessSlide
        val colorPickerView = view.colorPickerView
        val alphaSlideBar = view.alphaSlideBar


        val rgbPickerEvente =
            createRgbPickerSubject(brightnessSlideBar, colorPickerView, alphaSlideBar)
                .compose(
                    persistState<RGB>(
                        appScopeMap,
                        storageId = "rgbPicker${rgbDeviceState.id}",
                        storedItemsMaxCount = 1,
                        restoreState = { restore: Observable<RGB> ->
                            restore
                                .last(RGB())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeBy{ colorPickerView.fireColorListener(it.toIntColor(), false) }
                        }
                    )
                )


        val checkFieldsAndToggleConnection = toggleConnectionButton
            .checkedChanges()
            .subscribeOn(AndroidSchedulers.mainThread())
            .map {
                ToggleConnectionEvent(
                    toggleConnectionButton.isChecked,
                    udpIpAddress.text.toString().trim(),
                    udpPortEdit.text.toString().trim()
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
                    showUdpIpAddressError(model.validIPAddress)
                    showUdpPortError(model.validPort)

                    if (model.toggleEvent.buttonOn && (!model.validIPAddress || !model.validPort)) toggleConnectionButton.isChecked =
                        false

                    Log.d("subscribe model", "${Thread.currentThread()}")
                },
                onError = { throwable -> throw OnErrorNotImplementedException(throwable) }
            ).addTo(subscriptions)


        val socket = UdpSocket()

        val broadcastRgbViaUdpEvent = checkFieldsAndToggleConnection
            .observeOn(Schedulers.io())
            .compose(broadcastRgbViaUdp(rgbDeviceState.rgbEventRelay, socket, Schedulers.io()))
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

    private fun createRgbPickerSubject(
        brightnessSlideBar: BrightnessSlideBar,
        colorPickerView: ColorPickerView,
        alphaSlideBar: AlphaSlideBar
    ): Observable<RGB> {
        brightnessSlideBar.setOnTouchListener(DisableScrollOnTouchListener(brightnessSlideBar))
        colorPickerView.setOnTouchListener(DisableScrollOnTouchListener(colorPickerView))
        colorPickerView.attachBrightnessSlider(brightnessSlideBar)
        colorPickerView.attachAlphaSlider(alphaSlideBar) // not used but crashes without
        colorPickerView.setLifecycleOwner(this)
        val rgbPickerSubject = BehaviorSubject.create<RGB>()
        colorPickerView.setColorListener(
            ColorEnvelopeListener { envelope, fromUser ->
                rgbPickerSubject.onNext(
                    RGB(
                        envelope.argb[1],
                        envelope.argb[2],
                        envelope.argb[3]
                    )
                )
            })

        return rgbPickerSubject.debounce(2L, TimeUnit.MILLISECONDS, Schedulers.computation())

    }


    private fun showUdpPortError(isValid: Boolean) {
        if (!isValid) udpPortEditText.error =
            "Invalid port number 0-65535" else udpPortEditText.error = null
    }

    private fun showUdpIpAddressError(isValid: Boolean) {
        if (!isValid) udpIpAddressEditText.error =
            "Invalid IP address format xxx.xxx.xxx.xxx" else udpIpAddressEditText.error = null
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuHelp -> showHelp()
            android.R.id.home -> {
                // Respond to the action bar's Up/Home button
//                NavUtils.navigateUpFromSameTask(this)
                this.onBackPressed()
                return true
            }
        }
        return true
    }

    private fun showHelp() {
        val view = this.layoutInflater.inflate(R.layout.rgb_help_dialog, null)
        view.htmlHelpText.setHtml(R.raw.rgb_guide)

        val alertDialog = AlertDialog.Builder(this)
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
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun enableUdpFields(enable: Boolean) {
        udpIpAddressEditText.isEnabled = enable
        udpPortEditText.isEnabled = enable
    }


    override fun onDestroy() {
        subscriptions.clear()
        super.onDestroy()
    }

}


data class RgbDeviceState(
    val id: Int,
    val rgb: RGB = RGB()
) {
    val rgbEventRelay: BehaviorRelay<RGB> = BehaviorRelay.create()

}


data class State(val rgbDeviceStates: ArrayList<RgbDeviceState>) {
}

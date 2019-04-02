package org.oelab.octopusengine.octolabapp.ui.rgb

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.checkedChanges
import com.jakewharton.rxbinding3.widget.textChanges
import com.jakewharton.rxrelay2.PublishRelay
import com.skydoves.colorpickerview.ColorPickerView
import eu.malek.getOrCreateStreamWithInput
import eu.malek.persistStateOfSingleValueView
import eu.malek.persistState
import eu.malek.utils.UtilsViewGroup
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.exceptions.OnErrorNotImplementedException
import io.reactivex.observables.ConnectableObservable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.rgb_activity.*
import kotlinx.android.synthetic.main.rgb_help_dialog.view.*
import kotlinx.android.synthetic.main.rgb_remote_single_device.view.*
import org.oelab.octopusengine.octolabapp.R
import org.oelab.octopusengine.octolabapp.ui.App
import java.util.*
import kotlin.collections.ArrayList


class RGBActivity : AppCompatActivity() {

    private val disposeOnFinish = CompositeDisposable()

    private val subscriptions = disposeOnFinish

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        this.menuInflater.inflate(R.menu.menu_rgb, menu)
        return super.onCreateOptionsMenu(menu)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate() called with: savedInstanceState = [$savedInstanceState]")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.rgb_activity)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val appScopeMap = (this.application as App).scope

        floating_action_button
            .clicks()
            .compose(
                persistState(
                    scopeMap = appScopeMap,
                    storageId = "addRgbDevice"
                )
            )
            .compose(index(0))
            .subscribeBy(
                onNext = { index ->
                    addRgbDeviceView(index, appScopeMap)
                }
            ).addTo(subscriptions)
    }


    private fun addRgbDeviceView(
        deviceIndex: Int,
        appScopeMap: HashMap<String, Any>
    ) {
        Log.d(
            TAG, Thread.currentThread().name + " ### "
                    + "addRgbDeviceView() called with: deviceIndex = [$deviceIndex], appScopeMap = [$appScopeMap]"
        )

        val rgbDeviceLayout = addRgbDeviceLayout(contentLinearLayout, this)
        rgbDeviceLayout.doOnLayout { view ->
            UtilsViewGroup.smoothScrollToViewBottom(scrollView, view)
        }

        val rgbPickerChangedStream =
            createRgbPickerObservable(
                rgbDeviceLayout.brightnessSlide, rgbDeviceLayout.colorPickerView,
                rgbDeviceLayout.alphaSlideBar
            )
                .share()

        saveAndRestoreViewState(
            rgbDeviceLayout.toggleConnectionButton,
            appScopeMap,
            deviceIndex,
            rgbDeviceLayout.udpIpAddressEditText,
            rgbDeviceLayout.udpPortEditText,
            rgbPickerChangedStream,
            rgbDeviceLayout.colorPickerView
        )

        val toggleConnectionButtonChangedEvent = rgbDeviceLayout.toggleConnectionButton
            .checkedChanges()
            .skipInitialValue()
            .subscribeOn(AndroidSchedulers.mainThread())
            .map { isChecked ->
                ToggleConnectionEvent(
                    isChecked,
                    rgbDeviceLayout.udpIpAddressEditText.text.toString().trim(),
                    rgbDeviceLayout.udpPortEditText.text.toString().trim()
                )
            }
            .share()


        val rgbStreamWithInputRelays = getOrCreateStreamWithInput(
            appScopeMap,
            "rgbDevice${deviceIndex}_createRgbDeviceStreamWithState",
            { inputs: ArrayList<*> ->
                createRgbDeviceStreamWithState(
                    inputs[0] as Observable<ToggleConnectionEvent>,
                    inputs[1] as Observable<RGB>,
                    UdpSocket()
                )
            }
        )

        toggleConnectionButtonChangedEvent.subscribe(rgbStreamWithInputRelays.input0 as PublishRelay<ToggleConnectionEvent>).addTo(subscriptions)
        rgbPickerChangedStream.map { it.rgb }.subscribe(rgbStreamWithInputRelays.input1 as PublishRelay<RGB>).addTo(subscriptions)
        val rgbDeviceStream: Observable<*> = rgbStreamWithInputRelays.outputStream


        rgbDeviceStream
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { model ->
                    when (model) {
                        is OpenSocketErrorModel -> {
                            showMessage("Error! Connot open communication. Check permissions")
                            rgbDeviceLayout.toggleConnectionButton.isChecked = false
                        }
                        is SendErrorModel -> {
                            showMessage("Error cannot send data!")
                        }
                        is OpenedSocketModel -> {
                            showMessage("UDP Socket opened.")
                        }
                        is ClosedSocketModel -> {
                            showMessage("UDP Socket closed.")
                        }
                        is CheckedUdpFieldsUIModel -> {
                            showUdpIpAddressError(model.validIPAddress, rgbDeviceLayout.udpIpAddressEditText)
                            showUdpPortError(model.validPort, rgbDeviceLayout.udpPortEditText)

                            if (model.toggleEvent.buttonOn && (!model.validIPAddress || !model.validPort)) rgbDeviceLayout.toggleConnectionButton.isChecked =
                                false
                        }
                        is ToggleConnectionEvent -> {
                            enableUdpFields(
                                !model.buttonOn,
                                rgbDeviceLayout.udpIpAddressEditText,
                                rgbDeviceLayout.udpPortEditText
                            )
                        }
                    }
                },

                onError = { throwable -> throw OnErrorNotImplementedException(throwable) }
            ).addTo(subscriptions)

    }




    private fun addRgbDeviceLayout(linearLayout: LinearLayout, context: Context?): View {
        val view = View.inflate(context, R.layout.rgb_remote_single_device, null)
        linearLayout.addView(view)
        return view
    }

    private fun saveAndRestoreViewState(
        _toggleConnectionButton: ToggleButton,
        appScopeMap: HashMap<String, Any>,
        deviceIndex: Int,
        _udpIpAddressView: EditText,
        _udpPortEditView: EditText,
        rgbPickerEvent: Observable<ColorPickerState>,
        _colorPickerView: ColorPickerView
    ) {
        _toggleConnectionButton
            .clicks()
            .map { _toggleConnectionButton.isChecked }
            .compose(
                persistStateOfSingleValueView(
                    scopeMap = appScopeMap,
                    storageId = "rgbDevice${deviceIndex}_toggleConnection",
                    restoreValue = { checked: Boolean ->
                        _toggleConnectionButton.isChecked = checked
                        enableUdpFields(!checked, _udpIpAddressView, _udpPortEditView)
                    }
                )
            )
            .subscribe()
            .addTo(subscriptions)

        _udpIpAddressView.textChanges()
            .skipInitialValue()
            .map { it.toString() }
            .compose(
                persistStateOfSingleValueView(
                    appScopeMap,
                    "rgbDevice${deviceIndex}_udpIpAddress",
                    { text: String -> _udpIpAddressView.setText(text) }
                )
            )
            .subscribe()
            .addTo(subscriptions)

        _udpPortEditView.textChanges()
            .skipInitialValue()
            .map { it.toString() }
            .compose(persistStateOfSingleValueView(
                scopeMap = appScopeMap,
                storageId = "rgbDevice${deviceIndex}_udpPort",
                restoreValue = { text: String -> _udpPortEditView.setText(text) }
            )).subscribe()
            .addTo(subscriptions)

        rgbPickerEvent
            .compose(
                persistStateOfSingleValueView(
                    scopeMap = appScopeMap,
                    storageId = "rgbDevice${deviceIndex}_colorPicker",
                    restoreValue = { setColorPickerState(it, _colorPickerView) },
                    defaultItem = ColorPickerState()
                )
            ).subscribe()
            .addTo(subscriptions)
    }


    private fun showUdpPortError(isValid: Boolean, udpPortView: EditText) {
        if (!isValid) udpPortView.error =
            "Invalid port number 0-65535" else udpPortView.error = null
    }

    private fun showUdpIpAddressError(isValid: Boolean, udpIpAddressView: EditText) {
        if (!isValid) udpIpAddressView.error =
            "Invalid IP address format xxx.xxx.xxx.xxx" else udpIpAddressView.error = null
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

    private fun enableUdpFields(
        enable: Boolean,
        _udpIpAddressView: EditText,
        _udpPortEditView: EditText
    ) {
        _udpIpAddressView.isEnabled = enable
        _udpPortEditView.isEnabled = enable
    }


    override fun onDestroy() {
        subscriptions.clear()
        super.onDestroy()
    }

    companion object {
        val TAG = RGBActivity::class.simpleName
    }

}


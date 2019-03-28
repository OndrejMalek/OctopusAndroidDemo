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
import com.skydoves.colorpickerview.ColorPickerView
import eu.malek.INFINITE_SIZE
import eu.malek.persistState
import eu.malek.utils.UtilsViewGroup
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.exceptions.OnErrorNotImplementedException
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.rgb_activity.*
import kotlinx.android.synthetic.main.rgb_help_dialog.view.*
import kotlinx.android.synthetic.main.rgb_remote_single_device.view.*
import org.oelab.octopusengine.octolabapp.R
import org.oelab.octopusengine.octolabapp.ui.App
import java.util.*


class RGBActivity : AppCompatActivity() {

    private val subscriptions = CompositeDisposable()

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
                    storageId = "addRgbDevice",
                    storedItemsMaxCount = INFINITE_SIZE
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
            createRgbPickerSubject(
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


        val rgbDeviceStream =
            createRgbDeviceStream(toggleConnectionButtonChangedEvent, rgbPickerChangedStream)


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
            .compose(persistState(
                scopeMap = appScopeMap,
                storageId = "toggleConnectionButton${deviceIndex}",
                restoreState = { observable ->
                    observable
                        .lastElement()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeBy {
                            _toggleConnectionButton.isChecked = it
                            enableUdpFields(!it, _udpIpAddressView, _udpPortEditView)
                        }
                }
            )).subscribe()
            .addTo(subscriptions)

        _udpIpAddressView.textChanges()
            .skipInitialValue()
            .compose(
                persistSingleStateOfView(
                    appScopeMap,
                    "udpIpAddressView${deviceIndex}",
                    { text -> _udpIpAddressView.setText(text) },
                    "" as CharSequence
                )
            )
            .subscribe()
            .addTo(subscriptions)

        _udpPortEditView.textChanges()
            .skipInitialValue()
            .compose(persistState(
                scopeMap = appScopeMap,
                storageId = "udpPortEditView${deviceIndex}",
                restoreState = { observable ->
                    observable
                        .lastElement()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeBy { _udpPortEditView.setText(it) }
                }
            )).subscribe()
            .addTo(subscriptions)

        rgbPickerEvent
            .compose(
                persistState(
                    scopeMap = appScopeMap,
                    storageId = "rgbPickerEvent${deviceIndex}",

                    restoreState = { observable ->
                        observable.lastElement()
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeBy {
                                setColorPickerState(it, _colorPickerView)
                            }
                            .addTo(subscriptions)
                    }
                )
            ).subscribe()
            .addTo(subscriptions)
    }

    /**
     * Persist state of android view represented by single value (EditText, Button Click, Radio .ie not lists)
     */
    fun <T : Any> persistSingleStateOfView(
        appScopeMap: HashMap<String, Any>,
        storageId: String,
        setValue: (T) -> Unit,
        defaultItem: T
    ): (Observable<T>) -> Observable<T> {
        return persistState(
            scopeMap = appScopeMap,
            storageId = storageId,
            storedItemsMaxCount = 1,
            restoreState = { observable ->
                observable
                    .lastElement()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(onSuccess = setValue)
            }
        )
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

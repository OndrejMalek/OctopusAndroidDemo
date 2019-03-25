package org.oelab.octopusengine.octolabapp.ui.rgb

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.checkedChanges
import com.jakewharton.rxbinding3.widget.textChanges
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.sliders.AlphaSlideBar
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import eu.malek.persistState
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
        Log.d(
            TAG, Thread.currentThread().name + " ### "
                    + "subscribeRGBDeviceLayout() called with: view = [$view], rgbDeviceState = [$rgbDeviceState], appScopeMap = [$appScopeMap]"
        )

        val _toggleConnectionButton = view.toggleConnectionButton
        val _udpIpAddressView = view.udpIpAddressEditText
        val _udpPortEditView = view.udpPortEditText
        val _brightnessSlideBarView = view.brightnessSlide
        val _colorPickerView = view.colorPickerView
        val _alphaSlideBarView = view.alphaSlideBar

        toggleConnectionButton.clicks()
            .map { toggleConnectionButton.isChecked }
            .compose(persistState(
                scopeMap = appScopeMap,
                storedItemsMaxCount = 1,
                storageId = "toggleConnectionButton${rgbDeviceState.id}",
                restoreState = { observable ->
                    observable
                        .last(false)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeBy { toggleConnectionButton.isChecked = it }
                }
            )).subscribe()
            .addTo(subscriptions)

        _udpIpAddressView.textChanges()
            .skipInitialValue()
            .compose(persistState(
                scopeMap = appScopeMap,
                storedItemsMaxCount = 1,
                storageId = "udpIpAddressView${rgbDeviceState.id}",
                restoreState = { observable ->
                    observable
                        .last("")
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeBy { _udpIpAddressView.setText(it) }
                }
            )).subscribe()
            .addTo(subscriptions)

        _udpPortEditView.textChanges()
            .skipInitialValue()
            .compose(persistState(
                scopeMap = appScopeMap,
                storedItemsMaxCount = 1,
                storageId = "udpPortEditView${rgbDeviceState.id}",
                restoreState = { observable ->
                    observable
                        .last("")
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeBy { _udpPortEditView.setText(it) }
                }
            )).subscribe()
            .addTo(subscriptions)


        val rgbPickerEvent =
            createRgbPickerSubject(_brightnessSlideBarView, _colorPickerView, _alphaSlideBarView)
                .share()

        rgbPickerEvent
            .compose(
                persistState(
                    scopeMap = appScopeMap,
                    storageId = "rgbPickerEvent${rgbDeviceState.id}",
                    storedItemsMaxCount = 1,
                    restoreState = { observable ->
                        observable.last(ColorPickerState(pureColor = Color.WHITE, brightnessSliderSelectedX =))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeBy {
                                setColorPickerViewColor(it, _colorPickerView)
                            }
                            .addTo(subscriptions)
                    }
                )
            ).subscribe()
            .addTo(subscriptions)


        val checkFieldsAndToggleConnection = _toggleConnectionButton
            .checkedChanges()
            .skipInitialValue()
            .subscribeOn(AndroidSchedulers.mainThread())
            .map {
                ToggleConnectionEvent(
                    _toggleConnectionButton.isChecked,
                    _udpIpAddressView.text.toString().trim(),
                    _udpPortEditView.text.toString().trim()
                )
            }
            .compose(
                persistState(
                    scopeMap = appScopeMap,
                    storageId = "toggleConnection${rgbDeviceState.id}"
                )
            )
            .doOnNext { event: ToggleConnectionEvent ->
                enableUdpFields(
                    !event.buttonOn,
                    _udpIpAddressView,
                    _udpPortEditView
                )
            }
            .observeOn(Schedulers.io())
            .compose(checkUdpFields())
            .publish()

        checkFieldsAndToggleConnection
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { model: CheckedUdpFieldsUIModel ->
                    showUdpIpAddressError(model.validIPAddress, _udpIpAddressView)
                    showUdpPortError(model.validPort, _udpPortEditView)

                    if (model.toggleEvent.buttonOn && (!model.validIPAddress || !model.validPort)) _toggleConnectionButton.isChecked =
                        false

                    Log.d("subscribe model", "${Thread.currentThread()}")
                },
                onError = { throwable -> throw OnErrorNotImplementedException(throwable) }
            ).addTo(subscriptions)


        val socket = UdpSocket()

        val broadcastRgbViaUdpEvent = checkFieldsAndToggleConnection
            .observeOn(Schedulers.io())
            .compose(broadcastRgbViaUdp(rgbPickerEvent.map { it.rgb }, socket, Schedulers.io()))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { model: BroadcastModel ->
                    when (model) {
                        is OpenSocketErrorModel -> {
                            showMessage("Error! Connot open communication. Check permissions")
                            _toggleConnectionButton.isChecked = false
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

    private fun setColorPickerViewColor(
        it: ColorPickerState,
        _colorPickerView: ColorPickerView
    ) {
        Log.d(
            TAG, Thread.currentThread().name + " ### "
                    + "setColorPickerViewColor() called with: it = [$it], _colorPickerView = [$_colorPickerView]"
        )
        if (it.selectorX == ColorPickerState.CENTER_COLOR_PICKER_SELECTOR){
            setColorPickerDefaultValue(_colorPickerView)
        }else{
            _colorPickerView.setSelectorPoint(it.selectorX,it.selectorY)
            _colorPickerView.setCoordinate(it.selectorX,it.selectorY)
            _colorPickerView.pureColor = it.pureColor
            _colorPickerView.brightnessSlider.selectedX = it.brightnessSliderSelectedX
        }
        _colorPickerView.fireColorListener(it.rgb.toIntColor(), false)
    }

    private fun setColorPickerDefaultValue(_colorPickerView: ColorPickerView) {
        _colorPickerView.selectCenter()
        _colorPickerView.pureColor = Color.WHITE
    }

    private fun createRgbPickerSubject(
        _brightnessSlideBar: BrightnessSlideBar,
        _colorPickerView: ColorPickerView,
        _alphaSlideBar: AlphaSlideBar
    ): Observable<ColorPickerState> {
        _brightnessSlideBar.setOnTouchListener(DisableScrollOnTouchListener(_brightnessSlideBar))
        _colorPickerView.setOnTouchListener(DisableScrollOnTouchListener(_colorPickerView))
        _colorPickerView.attachBrightnessSlider(_brightnessSlideBar)
        _colorPickerView.attachAlphaSlider(_alphaSlideBar) // not used but crashes without
        setColorPickerDefaultValue(_colorPickerView)
        val rgbPickerSubject = BehaviorSubject.create<ColorPickerState>()
        _colorPickerView.setColorListener(
            ColorEnvelopeListener { envelope, fromUser ->
                rgbPickerSubject.onNext(
                    ColorPickerState(
                        RGB(
                            envelope.argb[1],
                            envelope.argb[2],
                            envelope.argb[3]
                        ),
                        _colorPickerView.selectedPoint.x,
                        _colorPickerView.selectedPoint.y,
                        _colorPickerView.pureColor,
                        _colorPickerView.brightnessSlider.selectedX

                    )

                )
            })

        return rgbPickerSubject.debounce(2L, TimeUnit.MILLISECONDS, Schedulers.computation())

    }

    data class ColorPickerState(
        val rgb: RGB = RGB(),
        val selectorX: Int = CENTER_COLOR_PICKER_SELECTOR,
        val selectorY: Int = CENTER_COLOR_PICKER_SELECTOR,
        val pureColor: Int = Color.WHITE,
        val brightnessSliderSelectedX: Int = 0
    ){
        companion object {
            const val CENTER_COLOR_PICKER_SELECTOR = -999
        }
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


data class RgbDeviceState(
    val id: Int,
    val rgb: RGB = RGB()
)

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
import androidx.core.app.NavUtils
import androidx.core.view.doOnLayout
import androidx.lifecycle.ViewModelProviders
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.checkedChanges
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import eu.malek.utils.DisableScrollOnTouchListener
import eu.malek.utils.UtilsViewGroup
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

        val rgbDeviceLayout1 = addRgbDeviceLayout(contentLinearLayout, this)
        subscribeRGBDeviceLayout(rgbDeviceLayout1)


        floating_action_button.clicks().subscribeBy(
            onNext = {
                val rgbDeviceLayout = addRgbDeviceLayout(contentLinearLayout, this)

                subscribeRGBDeviceLayout(rgbDeviceLayout)
            },
            onError = { throwable -> throw OnErrorNotImplementedException(throwable) }
        ).addTo(subscriptions)
    }

    private fun addRgbDeviceLayout(linearLayout: LinearLayout, context: Context?): View {
        val view = View.inflate(context, R.layout.rgb_remote_single_device, null)
        linearLayout.addView(view)
        view.doOnLayout { view ->
            UtilsViewGroup.smoothScrollToViewBottom(scrollView, view)
        }
        return view
    }

    fun subscribeRGBDeviceLayout(
        view: View
    ) {

        val toggleConnectionButton = view.toggleConnectionButton
        val udpIpAddress = view.udpIpAddressEditText
        val udpPortEdit = view.udpPortEditText
        val colorPickerView = view.colorPickerView
        val brightnessSlideBar = view.brightnessSlide
        val alphaSlideBar = view.alphaSlideBar


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

        val rgbEventSource = rgbEventSubject.debounce(2L, TimeUnit.MILLISECONDS, Schedulers.computation())

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

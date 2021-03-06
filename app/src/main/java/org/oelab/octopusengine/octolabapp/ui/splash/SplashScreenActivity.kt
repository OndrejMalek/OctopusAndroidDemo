package org.oelab.octopusengine.octolabapp.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import org.oelab.octopusengine.octolabapp.R
import org.oelab.octopusengine.octolabapp.ui.select.SelectActivity
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity() {

    private var subscribe: Disposable? = null

    @SuppressLint("RxSubscribeOnError")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splashscreen_activity)

        subscribe = Observable.just(Unit)
            .delay(800, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onNext = {
                this.startActivity(Intent(this, SelectActivity::class.java))
                finish()
            })
    }

    override fun onStop() {
        super.onStop()
        subscribe?.dispose()
    }
}

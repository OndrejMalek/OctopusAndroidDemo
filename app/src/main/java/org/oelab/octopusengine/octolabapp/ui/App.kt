package org.oelab.octopusengine.octolabapp.ui

import android.app.Application
import android.util.Log

import java.util.HashMap


class App : Application() {
    val scope: HashMap<String, Any> = HashMap()

    override fun onCreate() {
        super.onCreate()

        Log.d(Application::class.simpleName,"onCreate:")
    }
}

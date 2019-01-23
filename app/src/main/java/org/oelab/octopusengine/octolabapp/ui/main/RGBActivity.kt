package org.oelab.octopusengine.octolabapp.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_rgb.*
import org.oelab.octopusengine.octolabapp.R

class RGBActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rgb)
        setSupportActionBar(toolbar)

    }

}

package org.oelab.octopusengine.octolabapp.ui.main

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity;
import org.oelab.octopusengine.octolabapp.R

import kotlinx.android.synthetic.main.activity_rgb.*

class RGBActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rgb)
        setSupportActionBar(toolbar)

    }

}

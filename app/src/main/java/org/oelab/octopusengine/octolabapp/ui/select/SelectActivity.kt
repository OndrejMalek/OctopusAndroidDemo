package org.oelab.octopusengine.octolabapp.ui.select

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_select.*
import kotlinx.android.synthetic.main.content_select.*
import org.oelab.octopusengine.octolabapp.R
import org.oelab.octopusengine.octolabapp.ui.rgb.RGBActivity


class SelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select)
        setSupportActionBar(toolbar)

        RGBCardView.setOnClickListener {
            startActivity(Intent(this,RGBActivity::class.java))
        }
    }
}

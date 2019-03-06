package org.oelab.octopusengine.octolabapp.ui.select

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.select_activity.*
import kotlinx.android.synthetic.main.select_content.*
import org.oelab.octopusengine.octolabapp.R
import org.oelab.octopusengine.octolabapp.ui.rgb.RGBActivity


class SelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.select_activity)
        setSupportActionBar(toolbar)

        rgbCardView.setOnClickListener {
            startActivity(Intent(this,RGBActivity::class.java))
        }
    }
}

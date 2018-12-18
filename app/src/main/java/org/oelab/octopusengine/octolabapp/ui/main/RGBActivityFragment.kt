package org.oelab.octopusengine.octolabapp.ui.main

import androidx.fragment.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_rgb.*
import org.oelab.octopusengine.octolabapp.R

/**
 * A placeholder fragment containing a simple view.
 */
class RGBActivityFragment : androidx.fragment.app.Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val contentView = inflater.inflate(R.layout.fragment_rgb, container, false)



        return contentView
    }
}

package com.moyasar.android.sdkdriver

import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity

class CheckoutActivity : AppCompatActivity() {

    private val viewModel: CheckoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        val donateBtn = findViewById<Button>(R.id.button2)
        donateBtn.setOnClickListener {
            viewModel.beginDonation(this, R.id.paymentSheetFragment)
        }
    }
}

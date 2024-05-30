package com.moyasar.android.sdk

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.moyasar.android.sdk.ui.PaymentFragment

const val MOYASAR_PAYMENT_RESULT = "moyasar-payment-result"

abstract class MoyasarPaymentActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moyasar_payment)
        supportFragmentManager.beginTransaction().apply {
            add(
                R.id.payment_container, PaymentFragment.newInstance(
                    PaymentConfig(
                        amount = getAmount(),
                        description = getDescription(),
                        apiKey = getPublishableKey(),
                        manual = false,
                        metadata = mapOf(
                            "orderId" to getOrderId()
                        ),
                        saveCard = true
                    )
                )
            )
            commit()
        }
    }

    abstract fun getAmount(): Int
    abstract fun getDescription(): String
    abstract fun getPublishableKey(): String
    abstract fun getOrderId(): String
    open fun onPaymentResult(paymentResult: PaymentResult) {
        setResult(
            RESULT_OK, Intent().putExtra(
                MOYASAR_PAYMENT_RESULT, paymentResult
            )
        )
        finish()
    }
}
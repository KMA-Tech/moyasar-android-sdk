package com.moyasar.android.sdk.data

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.moyasar.android.sdk.PaymentResult
import com.moyasar.android.sdk.exceptions.PaymentSheetException
import com.moyasar.android.sdk.payment.models.Payment
import com.moyasar.android.sdk.ui.PaymentAuthFragment

class PaymentAuthFragmentViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(application = application, savedStateHandle = savedStateHandle) {

    val payment: Payment by lazy {
        GsonHolder.gson.fromJson(
            savedStateHandle.get<String>(PaymentAuthFragment.ARG_PAYMENT),
            Payment::class.java
        )
    }

    internal fun getPaymentResult(result: PaymentAuthFragment.AuthResult): PaymentResult {
        when (result) {
            is PaymentAuthFragment.AuthResult.Completed -> {
                if (result.id != payment.id) {
                    return PaymentResult.Failed(PaymentSheetException("Got different ID from auth process ${result.id} instead of ${payment?.id}"))
                }

                payment.apply {
                    status = result.status
                    source["message"] = result.message
                }

                return PaymentResult.Completed(payment)
            }

            is PaymentAuthFragment.AuthResult.Failed -> {
                return PaymentResult.Failed(PaymentSheetException(result.error))
            }

            is PaymentAuthFragment.AuthResult.Canceled -> {
                return PaymentResult.Canceled
            }
        }
    }


}
package com.moyasar.android.sdk.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import androidx.annotation.IdRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.textfield.TextInputLayout
import com.moyasar.android.sdk.InvalidConfigException
import com.moyasar.android.sdk.PaymentConfig
import com.moyasar.android.sdk.R
import com.moyasar.android.sdk.data.FieldType
import com.moyasar.android.sdk.data.GsonHolder
import com.moyasar.android.sdk.data.FieldValidator
import com.moyasar.android.sdk.data.PaymentFragmentUiState
import com.moyasar.android.sdk.data.PaymentFragmentViewModel

class PaymentFragment : Fragment() {

    private val viewModel: PaymentFragmentViewModel by viewModels()

    private var nameOnCardInput: TextInputLayout? = null
    private var numberInput: TextInputLayout? = null
    private var expiryInput: TextInputLayout? = null
    private var cvcInput: TextInputLayout? = null

    private val nameTextWatcher = object : SimpleTextWatcher() {
        override fun afterTextChanged(s: Editable?) {
            super.afterTextChanged(s)
            viewModel.onTextEvent(s, FieldType.Name)
        }
    }

    private val numberTextWatcher = object : SimpleTextWatcher() {
        override fun afterTextChanged(s: Editable?) {
            super.afterTextChanged(s)
            viewModel.onTextEvent(s, FieldType.Number)
        }
    }

    private val expiryWatcher = object : SimpleTextWatcher() {
        override fun afterTextChanged(s: Editable?) {
            super.afterTextChanged(s)
            viewModel.onTextEvent(s, FieldType.Expiry)
        }
    }

    private val cvcWatcher = object : SimpleTextWatcher() {
        override fun afterTextChanged(s: Editable?) {
            super.afterTextChanged(s)
            viewModel.onTextEvent(s, FieldType.Cvc)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        val view = inflater.inflate(R.layout.fragment_payment, container, false)

        //name on card
        nameOnCardInput = setupInputWatcher(
            parentView = view,
            viewId = R.id.nameOnCardInput,
            validation = FieldType.Name,
            textWatcher = nameTextWatcher
        )

        //card number
        numberInput = setupInputWatcher(
            parentView = view,
            viewId = R.id.cardNumberInput,
            validation = FieldType.Number,
            textWatcher = numberTextWatcher
        )

        //expiry
        expiryInput = setupInputWatcher(
            parentView = view,
            viewId = R.id.cardExpiryDateInput,
            validation = FieldType.Expiry,
            textWatcher = expiryWatcher
        )

        //cvc
        cvcInput = setupInputWatcher(
            parentView = view,
            viewId = R.id.cardSecurityCodeInput,
            validation = FieldType.Cvc,
            textWatcher = cvcWatcher
        )

        //submit button
        val submitButton = view.findViewById<Button>(R.id.payButton)
        submitButton.setOnClickListener {
            viewModel.submit()
        }
        submitButton.text = "${getString(R.string.payBtnLabel)} ${viewModel.amountLabel}"

        //Loading indicator
        val loadingIndicator = view.findViewById<ProgressBar>(R.id.circularProgressIndicator)

        viewModel.uiStateFlow.observe(viewLifecycleOwner) { state ->
            submitButton.isEnabled = state.canPay
            handleInputErrors(state)
            loadingIndicator.isVisible = state.status is PaymentFragmentViewModel.Status.Loading
            Log.d("ASEQW","$state")
            if (state.status is PaymentFragmentViewModel.Status.PaymentAuth3dSecure) {
                childFragmentManager.beginTransaction().apply {
                    replace(
                        R.id.payment_fragment_container,
                        PaymentAuthFragment.newInstance(state.status.payment)
                    )
                    commit()
                }
            }
        }

        return view
    }

    private fun removeWatchers() {
        nameOnCardInput.removeTextWatcher(nameTextWatcher)
        numberInput.removeTextWatcher(nameTextWatcher)
        expiryInput.removeTextWatcher(nameTextWatcher)
        cvcInput.removeTextWatcher(nameTextWatcher)
    }

    private fun handleInputErrors(state: PaymentFragmentUiState) {
        nameOnCardInput?.error = state.nameOnCard.errorText
        numberInput?.error = state.cardNo.errorText
        expiryInput?.error = state.expiry.errorText
        cvcInput?.error = state.cvc.errorText
    }

    override fun onDestroy() {
        super.onDestroy()
        removeWatchers()
    }

    private fun setupInputWatcher(
        parentView: View,
        @IdRes viewId: Int,
        validation: FieldType,
        textWatcher: TextWatcher
    ): TextInputLayout {
        val til = parentView.findViewById<TextInputLayout?>(viewId).apply {
            addTextWatcher(textWatcher)
            editText?.setOnFocusChangeListener { _, hasFocus ->
                viewModel.validateField(validation, hasFocus)
            }
        }
        return til
    }

    private fun TextInputLayout?.addTextWatcher(textWatcher: TextWatcher) {
        this?.editText?.addTextChangedListener(textWatcher)
    }

    private fun TextInputLayout?.removeTextWatcher(textWatcher: TextWatcher) {
        this?.editText?.removeTextChangedListener(textWatcher)
    }

    private abstract class SimpleTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            //no-op
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            //no-op
        }

        override fun afterTextChanged(s: Editable?) {
            //no-op
        }
    }

    companion object {
        internal const val ARG_CONFIG = "arg-config"
        fun newInstance(
            config: PaymentConfig
        ): PaymentFragment {
            val configError = config.validate()
            if (configError.any()) {
                throw InvalidConfigException(configError)
            }
            return PaymentFragment().apply {
                arguments = bundleOf(
                    ARG_CONFIG to GsonHolder.gson.toJson(config)
                )
            }
        }
    }
}
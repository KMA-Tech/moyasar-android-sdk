package com.moyasar.android.sdk.data

import android.app.Application
import android.os.Parcelable
import android.text.Editable
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.moyasar.android.sdk.PaymentConfig
import com.moyasar.android.sdk.PaymentResult
import com.moyasar.android.sdk.R
import com.moyasar.android.sdk.exceptions.ApiException
import com.moyasar.android.sdk.extensions.default
import com.moyasar.android.sdk.payment.PaymentService
import com.moyasar.android.sdk.payment.models.CardPaymentSource
import com.moyasar.android.sdk.payment.models.Payment
import com.moyasar.android.sdk.payment.models.PaymentRequest
import com.moyasar.android.sdk.payment.models.TokenRequest
import com.moyasar.android.sdk.ui.PaymentAuthFragment
import com.moyasar.android.sdk.ui.PaymentFragment
import com.moyasar.android.sdk.util.CreditCardNetwork
import com.moyasar.android.sdk.util.getNetwork
import com.moyasar.android.sdk.util.isValidLuhnNumber
import com.moyasar.android.sdk.util.parseExpiry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.text.DecimalFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.pow

class PaymentFragmentViewModel(
    private val application: Application, savedStateHandle: SavedStateHandle
) : BaseViewModel(application, savedStateHandle) {

    private val paymentConfig by lazy {
        GsonHolder.gson.fromJson(
            savedStateHandle.get<String>(PaymentFragment.ARG_CONFIG).orEmpty(),
            PaymentConfig::class.java
        )
    }

    private val _paymentService: PaymentService by lazy {
        PaymentService(paymentConfig.apiKey, paymentConfig.baseUrl)
    }

    private var textEventReceiveLocked = false

    private val _statusStateFlow = MutableStateFlow<Status>(Status.Reset)
    private val textEventsFlow = MutableStateFlow(TextEventsHolder())

    private val nameValidator = FieldValidator(value = {
        textEventsFlow.value.nameOnCard?.value
    }).apply {
        val latinRegex = Regex("^[a-zA-Z\\-\\s]+\$")
        val nameRegex = Regex("^[a-zA-Z\\-]+\\s+?([a-zA-Z\\-]+\\s?)+\$")

        addRule(application.getString(R.string.name_is_required)) { it.isNullOrBlank() }
        addRule(application.getString(R.string.only_english_alpha)) {
            !latinRegex.matches(
                it ?: ""
            )
        }
        addRule(application.getString(R.string.both_names_required)) {
            !nameRegex.matches(
                it ?: ""
            )
        }
    }

    private val numberValidator = FieldValidator(value = {
        textEventsFlow.value.cardNo?.value
    }).apply {
        addRule(application.getString(R.string.card_number_required)) { it.isNullOrBlank() }
        addRule(application.getString(R.string.invalid_card_number)) {
            !isValidLuhnNumber(
                it ?: ""
            )
        }
        addRule(application.getString(R.string.unsupported_network)) {
            getNetwork(
                it ?: ""
            ) == CreditCardNetwork.Unknown
        }
    }

    private val cvcValidator = FieldValidator(value = {
        textEventsFlow.value.cvc?.value
    }).apply {
        addRule(application.getString(R.string.cvc_required)) { it.isNullOrBlank() }
        addRule(application.getString(R.string.invalid_cvc)) {
            when (getNetwork(uiStateFlow.value?.cardNo?.value ?: "")) {
                CreditCardNetwork.Amex -> (it?.length ?: 0) < 4
                else -> (it?.length ?: 0) < 3
            }
        }
    }

    private val expiryValidator = FieldValidator(value = {
        textEventsFlow.value.expiry?.value
    }).apply {
        addRule(application.getString(R.string.expiry_is_required)) { it.isNullOrBlank() }
        addRule(application.getString(R.string.invalid_expiry)) {
            parseExpiry(it ?: "")?.isInvalid() ?: true
        }
        addRule(application.getString(R.string.expired_card)) {
            parseExpiry(it ?: "")?.expired() ?: false
        }
    }

    private val nameOnCard get() = uiStateFlow.value?.nameOnCard?.value.orEmpty()
    private val cleanCardNumber: String
        get() = uiStateFlow.value?.cardNo?.value.orEmpty().replace(" ", "")

    private val expiryMonth: String
        get() = parseExpiry(uiStateFlow.value?.expiry?.value.orEmpty())?.month.toString()

    private val expiryYear: String
        get() = parseExpiry(uiStateFlow.value?.expiry?.value.orEmpty())?.year.toString()

    private val cvc get() = uiStateFlow.value?.cvc?.value.orEmpty()

    // Done logic like this to replicate iOS SDK's behavior
    val amountLabel: String
        get() {
            val currentLocale = Locale.getDefault()
            val paymentCurrency = Currency.getInstance(paymentConfig.currency)

            val numberFormatter = DecimalFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = paymentCurrency.defaultFractionDigits
                isGroupingUsed = true
            }

            val currencyFormatter = DecimalFormat.getCurrencyInstance(currentLocale).apply {
                currency = paymentCurrency
            }

            val amount =
                paymentConfig.amount / (10.0.pow(currencyFormatter.currency!!.defaultFractionDigits.toDouble()))
            val formattedNumber = numberFormatter.format(amount)
            val currencySymbol = currencyFormatter.currency!!.symbol

            return if (currentLocale.language == "ar") {
                "$formattedNumber $currencySymbol"
            } else {
                "$currencySymbol $formattedNumber"
            }
        }

    private var submitJob: Job? = null

    val uiStateFlow = combine(textEventsFlow, _statusStateFlow) { holder, status ->
        PaymentFragmentUiState(
            nameOnCard = PaymentFormFieldUiStateState(
                value = holder.nameOnCard?.value.toString(), errorText = holder.nameOnCard?.error
            ), cardNo = PaymentFormFieldUiStateState(
                value = holder.cardNo?.value.toString(), errorText = holder.cardNo?.error
            ), expiry = PaymentFormFieldUiStateState(
                value = holder.expiry?.value.toString(), errorText = holder.expiry?.error
            ), cvc = PaymentFormFieldUiStateState(
                value = holder.cvc?.value.toString(), errorText = holder.cvc?.error
            ), canPay = !holder.hasErrors,
            status = status
        )
    }.asLiveData(Dispatchers.IO)


    private fun notifyPaymentResult(paymentResult: PaymentResult) {
        throw NotImplementedError("NOT IMPLEMENTED notifyPaymentResult")
    }

    private suspend fun createPayment(): Status {
        Log.d("ASQEW","nameOnCard $nameOnCard, cleanCardNumber $cleanCardNumber")
        val request = PaymentRequest(
            paymentConfig.amount,
            paymentConfig.currency,
            paymentConfig.description,
            PaymentAuthFragment.RETURN_URL,
            CardPaymentSource(
                nameOnCard,
                cleanCardNumber,
                expiryMonth,
                expiryYear,
                cvc,
                if (paymentConfig.manual) "true" else "false",
                if (paymentConfig.saveCard) "true" else "false",
            ),
            paymentConfig.metadata ?: HashMap()
        )

        val result = try {
            val response = _paymentService.create(request)
            RequestResult.Success(response)
        } catch (e: ApiException) {
            e.printStackTrace()
            RequestResult.Failure(e)
        } catch (e: Exception) {
            RequestResult.Failure(e)
        }

        return when (result) {
            is RequestResult.Success -> {
                when (result.payment.status.lowercase()) {
                    "initiated" -> {
                        Status.PaymentAuth3dSecure(result.payment)
                    }

                    else -> {
                        Status.Completed(result.payment)
                    }
                }
            }

            is RequestResult.Failure -> {
                Log.d("ASEQW", "${result.e}")
                Status.Error(result.e)
            }
        }
    }


    @Suppress("unused")
    private fun createSaveOnlyToken() {
        val request = TokenRequest(
            nameOnCard,
            cleanCardNumber,
            cvc,
            expiryMonth,
            expiryYear,
            true,
            "https://sdk.moyasar.com"
        )

        CoroutineScope(Job() + Dispatchers.Main).launch {
            notifyPaymentResult(
                try {
                    PaymentResult.CompletedToken(_paymentService.createToken(request))
                } catch (e: ApiException) {
                    PaymentResult.Failed(e)
                } catch (e: Exception) {
                    PaymentResult.Failed(e)
                }
            )
        }
    }

    fun validateField(fieldType: FieldType, hasFocus: Boolean) {
        when (fieldType) {
            FieldType.Name -> nameValidator.onFieldFocusChange(hasFocus)
            FieldType.Number -> numberValidator.onFieldFocusChange(hasFocus)
            FieldType.Cvc -> cvcValidator.onFieldFocusChange(hasFocus)
            FieldType.Expiry -> expiryValidator.onFieldFocusChange(hasFocus)
        }
    }

    fun submit() {
        val isJobActive = submitJob?.isActive ?: false
        if (isJobActive) return
        submitJob = viewModelScope.launch(Dispatchers.IO) {
            _statusStateFlow.value = Status.Loading
            _statusStateFlow.value = createPayment()
        }
    }

    fun onTextEvent(editable: Editable?, fieldType: FieldType, validate: Boolean = true) {
        if (textEventReceiveLocked) {
            return
        }
        textEventReceiveLocked = fieldType == FieldType.Number || fieldType == FieldType.Expiry
        val previousValue = textEventsFlow.value
        textEventsFlow.value = when (fieldType) {
            FieldType.Name -> previousValue.copy(
                nameOnCard = TextFieldUiState(
                    value = editable.toString(),
                    error = if (validate) nameValidator.validate(editable.toString()) else null
                )
            )

            FieldType.Number -> previousValue.copy(
                cardNo = TextFieldUiState(
                    value = editable?.formatCardNo().toString(),
                    error = if (validate) numberValidator.validate(editable.toString()) else null
                )
            )

            FieldType.Expiry -> previousValue.copy(
                expiry = TextFieldUiState(
                    value = editable?.formatExpiry().toString(),
                    error = expiryValidator.validate(editable.toString())
                )
            )

            FieldType.Cvc -> previousValue.copy(
                cvc = TextFieldUiState(
                    value = editable.toString(), error = cvcValidator.validate(editable.toString())
                )
            )
        }
        textEventReceiveLocked = false
    }

    private fun Editable.formatCardNo(): Editable {
        val input = this.toString().replace(" ", "")
        val formatted = StringBuilder()

        for ((current, char) in input.toCharArray().withIndex()) {
            if (current > 15) {
                break
            }

            if (current > 0 && current % 4 == 0) {
                formatted.append(' ')
            }

            formatted.append(char)
        }

        this.replace(0, this.length, formatted.toString())

        return this
    }

    private fun Editable.formatExpiry(): Editable {
        val input = this.toString().replace(" ", "").replace("/", "")
        val formatted = StringBuilder()

        for ((current, char) in input.toCharArray().withIndex()) {
            if (current > 5) {
                break
            }

            if (current == 2) {
                formatted.append(" / ")
            }

            formatted.append(char)
        }

        this.replace(0, this.length, formatted.toString())

        return this
    }

    sealed class Status : Parcelable {
        @Parcelize
        data object Reset : Status()

        @Parcelize
        data object Loading : Status()

        @Parcelize
        data class PaymentAuth3dSecure(val payment: Payment) : Status()

        @Parcelize
        data class Completed(val payment: Payment) : Status()

        @Parcelize
        data class Error(val e: Exception) : Status()
    }

    internal sealed class RequestResult {
        data class Success(val payment: Payment) : RequestResult()
        data class Failure(val e: Exception) : RequestResult()
    }
}

data class TextEventsHolder(
    val nameOnCard: TextFieldUiState? = null,
    val cardNo: TextFieldUiState? = null,
    val expiry: TextFieldUiState? = null,
    val cvc: TextFieldUiState? = null
) {
    val hasErrors get() = nameOnCard.isErrorOrEmpty || cardNo.isErrorOrEmpty || expiry.isErrorOrEmpty || cvc.isErrorOrEmpty
}

data class TextFieldUiState(
    val value: String? = null, val error: String? = null
)

val TextFieldUiState?.isErrorOrEmpty get() = this?.value.isNullOrEmpty() || this?.error != null


enum class FieldType {
    Name, Number, Expiry, Cvc
}

data class PaymentFragmentUiState(
    val nameOnCard: PaymentFormFieldUiStateState = PaymentFormFieldUiStateState(),
    val cardNo: PaymentFormFieldUiStateState = PaymentFormFieldUiStateState(),
    val expiry: PaymentFormFieldUiStateState = PaymentFormFieldUiStateState(),
    val cvc: PaymentFormFieldUiStateState = PaymentFormFieldUiStateState(),
    val payPrice: String = "",
    val canPay: Boolean = false,
    val status: PaymentFragmentViewModel.Status = PaymentFragmentViewModel.Status.Reset
)

data class PaymentFormFieldUiStateState(
    val value: String = "", val errorText: String? = null
)
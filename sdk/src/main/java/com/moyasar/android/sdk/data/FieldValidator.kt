package com.moyasar.android.sdk.data

import androidx.lifecycle.MutableLiveData

typealias Predicate = (value: String?) -> Boolean

class FieldValidator(private val value: () -> String?) {
    private val rules = mutableListOf<ValidationRule>()

    val error = MutableLiveData<String?>()

    fun isValid(): Boolean {
        return validate(value.invoke()) == null
    }

    fun validate(value: String?): String? {
        for (rule in rules) {
            if (rule.predicate(value)) {
                error.value = rule.error
                return rule.error
            }
        }

        error.value = null
        return error.value
    }

    fun isValidWithoutErrorMessage(): Boolean {
        for (rule in rules) {
            if (rule.predicate(value.invoke())) {
                return false
            }
        }

        return true
    }

    fun addRule(message: String, predicate: Predicate) {
        rules.add(ValidationRule(predicate, message))
    }

    fun onFieldFocusChange(hasFocus: Boolean) {
        when (hasFocus) {
            true -> error.value = null
            false -> isValid()
        }
    }

    data class ValidationRule(val predicate: Predicate, val error: String)
}

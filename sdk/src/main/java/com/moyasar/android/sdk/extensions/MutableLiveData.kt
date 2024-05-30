package com.moyasar.android.sdk.extensions

import androidx.lifecycle.MutableLiveData


fun <T : Any?> MutableLiveData<T>.default(initialValue: T) = apply { setValue(initialValue) }
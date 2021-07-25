package com.moyasar.android.sdk

import java.lang.IllegalArgumentException

class InvalidConfigException(
    private val errors: Array<String>
) : IllegalArgumentException() {

}

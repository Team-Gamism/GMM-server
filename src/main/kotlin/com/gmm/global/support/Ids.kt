package com.gmm.global.support

import java.util.UUID

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}

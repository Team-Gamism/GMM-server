package com.gmm.global.exception

class GmmException(val code: ErrorCode, override val message: String = code.message) : RuntimeException(message)

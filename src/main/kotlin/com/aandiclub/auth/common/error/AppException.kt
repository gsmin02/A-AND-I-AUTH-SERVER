package com.aandiclub.auth.common.error

class AppException(
	val errorCode: ErrorCode,
	override val message: String = errorCode.defaultMessage,
	val value: String = errorCode.name,
	val alert: String = message,
) : RuntimeException(message)

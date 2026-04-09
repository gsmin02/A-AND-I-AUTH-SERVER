package com.aandiclub.auth.common.api.v2

import java.time.Instant

data class V2ApiResponse<T>(
	val success: Boolean,
	val data: T? = null,
	val error: V2ApiError? = null,
	val timestamp: Instant = Instant.now(),
) {
	companion object {
		fun <T> success(data: T): V2ApiResponse<T> = V2ApiResponse(success = true, data = data)

		fun failure(error: V2ApiError): V2ApiResponse<Nothing> =
			V2ApiResponse(success = false, error = error)
	}
}

data class V2ApiError(
	val code: Int,
	val message: String,
	val value: String,
	val alert: String,
)

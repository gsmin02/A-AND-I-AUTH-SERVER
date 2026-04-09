package com.aandiclub.auth.common.web.v2

import com.aandiclub.auth.common.error.v2.V2ErrorFactory
import com.aandiclub.auth.common.error.v2.V2ErrorResponseWriter
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Instant

@Component
class V2HeaderValidationWebFilter(
	private val errorFactory: V2ErrorFactory,
	private val responseWriter: V2ErrorResponseWriter,
) : WebFilter {

	override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
		val path = exchange.request.path.value()
		if (!V2ApiPaths.isV2(path)) {
			return chain.filter(exchange)
		}

		val deviceOs = exchange.request.headers.getFirst(DEVICE_OS_HEADER)?.trim()
		if (deviceOs.isNullOrBlank()) {
			return responseWriter.write(
				response = exchange.response,
				status = HttpStatus.BAD_REQUEST,
				error = errorFactory.validation(
					path = path,
					message = "deviceOS header is required.",
					value = "MISSING_DEVICE_OS_HEADER",
					detail = 101,
				),
			)
		}

		val timestamp = exchange.request.headers.getFirst(TIMESTAMP_HEADER)?.trim()
		if (timestamp.isNullOrBlank()) {
			return responseWriter.write(
				response = exchange.response,
				status = HttpStatus.BAD_REQUEST,
				error = errorFactory.validation(
					path = path,
					message = "timestamp header is required.",
					value = "MISSING_TIMESTAMP_HEADER",
					detail = 102,
				),
			)
		}

		if (parseTimestamp(timestamp) == null) {
			return responseWriter.write(
				response = exchange.response,
				status = HttpStatus.BAD_REQUEST,
				error = errorFactory.validation(
					path = path,
					message = "timestamp header must be epoch milliseconds or ISO-8601.",
					value = "INVALID_TIMESTAMP_HEADER",
					detail = 103,
				),
			)
		}

		return chain.filter(exchange)
	}

	private fun parseTimestamp(raw: String): Instant? =
		runCatching { Instant.ofEpochMilli(raw.toLong()) }.getOrNull()
			?: runCatching { Instant.parse(raw) }.getOrNull()

	companion object {
		const val DEVICE_OS_HEADER = "deviceOS"
		const val AUTHENTICATE_HEADER = "Authenticate"
		const val TIMESTAMP_HEADER = "timestamp"
		const val SALT_HEADER = "salt"
	}
}

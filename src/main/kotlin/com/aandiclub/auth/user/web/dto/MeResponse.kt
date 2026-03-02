package com.aandiclub.auth.user.web.dto

import com.aandiclub.auth.user.domain.UserRole
import com.aandiclub.auth.user.domain.UserTrack
import java.util.UUID

data class MeResponse(
	val id: UUID,
	val username: String,
	val role: UserRole,
	val userTrack: UserTrack,
	val publicCode: String,
	val nickname: String? = null,
	val profileImageUrl: String? = null,
)

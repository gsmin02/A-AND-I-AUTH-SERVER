package com.aandiclub.auth.user.web.dto.v2

import com.aandiclub.auth.user.domain.UserRole
import com.aandiclub.auth.user.domain.UserTrack
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class V2MeResponse(
	val id: UUID,
	val username: String,
	val role: UserRole,
	val userTrack: UserTrack,
	val publicCode: String,
	val nickname: String? = null,
	val profileImageUrl: String? = null,
)

data class V2UpdateProfileRequest(
	@field:Size(max = 40, message = "nickname length must be less than or equal to 40")
	@field:Pattern(
		regexp = "^[\\p{L}\\p{N} _.-]{1,40}$",
		message = "nickname allows only letters, numbers, spaces, underscores, hyphens, and dots.",
	)
	val nickname: String? = null,
	@field:Size(max = 2048, message = "profileImageUrl length must be less than or equal to 2048")
	val profileImageUrl: String? = null,
)

data class V2CreateProfileImageUploadUrlRequest(
	@field:NotBlank(message = "contentType is required")
	@field:Size(max = 100, message = "contentType length must be less than or equal to 100")
	val contentType: String,
	@field:Size(max = 255, message = "fileName length must be less than or equal to 255")
	val fileName: String? = null,
)

data class V2CreateProfileImageUploadUrlResponse(
	val uploadUrl: String,
	val profileImageUrl: String,
	val objectKey: String,
	val expiresInSeconds: Long,
)

data class V2ChangePasswordRequest(
	@field:NotBlank(message = "currentPassword is required")
	val currentPassword: String,
	@field:NotBlank(message = "newPassword is required")
	@field:Size(min = 12, max = 128, message = "newPassword length must be between 12 and 128")
	val newPassword: String,
)

data class V2ChangePasswordResponse(
	val changed: Boolean,
)

data class V2UserLookupResponse(
	val id: UUID,
	val username: String,
	val role: UserRole,
	val publicCode: String,
	val nickname: String? = null,
	val profileImageUrl: String? = null,
)

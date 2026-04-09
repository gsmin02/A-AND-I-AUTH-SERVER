package com.aandiclub.auth.admin.web.dto.v2

import com.aandiclub.auth.admin.web.dto.ProvisionType
import com.aandiclub.auth.user.domain.UserRole
import com.aandiclub.auth.user.domain.UserTrack
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class V2AdminPingResponse(
	val ok: Boolean,
)

data class V2AdminUserSummary(
	val id: UUID,
	val username: String,
	val role: UserRole,
	val userTrack: UserTrack,
	val cohort: Int,
	val cohortOrder: Int,
	val publicCode: String,
	val isActive: Boolean,
	val forcePasswordChange: Boolean,
	val nickname: String? = null,
	val inviteLink: String? = null,
	val inviteExpiresAt: Instant? = null,
)

data class V2CreateAdminUserRequest(
	@field:Min(0, message = "cohort must be between 0 and 9")
	@field:Max(9, message = "cohort must be between 0 and 9")
	val cohort: Int,
	val role: UserRole = UserRole.USER,
	val provisionType: ProvisionType = ProvisionType.INVITE,
)

data class V2CreateAdminUserResponse(
	val id: UUID,
	val username: String,
	val role: UserRole,
	val userTrack: UserTrack,
	val cohort: Int,
	val cohortOrder: Int,
	val publicCode: String,
	val provisionType: ProvisionType,
	val inviteLink: String? = null,
	val expiresAt: Instant? = null,
	val temporaryPassword: String? = null,
)

data class V2InviteMailRequest(
	@field:Valid
	@field:NotEmpty(message = "emails must not be empty")
	@field:Size(max = 100, message = "emails size must be less than or equal to 100")
	val emails: List<@Email(message = "emails must contain valid email addresses") String>,
	val role: UserRole = UserRole.USER,
	@field:Min(value = 0, message = "cohort must be between 0 and 9")
	@field:Max(value = 9, message = "cohort must be between 0 and 9")
	val cohort: Int? = null,
	@field:Min(value = 0, message = "cohortOrder must be greater than or equal to 0")
	val cohortOrder: Int? = null,
	val userTrack: String? = null,
)

data class V2InviteMailTarget(
	val email: String,
	val username: String,
	val role: UserRole,
	val inviteExpiresAt: Instant,
	val cohort: Int = 0,
	val cohortOrder: Int = 0,
	val userTrack: String = "NO",
	val publicCode: String? = null,
)

data class V2InviteMailResponse(
	val sentCount: Int,
	val invites: List<V2InviteMailTarget>,
	val username: String? = null,
	val role: UserRole? = null,
	val inviteExpiresAt: Instant? = null,
	val cohort: Int? = null,
	val cohortOrder: Int? = null,
	val userTrack: String? = null,
	val publicCode: String? = null,
)

data class V2ResetPasswordResponse(
	val temporaryPassword: String,
)

data class V2UpdateUserRoleRequest(
	val role: UserRole,
)

data class V2UpdateUserRoleResponse(
	val id: UUID,
	val username: String,
	val role: UserRole,
	val userTrack: UserTrack,
	val cohort: Int,
	val cohortOrder: Int,
	val publicCode: String,
)

data class V2UpdateUserRequest(
	val role: UserRole? = null,
	val userTrack: UserTrack? = null,
	@field:Min(0, message = "cohort must be between 0 and 9")
	@field:Max(9, message = "cohort must be between 0 and 9")
	val cohort: Int? = null,
	val nickname: String? = null,
)

data class V2UpdateUserResponse(
	val id: UUID,
	val username: String,
	val role: UserRole,
	val userTrack: UserTrack,
	val cohort: Int,
	val cohortOrder: Int,
	val publicCode: String,
	val nickname: String? = null,
)

data class V2DeleteUserResponse(
	val userId: UUID,
	val deleted: Boolean,
)

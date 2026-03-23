package com.aandiclub.auth.auth.service

import com.aandiclub.auth.auth.service.impl.AuthServiceImpl
import com.aandiclub.auth.admin.domain.UserInviteEntity
import com.aandiclub.auth.admin.invite.InviteTokenCacheService
import com.aandiclub.auth.admin.repository.UserInviteRepository
import com.aandiclub.auth.auth.web.dto.ActivateRequest
import com.aandiclub.auth.auth.web.dto.LoginRequest
import com.aandiclub.auth.auth.web.dto.LogoutRequest
import com.aandiclub.auth.auth.web.dto.RefreshRequest
import com.aandiclub.auth.common.error.AppException
import com.aandiclub.auth.common.error.ErrorCode
import com.aandiclub.auth.security.jwt.JwtPrincipal
import com.aandiclub.auth.security.jwt.JwtToken
import com.aandiclub.auth.security.jwt.JwtTokenType
import com.aandiclub.auth.security.service.JwtService
import com.aandiclub.auth.security.service.PasswordService
import com.aandiclub.auth.security.token.TokenHashService
import com.aandiclub.auth.security.token.RefreshTokenStateService
import com.aandiclub.auth.user.domain.UserEntity
import com.aandiclub.auth.user.domain.UserRole
import com.aandiclub.auth.user.repository.UserRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.dao.DataIntegrityViolationException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AuthServiceImplTest : FunSpec({
	val userRepository = mockk<UserRepository>()
	val userInviteRepository = mockk<UserInviteRepository>()
	val inviteTokenCacheService = mockk<InviteTokenCacheService>()
	val passwordService = mockk<PasswordService>()
	val jwtService = mockk<JwtService>()
	val tokenHashService = mockk<TokenHashService>()
	val refreshTokenStateService = mockk<RefreshTokenStateService>()
	val clock = Clock.fixed(Instant.parse("2026-02-18T00:00:00Z"), ZoneOffset.UTC)

	val authService = AuthServiceImpl(
		userRepository = userRepository,
		userInviteRepository = userInviteRepository,
		inviteTokenCacheService = inviteTokenCacheService,
		passwordService = passwordService,
		jwtService = jwtService,
		tokenHashService = tokenHashService,
		refreshTokenStateService = refreshTokenStateService,
		clock = clock,
	)

	test("login should return access and refresh tokens") {
		val userId = UUID.randomUUID()
		val user = UserEntity(
			id = userId,
			username = "user_01",
			passwordHash = "hashed",
			role = UserRole.USER,
		)
		every { userRepository.findByUsername("user_01") } returns Mono.just(user)
		every { passwordService.matches("password", "hashed") } returns true
		every { userRepository.save(any()) } answers { Mono.just(firstArg()) }
		every { jwtService.issueAccessToken(userId, "user_01", UserRole.USER) } returns JwtToken(
			value = "access-token",
			expiresAt = Instant.parse("2026-02-18T01:00:00Z"),
			tokenType = JwtTokenType.ACCESS,
		)
		every { jwtService.issueRefreshToken(userId, "user_01", UserRole.USER) } returns JwtToken(
			value = "refresh-token",
			expiresAt = Instant.parse("2026-04-19T00:00:00Z"),
			tokenType = JwtTokenType.REFRESH,
		)

		StepVerifier.create(authService.login(LoginRequest("user_01", "password")))
			.assertNext { response ->
				response.accessToken shouldBe "access-token"
				response.refreshToken shouldBe "refresh-token"
				response.tokenType shouldBe "Bearer"
				response.user.id shouldBe userId
				response.forcePasswordChange shouldBe false
			}
			.verifyComplete()
	}

	test("login should reject invalid password") {
		val user = UserEntity(
			id = UUID.randomUUID(),
			username = "user_01",
			passwordHash = "hashed",
			role = UserRole.USER,
		)
		every { userRepository.findByUsername("user_01") } returns Mono.just(user)
		every { passwordService.matches("wrong", "hashed") } returns false

		StepVerifier.create(authService.login(LoginRequest("user_01", "wrong")))
			.expectErrorSatisfies { ex ->
				(ex as AppException).errorCode shouldBe ErrorCode.UNAUTHORIZED
				ex.message shouldBe "Invalid username or password."
			}
			.verify()
	}

	test("login should reject inactive account") {
		val user = UserEntity(
			id = UUID.randomUUID(),
			username = "user_01",
			passwordHash = "hashed",
			role = UserRole.USER,
			isActive = false,
		)
		every { userRepository.findByUsername("user_01") } returns Mono.just(user)

		StepVerifier.create(authService.login(LoginRequest("user_01", "password")))
			.expectErrorSatisfies { ex ->
				(ex as AppException).errorCode shouldBe ErrorCode.UNAUTHORIZED
				ex.message shouldBe "Invalid username or password."
			}
			.verify()
	}

	test("refresh should reject logged-out token") {
		val refreshToken = "refresh-token"
		every { jwtService.verifyAndParse(refreshToken, JwtTokenType.REFRESH) } returns JwtPrincipal(
			userId = UUID.randomUUID(),
			username = "user_02",
			role = UserRole.USER,
			tokenType = JwtTokenType.REFRESH,
			issuedAt = Instant.parse("2026-02-18T00:00:00Z"),
			expiresAt = Instant.parse("2026-04-18T00:00:00Z"),
			jti = "jti-1",
		)
		every { refreshTokenStateService.rejectIfLoggedOut(refreshToken) } returns
			Mono.error(AppException(ErrorCode.UNAUTHORIZED, "Refresh token is logged out."))

		StepVerifier.create(authService.refresh(RefreshRequest(refreshToken)))
			.expectErrorSatisfies { ex ->
				(ex as AppException).errorCode shouldBe ErrorCode.UNAUTHORIZED
			}
			.verify()
	}

	test("logout should mark refresh token as logged-out") {
		val refreshToken = "refresh-token"
		val expiresAt = Instant.parse("2026-04-18T00:00:00Z")
		every { jwtService.verifyAndParse(refreshToken, JwtTokenType.REFRESH) } returns JwtPrincipal(
			userId = UUID.randomUUID(),
			username = "user_02",
			role = UserRole.USER,
			tokenType = JwtTokenType.REFRESH,
			issuedAt = Instant.parse("2026-02-18T00:00:00Z"),
			expiresAt = expiresAt,
			jti = "jti-2",
		)
		every { refreshTokenStateService.markLoggedOut(refreshToken, expiresAt) } returns Mono.just(true)

		StepVerifier.create(authService.logout(LogoutRequest(refreshToken)))
			.assertNext { response ->
				response.success shouldBe true
			}
			.verifyComplete()
	}

	test("refresh with invalid format should throw unauthorized") {
		every { jwtService.verifyAndParse("bad-token", JwtTokenType.REFRESH) } throws
			AppException(ErrorCode.UNAUTHORIZED, "Invalid token format.")

		StepVerifier.create(authService.refresh(RefreshRequest("bad-token")))
			.expectErrorSatisfies { ex ->
				(ex as AppException).errorCode shouldBe ErrorCode.UNAUTHORIZED
				ex.message shouldBe "Invalid token format."
			}
			.verify()
	}

	test("activate should set user active and consume invite token") {
		val userId = UUID.randomUUID()
		val inviteId = UUID.randomUUID()
		val invite = UserInviteEntity(
			id = inviteId,
			userId = userId,
			tokenHash = "invite-hash",
			expiresAt = Instant.parse("2026-02-20T00:00:00Z"),
			usedAt = null,
			createdAt = Instant.parse("2026-02-18T00:00:00Z"),
		)
		val user = UserEntity(
			id = userId,
			username = "user_09",
			passwordHash = "placeholder",
			role = UserRole.USER,
			forcePasswordChange = true,
			isActive = false,
		)

		every { tokenHashService.sha256Hex("invite-token") } returns "invite-hash"
		every { userInviteRepository.findByTokenHash("invite-hash") } returns Mono.just(invite)
		every { userRepository.findById(userId) } returns Mono.just(user)
		every { passwordService.hash("new-password-123") } returns "new-hash"
		every { userRepository.save(any()) } answers { Mono.just(firstArg()) }
		every { userInviteRepository.save(any()) } answers { Mono.just(firstArg()) }
		every { inviteTokenCacheService.deleteToken("invite-hash") } returns Mono.just(true)

		StepVerifier.create(authService.activate(ActivateRequest("invite-token", "new-password-123")))
			.assertNext { response ->
				response.success shouldBe true
			}
			.verifyComplete()
	}

	test("activate should allow username change when requested username is available") {
		val userId = UUID.randomUUID()
		val invite = UserInviteEntity(
			id = UUID.randomUUID(),
			userId = userId,
			tokenHash = "invite-hash",
			expiresAt = Instant.parse("2026-02-20T00:00:00Z"),
			usedAt = null,
			createdAt = Instant.parse("2026-02-18T00:00:00Z"),
		)
		val user = UserEntity(
			id = userId,
			username = "user_09",
			passwordHash = "placeholder",
			role = UserRole.USER,
			forcePasswordChange = true,
			isActive = false,
		)
		val savedUserSlot = slot<UserEntity>()

		every { tokenHashService.sha256Hex("invite-token") } returns "invite-hash"
		every { userInviteRepository.findByTokenHash("invite-hash") } returns Mono.just(invite)
		every { userRepository.findById(userId) } returns Mono.just(user)
		every { userRepository.findByUsername("member_09") } returns Mono.empty()
		every { passwordService.hash("new-password-123") } returns "new-hash"
		every { userRepository.save(capture(savedUserSlot)) } answers { Mono.just(firstArg()) }
		every { userInviteRepository.save(any()) } answers { Mono.just(firstArg()) }
		every { inviteTokenCacheService.deleteToken("invite-hash") } returns Mono.just(true)

		StepVerifier.create(authService.activate(ActivateRequest("invite-token", "new-password-123", "member_09")))
			.assertNext { response ->
				response.success shouldBe true
			}
			.verifyComplete()

		savedUserSlot.captured.username shouldBe "member_09"
		savedUserSlot.captured.isActive shouldBe true
		savedUserSlot.captured.forcePasswordChange shouldBe false
	}

	test("activate should preserve requested username casing") {
		val userId = UUID.randomUUID()
		val invite = UserInviteEntity(
			id = UUID.randomUUID(),
			userId = userId,
			tokenHash = "invite-hash",
			expiresAt = Instant.parse("2026-02-20T00:00:00Z"),
			usedAt = null,
			createdAt = Instant.parse("2026-02-18T00:00:00Z"),
		)
		val user = UserEntity(
			id = userId,
			username = "user_09",
			passwordHash = "placeholder",
			role = UserRole.USER,
			forcePasswordChange = true,
			isActive = false,
		)
		val savedUserSlot = slot<UserEntity>()

		every { tokenHashService.sha256Hex("invite-token") } returns "invite-hash"
		every { userInviteRepository.findByTokenHash("invite-hash") } returns Mono.just(invite)
		every { userRepository.findById(userId) } returns Mono.just(user)
		every { userRepository.findByUsername("Member_09") } returns Mono.empty()
		every { passwordService.hash("new-password-123") } returns "new-hash"
		every { userRepository.save(capture(savedUserSlot)) } answers { Mono.just(firstArg()) }
		every { userInviteRepository.save(any()) } answers { Mono.just(firstArg()) }
		every { inviteTokenCacheService.deleteToken("invite-hash") } returns Mono.just(true)

		StepVerifier.create(authService.activate(ActivateRequest("invite-token", "new-password-123", "Member_09")))
			.assertNext { response ->
				response.success shouldBe true
			}
			.verifyComplete()

		savedUserSlot.captured.username shouldBe "Member_09"
	}

	test("activate should reject username change when requested username is already used") {
		val userId = UUID.randomUUID()
		val invite = UserInviteEntity(
			id = UUID.randomUUID(),
			userId = userId,
			tokenHash = "invite-hash",
			expiresAt = Instant.parse("2026-02-20T00:00:00Z"),
			usedAt = null,
			createdAt = Instant.parse("2026-02-18T00:00:00Z"),
		)
		val user = UserEntity(
			id = userId,
			username = "user_09",
			passwordHash = "placeholder",
			role = UserRole.USER,
			forcePasswordChange = true,
			isActive = false,
		)
		val existingUser = UserEntity(
			id = UUID.randomUUID(),
			username = "member_09",
			passwordHash = "placeholder",
			role = UserRole.USER,
			forcePasswordChange = false,
			isActive = true,
		)

		every { tokenHashService.sha256Hex("invite-token") } returns "invite-hash"
		every { userInviteRepository.findByTokenHash("invite-hash") } returns Mono.just(invite)
		every { userRepository.findById(userId) } returns Mono.just(user)
		every { userRepository.findByUsername("member_09") } returns Mono.just(existingUser)

		StepVerifier.create(authService.activate(ActivateRequest("invite-token", "new-password-123", "member_09")))
			.expectErrorSatisfies { ex ->
				(ex as AppException).errorCode shouldBe ErrorCode.INVALID_REQUEST
				ex.message shouldBe "Requested username is not available."
			}
			.verify()
	}

	test("activate should map username unique constraint conflicts to invalid request") {
		val userId = UUID.randomUUID()
		val invite = UserInviteEntity(
			id = UUID.randomUUID(),
			userId = userId,
			tokenHash = "invite-hash",
			expiresAt = Instant.parse("2026-02-20T00:00:00Z"),
			usedAt = null,
			createdAt = Instant.parse("2026-02-18T00:00:00Z"),
		)
		val user = UserEntity(
			id = userId,
			username = "user_09",
			passwordHash = "placeholder",
			role = UserRole.USER,
			forcePasswordChange = true,
			isActive = false,
		)

		every { tokenHashService.sha256Hex("invite-token") } returns "invite-hash"
		every { userInviteRepository.findByTokenHash("invite-hash") } returns Mono.just(invite)
		every { userRepository.findById(userId) } returns Mono.just(user)
		every { userRepository.findByUsername("member_09") } returns Mono.empty()
		every { passwordService.hash("new-password-123") } returns "new-hash"
		every { userRepository.save(any()) } returns Mono.error(DataIntegrityViolationException("duplicate username"))

		StepVerifier.create(authService.activate(ActivateRequest("invite-token", "new-password-123", "member_09")))
			.expectErrorSatisfies { ex ->
				(ex as AppException).errorCode shouldBe ErrorCode.INVALID_REQUEST
				ex.message shouldBe "Requested username is not available."
			}
			.verify()
	}

	test("activate should reject expired invite token") {
		val invite = UserInviteEntity(
			id = UUID.randomUUID(),
			userId = UUID.randomUUID(),
			tokenHash = "invite-hash",
			expiresAt = Instant.parse("2026-02-17T00:00:00Z"),
			usedAt = null,
			createdAt = Instant.parse("2026-02-16T00:00:00Z"),
		)

		every { tokenHashService.sha256Hex("invite-token") } returns "invite-hash"
		every { userInviteRepository.findByTokenHash("invite-hash") } returns Mono.just(invite)

		StepVerifier.create(authService.activate(ActivateRequest("invite-token", "new-password-123")))
			.expectErrorSatisfies { ex ->
				(ex as AppException).errorCode shouldBe ErrorCode.UNAUTHORIZED
				ex.message shouldBe "Invalid or expired invite token."
			}
			.verify()
	}
})

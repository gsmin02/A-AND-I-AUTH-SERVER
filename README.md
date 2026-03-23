# auth

`aandiclub.com` 핵심 인증 서비스입니다.

## Local Run (Docker Compose)
사전 준비:
```bash
cp .env.example .env
```

필수 확인(게이트웨이/인증 값 일치):
- `JWT_SECRET` == `AUTH_JWT_SECRET`
- `JWT_ISSUER` == `AUTH_ISSUER_URI`
- `JWT_AUDIENCE` == `AUTH_AUDIENCE`
- `DB_MIGRATION_ENABLED=true`
- `DB_JDBC_URL` 과 `DB_R2DBC_URL` 은 반드시 같은 DB를 가리켜야 함

```bash
docker compose up --build
```

기본 포트:
- Auth API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## Profile Image Upload (S3 Presigned URL)
- `POST /v1/me`를 `multipart/form-data`로 호출해 `nickname` + `profileImage`를 한 번에 처리
  - 서버가 이미지를 S3에 업로드한 뒤 사용자 프로필을 갱신
- (옵션) `POST /v1/me/profile-image/upload-url` presigned 업로드 방식도 지원
  - presigned 업로드 후 `PATCH /v1/me`에 `profileImageUrl`을 보내 사용자 프로필에 반영

필수 환경 변수:
- `APP_PROFILE_ALLOWED_IMAGE_HOSTS`: 저장 허용할 이미지 호스트(콤마 구분)
- `APP_PROFILE_IMAGE_ENABLED`: `true`로 설정 시 S3 업로드 URL 발급 활성화
- `APP_PROFILE_IMAGE_BUCKET`, `APP_PROFILE_IMAGE_REGION`
- `APP_PROFILE_IMAGE_KEY_PREFIX`(기본 `profiles`)
- `APP_PROFILE_IMAGE_PUBLIC_BASE_URL`(선택, CloudFront 사용 시)

## User CRUD Event
현재 구현은 유저 변경 이벤트를 `UserCreated` / `UserUpdated` / `UserDeleted`로 분리하지 않고, 변경 후 최신 사용자 스냅샷을 담는 `UserProfileUpdated` 이벤트 하나로 발행합니다.

CRUD별 발행 여부:
- Create: 발행됨 (`AdminServiceImpl.createUser`, 비밀번호 생성/초대 생성 모두 포함)
- Read: 발행되지 않음
- Update: 발행됨 (`UserServiceImpl.updateProfile`, `AdminServiceImpl.updateUser`, `AdminServiceImpl.updateUserRole`)
- Delete: 발행되지 않음 (`AdminServiceImpl.deleteUser`는 삭제 감사 로그만 남김)

SNS 발행 형식:
- Message body: 아래 JSON
- Message attribute: `eventType=UserProfileUpdated`
- FIFO Topic 사용 시:
  - `MessageGroupId=${APP_USER_PROFILE_EVENT_FIFO_MESSAGE_GROUP_ID:user-profile-updated}`
  - `MessageDeduplicationId=eventId`

메시지 바디 스키마:
```json
{
  "eventId": "uuid",
  "type": "UserProfileUpdated",
  "occurredAt": "2026-03-21T10:15:30Z",
  "userId": "uuid",
  "username": "user_01",
  "role": "USER",
  "userTrack": "NO",
  "cohort": 4,
  "cohortOrder": 1,
  "publicCode": "#NO401",
  "nickname": "andi",
  "profileImageUrl": "https://images.aandiclub.com/profiles/user_01.png",
  "version": 1
}
```

필드 설명:
- `eventId`: 이벤트 고유 ID (`UUID`)
- `type`: 현재 항상 `UserProfileUpdated`
- `occurredAt`: 사용자 레코드의 `updatedAt`
- `userId`: 사용자 ID
- `username`: 내부 username
- `role`: `ADMIN`, `ORGANIZER`, `USER`
- `userTrack`: `NO`, `FL`, `SP`
- `cohort`, `cohortOrder`, `publicCode`: 사용자 공개 식별 정보 계산에 사용되는 값
- `nickname`, `profileImageUrl`: 최신 프로필 값
- `version`: `users.profile_version`

CRUD 예시:

Create 시 예시:
```json
{
  "eventId": "9f4d1b9c-5dd4-4d05-93e4-53dc6740d9a3",
  "type": "UserProfileUpdated",
  "occurredAt": "2026-03-21T10:15:30Z",
  "userId": "a6d3eb0d-5174-4aa9-8f87-e1f91228a9c8",
  "username": "user_01",
  "role": "USER",
  "userTrack": "NO",
  "cohort": 4,
  "cohortOrder": 1,
  "publicCode": "#NO401",
  "nickname": null,
  "profileImageUrl": null,
  "version": 0
}
```

Update 시 예시:
```json
{
  "eventId": "3a3c6f82-4ec4-4f91-a57f-2b4d745c4b31",
  "type": "UserProfileUpdated",
  "occurredAt": "2026-03-21T10:18:44Z",
  "userId": "a6d3eb0d-5174-4aa9-8f87-e1f91228a9c8",
  "username": "user_01",
  "role": "USER",
  "userTrack": "NO",
  "cohort": 4,
  "cohortOrder": 1,
  "publicCode": "#NO401",
  "nickname": "andi",
  "profileImageUrl": "https://images.aandiclub.com/profiles/user_01.png",
  "version": 1
}
```

Delete 시 예시:
- 현재 삭제 이벤트는 발행하지 않습니다.
- 필요하면 `UserDeleted` 같은 별도 이벤트 계약을 추가해야 합니다.

## CI/CD Workflow
- CI (`.github/workflows/ci.yml`)
  - Trigger: `main` 브랜치 `push`, `pull_request`
  - Actions: `./gradlew test`, Docker build check, `docker-compose.yml` 유효성 검사

- CD (`.github/workflows/cd.yml`)
  - Trigger: 태그 `vX.Y.Z` 푸시 (예: `v1.2.3`)
  - Actions: OIDC로 AWS Role Assume -> ECR 이미지 푸시 -> SSH로 EC2 배포

## AWS CD Prerequisites
- GitHub Actions Repository Variables
  - `AWS_REGION`: 예) `ap-northeast-2`
  - `ECR_REPOSITORY`: 예) `aandiclub/auth`
  - `APP_DIR`: EC2 내 compose 배포 경로 (기본 `/opt/auth`)
  - `POSTGRES_DB`: 기본 `auth`
  - `POSTGRES_USER`: 기본 `auth`
  - `APP_CORS_ALLOWED_ORIGINS`: 예) `https://aandiclub.com,https://admin.aandiclub.com,https://auth.aandiclub.com,https://api.aandiclub.com`
  - `JWT_ISSUER`: 예) `https://auth.aandiclub.com`
  - `JWT_AUDIENCE`: 예) `aandiclub-api`
  - `AWS_PORT`: 기본 `22` (옵션)

- GitHub Actions Repository Secrets
  - `AWS_ROLE_TO_ASSUME`: OIDC 신뢰 정책이 설정된 IAM Role ARN
  - `AWS_HOST`: 배포 대상 EC2 호스트
  - `AWS_USER`: SSH 유저
  - `AWS_SSH_KEY`: SSH 개인키
  - `JWT_SECRET`: JWT 서명 키 (32 bytes 이상 강한 랜덤값)
  - `POSTGRES_PASSWORD`: PostgreSQL 비밀번호
  - `REDIS_PASSWORD`: Redis 비밀번호

- EC2 사전 조건
  - Docker + Docker Compose 설치
  - AWS CLI 설치 및 인스턴스 Role에 ECR Pull 권한 부여

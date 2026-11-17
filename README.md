# GMM-server
게이미즘 게임잼 프로젝트 관리 서비스 서버 저장소입니다.

## 역할

- DataGSM OAuth(PKCE) 토큰 교환과 세션 쿠키 발급
- 게임잼, 게임, 평가, 결과 API (`/api/v1`)
- Unity WebGL 빌드 zip 검증, 압축 해제, 저장 (서빙은 별도 도메인의 nginx)

## 스택

- Kotlin, Spring Boot 4.1, Java 27
- MySQL 8, Spring Data JPA, Flyway
- 테스트: Kotest, MockK

## 로컬 실행

```bash
docker compose up -d          # MySQL(3306) + 빌드 서빙 nginx(8081)
DATAGSM_CLIENT_ID=... GMM_SESSION_COOKIE_SECURE=false ./gradlew bootRun
```

| 포트 | 용도 |
| --- | --- |
| 8080 | API (`/api/v1`) |
| 8081 | 빌드 정적 서빙 (`/builds/{gameId}/{uploadId}/`) |

## 환경 변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `3306`, `gmm` | MySQL 접속 정보 |
| `DB_USERNAME`, `DB_PASSWORD` | `gmm`, `gmm` | |
| `DATAGSM_CLIENT_ID` | (없음) | DataGSM 클라이언트 ID. 없으면 로그인이 실패해요 |
| `DATAGSM_REDIRECT_URIS` | `http://localhost:5173/auth/callback` | 허용할 redirect URI 목록이에요(쉼표 구분) |
| `DATAGSM_ALLOW_TEACHER` | `false` | 교사 계정 로그인 허용 |
| `GMM_ADMIN_EMAILS` | (없음) | 로그인 시 어드민 권한을 줄 DataGSM 이메일(쉼표 구분) |
| `GMM_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | 프론트 출처. 같은 도메인 프록시면 비워요 |
| `GMM_SESSION_COOKIE_SECURE` | `true` | HTTP로 개발할 때만 `false` |
| `GMM_SESSION_TTL` | `14d` | 세션 유효 기간 |
| `GMM_BUILD_ROOT_DIR` | `./data` | `builds/`(공개)와 `staging/`(비공개)를 담는 폴더 |
| `GMM_BUILD_PUBLIC_BASE_URL` | `http://localhost:8081` | 빌드 서빙 도메인. `buildUrl`의 앞부분 |
| `GMM_BUILD_MAX_ZIP_SIZE` | `200MB` | 업로드 zip 상한. nginx `client_max_body_size`와 맞춰요 |
| `GMM_BUILD_MAX_EXTRACTED_SIZE` | `500MB` | 압축 해제 후 총량 상한 |
| `GMM_BUILD_MAX_FILE_COUNT` | `2000` | zip 안 파일 수 상한 |

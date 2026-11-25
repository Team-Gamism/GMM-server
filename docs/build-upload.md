# Unity WebGL 빌드 업로드와 서빙

참가자가 올린 Unity WebGL 빌드 zip을 서버가 받아서 검증하고, 압축을 풀어 저장한 뒤 별도 도메인에서 서빙하는 방식을 설명해요.

- DB에는 경로와 메타데이터만 저장해요.
- 파일은 디스크에 풀어 두고, Spring이 아니라 nginx가 서빙해요.

## 전체 흐름

```
POST /api/v1/games  (multipart: data=JSON, buildFile=zip)
  │
  ├─ 1. 권한·입력 검사 (zip을 읽기 전에 빠르게 실패)
  ├─ 2. zip을 staging/{uploadId}/upload.zip 에 저장
  ├─ 3. 압축을 풀기 전에 목록만 읽어 검증
  ├─ 4. staging/{uploadId}/build/ 에 압축 해제 (실제 바이트를 세면서)
  ├─ 5. builds/{gameId}/{uploadId}/ 로 원자적 이동 (공개)
  ├─ 6. DB 커밋 (buildPath, buildName, reviewStatus=pending)
  │     └─ 실패하면 5에서 옮긴 폴더를 지움
  ├─ 7. 커밋 후 이전 빌드 폴더 삭제 (빌드 교체일 때)
  └─ 8. staging/{uploadId}/ 삭제 (성공·실패 모두)

nginx (빌드 전용 도메인) ── builds/ 를 정적 서빙
  └─ 프론트: <iframe src="{buildUrl}/index.html" sandbox="...">
```

빌드 교체(`PUT /api/v1/games/{gameId}`에 `buildFile`을 넣은 요청)도 같은 흐름이에요. 참가자는 새 업로드처럼 게임잼이 진행 중일 때만 빌드를 바꿀 수 있어요. 그래서 심사받은 빌드가 마감 뒤에 바뀌지 않아요. 빌드 없이 게임 정보만 고치는 요청과 어드민의 요청은 언제든 돼요.

게임 정보 수정, 빌드 교체, 어드민 업로드 심사가 같은 게임에 동시에 들어오면 먼저 커밋한 요청만 반영되고, 나머지는 `409 CONFLICT`로 실패해요(`tb_game.version` 낙관적 잠금). 실패한 빌드 교체는 6단계처럼 새 폴더를 지우고, 이전 빌드는 지우지 않아요.

참가자는 게임잼 하나에 게임을 하나만 올릴 수 있어요(`400 GAME_LIMIT_REACHED`). 디스크를 지키려는 제한이고, 다시 올리고 싶으면 올린 게임을 수정해요. 어드민은 제한이 없어요. 동시에 보낸 두 업로드가 둘 다 통과하지 않도록, 1단계에서 미리 확인하고 6단계에서 게임잼 행을 잠근 뒤 다시 확인해요.

관련 코드는 아래 파일에 있어요.

| 역할 | 파일 |
| --- | --- |
| 순서 조정 (트랜잭션 밖) | `domain/game/service/GameUploadService.kt` |
| DB 저장, 이전 빌드 삭제 예약 | `domain/game/service/GameService.kt` |
| zip 검증과 압축 해제 | `domain/build/service/UnityBuildProcessor.kt` |
| 디스크 저장 | `domain/build/storage/LocalDiskBuildStorage.kt` |
| 남은 폴더 정리 | `domain/build/service/BuildCleanupJob.kt` |
| nginx 설정 | `deploy/nginx/builds.conf` |

## 검증 규칙

프론트 업로드 화면의 자동 검사(`src/lib/unityZip.ts`)와 사용자 가이드(`/guide`)와 같은 규칙이에요. 한쪽을 바꾸면 다른 쪽도 함께 바꿔 주세요.

| # | 규칙 | 실패 응답 |
| --- | --- | --- |
| 1 | 파일명이 `.zip`이고 zip으로 열려야 해요 | 400 `BUILD_INVALID` |
| 2 | 최상위에 `index.html`과 `Build/`가 있어야 해요. 폴더째 압축한 zip은 따로 안내해요 | 400 `BUILD_INVALID` |
| 3 | `Build/{name}.loader.js`, `.data`, `.framework.js`, `.wasm`이 같은 `{name}`으로 하나만 있어야 해요. 이 `{name}`이 `buildName`이에요 | 400 `BUILD_INVALID` |
| 4 | `Build/` 안에 `.gz`, `.br`, `.unityweb`(압축 빌드)가 있으면 거부해요 | 400 `BUILD_INVALID` |
| 5 | `__MACOSX/`와 `.`으로 시작하는 파일·폴더는 무시해요 | – |
| 6 | `..`, 절대경로, 드라이브 문자(`C:`)가 들어간 경로(zip slip)는 거부해요 | 400 `BUILD_INVALID` |
| 7 | 파일 수가 `GMM_BUILD_MAX_FILE_COUNT`를 넘으면 거부해요 | 400 `BUILD_INVALID` |
| 8 | 압축 해제 총량이 `GMM_BUILD_MAX_EXTRACTED_SIZE`를 넘으면 거부해요 | 413 `BUILD_TOO_LARGE` |
| 9 | zip 파일 자체가 `GMM_BUILD_MAX_ZIP_SIZE`를 넘으면 거부해요 | 413 `FILE_TOO_LARGE` |

참고할 점이 세 가지 있어요.

- **zip 폭탄 대비:** zip 헤더에 적힌 압축 해제 크기는 조작할 수 있어요. 그래서 총량 상한(8번)은 실제로 쓴 바이트를 세면서 검사해요. 헤더 크기로는 1차로 빨리 거르는 데만 써요.
- **한글 파일명:** 한글 Windows에서 만든 zip은 파일명이 UTF-8 표시 없이 CP949로 저장돼 있는 경우가 많아요. 그래서 UTF-8로 먼저 열어 보고, 실패하면 MS949로 다시 열어요.
- **압축 빌드 검사 범위:** `Build/` 안만 봐요. `StreamingAssets/`에 게임이 직접 쓰는 `.gz` 파일이 있을 수 있어서예요. 프론트 검사 범위와 같은지는 #8에서 확인해요.

## 저장 구조

```
{GMM_BUILD_ROOT_DIR}/
├── images/                       ← 공개. 업로드한 아이콘·대표 이미지·프로필 사진
├── builds/                       ← 공개 (nginx가 서빙)
│   └── {gameId}/
│       └── {uploadId}/
│           ├── index.html
│           ├── Build/{buildName}.loader.js, .data, .framework.js, .wasm
│           └── StreamingAssets/...
└── staging/                      ← 비공개 작업 공간 (절대 서빙하지 않음)
    └── {uploadId}/
```

- **업로드마다 새 경로:** `{uploadId}`가 매번 달라서 같은 경로를 덮어쓰는 일이 없어요. 그래서 브라우저에 이전 빌드가 캐시돼 있어도 문제가 없고, nginx에서 영구 캐시(`immutable`)를 걸 수 있어요.
- **같은 디스크에 둬요:** `staging/`과 `builds/`가 같은 파일 시스템에 있어야 5단계의 `ATOMIC_MOVE`가 동작해요. 그래서 반쯤 풀린 빌드가 공개되는 일이 없어요.
- **DB에는 상대 경로만:** DB(`tb_game.build_path`)에는 `{gameId}/{uploadId}`만 저장해요. `buildUrl`은 응답할 때 `{GMM_BUILD_PUBLIC_BASE_URL}/builds/{build_path}`로 만들어요. 그래서 도메인이 바뀌어도 DB를 고칠 필요가 없어요.
- **경로 검사:** 저장소는 UUID 형식이 아닌 ID를 거부해요. 그래서 잘못된 값이 들어와도 `builds/` 밖의 파일을 지우지 못해요.

## 파일과 DB의 순서

파일 작업은 DB 트랜잭션처럼 롤백되지 않아요. 그래서 어느 단계에서 실패해도 **DB가 없는 폴더를 가리키지 않도록** 순서를 고정했어요.

| 실패 지점 | 남는 것 | 처리 |
| --- | --- | --- |
| 1~4단계 | staging 폴더 | 8단계에서 바로 삭제해요 |
| 6단계 (DB 커밋 실패) | 새 빌드 폴더 | catch에서 바로 삭제해요 |
| 6~7단계 사이에 서버 종료 | 이전 빌드 폴더 | 매일 정리 작업이 지워요 |
| 7단계 삭제 실패 | 이전 빌드 폴더 | 경고 로그를 남기고, 매일 정리 작업이 지워요 |

빌드 처리는 몇 초 걸려요. 그래서 `GameUploadService`는 트랜잭션 밖에서 순서만 조정하고, DB 작업만 `GameService`의 `@Transactional` 메서드에서 해요. 이전 빌드 삭제는 `afterCommit { }`으로 예약해서, 롤백되면 지우지 않아요.

**매일 정리 작업 (`BuildCleanupJob`, 매일 04:00 KST)**
- `staging/` 아래 오래된 폴더를 지워요.
- `builds/` 아래에서 DB가 가리키지 않는 오래된 폴더를 지워요.
- "오래된"의 기준은 `cleanupGracePeriod`(기본 6시간)예요. 이보다 최근 폴더는 지우지 않아서, 진행 중인 업로드와 겹치지 않아요.

## 서빙: 별도 도메인과 iframe

업로드된 `loader.js`는 사용자가 만든 스크립트예요. GMM 페이지에서 직접 실행하면 GMM 사이트 권한으로 돌아가요. 그래서 로그인한 사용자의 쿠키로 API를 호출할 수 있어요. 이를 막는 방법은 이래요.

- **서버:** 빌드를 GMM 프론트·API와 **다른 사이트**(예: `games.<별도 도메인>`)에서 서빙해요. 서브도메인이 같은 사이트로 취급되는 구성(예: `gmm.example.com`과 `games.example.com`)은 피해요.
- **프론트:** `<iframe src="{buildUrl}/index.html" sandbox="allow-scripts allow-same-origin allow-pointer-lock" allowfullscreen>`로 띄워요.
- **`allow-same-origin`:** 빌드가 자기 도메인에서 `.data`·`.wasm`을 불러오고 IndexedDB 캐시를 쓰는 데 필요해요. 빌드 도메인이 GMM과 다르니 이 권한을 줘도 GMM의 쿠키·API에는 닿지 않아요.

`deploy/nginx/builds.conf`는 아래를 설정해요.

| 설정 | 이유 |
| --- | --- |
| `/builds/`와 `/images/`만 열고 나머지는 404 | `staging/`이 노출되지 않게 해요. `/images/`는 이미지 업로드 API(`POST /uploads/images`)가 저장한 파일이에요 |
| `application/wasm` MIME | 없으면 WASM 스트리밍 컴파일이 안 돼서 로딩이 느려지거나 실패해요 |
| `.js` → `text/javascript`, `.data` → `application/octet-stream` | 명세서 요구사항이에요 |
| `gzip on` | 빌드는 압축 없이 올라와요. 전송할 때 압축해서 용량을 줄여요 |
| `Cache-Control: immutable` | 경로가 업로드마다 달라서 안전해요 |
| `Content-Security-Policy: frame-ancestors` | GMM 프론트만 빌드를 iframe으로 띄울 수 있게 해요. 운영 도메인으로 바꿔야 해요 |
| 빌드 `index.html`에 화면 맞춤 CSS 삽입 (`sub_filter`) | Unity 템플릿은 캔버스 크기를 고정해서(Default는 푸터도 붙여요) iframe 밖으로 넘쳐요. 캔버스를 iframe에 꽉 채우고 푸터를 숨겨요. 이 파일만 `no-cache`라서 CSS를 바꾸면 이미 올라온 빌드에도 바로 반영돼요 |

## 운영 설정

| 환경 변수 | 기본값 | 맞춰야 하는 곳 |
| --- | --- | --- |
| `GMM_BUILD_ROOT_DIR` | `./data` | nginx 컨테이너와 같은 볼륨 (`/data/gmm`) |
| `GMM_BUILD_PUBLIC_BASE_URL` | `http://localhost:8081` | nginx 빌드 도메인 |
| `GMM_BUILD_MAX_ZIP_SIZE` | `200MB` | API 앞단 nginx의 `client_max_body_size`와 같게 |
| `GMM_BUILD_MAX_REQUEST_SIZE` | `210MB` | zip 상한 + JSON 여유분 |
| `GMM_BUILD_MAX_EXTRACTED_SIZE` | `500MB` | – |
| `GMM_BUILD_MAX_FILE_COUNT` | `2000` | – |

용량 상한은 아직 임시값이에요. 확정은 #8에서 해요.

## 저장소 교체 (S3 호환 스토리지 + CDN)

`BuildStorage` 인터페이스만 새로 구현하면 돼요. 호출하는 쪽은 바꿀 필요가 없어요. 구현할 때 주의할 점은 이래요.

- `publish`는 압축을 푼 파일을 하나씩 올리면서, 파일마다 `Content-Type`을 지정해야 해요. 특히 `.wasm`에는 `application/wasm`을 지정해야 해요.
- `publicUrl`은 CDN 주소를 돌려줘요.
- 정리 작업(`deleteOrphans`)은 지금 `LocalDiskBuildStorage` 전용이에요. 저장소를 바꾸면 버킷 목록을 기준으로 다시 구현해야 해요.

## 직접 테스트하기

```bash
docker compose up -d
GMM_SESSION_COOKIE_SECURE=false ./gradlew bootRun

# 로그인 후 브라우저 쿠키의 GMM_SESSION 값을 사용
curl -i http://localhost:8080/api/v1/games \
  -H "Cookie: GMM_SESSION=<세션 값>" \
  -F 'data={"jamId":"<게임잼 ID>","title":"테스트","tagline":"","description":"","genre":"액션","iconUrl":null,"orientation":"landscape","env":{"platforms":["PC"],"inputs":["키보드"],"note":""}};type=application/json' \
  -F 'buildFile=@MyGame.zip'

# 응답의 buildUrl 로 확인
curl -I "<buildUrl>/Build/<buildName>.wasm"   # Content-Type: application/wasm
```

`data`는 일반 폼 필드로 보내도 되고, JSON Blob으로 보내도 돼요. 서버는 두 경우 모두 UTF-8로 읽어요.

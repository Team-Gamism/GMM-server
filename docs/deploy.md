# 배포 (CI/CD)

GMM 서버는 학교 서버 한 대에서 Docker Compose로 돌아가요. `main`에 병합하면 GitHub Actions가 이미지를 만들고, 학교 서버에 설치한 self-hosted runner가 그 이미지로 교체해요.

## 흐름

```
PR (develop·main 대상) ─▶ CI: GitHub 호스팅 runner
                            ├─ Docker 안에서 ./gradlew build (ktlint, 단위 테스트)
                            └─ 운영 이미지 빌드 확인

main push ─▶ CD 1: GitHub 호스팅 runner
               └─ 검사 후 ghcr.io/team-gamism/gmm-server:{커밋 SHA}, :latest 푸시
           ─▶ CD 2: 학교 서버 runner (라벨 gmm-prod, environment production)
               └─ deploy/scripts/deploy.sh {커밋 SHA}
                    ├─ compose 파일과 nginx 템플릿을 $GMM_DEPLOY_DIR로 복사
                    ├─ docker compose pull, up -d
                    ├─ api 컨테이너 헬스 체크 (최대 3분)
                    └─ 실패하면 직전 버전으로 롤백하고 실패로 끝남
```

| 파일 | 역할 |
| --- | --- |
| `.github/workflows/ci.yml` | PR 검사 |
| `.github/workflows/cd.yml` | 이미지 푸시와 배포 |
| `Dockerfile` | `check`(빌드·테스트), `runtime`(운영 이미지) 단계 |
| `deploy/compose.prod.yaml` | 운영 스택: MySQL, API, 빌드·이미지 서빙 nginx |
| `deploy/scripts/deploy.sh` | 배포와 롤백 |
| GitHub `production` environment | 운영 설정값과 비밀값. 배포할 때마다 서버의 `.env`로 써져요 (아래 "운영 설정값") |

## 왜 self-hosted runner인가요

runner는 서버가 GitHub에 **먼저 연결해서** 작업을 받아와요. 그래서 서버에서 `github.com`, `ghcr.io`로 나가는 HTTPS만 되면 되고, GitHub에 VM 셸 권한을 주는 SSH 키를 맡기지 않아도 돼요. GSM SV는 SSH 포트포워딩이 열려 있어서 GitHub 호스팅 runner가 SSH로 배포할 수도 있지만, 공개 저장소의 Secret에 서버 키를 두지 않으려고 이 방식을 써요. 같은 학교 서버를 쓰는 cowork-server도 GHCR과 self-hosted runner로 배포해요.

이 저장소는 공개 저장소예요. 그래서 다른 사람이 보낸 PR의 코드가 학교 서버에서 실행되지 않도록 아래처럼 나눠요.

- CI는 항상 GitHub 호스팅 runner(`ubuntu-latest`)에서 돌아요.
- 학교 서버 runner는 `main` push와 수동 실행(`workflow_dispatch`)의 배포 작업만 받아요.
- 아래 "GitHub 설정"의 environment 보호 규칙으로 `main` 외의 브랜치는 배포할 수 없게 막아요.

## 처음 한 번 할 일

### 1. 학교 서버

1. Docker Engine과 Docker Compose 플러그인을 [Docker 공식 apt 저장소](https://docs.docker.com/engine/install/ubuntu/)로 설치해요 (`docker compose version`이 동작해야 해요). 데몬 전체의 MTU도 1400으로 맞춰요(아래 "Docker MTU 1400").
   ```bash
   echo '{ "mtu": 1400 }' | sudo tee /etc/docker/daemon.json && sudo systemctl restart docker
   ```
2. runner를 실행할 계정 `gha`를 만들고 `docker` 그룹에 넣어요. root로 runner를 실행하지 않아요.
   ```bash
   sudo useradd -m -s /bin/bash gha && sudo usermod -aG docker gha
   ```
3. 배포 폴더를 만들어요. `.env`는 배포 스크립트가 GitHub 설정값으로 매번 새로 써요(권한 600). 서버에서 직접 고치지 않아요.
   ```bash
   sudo mkdir -p /srv/gmm && sudo chown gha:gha /srv/gmm && sudo chmod 750 /srv/gmm
   ```
   다른 폴더를 쓰려면 저장소 변수 `GMM_DEPLOY_DIR`에 경로를 넣어요.
4. `gha` 계정의 `~/actions-runner`에 runner를 내려받고, 라벨 `gmm-prod`로 등록해요. 내려받는 주소와 등록 토큰은 GitHub 저장소 → Settings → Actions → Runners → **New self-hosted runner**에 나와요.
   ```bash
   sudo -u gha ./config.sh --unattended --url https://github.com/Team-Gamism/GMM-server \
     --token <발급 토큰> --name gmm-server --labels gmm-prod
   sudo ./svc.sh install gha && sudo ./svc.sh start   # 재부팅해도 다시 켜지게 서비스로 등록
   ```
   Runners 목록에 `gmm-server`가 **Idle**(online)로 보이면 끝이에요.

### 2. GitHub 설정

1. Settings → Environments → **production**을 만들고, Deployment branches를 `main`만 허용해요. 배포 전에 승인을 받고 싶으면 Required reviewers도 켜요.
2. Settings → Actions → General → Fork pull request workflows에서 **외부 기여자의 워크플로 실행에 승인 필요**를 켜요.
3. 첫 배포 뒤 Packages의 `gmm-server` 이미지가 저장소에 연결됐는지 확인해요. 배포 작업은 `GITHUB_TOKEN`으로 로그인해서 받아요.
4. 아래 "운영 설정값"을 넣어요. Variables는 **production** environment에 넣어요. Secrets는 지금 저장소 단위(Settings → Secrets and variables → Actions)에 있어요. production environment에 같은 이름이 있으면 그 값이 우선해요.

### 운영 설정값 (GitHub `production` environment)

배포 작업(`cd.yml`)이 이 값들을 `deploy.sh`에 넘기고, `deploy.sh`가 서버의 `$GMM_DEPLOY_DIR/.env`를 새로 써요. 필수 값이 비어 있으면 배포가 이름을 알려 주며 실패해요. 값에는 작은따옴표(`'`)와 줄바꿈을 쓸 수 없어요.

**Secrets**

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `MYSQL_PASSWORD` | ✅ | 앱이 쓰는 DB 계정 비밀번호 |
| `MYSQL_ROOT_PASSWORD` | ✅ | MySQL root 비밀번호 |

MySQL은 데이터가 비어 있을 때(첫 배포)만 이 값으로 계정을 만들어요. 나중에 바꾸려면 DB에서 `ALTER USER`로 먼저 바꾸고 Secret을 고쳐요. 두 값은 백업 복원에 필요하니 팀 비밀번호 관리 도구에도 적어 둬요.

**Variables**

| 이름 | 필수 | 현재 값 / 기본값 | 설명 |
| --- | --- | --- | --- |
| `DATAGSM_CLIENT_ID` | ✅ | DataGSM 클라이언트 `gmm-oauth`의 ID | |
| `DATAGSM_REDIRECT_URIS` | ✅ | `https://gmm.https.gsmsv.site/auth/callback` | DataGSM에 등록한 주소와 같아야 해요 |
| `GMM_BUILD_PUBLIC_BASE_URL` | ✅ | `https://gmm-games.https.gsmsv.site` | 빌드·이미지 URL의 앞부분 |
| `GMM_FRONTEND_ORIGIN` | ✅ | `https://gmm.https.gsmsv.site` | 빌드를 iframe으로 띄울 수 있는 출처예요. API도 브라우저의 쓰기 요청(POST·PUT·PATCH·DELETE)을 이 출처와 `GMM_CORS_ALLOWED_ORIGINS`에서만 받아요 |
| `COMPOSE_PROFILES` | | `front` | 프론트를 이 VM에서 서빙할 때 |
| `GMM_ADMIN_EMAILS` | | (없음) | 어드민 DataGSM 이메일, 쉼표 구분. **이 목록만이 어드민 기준이에요.** 빼면 다음 배포 후 바로 일반 사용자가 돼요. 비우면 어드민이 없어서 게임잼을 만들 수 없어요 |
| `GMM_CORS_ALLOWED_ORIGINS` | | (없음) | 프론트가 다른 도메인일 때만 |
| `DATAGSM_ALLOW_TEACHER` | | `false` | |
| `GMM_SESSION_COOKIE_SECURE` | | `true` | |
| `GMM_API_DOCS_ENABLED` | | `false` | 운영에서 Swagger를 열 때만 `true` |
| `MYSQL_DATABASE`, `MYSQL_USER` | | `gmm`, `gmm` | |
| `GMM_FRONT_DIR` | | `./front` (배포 폴더 기준) | 프론트 빌드 결과물 위치 |
| `FRONT_BIND`, `FRONT_PORT` | | `0.0.0.0`, `80` | |
| `BUILDS_BIND`, `BUILDS_PORT` | | `0.0.0.0`, `8081` | |
| `API_BIND`, `API_PORT` | | `127.0.0.1`, `8080` | |
| `API_MEM_LIMIT`, `MYSQL_MEM_LIMIT`, `MYSQL_BUFFER_POOL` | | `1536m`, `1g`, `512M` (기본값 `768m`, `512m`, `128M`) | 아래 "메모리" 참고 |
| `JAVA_TOOL_OPTIONS` | | (없음) | JVM 옵션 추가 |
| `GMM_DEPLOY_DIR` | | `/srv/gmm` | 배포 폴더 |

새 설정을 추가하면 `cd.yml`의 `env`, `deploy.sh`의 `REQUIRED`·`OPTIONAL`, `compose.prod.yaml`, 이 표를 함께 고쳐요.

### 3. 학교 서버(GSM SV) 설정

GMM 서버는 GSM SV의 VM `gmm-server`에서 돌아요. 관리 화면은 <https://gsmsv.site/instances>예요.

| 항목 | 값 |
| --- | --- |
| 계정 | 프로젝트 오너(PO) 계정 |
| 노드 | GSM GPU 3 (프로젝트 전용) |
| 사양 | 2 vCPU, 4 GB, 디스크 40 GB, Ubuntu 22.04 |
| 만료 | 없어요 (PO VM) |
| 자동 스냅샷 | 켜져 있어요 |

SSH 주소와 포트는 VM 상세 페이지의 "접속 정보"에서 확인해요. 처음 받은 `ubuntu` 계정 비밀번호는 `passwd`로 바꿔요.

**HTTPS 도메인 (HTTPS 탭, VM당 2개)**

학교 프록시가 `*.https.gsmsv.site`의 HTTPS를 맡고, 지정한 VM 내부 포트로 넘겨요. 그래서 compose는 공개할 포트를 `0.0.0.0`으로 열어요. `127.0.0.1`로는 프록시가 들어오지 못해요.

| 서브도메인 | 내부 포트 | compose 서비스 | 용도 |
| --- | --- | --- | --- |
| `gmm` | 80 | `front` | 프론트 정적 파일과 `/api` → API |
| `gmm-games` | 8081 | `builds` | 빌드·이미지 서빙 |

프론트를 다른 곳에 배포하면 Variable `COMPOSE_PROFILES`를 비우고, `gmm-api` 서브도메인을 8080에 연결해요. 이때는 `API_BIND=0.0.0.0`, `GMM_CORS_ALLOWED_ORIGINS=<프론트 주소>`도 넣어요.

프론트 빌드 결과물(`dist/`)은 `GMM_FRONT_DIR`(기본 `/srv/gmm/front`)에 두면 `front` nginx가 서빙해요. 프론트 쪽 배포 방법이 정해지면 이 폴더를 채우는 방식을 맞춰요.

**Docker MTU 1400 (필수)**

GSM SV VM은 GRE 터널로 외부와 통신해서 실제 MTU가 1400이에요. Docker 기본값 1500으로 두면 컨테이너에서 나가는 요청이 응답 없이 실패하고, DataGSM 로그인이 안 돼요. `compose.prod.yaml`의 기본 네트워크가 MTU 1400이고, VM의 `/etc/docker/daemon.json`에도 `"mtu": 1400`을 넣어 둬서 다른 네트워크도 안전해요. 다른 compose 파일을 쓸 때도 이 설정을 넣어요 (학교 문서: <https://gsmsv.site/docs/docker>).

**메모리**

| VM 메모리 | 권장 Variables |
| --- | --- |
| 2 GB | 기본값 (`API_MEM_LIMIT=768m`, `MYSQL_MEM_LIMIT=512m`, `MYSQL_BUFFER_POOL=128M`) |
| 4 GB (지금) | `API_MEM_LIMIT=1536m`, `MYSQL_MEM_LIMIT=1g`, `MYSQL_BUFFER_POOL=512M` (production에 넣어 뒀어요) |

VM에는 MySQL, API, nginx 2개, runner가 함께 올라가요. PO VM은 설정 탭에서 재시작 없이 CPU와 메모리를 늘릴 수 있어요. 늘리면 위 Variables도 함께 올려요.

**만료와 스냅샷**

- PO 계정의 VM은 만료가 없어요. 일반 사용자 VM은 만든 지 30일 뒤 삭제되니 운영용으로 쓰지 않아요.
- 설정 탭의 **매일 자동 스냅샷**(00:10 KST)을 켜 뒀어요. VM 전체를 하루 단위로 되돌릴 수 있고, 하루치만 남아서 아래 DB 백업과 함께 써요.

**기타**

- VM 기본 포트(22, 80, 10000)는 `service.gsmsv.site`의 포트포워딩으로도 열려 있어요. 서비스 주소는 HTTPS 서브도메인을 쓰고, 이 포트로 안내하지 않아요.

## 배포하기

1. 기능은 `develop`에 병합해요 (CI만 돌아요).
2. 출시할 때 `develop` → `main` PR을 만들고 병합해요. CD가 자동으로 돌아요.
3. 같은 커밋을 다시 배포하려면 Actions → CD → **Run workflow**를 눌러요.

## 롤백과 장애 대응

- **자동 롤백:** 새 버전이 3분 안에 healthy가 되지 않으면 `/srv/gmm/.deployed-tag`에 적힌 직전 버전으로 되돌리고 실패로 끝나요.
- **직접 롤백:** 원하는 커밋을 지정해서 실행해요.
  ```bash
  cd /srv/gmm
  IMAGE_TAG=<커밋 SHA> docker compose --env-file .env -f compose.prod.yaml up -d api
  ```
- **DB 마이그레이션 주의:** Flyway는 새 버전이 켜질 때 바로 적용돼요. 롤백해도 스키마는 되돌아가지 않아요. 그래서 마이그레이션은 **이전 버전 코드도 그대로 동작하도록**(컬럼 추가는 nullable로, 삭제는 다음 출시에) 작성해요.
- **로그 보기:** `docker compose --env-file .env -f compose.prod.yaml logs -f api`

## 데이터와 백업

| 볼륨 | 내용 |
| --- | --- |
| `gmm_mysql-data` | MySQL 데이터 |
| `gmm_gmm-data` | 업로드한 빌드(`builds/`)와 이미지(`images/`) |

두 볼륨이 지워지면 데이터가 사라져요. `docker compose down -v`는 쓰지 않아요. 백업 방법(주기, 보관 위치)은 학교 서버 사정에 맞춰 정해야 해요. 예를 들면 이렇게 해요.

```bash
docker compose --env-file .env -f compose.prod.yaml exec -T mysql \
  sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction gmm' > gmm-$(date +%F).sql
```

학교 서버를 다른 프로젝트와 함께 쓸 수 있으니, 배포 스크립트는 오래된 이미지를 자동으로 지우지 않아요. 디스크가 부족하면 `docker image ls ghcr.io/team-gamism/gmm-server`로 확인하고 직접 지워요.

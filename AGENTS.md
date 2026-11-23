# AGENTS.md

Operating guide for AI coding agents (and humans) working in this repository. Read
this first; follow links only when the task needs them.

## What GMM-server is

The API server for the Gamism game jam management service. It exchanges DataGSM
OAuth (PKCE) tokens and issues session cookies, serves the jam, game, rating and
result API under `/api/v1`, and validates, extracts and stores Unity WebGL build zips.
The builds themselves are served by nginx on a separate domain, not by Spring.

Stack: Kotlin, Spring Boot 4.1, Java 27, MySQL 8, Spring Data JPA, Flyway. Tests use
Kotest and MockK.

**An agent working here must never lose user data or uploaded builds, and never
expose a file outside `builds/` and `images/`. When uncertain, stop and ask; never guess.**

## Where things are

| Need | Go to |
| --- | --- |
| Build upload flow, validation rules, storage layout | [`docs/build-upload.md`](docs/build-upload.md) |
| Image upload (JPEG, PNG, WebP only, detected from file bytes) | `src/main/kotlin/com/gmm/domain/image/` |
| Domains (auth, user, session, jam, game, build) | `src/main/kotlin/com/gmm/domain/<name>/` (`entity`, `repository`, `service`, `presentation`) |
| Shared code: auth filter, config, errors, helpers | `src/main/kotlin/com/gmm/global/` |
| Error codes and their user-facing messages | [`global/exception/ErrorCode.kt`](src/main/kotlin/com/gmm/global/exception/ErrorCode.kt) |
| App config and env var defaults | [`application.yml`](src/main/resources/application.yml), [`GmmProperties.kt`](src/main/kotlin/com/gmm/global/config/GmmProperties.kt) |
| DB schema (Flyway) | [`src/main/resources/db/migration/`](src/main/resources/db/migration/) |
| Tests | `src/test/kotlin/com/gmm/domain/<name>/` |
| Dependency versions | [`gradle/libs.versions.toml`](gradle/libs.versions.toml) |
| Local MySQL and build-serving nginx (API on 8080, builds on 8081) | [`compose.yaml`](compose.yaml), [`deploy/nginx/builds.conf`](deploy/nginx/builds.conf) |
| CI/CD, production stack, school server runner | [`docs/deploy.md`](docs/deploy.md), [`.github/workflows/`](.github/workflows/), [`deploy/`](deploy/) |
| Code style | [`.editorconfig`](.editorconfig) (ktlint, `intellij_idea`, 120 columns) |

Do not load the whole repository into context. Read the domain you are changing, the
`global/` code it uses, and the tests that cover it.

## Working loop

1. **Discover:** `git status`, current branch, open PRs (`gh pr list`), the issue.
2. **Understand:** read the code path and every file it touches. A rule often lives in
   two places, e.g. `ErrorCode.kt` and the service that throws it, `application.yml` and
   `GmmProperties.kt`, or the build validator and `docs/build-upload.md`. Change both.
3. **Plan the smallest change** that is one logical unit. If it mixes concerns, split it.
4. **Implement** on a branch named `<type>/<short-slug>` from an up-to-date `develop`.
5. **Verify:**
   - `./gradlew ktlintCheck` (`./gradlew ktlintFormat` to fix)
   - `./gradlew test`
   - `./gradlew build` when config, dependencies or migrations changed
   - Manual check against a real DB when an API or migration changed:
     `docker compose up -d`, then
     `DATAGSM_CLIENT_ID=... GMM_SESSION_COOKIE_SECURE=false ./gradlew bootRun`

   Fix root causes; never weaken a check or a test.
6. **Commit** as `<type>: <what changed>`, type `feat`, `fix`, `docs`, `ci`, `test`,
   `refactor` or `chore`. The PR title uses the same format.
7. **PR:** `gh pr create` with Summary, Changes and Testing (what you ran and the result).
8. **Checks green → merge** into `develop` with squash. `main` receives `develop` for releases.

## Hard rules

### Data and files

- Never edit a Flyway migration that has been applied anywhere (`V1__init.sql` and up).
  Schema changes go in a new `V<n>__<description>.sql`. Keep `ddl-auto: none`.
- Store every time in UTC (`Instant`, `DATETIME(6)`). Get the current time from the
  injected `Clock`, never `Instant.now()` directly, so tests can fix it.
- IDs are UUID strings from `Ids.new()`.
- File side effects that cannot roll back (deleting a build folder) run after commit,
  through `afterCommit { }`. Never delete files inside a transaction that may still
  roll back.
- Build storage paths come only from validated UUIDs. Nothing from the request (zip
  entry names, IDs, file names) may reach a path outside `builds/` or `staging/`.
  Keep the zip slip, file count and extracted size checks in
  [`docs/build-upload.md`](docs/build-upload.md#검증-규칙) intact.
- `staging/` is never served. Only `builds/` and `images/` are public.
- The build validation rules match the frontend's (`src/lib/unityZip.ts`) and the
  user guide. Changing one means updating the others; say so in the PR.
- Upload size limits live in three places: `gmm.build.max-zip-size`, Spring
  `multipart.max-file-size` and nginx `client_max_body_size`. Change them together.

### Auth and security

- Every endpoint under `com.gmm.domain` gets the `/api/v1` prefix from `WebConfig`.
  Do not hardcode the prefix in `@RequestMapping`.
- Get the caller with `@CurrentUser user: AuthUser`. Check ownership and role in the
  service, not only in the controller.
- Never log or return session cookie values, OAuth codes, tokens or secrets. Sessions
  are stored as a SHA-256 hash; keep it that way.
- Never commit secrets or a `.env` file. New config gets an env var with a safe
  default in `application.yml`. Production values live in the GitHub `production` environment;
  a new one also goes into `cd.yml`, `deploy.sh` and the table in `docs/deploy.md`.

### Errors and API

- Throw `GmmException(ErrorCode.X)`; `GlobalExceptionHandler` turns it into the error
  response. Do not build error responses by hand.
- `ErrorCode.message` is shown to the user as is: a Korean sentence in 해요체. Add a
  new code under the right domain section instead of reusing an unrelated one.
- Do not break the response shape the frontend uses. A breaking API change needs the
  user's confirmation first.

### Git

- One logical change = one commit = one PR. No unrelated changes in a PR.
- Never use `--no-verify`, never force-push `main` or `develop`, never merge with failing checks.
- **Never add `Co-Authored-By` or any AI attribution trailer** to commits or PRs. Use
  the repository's configured Git identity; never change it.
- Fixes to merged work go in a new commit and a new PR.

### Content

- Code, comments and KDoc are in English. User-facing messages and docs under
  `docs/` and `README.md` are in Korean (해요체 for docs, as the existing ones are).
  Agents talk to the user in the user's language.
- Documentation is part of the change: update `application.yml` defaults, `docs/`
  and comments that your change makes stale.
- Follow `.editorconfig`; ktlint enforces it.

## Decision-making

- If a request would lose data, delete builds, or expose files or secrets, do not
  silently comply. Explain the conflict and propose a safer alternative.
- Work with the user, not for them: when a request is ambiguous or a choice (scope,
  API shape, schema) is the user's to make, ask with the options and a recommended one.
- If two approaches are plausible, pick the simpler one that keeps the existing
  patterns, and say why in the PR.
- Low confidence: choose the safer, reversible option and say so.

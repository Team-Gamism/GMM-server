# CLAUDE.md

@AGENTS.md

## Claude Code specifics

- Use targeted search (Grep/Glob) and read only the files a task needs. Start from the
  domain folder under `src/main/kotlin/com/gmm/domain/` and its tests.
- To check an API change, run the app (`docker compose up -d`, then `./gradlew bootRun`)
  and call the endpoint. Say in the PR what you ran and the result.
- For web lookups, prefer official documentation (Spring Boot, Kotlin, Flyway, Kotest,
  MockK, DataGSM, GitHub CLI manual).
- Long tasks: one PR at a time; report which PRs merged and what remains, including any
  check that failed or was skipped.
- Never add `Co-Authored-By` or other attribution trailers to commits or PRs.

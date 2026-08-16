# Social Profile 0.3.0 — Implementation Plan

> **For agentic workers:** execute task-by-task; checkboxes track progress.

**Goal:** Ship social profile landing + SQLite backend + tunnel + Android UI as app version 0.3.0.

**Architecture:** Node `node:sqlite` HTTP API; Android OkHttp client; Profile feature hosts social navigation.

**Tech Stack:** Node 22, SQLite, Cloudflare cloudflared quick tunnel, Kotlin Compose, OkHttp, Hilt.

## Global Constraints

- Android `versionName` 0.3.0 / `versionCode` 12
- No passwords; deviceId + token only
- Result/`errorKey` style for user errors
- Branch prefix `refactor/` for structural work if branching

---

### Task 1: Backend API + SQLite

- [ ] `android/backend/server.mjs` + schema + README + start/tunnel scripts

### Task 2: Domain + HTTP client

- [ ] Models, `SocialRepository`, `HttpSocialRepository`, DI, `SOCIAL_API_URL`

### Task 3: Profile UI redesign

- [ ] Social landing, user profile, search, edit sheet, friends list

### Task 4: Public deck + chat + DM

- [ ] Screens wired from profile

### Task 5: Release 0.3.0

- [ ] Bump, assembleRelease, update.json, GitHub release, start backend+tunnel notes

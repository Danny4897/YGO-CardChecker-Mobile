# YGOChecker Social API (SQLite) — deprecated

**Superseded by a hosted Supabase project** (Postgres + Auth + Realtime). The
app no longer talks to this server; `SocialRepository` is now implemented by
`android/data/social` against Supabase. This directory is kept only as a
reference for the original API shape (routes, request/response bodies) that
the Supabase schema and RLS policies were modeled after.

Why: this server had to run on a developer's own PC, reachable only through a
Cloudflare Quick Tunnel whose URL rotated on every restart — fine for solo
testing, not for anyone else's install to depend on. It also had no real
account recovery: identity was a bare Android device ID, so reinstalling the
app meant losing friends, published decks, and chat history.

<details>
<summary>Original README (historical)</summary>

Micro-backend for profiles, friends, public decks, DMs, and deck chat.

## Requirements

- Node.js **22+** (`node:sqlite`)
- Optional: [cloudflared](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/install-and-setup/installation/) for public HTTPS URL (no Cloudflare account needed for Quick Tunnels)

## Run locally

```bash
cd android/backend
npm start
# → http://127.0.0.1:8787/health
```

## Public URL for remote testers

```bash
# terminal 1
npm start

# terminal 2 — prints https://….trycloudflare.com and writes data/tunnel-url.txt
npm run tunnel
```

Or both:

```bash
npm run dev
```

Put the printed URL into `android/local.properties`:

```properties
SOCIAL_API_URL=https://xxxx.trycloudflare.com
```

Rebuild the app. Keep the PC online while testers use social features.

## Auth

1. App `POST /v1/register` with stable `deviceId` → `{ token, user }`
2. All other `/v1/*` calls: `Authorization: Bearer <token>`

Friend codes look like `YG-AB12CD`.

</details>

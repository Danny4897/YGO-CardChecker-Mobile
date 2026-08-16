# YGOChecker Social API (SQLite)

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

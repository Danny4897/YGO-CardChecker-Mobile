# Social profile + friends + deck chat (v0.3.0)

## Goal

Profile as landing for finding friends: public decks, friend request / DM, global chat on public decks.

## Decisions

| Topic | Choice |
|-------|--------|
| Persistence | Local **SQLite** API on PC (`android/backend`) |
| Reachability | **Cloudflare Quick Tunnel** (HTTPS URL, no CF account required for trycloudflare) |
| Identity | Device UUID + nickname + friend code `YG-XXXXXX`; bearer token |
| Add friend | Search nickname **or** friend code → open profile → request |
| Own vs other | Same social layout; own profile has **Edit** for local settings |
| Deck click | Public deck screen: view cards + world chat (language filter) |
| DM | Only when friendship accepted |

## Out of scope (later)

OAuth sync, multi-device profile merge, push notifications, moderation tools.

## API surface (v1)

- `POST /v1/register` — deviceId, username, avatarCardId?
- `PATCH /v1/me` — username, avatarCardId
- `GET /v1/users/search?q=`
- `GET /v1/users/:idOrCode`
- `POST /v1/friends/request` `{ toUserId }`
- `POST /v1/friends/accept` `{ fromUserId }`
- `GET /v1/friends`
- `PUT /v1/me/decks/:localId` — publish public deck payload
- `DELETE /v1/me/decks/:localId`
- `GET /v1/users/:id/decks`
- `GET /v1/decks/:id`
- `GET|POST /v1/decks/:id/messages?lang=`
- `GET|POST /v1/dm/:peerUserId/messages`

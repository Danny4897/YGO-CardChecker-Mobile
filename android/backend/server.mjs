/**
 * YGOChecker social micro-API — SQLite (node:sqlite) + plain HTTP.
 * Run: node --experimental-sqlite server.mjs
 * Env: PORT=8787 DB_PATH=./data/ygochecker-social.db
 */
import { createServer } from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { mkdirSync, existsSync, writeFileSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes, randomUUID, createHash } from 'node:crypto';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 8787);
const DB_PATH = process.env.DB_PATH || join(__dirname, 'data', 'ygochecker-social.db');
const TUNNEL_URL_FILE = join(__dirname, 'data', 'tunnel-url.txt');

mkdirSync(dirname(DB_PATH), { recursive: true });

const db = new DatabaseSync(DB_PATH);
db.exec(`
PRAGMA journal_mode = WAL;
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  device_id TEXT NOT NULL UNIQUE,
  token_hash TEXT NOT NULL,
  username TEXT NOT NULL,
  friend_code TEXT NOT NULL UNIQUE,
  avatar_card_id INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username COLLATE NOCASE);
CREATE TABLE IF NOT EXISTS friendships (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_a TEXT NOT NULL,
  user_b TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('pending','accepted')),
  requested_by TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  UNIQUE(user_a, user_b)
);
CREATE TABLE IF NOT EXISTS public_decks (
  id TEXT PRIMARY KEY,
  owner_id TEXT NOT NULL,
  local_deck_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  cover_card_ids TEXT NOT NULL DEFAULT '[]',
  cards_json TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  UNIQUE(owner_id, local_deck_id)
);
CREATE TABLE IF NOT EXISTS deck_messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  deck_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  body TEXT NOT NULL,
  lang TEXT NOT NULL DEFAULT 'en',
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_deck_msg ON deck_messages(deck_id, created_at);
CREATE TABLE IF NOT EXISTS dm_messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  thread_key TEXT NOT NULL,
  from_user_id TEXT NOT NULL,
  to_user_id TEXT NOT NULL,
  body TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_dm ON dm_messages(thread_key, created_at);
`);

function now() {
  return Date.now();
}

function hashToken(token) {
  return createHash('sha256').update(token).digest('hex');
}

function friendCode() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let s = '';
  const bytes = randomBytes(6);
  for (let i = 0; i < 6; i++) s += alphabet[bytes[i] % alphabet.length];
  return `YG-${s}`;
}

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
    'Access-Control-Allow-Methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8');
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(new Error('invalid_json'));
      }
    });
    req.on('error', reject);
  });
}

function authUser(req) {
  const h = req.headers.authorization || '';
  const m = /^Bearer\s+(.+)$/i.exec(h);
  if (!m) return null;
  const tokenHash = hashToken(m[1].trim());
  return db.prepare('SELECT * FROM users WHERE token_hash = ?').get(tokenHash) || null;
}

function publicUser(row, friendship = null) {
  return {
    id: row.id,
    username: row.username,
    friendCode: row.friend_code,
    avatarCardId: row.avatar_card_id ?? null,
    friendship,
  };
}

function orderedPair(a, b) {
  return a < b ? [a, b] : [b, a];
}

function friendshipBetween(meId, otherId) {
  const [a, b] = orderedPair(meId, otherId);
  const row = db
    .prepare('SELECT * FROM friendships WHERE user_a = ? AND user_b = ?')
    .get(a, b);
  if (!row) return 'none';
  if (row.status === 'accepted') return 'friends';
  return row.requested_by === meId ? 'pending_out' : 'pending_in';
}

function areFriends(a, b) {
  return friendshipBetween(a, b) === 'friends';
}

function threadKey(a, b) {
  const [x, y] = orderedPair(a, b);
  return `${x}:${y}`;
}

function findUser(idOrCode) {
  const q = String(idOrCode || '').trim();
  if (!q) return null;
  return (
    db.prepare('SELECT * FROM users WHERE id = ? OR friend_code = ? COLLATE NOCASE').get(q, q) ||
    null
  );
}

/** Resolve public deck by id (`owner--local` or legacy `owner:local`) or owner+local. */
function findPublicDeck(rawId) {
  const id = String(rawId || '').trim();
  if (!id) return null;
  let row = db.prepare('SELECT * FROM public_decks WHERE id = ?').get(id);
  if (row) return row;
  if (id.includes('--')) {
    row = db.prepare('SELECT * FROM public_decks WHERE id = ?').get(id.replace('--', ':'));
    if (row) return row;
    const [owner, local] = id.split('--');
    if (owner && local) {
      return (
        db
          .prepare('SELECT * FROM public_decks WHERE owner_id = ? AND local_deck_id = ?')
          .get(owner, Number(local)) || null
      );
    }
  }
  if (id.includes(':')) {
    row = db.prepare('SELECT * FROM public_decks WHERE id = ?').get(id.replace(':', '--'));
    if (row) return row;
    const idx = id.lastIndexOf(':');
    const owner = id.slice(0, idx);
    const local = id.slice(idx + 1);
    if (owner && local) {
      return (
        db
          .prepare('SELECT * FROM public_decks WHERE owner_id = ? AND local_deck_id = ?')
          .get(owner, Number(local)) || null
      );
    }
  }
  return null;
}

function deckPayload(deck) {
  const owner = db.prepare('SELECT * FROM users WHERE id = ?').get(deck.owner_id);
  return {
    id: deck.id,
    ownerId: deck.owner_id,
    ownerUsername: owner?.username || '?',
    localDeckId: deck.local_deck_id,
    name: deck.name,
    coverCardIds: JSON.parse(deck.cover_card_ids || '[]'),
    cards: JSON.parse(deck.cards_json || '[]'),
    updatedAt: deck.updated_at,
  };
}

function parsePath(url) {
  const u = new URL(url, 'http://localhost');
  return { pathname: u.pathname.replace(/\/+$/, '') || '/', searchParams: u.searchParams };
}

async function handler(req, res) {
  if (req.method === 'OPTIONS') {
    return json(res, 204, {});
  }

  const { pathname, searchParams } = parsePath(req.url || '/');

  try {
    if (req.method === 'GET' && pathname === '/health') {
      return json(res, 200, { ok: true, db: DB_PATH });
    }

    if (req.method === 'GET' && pathname === '/v1/tunnel-url') {
      let url = null;
      if (existsSync(TUNNEL_URL_FILE)) {
        url = readFileSync(TUNNEL_URL_FILE, 'utf8').trim() || null;
      }
      return json(res, 200, { url });
    }

    if (req.method === 'POST' && pathname === '/v1/register') {
      const body = await readBody(req);
      const deviceId = String(body.deviceId || '').trim().slice(0, 80);
      let username = String(body.username || '').trim().slice(0, 32);
      const avatarCardId =
        body.avatarCardId == null || body.avatarCardId === ''
          ? null
          : Number(body.avatarCardId);
      if (deviceId.length < 8) return json(res, 400, { errorKey: 'social.device_invalid' });
      if (username.length < 2) username = `Duelist${Math.floor(Math.random() * 9000 + 1000)}`;

      const existing = db.prepare('SELECT * FROM users WHERE device_id = ?').get(deviceId);
      if (existing) {
        const token = randomBytes(24).toString('hex');
        db.prepare(
          'UPDATE users SET token_hash = ?, username = ?, avatar_card_id = COALESCE(?, avatar_card_id), updated_at = ? WHERE id = ?',
        ).run(hashToken(token), username, avatarCardId, now(), existing.id);
        const row = db.prepare('SELECT * FROM users WHERE id = ?').get(existing.id);
        return json(res, 200, { token, user: publicUser(row) });
      }

      const id = randomUUID();
      const token = randomBytes(24).toString('hex');
      let code = friendCode();
      for (let i = 0; i < 8; i++) {
        const clash = db.prepare('SELECT 1 FROM users WHERE friend_code = ?').get(code);
        if (!clash) break;
        code = friendCode();
      }
      const t = now();
      db.prepare(
        `INSERT INTO users (id, device_id, token_hash, username, friend_code, avatar_card_id, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      ).run(id, deviceId, hashToken(token), username, code, avatarCardId, t, t);
      const row = db.prepare('SELECT * FROM users WHERE id = ?').get(id);
      return json(res, 201, { token, user: publicUser(row) });
    }

    const me = authUser(req);
    if (!me && pathname.startsWith('/v1/')) {
      return json(res, 401, { errorKey: 'social.unauthorized' });
    }

    if (req.method === 'GET' && pathname === '/v1/me') {
      return json(res, 200, { user: publicUser(me) });
    }

    if (req.method === 'PATCH' && pathname === '/v1/me') {
      const body = await readBody(req);
      const username =
        body.username != null ? String(body.username).trim().slice(0, 32) : me.username;
      if (username.length < 2) return json(res, 400, { errorKey: 'social.username_invalid' });
      const avatarCardId =
        body.avatarCardId === undefined
          ? me.avatar_card_id
          : body.avatarCardId == null
            ? null
            : Number(body.avatarCardId);
      db.prepare(
        'UPDATE users SET username = ?, avatar_card_id = ?, updated_at = ? WHERE id = ?',
      ).run(username, avatarCardId, now(), me.id);
      const row = db.prepare('SELECT * FROM users WHERE id = ?').get(me.id);
      return json(res, 200, { user: publicUser(row) });
    }

    if (req.method === 'GET' && pathname === '/v1/users/search') {
      const q = String(searchParams.get('q') || '')
        .trim()
        .slice(0, 40);
      if (q.length < 2) return json(res, 200, { users: [] });
      // Escape LIKE wildcards in user input; match username or friend code (case-insensitive).
      const safe = q.replace(/[%_]/g, '');
      if (safe.length < 2) return json(res, 200, { users: [] });
      const needle = `%${safe}%`;
      const codeNeedle = `%${safe.toUpperCase()}%`;
      const rows = db
        .prepare(
          `SELECT * FROM users
           WHERE id != ?
             AND (
               lower(username) LIKE lower(?)
               OR upper(friend_code) LIKE ?
             )
           ORDER BY username COLLATE NOCASE
           LIMIT 30`,
        )
        .all(me.id, needle, codeNeedle);
      return json(res, 200, {
        users: rows.map((r) => publicUser(r, friendshipBetween(me.id, r.id))),
      });
    }

    const userMatch = /^\/v1\/users\/([^/]+)$/.exec(pathname);
    if (req.method === 'GET' && userMatch) {
      const row = findUser(decodeURIComponent(userMatch[1]));
      if (!row) return json(res, 404, { errorKey: 'social.user_not_found' });
      return json(res, 200, {
        user: publicUser(row, friendshipBetween(me.id, row.id)),
      });
    }

    const userDecksMatch = /^\/v1\/users\/([^/]+)\/decks$/.exec(pathname);
    if (req.method === 'GET' && userDecksMatch) {
      const row = findUser(decodeURIComponent(userDecksMatch[1]));
      if (!row) return json(res, 404, { errorKey: 'social.user_not_found' });
      const decks = db
        .prepare(
          `SELECT d.id, d.owner_id, d.local_deck_id, d.name, d.cover_card_ids, d.updated_at, u.username AS owner_username
           FROM public_decks d
           LEFT JOIN users u ON u.id = d.owner_id
           WHERE d.owner_id = ?
           ORDER BY d.updated_at DESC`,
        )
        .all(row.id)
        .map((d) => ({
          id: d.id,
          ownerId: d.owner_id,
          ownerUsername: d.owner_username || row.username,
          localDeckId: d.local_deck_id,
          name: d.name,
          coverCardIds: JSON.parse(d.cover_card_ids || '[]'),
          updatedAt: d.updated_at,
        }));
      return json(res, 200, { decks });
    }

    if (req.method === 'POST' && pathname === '/v1/friends/request') {
      const body = await readBody(req);
      const target = findUser(body.toUserId || body.toFriendCode || '');
      if (!target) return json(res, 404, { errorKey: 'social.user_not_found' });
      if (target.id === me.id) return json(res, 400, { errorKey: 'social.friend_self' });
      const [a, b] = orderedPair(me.id, target.id);
      const existing = db
        .prepare('SELECT * FROM friendships WHERE user_a = ? AND user_b = ?')
        .get(a, b);
      if (existing?.status === 'accepted') {
        return json(res, 200, { friendship: 'friends' });
      }
      if (existing?.status === 'pending') {
        if (existing.requested_by !== me.id) {
          db.prepare(
            'UPDATE friendships SET status = ?, created_at = ? WHERE id = ?',
          ).run('accepted', now(), existing.id);
          return json(res, 200, { friendship: 'friends' });
        }
        return json(res, 200, { friendship: 'pending_out' });
      }
      db.prepare(
        `INSERT INTO friendships (user_a, user_b, status, requested_by, created_at)
         VALUES (?, ?, 'pending', ?, ?)`,
      ).run(a, b, me.id, now());
      return json(res, 201, { friendship: 'pending_out' });
    }

    if (req.method === 'POST' && pathname === '/v1/friends/accept') {
      const body = await readBody(req);
      const from = findUser(body.fromUserId || '');
      if (!from) return json(res, 404, { errorKey: 'social.user_not_found' });
      const [a, b] = orderedPair(me.id, from.id);
      const row = db
        .prepare('SELECT * FROM friendships WHERE user_a = ? AND user_b = ?')
        .get(a, b);
      if (!row || row.status !== 'pending' || row.requested_by === me.id) {
        return json(res, 400, { errorKey: 'social.friend_no_pending' });
      }
      db.prepare('UPDATE friendships SET status = ? WHERE id = ?').run('accepted', row.id);
      return json(res, 200, { friendship: 'friends' });
    }

    if (req.method === 'GET' && pathname === '/v1/friends') {
      const rows = db
        .prepare(
          `SELECT * FROM friendships
           WHERE (user_a = ? OR user_b = ?) AND status = 'accepted'`,
        )
        .all(me.id, me.id);
      const pendingIn = db
        .prepare(
          `SELECT * FROM friendships
           WHERE (user_a = ? OR user_b = ?) AND status = 'pending' AND requested_by != ?`,
        )
        .all(me.id, me.id, me.id);
      const mapPeer = (row) => (row.user_a === me.id ? row.user_b : row.user_a);
      const friends = rows
        .map((r) => {
          const peer = db.prepare('SELECT * FROM users WHERE id = ?').get(mapPeer(r));
          return peer ? publicUser(peer, 'friends') : null;
        })
        .filter(Boolean);
      const incoming = pendingIn
        .map((r) => {
          const peer = db.prepare('SELECT * FROM users WHERE id = ?').get(mapPeer(r));
          return peer ? publicUser(peer, 'pending_in') : null;
        })
        .filter(Boolean);
      return json(res, 200, { friends, incoming });
    }

    if (req.method === 'GET' && pathname === '/v1/decks') {
      const limit = Math.min(100, Math.max(1, Number(searchParams.get('limit') || 50)));
      const decks = db
        .prepare(
          `SELECT d.id, d.owner_id, d.local_deck_id, d.name, d.cover_card_ids, d.updated_at, u.username AS owner_username
           FROM public_decks d
           JOIN users u ON u.id = d.owner_id
           ORDER BY d.updated_at DESC
           LIMIT ?`,
        )
        .all(limit)
        .map((d) => ({
          id: d.id,
          ownerId: d.owner_id,
          ownerUsername: d.owner_username || '?',
          localDeckId: d.local_deck_id,
          name: d.name,
          coverCardIds: JSON.parse(d.cover_card_ids || '[]'),
          updatedAt: d.updated_at,
        }));
      return json(res, 200, { decks });
    }

    const putDeck = /^\/v1\/me\/decks\/(\d+)$/.exec(pathname);
    if (req.method === 'PUT' && putDeck) {
      const localId = Number(putDeck[1]);
      const body = await readBody(req);
      const name = String(body.name || 'Deck').trim().slice(0, 80) || 'Deck';
      const coverCardIds = Array.isArray(body.coverCardIds)
        ? body.coverCardIds.map(Number).filter((n) => n > 0).slice(0, 2)
        : [];
      const cards = Array.isArray(body.cards) ? body.cards : [];
      const id = `${me.id}--${localId}`;
      const t = now();
      db.prepare(
        `INSERT INTO public_decks (id, owner_id, local_deck_id, name, cover_card_ids, cards_json, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT(owner_id, local_deck_id) DO UPDATE SET
           id = excluded.id,
           name = excluded.name,
           cover_card_ids = excluded.cover_card_ids,
           cards_json = excluded.cards_json,
           updated_at = excluded.updated_at`,
      ).run(id, me.id, localId, name, JSON.stringify(coverCardIds), JSON.stringify(cards), t);
      return json(res, 200, { id, name, updatedAt: t });
    }

    const delDeck = /^\/v1\/me\/decks\/(\d+)$/.exec(pathname);
    if (req.method === 'DELETE' && delDeck) {
      const localId = Number(delDeck[1]);
      db.prepare('DELETE FROM public_decks WHERE owner_id = ? AND local_deck_id = ?').run(
        me.id,
        localId,
      );
      return json(res, 200, { ok: true });
    }

    const ownerDeck = /^\/v1\/u\/([^/]+)\/d\/(\d+)$/.exec(pathname);
    if (req.method === 'GET' && ownerDeck) {
      const ownerId = decodeURIComponent(ownerDeck[1]);
      const localId = Number(ownerDeck[2]);
      const deck = db
        .prepare('SELECT * FROM public_decks WHERE owner_id = ? AND local_deck_id = ?')
        .get(ownerId, localId);
      if (!deck) return json(res, 404, { errorKey: 'social.deck_not_found' });
      return json(res, 200, { deck: deckPayload(deck) });
    }

    const deckMatch = /^\/v1\/decks\/([^/]+)$/.exec(pathname);
    if (req.method === 'GET' && deckMatch) {
      const deck = findPublicDeck(decodeURIComponent(deckMatch[1]));
      if (!deck) return json(res, 404, { errorKey: 'social.deck_not_found' });
      return json(res, 200, { deck: deckPayload(deck) });
    }

    const deckMsg = /^\/v1\/decks\/([^/]+)\/messages$/.exec(pathname);
    if (deckMsg) {
      const deckIdRaw = decodeURIComponent(deckMsg[1]);
      const deckRow = findPublicDeck(deckIdRaw);
      if (!deckRow) return json(res, 404, { errorKey: 'social.deck_not_found' });
      const deckId = deckRow.id;
      if (req.method === 'GET') {
        const lang = String(searchParams.get('lang') || '').trim().toLowerCase();
        const rows = lang
          ? db
              .prepare(
                `SELECT m.*, u.username FROM deck_messages m
                 JOIN users u ON u.id = m.user_id
                 WHERE m.deck_id = ? AND m.lang = ?
                 ORDER BY m.created_at ASC LIMIT 200`,
              )
              .all(deckId, lang)
          : db
              .prepare(
                `SELECT m.*, u.username FROM deck_messages m
                 JOIN users u ON u.id = m.user_id
                 WHERE m.deck_id = ?
                 ORDER BY m.created_at ASC LIMIT 200`,
              )
              .all(deckId);
        return json(res, 200, {
          messages: rows.map((m) => ({
            id: m.id,
            userId: m.user_id,
            username: m.username,
            body: m.body,
            lang: m.lang,
            createdAt: m.created_at,
          })),
        });
      }
      if (req.method === 'POST') {
        const body = await readBody(req);
        const text = String(body.body || '').trim().slice(0, 500);
        const lang = String(body.lang || 'en').trim().toLowerCase().slice(0, 8) || 'en';
        if (text.length < 1) return json(res, 400, { errorKey: 'social.message_empty' });
        const info = db
          .prepare(
            `INSERT INTO deck_messages (deck_id, user_id, body, lang, created_at)
             VALUES (?, ?, ?, ?, ?)`,
          )
          .run(deckId, me.id, text, lang, now());
        return json(res, 201, {
          message: {
            id: Number(info.lastInsertRowid),
            userId: me.id,
            username: me.username,
            body: text,
            lang,
            createdAt: now(),
          },
        });
      }
    }

    const dmMatch = /^\/v1\/dm\/([^/]+)\/messages$/.exec(pathname);
    if (dmMatch) {
      const peer = findUser(decodeURIComponent(dmMatch[1]));
      if (!peer) return json(res, 404, { errorKey: 'social.user_not_found' });
      if (!areFriends(me.id, peer.id)) {
        return json(res, 403, { errorKey: 'social.dm_friends_only' });
      }
      const key = threadKey(me.id, peer.id);
      if (req.method === 'GET') {
        const rows = db
          .prepare(
            `SELECT * FROM dm_messages WHERE thread_key = ?
             ORDER BY created_at ASC LIMIT 200`,
          )
          .all(key);
        return json(res, 200, {
          messages: rows.map((m) => ({
            id: m.id,
            fromUserId: m.from_user_id,
            toUserId: m.to_user_id,
            body: m.body,
            createdAt: m.created_at,
          })),
        });
      }
      if (req.method === 'POST') {
        const body = await readBody(req);
        const text = String(body.body || '').trim().slice(0, 500);
        if (text.length < 1) return json(res, 400, { errorKey: 'social.message_empty' });
        const t = now();
        const info = db
          .prepare(
            `INSERT INTO dm_messages (thread_key, from_user_id, to_user_id, body, created_at)
             VALUES (?, ?, ?, ?, ?)`,
          )
          .run(key, me.id, peer.id, text, t);
        return json(res, 201, {
          message: {
            id: Number(info.lastInsertRowid),
            fromUserId: me.id,
            toUserId: peer.id,
            body: text,
            createdAt: t,
          },
        });
      }
    }

    return json(res, 404, { errorKey: 'social.not_found' });
  } catch (err) {
    if (err?.message === 'invalid_json') {
      return json(res, 400, { errorKey: 'social.bad_json' });
    }
    console.error(err);
    return json(res, 500, { errorKey: 'social.server_error' });
  }
}

createServer(handler).listen(PORT, '0.0.0.0', () => {
  console.log(`YGOChecker social API on http://0.0.0.0:${PORT}`);
  console.log(`SQLite: ${DB_PATH}`);
});

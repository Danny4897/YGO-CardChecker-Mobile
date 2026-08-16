/**
 * Cloudflare Quick Tunnel → local social API.
 * Downloads cloudflared on first run if missing (Windows/mac/linux).
 */
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync, chmodSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { platform, arch } from 'node:os';
import { createWriteStream } from 'node:fs';
import { pipeline } from 'node:stream/promises';
import { Readable } from 'node:stream';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, '..');
const binDir = join(root, 'data', 'bin');
const urlFile = join(root, 'data', 'tunnel-url.txt');
const PORT = process.env.PORT || '8787';
const target = `http://127.0.0.1:${PORT}`;

function assetName() {
  const p = platform();
  const a = arch();
  if (p === 'win32') return a === 'arm64' ? 'cloudflared-windows-arm64.exe' : 'cloudflared-windows-amd64.exe';
  if (p === 'darwin') return a === 'arm64' ? 'cloudflared-darwin-arm64.tgz' : 'cloudflared-darwin-amd64.tgz';
  return a === 'arm64' ? 'cloudflared-linux-arm64' : 'cloudflared-linux-amd64';
}

function localBinary() {
  const p = platform();
  return join(binDir, p === 'win32' ? 'cloudflared.exe' : 'cloudflared');
}

async function ensureCloudflared() {
  const bin = localBinary();
  if (existsSync(bin)) return bin;
  mkdirSync(binDir, { recursive: true });
  const name = assetName();
  const url = `https://github.com/cloudflare/cloudflared/releases/latest/download/${name}`;
  console.log(`Downloading cloudflared…\n${url}`);
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Download failed: ${res.status}`);
  const dest = join(binDir, name);
  await pipeline(Readable.fromWeb(res.body), createWriteStream(dest));
  if (name.endsWith('.tgz')) {
    const { execSync } = await import('node:child_process');
    execSync(`tar -xzf "${dest}" -C "${binDir}"`, { stdio: 'inherit' });
    const extracted = join(binDir, 'cloudflared');
    if (existsSync(extracted)) {
      chmodSync(extracted, 0o755);
      return extracted;
    }
  }
  if (platform() !== 'win32') chmodSync(dest, 0o755);
  if (dest !== bin) {
    const { copyFileSync } = await import('node:fs');
    copyFileSync(dest, bin);
    if (platform() !== 'win32') chmodSync(bin, 0o755);
  }
  return bin;
}

const bin = await ensureCloudflared();
mkdirSync(dirname(urlFile), { recursive: true });

const child = spawn(bin, ['tunnel', '--url', target], {
  stdio: ['ignore', 'pipe', 'pipe'],
});

function maybeCapture(line) {
  const m = line.match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/i);
  if (m) {
    writeFileSync(urlFile, m[0] + '\n', 'utf8');
    console.log(`\nPublic URL: ${m[0]}`);
    console.log(`Saved to ${urlFile}`);
    console.log(`Set in android/local.properties:\nSOCIAL_API_URL=${m[0]}\n`);
  }
}

child.stdout.on('data', (buf) => {
  const s = buf.toString();
  process.stdout.write(s);
  maybeCapture(s);
});
child.stderr.on('data', (buf) => {
  const s = buf.toString();
  process.stderr.write(s);
  maybeCapture(s);
});

child.on('exit', (code) => process.exit(code ?? 1));

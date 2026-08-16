import { spawn } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const node = process.execPath;

const api = spawn(node, ['--experimental-sqlite', join(root, 'server.mjs')], {
  cwd: root,
  stdio: 'inherit',
  env: process.env,
});

setTimeout(() => {
  const tunnel = spawn(node, [join(root, 'scripts', 'tunnel.mjs')], {
    cwd: root,
    stdio: 'inherit',
    env: process.env,
  });
  const shutdown = () => {
    api.kill();
    tunnel.kill();
    process.exit(0);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
  tunnel.on('exit', (code) => {
    api.kill();
    process.exit(code ?? 1);
  });
}, 800);

api.on('exit', (code) => process.exit(code ?? 1));

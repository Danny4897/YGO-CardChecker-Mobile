#!/usr/bin/env node
/** Adapts Cursor beforeShellExecution JSON → harness block-no-verify hook */
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));
const harnessHook = path.resolve(dir, '../../.harness/hooks/block-no-verify.js');

let raw = '';
try {
  raw = readFileSync(0, 'utf-8');
} catch {
  process.exit(0);
}

if (!raw.trim()) process.exit(0);

let input;
try {
  input = JSON.parse(raw);
} catch {
  process.exit(0);
}

const command = input.command ?? input.tool_input?.command ?? '';
const adapted = JSON.stringify({ tool_input: { command } });
const result = spawnSync('node', [harnessHook], { input: adapted, encoding: 'utf-8' });
if (result.stderr) process.stderr.write(result.stderr);
process.exit(result.status ?? 0);

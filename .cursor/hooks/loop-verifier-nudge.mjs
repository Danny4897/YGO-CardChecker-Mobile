#!/usr/bin/env node
/** Loop engineering: nudge verifier after implementer subagent completes */
import { readFileSync, writeFileSync } from 'node:fs';

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

const type = (input.subagent_type ?? input.agent_type ?? '').toLowerCase();
if (type.includes('verif')) process.exit(0);

const msg =
  'Loop step: invoke `verifier` subagent. Run harness validate + project tests before marking done. Update PROGRESS.md.';

process.stdout.write(JSON.stringify({ followup_message: msg }));
process.exit(0);

#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const directory = path.join(root, '.tools/thrifty-preview');
const manifest = require('../preview/manifest.json');
const patch = path.join(root, 'preview', manifest.patch);
const git = (args, cwd = directory) => execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();

try {
  git(['check-ignore', '.tools/thrifty-preview'], root);
  fs.accessSync(patch);
  if (!fs.existsSync(directory)) {
    fs.mkdirSync(path.dirname(directory), { recursive: true });
    git(['clone', '--filter=blob:none', '--no-checkout', '--single-branch', '--branch', 'develop', manifest.repository, directory], root);
    git(['fetch', 'origin', manifest.baseCommit]);
    git(['switch', '--create', manifest.branch, manifest.baseCommit]);
  }
  if (git(['rev-parse', 'HEAD']) !== manifest.baseCommit) {
    throw new Error('Preview HEAD differs from the pinned base. Preserve your work; use a separate folder instead of reset.');
  }
  if (git(['remote', 'get-url', 'origin']) !== manifest.repository) throw new Error('Unexpected preview origin.');
  git(['remote', 'set-url', '--push', 'origin', 'disabled://local-preview-read-only']);
  try {
    git(['apply', '--reverse', '--check', patch]);
    console.log('Preview patch is already present. Existing edits preserved.');
  } catch {
    if (git(['status', '--porcelain'])) throw new Error('Preview has local edits. Refusing to overwrite them.');
    git(['apply', '--check', patch]);
    git(['apply', patch]);
    console.log('Thrifty preview applied to the pinned develop snapshot.');
  }
  console.log('Next: npm --prefix .tools/thrifty-preview/frontend ci');
  console.log('Copy your local public build configuration to the ignored preview frontend/.env.local, then npm run dev:preview.');
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}

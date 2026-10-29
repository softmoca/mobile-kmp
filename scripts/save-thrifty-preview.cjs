#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const directory = path.join(root, '.tools/thrifty-preview');
const manifest = require('../preview/manifest.json');
const git = (args) => execFileSync('git', args, { cwd: directory, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
try {
  git(['merge-base', '--is-ancestor', manifest.baseCommit, 'HEAD']);
  git(['add', '--intent-to-add', '--', 'frontend/src', 'frontend/test', 'frontend/docs', 'frontend/README.md']);
  const patch = git(['diff', '--binary', manifest.baseCommit, '--', 'frontend/src', 'frontend/test', 'frontend/docs', 'frontend/README.md']);
  fs.writeFileSync(path.join(root, 'preview', manifest.patch), patch);
  console.log(`Saved committed and working frontend changes to preview/${manifest.patch}. No commit or push performed.`);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}

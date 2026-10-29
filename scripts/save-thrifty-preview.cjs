#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const directory = path.join(root, '.tools/thrifty-preview');
const manifest = require('../preview/manifest.json');
const git = (args) => execFileSync('git', args, { cwd: directory, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
try {
  if (git(['rev-parse', 'HEAD']).trim() !== manifest.baseCommit) throw new Error('Preview base changed; update the manifest deliberately.');
  git(['add', '--intent-to-add', '--', 'frontend/src', 'frontend/test']);
  const patch = git(['diff', '--binary', 'HEAD', '--', 'frontend/src', 'frontend/test']);
  fs.writeFileSync(path.join(root, 'preview', manifest.patch), patch);
  console.log('Saved frontend source/test changes to preview/thrifty.patch. No commit or push performed.');
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}

#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const frontend = path.join(root, '.tools/thrifty-preview/frontend');
const manifest = require('../preview/manifest.json');
try {
  execFileSync('git', ['merge-base', '--is-ancestor', manifest.baseCommit, 'HEAD'], { cwd: frontend });
  if (!fs.existsSync(path.join(frontend, manifest.entry || 'src/pages/map/MapThriftyPage.tsx'))) {
    throw new Error('Run npm run preview:prepare before starting the preview.');
  }
  process.env.JACHWI_FRONTEND_PATH = frontend;
  process.env.MAP_PROVIDER_MODE = 'naver';
  process.env.ENABLE_MSW = 'false';
  process.env.POSTHOG_PROJECT_TOKEN = '';
  process.env.POSTHOG_HOST = '';
  // This mode never uses the team's demo map or MSW fixtures as a fallback.
  process.env.JACHWI_REQUIRE_NAVER = 'true';
  require('./local-web.cjs');
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}

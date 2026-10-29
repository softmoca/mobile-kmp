#!/usr/bin/env node
// Read the team's source/config; keep every generated file inside this repository.
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');

const root = path.resolve(__dirname, '..');
const frontend = path.resolve(root, process.env.JACHWI_FRONTEND_PATH || '../frontend');
const output = path.join(root, '.tools/local-web');
const rawOrigin = process.env.JACHWI_LOCAL_ORIGIN;

function localOrigin(value) {
  const url = new URL(value);
  const parts = url.hostname.split('.');
  const ip = parts.length === 4 && parts.every((part) => /^\d{1,3}$/.test(part) && Number(part) <= 255);
  const privateIP = ip && (parts[0] === '10' || (parts[0] === '172' && +parts[1] >= 16 && +parts[1] <= 31)
    || (parts[0] === '192' && parts[1] === '168') || url.hostname === '127.0.0.1');
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash
    || url.pathname !== '/' || !(privateIP || url.hostname === 'localhost' || url.hostname.endsWith('.local'))) {
    throw new Error('JACHWI_LOCAL_ORIGIN must be an HTTPS LAN IP, localhost, or .local origin.');
  }
  return url;
}

async function main() {
  if (!rawOrigin) throw new Error('Set JACHWI_LOCAL_ORIGIN=https://<Mac-LAN-IP>:3000 first.');
  const origin = localOrigin(rawOrigin);
  const certPath = path.resolve(root, process.env.JACHWI_TLS_CERT || '.tools/certs/local.pem');
  const keyPath = path.resolve(root, process.env.JACHWI_TLS_KEY || '.tools/certs/local-key.pem');
  // Fail before starting a listener; certificates must be trusted by the device, never bypass TLS.
  const cert = fs.readFileSync(certPath);
  const key = fs.readFileSync(keyPath);
  const frontendRequire = createRequire(path.join(frontend, 'package.json'));
  // Babel presets and the team's HTML template resolve against the frontend source tree.
  process.chdir(frontend);
  const webpack = frontendRequire('webpack');
  const WebpackDevServer = frontendRequire('webpack-dev-server');
  process.env.ENABLE_MSW = 'false';
  process.env.DEV_API_PROXY_TARGET = 'https://dev-api.jachwi-sunbae.kr';
  process.env.PORT = origin.port || '443';
  const createConfig = frontendRequire('./webpack/webpack.config.cjs');
  const config = createConfig({}, { mode: 'development' });
  const definitions = config.plugins.find((plugin) => plugin instanceof webpack.DefinePlugin
    && Object.hasOwn(plugin.definitions, '__API_BASE_URL__'));
  if (!definitions) throw new Error('Team webpack API configuration changed; review this adapter.');
  // The upstream builder hardcodes http://localhost when proxying. Only override the local build.
  definitions.definitions.__API_BASE_URL__ = JSON.stringify(origin.origin);
  config.context = frontend;
  config.output.path = path.join(output, 'dist');
  config.cache.cacheDirectory = path.join(output, 'cache');
  config.resolveLoader = { modules: [path.join(frontend, 'node_modules')] };
  config.devServer = {
    ...config.devServer,
    host: origin.hostname,
    port: Number(origin.port || 443),
    allowedHosts: [origin.hostname],
    server: { type: 'https', options: { key, cert } },
    client: { ...config.devServer.client, webSocketURL: {
      protocol: 'wss', hostname: origin.hostname, port: origin.port || '443', pathname: '/ws',
    } },
  };
  // Use the existing /api proxy, keeping Secure, HttpOnly, SameSite and certificate checks intact.
  if (config.devServer.proxy?.length !== 1
    || config.devServer.proxy[0].target !== 'https://dev-api.jachwi-sunbae.kr') {
    throw new Error('Expected exactly the development API proxy. Refusing to start.');
  }
  if (process.argv.includes('--check')) {
    console.log('HTTPS origin, dev API proxy, WSS HMR and mobile-only build paths validated.');
    return;
  }
  fs.mkdirSync(output, { recursive: true });
  const compiler = webpack(config);
  const server = new WebpackDevServer(config.devServer, compiler);
  await server.start();
  console.log(`iPhone Debug JACHWI_WEB_URL=${origin.origin}`);
  console.log('Trust the development CA on your test device. No TLS/ATS bypass is enabled.');
}

main().catch((error) => { console.error(error.message); process.exitCode = 1; });

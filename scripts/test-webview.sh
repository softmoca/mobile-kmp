#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .tools/webview-tests
xcrun swiftc -D DEBUG iosApp/iosApp/WebViewConfiguration.swift tests/WebViewConfigurationTests.swift -o .tools/webview-tests/debug
.tools/webview-tests/debug
xcrun swiftc iosApp/iosApp/WebViewConfiguration.swift tests/WebViewConfigurationTests.swift -o .tools/webview-tests/release
.tools/webview-tests/release
node --check scripts/local-web.cjs

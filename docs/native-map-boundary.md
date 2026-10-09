# Android Web map boundary

This host uses the shared Web map; it does not add a native map SDK.

## Enabled mode

`androidx.webkit:webkit:1.14.0` supplies `WEB_MESSAGE_LISTENER` and
`DOCUMENT_START_SCRIPT`. Both must be supported by the installed WebView.
Installation removes the legacy all-frame `JSAndroid` interface and registers an
origin-filtered authorization listener. Every authorization request must come
from the exact `http://127.0.0.1:6806` origin and the main frame, as reported by
WebView. The current document must also be one of the host's exact application
entry paths. `WebView.getUrl()` is never used to infer the calling frame.

A document-start script creates `JSAndroid` only in a trusted main document. Its
origin-aware `hello` obtains a fresh random document capability. That capability
stays in a lexical closure: it is not a global property, DOM source, log entry, or
the result of `JSAndroid.ready`. The all-frame `SiYuanNativeSync` object requires
this secret on **every** synchronous call. It cannot issue or renew a capability.
This is capability-based isolation; the synchronous Java interface itself does
not report the calling frame. An opaque sandboxed map cannot obtain the secret.
Trusted parent scripts can use native functionality, as before.

Native dispatch uses an explicit method allowlist and typed arguments, never
reflection. Existing methods retain their synchronous return values, including
word-selection preparation that must precede the native context menu. The
frontend must await `window.JSAndroid.ready` before app initialization or the auth
page's first synchronous bridge read. That Promise resolves to `undefined`, not
the secret. Early void calls queue; early returning calls fail explicitly. The
boot document currently has no bridge calls. Packaging this host with an older
frontend that does not await readiness is unsupported: startup reads may race.

`window.getAVMapNativeBoundary()` (also on `JSAndroid`) returns a Promise resolving
to `{version:1, enabled:true}` only after successful authorization and a current
native capability check. The frontend must await it, and treat absence, timeout,
or rejection as disabled. Neither bridge-name presence nor the app user agent is
a capability.

Navigation changes the native generation and session. Pending old responses
cannot enter a new document. Page hiding, explicit disposal and request timeouts
settle JavaScript requests; a BFCache restore reloads to obtain a fresh session.
Renderer recovery installs a fresh boundary on the replacement WebView.

Subframe navigation is never promoted with `view.loadUrl`. Navigations to the
kernel's privileged app entry documents (including decoded/normalized aliases)
are denied in child frames. Map bootstrap documents never gain native bridge
authority. Their exact paths, provider parameters and allowed child navigation
are validated by the shared map host and its server-delivered CSP. Ordinary HTML assets,
widgets, and user-authored HTTP(S) iframe links, including local services, retain
in-frame navigation. Non-Web external schemes are denied. Main navigation stays
inside exact host entry points; ordinary external HTTP(S) links open externally.
This is not a network firewall or cookie partition. The shared map must still use
its opaque sandbox, narrow message protocol, CSP and credentialless/network
policy. Application scripts in the trusted parent remain trusted.

## Old WebViews

If either AndroidX feature is unavailable (or installation fails), the existing
synchronous Java bridge remains available to preserve ordinary app functionality.
No map-boundary capability is installed. This legacy mode does **not** claim
all-frame bridge isolation; shared map code must not create its map iframe there.

## Lightweight checks

From the repository root (a JDK and Node are sufficient):

```sh
mkdir -p /tmp/siyuan-android-boundary-classes
java com.sun.tools.javac.Main -d /tmp/siyuan-android-boundary-classes \
  app/src/main/java/org/b3log/siyuan/NativeBridgePolicy.java \
  app/src/test/java/org/b3log/siyuan/NativeBridgePolicyTest.java
java -cp /tmp/siyuan-android-boundary-classes org.b3log.siyuan.NativeBridgePolicyTest
node app/src/test/js/native-bridge.test.cjs
git diff --check
```

These tests cover URL policy and a mocked document-start facade, including child
frames, spoofed origins, synchronous returns, hidden capabilities, errors,
timeout, navigation, BFCache, recovery and disposal. A native callback/dispatch
mock test can also run with an `org.json` JAR, plus an optional official AndroidX
WebKit `classes.jar` extracted from its AAR for an API-signature compile:

```sh
python app/src/test/native-bridge-mock.py /path/to/json.jar /path/to/webkit.jar
```

The native mock exercises main-frame and origin authorization, guessed/old
capabilities, ordinary synchronous methods, exact argument validation, navigation,
old-engine fallback, partial-install failure and destruction. They do not substitute for an Android build or WebView
instrumentation. Device verification must cover actual iframe redirects and
origin/main-frame reporting; clipboard, notification and editor integration;
legacy engines; renderer recovery; and real provider keys. The native
context-menu word-selection handshake remains synchronous; its real UI behavior
still requires device regression coverage.

## API references

- [Origin-aware WebMessageListener and document-start scripts](https://developer.android.com/reference/androidx/webkit/WebViewCompat)
- [AndroidX WebKit releases](https://developer.android.com/jetpack/androidx/releases/webkit)

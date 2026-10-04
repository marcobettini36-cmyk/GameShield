# GameShield 0.3.0: forwarding and connectivity diagnostics

## Findings in the actual transport

The 0.2.0 Java relay closed both sockets as soon as its download pump reached EOF. TCP permits a peer to half-close its sending direction while still receiving an upload. With the previous relay, a controlled 64 KiB upload after the server's FIN arrived as **zero bytes**. The same regression passes with the corrected relay. The relay also waited for client bytes before starting its download pump on ports 443/853: server-first traffic timed out. Both pumps now start independently, propagate directional FIN, and retain the other direction until it finishes.

Initial HTTP/TLS inspection previously used readFully and discarded bytes already consumed when a timeout occurred. The corrected bounded classifier preserves captured bytes and forwards them unchanged when the name is undecidable. A known blocked HTTP Host or visible TLS SNI stops the connection; an allowed name is forwarded byte-for-byte. There is no TLS interception, custom CA or application TLS decryption. ECH, incomplete/fragmented ClientHello records and invisible names remain filtering limitations, not reasons to drop an unknown legitimate connection.

The native HEV session destructor unconditionally called lwIP tcp_abort(), including after completed transfers. That emits RST. A versioned downstream patch now uses tcp_close() for fully drained bidirectional EOF; abort remains for failed/incomplete sessions or a failed close. EOF flags are captured while the native ring buffer is still alive; the destructor never accesses that stack allocation. Gradle applies and checks the patch against the pinned native checkout on every platform. Upstream reference: [lwIP TCP API](https://www.nongnu.org/lwip/2_1_x/group__tcp__raw.html).

UDP previously silently dropped the ninth destination on an association. A regression using 16 distinct destinations reproduces that failure on 0.2.0 and passes after the change. Routes now have a bounded LRU cache of 64 entries; closed entries are removed. QUIC/443 and remote 853 remain permitted. DNS requests have their own bounded executor and preserve UDP/TCP framing.

Outbound TCP socket creation still follows **bind ephemeral fd → VpnService.protect() → bind physical Network → connect**. That fd creation fix was verified in 0.2.0 and is retained. Datagram sockets are protected before upstream traffic. Physical-network selection now retains a usable selected network while Android's active network is the VPN, instead of switching unpredictably between validated Wi-Fi/mobile candidates. A physical default change or disappearance can still select a replacement. Both IPv4 and IPv6 destination addresses and TUN routes are retained; absence of upstream IPv6 connectivity must not prevent the probes from trying IPv4.

## Self-test policy

The probe separately reports DNS, plain TCP 443, and certificate-verified HTTPS for google.it, youtube.com, wikipedia.org and github.com. DNS failure records the reason and explicitly marks dependent stages unexecuted. TCP failure does not suppress a separate HTTPS attempt. HTTPS retries bounded addresses and accepts HTTP error statuses as proof of TLS/HTTP transport. The check uses VPN-bound sockets and a bounded raw DNS exchange; it does not use protected upstream sockets as a false positive.

Normal keeps the VPN after a single failure, an incomplete diagnostic, a partial host outage, an offline physical network or a network transition. Automatic fail-open requires **three consecutive checks with no successful allowed HTTPS through the VPN while HTTPS on the unchanged physical network works**. Each failure count and per-stage result is displayed. A stopped native engine remains an immediate fail-open condition. Strong retains its TUN and reports the fault. The diagnostic does not fix a transport failure by hiding it behind immediate teardown.

## Temporary diagnostics

Debug APKs log GameShieldTransport with domain, destination IP/port, protocol, ALLOW/BLOCK, protect() result, physical binding and IOException/SocketException/reset/DNS details. Verbose domain/transport logging expires 15 minutes after transport creation and is absent in release builds. Lifecycle failures and GameShieldConnectivity reports are logged separately. Payloads, custodian codes and SOCKS credentials are never logged. Android logcat is a diagnostic log, not a persistent browsing-history feature.

## Verification and limits

The Android suite uses the real VpnService, HEV JNI/TUN, local SOCKS relay and protected outbound sockets, not a mocked data path. A controlled physical TCP peer verifies FIN, complete 64 KiB downloads and acknowledged half-close uploads. Concurrent HTTPS runs for at least three minutes with the same VPN Network still active, spanning multiple self-tests. The suite also tests cached-IP gambling TLS, gambling NXDOMAIN, a manually stored example.com rule through RELOAD, Google Play HTTPS endpoints, Google Play launch/screenshots, Chromium WebView, clean stop and sustained broken-relay policy. Both editions run with Private DNS Off/Automatic on the Android 15 Google Play emulator.

Build and runtime results must be read from the final workflow's validation-reports and native-tunnel-device-reports artifacts. An APK build alone does not establish stable forwarding. The Samsung S23 Ultra is not attached; OEM handovers, Samsung Internet, authenticated Play Store downloads, Chrome Secure DNS and a genuinely IPv6-only upstream need physical-device acceptance. The original S23 log was not supplied, so these reproduced defects establish concrete transport failure mechanisms, not an invented diagnosis of its exact failing connection.

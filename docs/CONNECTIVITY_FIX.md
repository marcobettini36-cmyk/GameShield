# Connectivity regression — 0.2.0

## Confirmed defects in 0.1.0

`ProtectedNetwork.denied()` denied every UDP/443 packet and every connection on port 853, without considering the destination. `bypass.txt` was merged into the gambling feed, so DNS providers such as dns.google were denied through DNS and TLS SNI. Browser Secure DNS in strict mode cannot fall back reliably, and QUIC-only applications cannot fall back to TCP at all. These are provider/protocol bans, not gambling filtering.

The upstream DNS path used only 1.1.1.1/9.9.9.9 over UDP, retrying TCP only for a truncated reply. Networks restricting external UDP/53 could therefore make all allowed name resolutions fail. DNS handling synchronously occupied the UDP relay while awaiting upstream replies. Tunnel health only checked a native thread flag: an alive but non-forwarding tunnel retained both default routes indefinitely. These defects explain mechanisms for total loss of connectivity; the original user's phone/network logs were not available to isolate which mechanism triggered their incident.

## Changes

- No blanket bans on UDP/443 or TCP/UDP 853 in either edition. DNS providers are excluded from feed generation and removed from cached 0.1.0 rules on load; explicit user-added rules still apply.
- Allowed QUIC packets are forwarded. Known gambling ClientHello SNI in complete QUIC v1/v2 Initial packets is filtered with RFC 9001/9369 public Initial keys. Application TLS traffic is not decrypted. Unsupported versions, ECH and cross-datagram fragmentation remain visibility limits.
- TLS/HTTP filtering still blocks gambling when an IP has been cached or an encrypted resolver was used. Plain non-TLS traffic on port 443 is forwarded without interpreting arbitrary bytes as TLS record lengths.
- Protected outgoing sockets are bound to an actual non-VPN Android network. DNS uses that network's advertised resolvers first, then public fallbacks; UDP errors/timeouts also trigger TCP retries. The UDP relay uses a separate bounded DNS executor.
- VPN-bound connectivity self-test checks Android DNS, plain TCP connect to port 443, certificate-validated TLS and an HTTP response for google.com, wikipedia.org and github.com. It runs after startup and each minute, with a 30-second deadline; results appear in the dashboard.
- Normal releases the TUN before native shutdown on failure, marks the filter inactive, clears restart intent and uses START_NOT_STICKY. Its manifest disables Android always-on support. Strong keeps TUN/lockdown and reports a failure; a successful self-test is required before applying Device Owner restrictions.

## Android integration tests

`TunnelDeviceTest` runs on Android with the actual HEV JNI engine, TUN, LocalProxy and ProtectedNetwork. It checks allowed DNS/TCP/HTTPS, NXDOMAIN for Playzilla, Excitewin, bet365.it, stake.com and a new subdomain, cached-IP TLS filtering, DNS-provider HTTPS, Chromium WebView browsing and deliberate relay failure. Normal must release VPN routes and restore Google HTTPS; Strong must retain the TUN. GitHub Actions runs all tests for both editions with Private DNS Off and Automatic (opportunistic), saving instrumentation and logcat reports.

Chrome and Samsung Internet are separate products and are not installed in the AOSP test image. WebView exercises Chromium on Android but is not a claim that those browsers have been tested. Strict Android Private DNS, custom browser DoH + ECH, OEM network transitions and Device Owner policy enforcement still need device-specific acceptance tests. Normal cannot override an externally imposed Android lockdown policy; it no longer advertises support for that mode.

See the workflow's `native-tunnel-device-reports` artifact for the actual test result. A build alone does not establish that these device tests passed.

Protocol references: [RFC 9001](https://www.rfc-editor.org/rfc/rfc9001.html), [RFC 9369](https://www.rfc-editor.org/rfc/rfc9369.html), [Android VPN](https://developer.android.com/develop/connectivity/vpn).

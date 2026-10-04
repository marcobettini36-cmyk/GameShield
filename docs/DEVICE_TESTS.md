# Device acceptance checklist

These checks require a real/emulated Android device and are not implied by a successful build.

1. Install Normal; deny VPN consent: no tunnel should start. Grant consent: the foreground notification must appear and Internet must continue working.
2. With mobile/Wi-Fi network, resolve `bet365.it`, `stake.com` and a nested subdomain of a blocked base: expect NXDOMAIN. Resolve `example.org`: expect an address. Verify HTTPS to an allowed site, HTTP, downloads, IPv4 and IPv6.
3. Resolve a controlled allowed name with a CNAME to a blocked domain: expect NXDOMAIN. Try DNS UDP/TCP to a resolver other than the VPN DNS: the VPN should still apply rules. Repeat allowed browsing with Android Private DNS Off, Automatic and strict dns.google. Port 853 and known DoH providers are allowed; encrypted DNS can reduce DNS visibility, while visible gambling TLS/QUIC SNI remains filtered. Repeat using Chrome and Samsung Internet with Secure DNS Off/Automatic/strict, and record ECH limitations.
4. Add a controlled domain from the app; verify DNS and HTTPS SNI blocking with cached/direct DNS records. Verify lookalikes such as `notcasino.com` are not blocked solely by substring.
5. Simulate an unavailable/invalid feed and keep the bundled/last valid rules; update warning should appear. Restore feed and verify last-update date and rule count. Large lists must not block the UI.
6. Switch Wi-Fi/mobile, airplane mode and reconnect; test TCP half-close, UDP sessions, timeouts, sustained browsing and concurrent DNS. The VPN must remain usable and bounded in memory/threads.
7. Reboot, force-stop Normal, revoke VPN consent and start another VPN; dashboard must show true protection state. Configure always-on manually and verify platform restart behavior.
8. Fresh dedicated Android 11+ device: provision Strong as Device Owner; set custodian code; verify allowed browsing before applying restrictions.
9. Apply Strong; verify uninstall blocked, app-data clearing/force-stop unavailable, VPN setting changes blocked, always-on+lockdown, USB debug/safe-boot/new-user restrictions. Stop or crash VPN during a test: network must fail closed. Reboot: tunnel must reconnect.
10. Wrong custodian codes must fail; after five failures retry delay persists across app restart. Offline correct-code release must restore settings, stop VPN and remove Device Owner, allowing uninstall. Repeat release on each supported OEM.
11. Explicitly test known limitations: custom DoH using unknown host or IP, ECH, TLS fragmentation, application tunnels and direct IP services. Record any bypass; do not claim total gambling prevention.
12. Dashboard self-test must show successful DNS, TCP 443 and HTTPS for google.com, wikipedia.org and github.com. Block Playzilla/Excitewin over DNS and visible TLS SNI using cached IPs. Allow UDP/443 for legitimate sites without assuming QUIC-to-TCP fallback. Induce relay failure: Normal must close VPN routes within the self-test deadline, mark protection inactive and restore unfiltered Internet; Strong must keep TUN/lockdown and show the diagnostic. Normal must not offer Android always-on/lockdown.

Use a stable signing key for deployment. Device Owner on the only personal device should wait until this checklist has passed on a spare device, with a working offline release path.

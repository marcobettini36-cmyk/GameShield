#!/usr/bin/env python3
"""Dedicated Android emulator only. Exercise real TUN with Private DNS Off/Automatic."""
import pathlib, subprocess, sys, time, xml.etree.ElementTree as ET
from vpn_diagnostics import has_vpn_agent
apks = pathlib.Path(sys.argv[1])
reports = pathlib.Path('device-reports'); reports.mkdir(exist_ok=True)
peer = subprocess.Popen([sys.executable, 'scripts/transport_peer.py'])
import atexit
atexit.register(peer.terminate)
def adb(*args, timeout=60):
    p = subprocess.run(['adb', *args], capture_output=True, text=True, timeout=timeout)
    if p.returncode: raise RuntimeError(p.stdout + p.stderr)
    return p.stdout
adb('shell', 'settings', 'delete', 'global', 'private_dns_specifier')
editions = (sys.argv[2],) if len(sys.argv) > 2 else ('normal', 'strong')
modes = (sys.argv[3],) if len(sys.argv) > 3 else ('off', 'opportunistic')
boot_only = '--boot-only' in sys.argv[4:]
if boot_only:
    if modes != ('off',): raise ValueError('Targeted reboot check requires Private DNS off')
    adb('shell', 'settings', 'put', 'global', 'private_dns_mode', 'off')
for edition in editions:
    package = 'it.gameshield' + ('.strong' if edition == 'strong' else '')
    for name in (f'app-{edition}-debug.apk', f'app-{edition}-debug-androidTest.apk'):
        matches = list(apks.rglob(name))
        if len(matches) != 1: raise RuntimeError(f'Expected one {name}, got {matches}')
        adb('install', '-r', str(matches[0]), timeout=120)
    adb('shell', 'appops', 'set', package, 'ACTIVATE_VPN', 'allow')
    if edition == 'normal' and 'off' in modes and not boot_only:
        adb('logcat', '-c')
        preflight = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
            'it.gameshield.TunnelDeviceTest#testNativeTcpFinAndHalfClose',
            package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=240)
        (reports / 'startup-preflight.txt').write_text(preflight)
        (reports / 'startup-preflight-logcat.txt').write_text(adb('logcat', '-d', '-s', 'GameShieldVpn:I', 'GameShieldTransport:I', 'AndroidRuntime:E', 'ActivityManager:I', '*:S'))
        print(preflight, flush=True)
        if 'OK (1 test)' not in preflight: raise RuntimeError('Native TUN startup/FIN preflight failed')
    for mode in (() if boot_only else modes):
        adb('shell', 'settings', 'put', 'global', 'private_dns_mode', mode)
        adb('logcat', '-c')
        adb('logcat', '-c')
        auto_result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
            'it.gameshield.AndroidAutoDeviceTest',
            package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=360)
        (reports / f'{edition}-{mode}-android-auto.txt').write_text(auto_result)
        (reports / f'{edition}-{mode}-android-auto-logcat.txt').write_text(adb('logcat', '-d', '-s', 'AndroidAuto:I', 'AndroidAutoDeviceTest:I', 'GameShieldVpn:I', 'AndroidRuntime:E', '*:S'))
        print(auto_result, flush=True)
        if 'OK (2 tests)' not in auto_result or 'FAILURES' in auto_result:
            raise RuntimeError('Android Auto security/UI regression tests failed')

        # Dedicated emulator: prove PackageManager's genuinely absent-host path,
        # then restore the original official system package before browser tests.
        host = 'com.google.android.projection.gearhead'
        installed = ('package:' + host) in adb('shell', 'pm', 'list', 'packages', '--user', '0', host)
        if installed:
            removed = adb('shell', 'pm', 'uninstall', '--user', '0', host)
            if 'Success' not in removed: raise RuntimeError('Cannot prepare absent Android Auto fixture: ' + removed)
        try:
            adb('logcat', '-c')
            absent = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'expectAbsent', 'true', '-e', 'class',
                'it.gameshield.AndroidAutoDeviceTest#missingOrUntrustedHostAndForgedConnectionRetainFilter',
                package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
            (reports / f'{edition}-{mode}-android-auto-absent.txt').write_text(absent)
            (reports / f'{edition}-{mode}-android-auto-absent-logcat.txt').write_text(adb('logcat', '-d', '-s', 'AndroidAuto:I', 'AndroidAutoDeviceTest:I', 'GameShieldVpn:I', 'AndroidRuntime:E', '*:S'))
            print(absent, flush=True)
            if 'OK (1 test)' not in absent or 'FAILURES' in absent:
                raise RuntimeError('Absent Android Auto regression failed')
        finally:
            if installed: adb('shell', 'cmd', 'package', 'install-existing', '--user', '0', host)

        if edition == 'normal':
            adb('logcat', '-c')
            ui_result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                'it.gameshield.NormalDisableDeviceTest',
                package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=600)
            (reports / f'{edition}-{mode}-disable-ui.txt').write_text(ui_result)
            (reports / f'{edition}-{mode}-disable-ui-logcat.txt').write_text(adb('logcat', '-d', '-s', 'GameShieldVpn:I', 'GameShieldConnectivity:I', 'AndroidRuntime:E', '*:S'))
            print(ui_result, flush=True)
            if 'OK (2 tests)' not in ui_result or 'FAILURES' in ui_result:
                raise RuntimeError('Normal deactivation UI tests failed')

        adb('logcat', '-c')
        result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'it.gameshield.TunnelDeviceTest',
            package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=900)
        (reports / f'{edition}-{mode}.txt').write_text(result)
        (reports / f'{edition}-{mode}-logcat.txt').write_text(adb('logcat', '-d', '-s', 'GameShieldDeviceTest:I', 'GameShieldTransport:I', 'GameShieldConnectivity:I', 'GameShieldVpn:I', 'AndroidRuntime:E', '*:S'))
        (reports / f'{edition}-{mode}-crash.txt').write_text(adb('logcat', '-b', 'crash', '-d'))
        (reports / f'{edition}-{mode}-network.txt').write_text(adb('shell', 'dumpsys', 'connectivity') + adb('shell', 'dumpsys', 'dnsresolver'))
        for extension in ('png', 'xml'):
            subprocess.run(['adb', 'pull', '/sdcard/gameshield-store.' + extension,
                str(reports / f'{edition}-{mode}-store.{extension}')], check=False, capture_output=True)
        subprocess.run(['adb', 'pull', '/sdcard/gameshield-chrome.png',
            str(reports / f'{edition}-{mode}-chrome.png')], check=False, capture_output=True)
        print(f'{edition}/{mode}:\n{result}', flush=True)
        if 'OK (5 tests)' not in result or 'FAILURES' in result: raise RuntimeError('Native tunnel instrumentation failed')

    if 'off' in modes:
        # BootReceiver / always-on acceptance on a disposable emulator, never a user device.
        owner = edition == 'strong'
        owner_provisioned = False
        try:
            if owner:
                # Cold Google Play boot may still be initializing account authenticators.
                # Wait for that setup; never remove accounts or weaken owner requirements.
                provision_deadline = time.monotonic() + 180
                while True:
                    try:
                        provision = adb('shell', 'dpm', 'set-device-owner', package + '/it.gameshield.AdminReceiver')
                        owner_provisioned = True
                        break
                    except RuntimeError as pending:
                        if 'accounts on the device' not in str(pending) or time.monotonic() >= provision_deadline: raise
                        print('Waiting for disposable emulator account setup before owner provisioning', flush=True)
                        time.sleep(10)
                (reports / 'strong-owner-provision.txt').write_text(provision)
                arm_class = 'it.gameshield.AndroidAutoOwnerDeviceTest#lockdownExceptionRemainsNarrowAndArmsReboot'
                arm_args = []
            else:
                arm_class = 'it.gameshield.AndroidAutoDeviceTest#missingOrUntrustedHostAndForgedConnectionRetainFilter'
                arm_args = ['-e', 'leaveRunning', 'true']
            arm = adb('shell', 'am', 'instrument', '-w', '-r', *arm_args, '-e', 'class', arm_class,
                package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=600)
            (reports / f'{edition}-reboot-arm.txt').write_text(arm)
            print(arm, flush=True)
            if 'OK (1 test)' not in arm or 'FAILURES' in arm: raise RuntimeError('Cannot arm protected reboot test')
            before_boot = adb('shell', 'settings', 'get', 'global', 'boot_count').strip()
            adb('reboot'); adb('wait-for-disconnect', timeout=60); adb('wait-for-device', timeout=180)
            deadline = time.monotonic() + 300
            ready_samples = 0
            while time.monotonic() < deadline:
                if adb('shell', 'getprop', 'sys.boot_completed').strip() == '1': break
                time.sleep(2)
            else: raise RuntimeError('Emulator did not finish actual reboot')
            adb('shell', 'input', 'keyevent', '82')
            after_boot = adb('shell', 'settings', 'get', 'global', 'boot_count').strip()
            if before_boot == after_boot: raise RuntimeError('Boot counter did not change')
            deadline = time.monotonic() + 300
            while time.monotonic() < deadline:
                try:
                    text = adb('shell', 'run-as', package, 'cat', 'shared_prefs/shield.xml')
                    root = ET.fromstring(text)
                    flags = {e.get('name'): e.get('value') for e in root.findall('boolean')}
                    nets = adb('shell', 'dumpsys', 'connectivity')
                    # LISTEN requests also contain "Transports: VPN" before any VPN exists.
                    # Require an actual NetworkAgent and stable fresh readiness after startup.
                    vpn_agent = has_vpn_agent(nets)
                    (reports / f'{edition}-reboot-last-network.txt').write_text(nets)
                    ready_samples = ready_samples + 1 if flags.get('wanted') == 'true' and flags.get('connectivityOk') == 'true' and vpn_agent else 0
                    if ready_samples >= 3:
                        (reports / f'{edition}-reboot-network.txt').write_text(nets)
                        (reports / f'{edition}-reboot.txt').write_text(f'PASS: actual reboot {before_boot} -> {after_boot}; VPN present; wanted=true; internal DNS/TCP443/HTTPS self-test complete.\n')
                        print(f'{edition}: actual reboot restored verified VPN', flush=True)
                        break
                except (RuntimeError, ET.ParseError): pass
                time.sleep(2)
            else: raise RuntimeError('Protection/self-test not restored after actual reboot')
        finally:
            (reports / f'{edition}-reboot-logcat.txt').write_text(adb('logcat', '-d', '-s', 'AndroidAuto:I', 'AndroidAutoOwnerTest:I', 'GameShieldVpn:I', 'GameShieldConnectivity:I', 'AndroidRuntime:E', '*:S'))
            if owner_provisioned:
                cleanup = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                    'it.gameshield.AndroidAutoOwnerDeviceTest#custodianCleanup',
                    package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=120)
                (reports / 'strong-owner-cleanup.txt').write_text(cleanup)
                if 'OK (1 test)' not in cleanup or 'FAILURES' in cleanup: raise RuntimeError('Disposable emulator custodian release failed')
                always_on = adb('shell', 'settings', 'get', 'secure', 'always_on_vpn_app').strip()
                if always_on not in ('', 'null'): raise RuntimeError('Custodian release left always-on configured')

    adb('shell', 'am', 'force-stop', package)

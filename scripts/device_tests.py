#!/usr/bin/env python3
"""Dedicated Android emulator only. Exercise real TUN with Private DNS Off/Automatic."""
import pathlib, subprocess, sys
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
for edition in editions:
    package = 'it.gameshield' + ('.strong' if edition == 'strong' else '')
    for name in (f'app-{edition}-debug.apk', f'app-{edition}-debug-androidTest.apk'):
        matches = list(apks.rglob(name))
        if len(matches) != 1: raise RuntimeError(f'Expected one {name}, got {matches}')
        adb('install', '-r', str(matches[0]), timeout=120)
    adb('shell', 'appops', 'set', package, 'ACTIVATE_VPN', 'allow')
    if edition == 'normal' and 'off' in modes:
        adb('logcat', '-c')
        preflight = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
            'it.gameshield.TunnelDeviceTest#testNativeTcpFinAndHalfClose',
            package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=240)
        (reports / 'startup-preflight.txt').write_text(preflight)
        (reports / 'startup-preflight-logcat.txt').write_text(adb('logcat', '-d', '-s', 'GameShieldVpn:I', 'GameShieldTransport:I', 'AndroidRuntime:E', 'ActivityManager:I', '*:S'))
        print(preflight, flush=True)
        if 'OK (1 test)' not in preflight: raise RuntimeError('Native TUN startup/FIN preflight failed')
    for mode in modes:
        adb('shell', 'settings', 'put', 'global', 'private_dns_mode', mode)
        adb('logcat', '-c')
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
        auto_result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
            'it.gameshield.AndroidAutoDeviceTest',
            package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=360)
        (reports / f'{edition}-{mode}-android-auto.txt').write_text(auto_result)
        (reports / f'{edition}-{mode}-android-auto-logcat.txt').write_text(adb('logcat', '-d', '-s', 'AndroidAuto:I', 'AndroidAutoDeviceTest:I', 'GameShieldVpn:I', 'AndroidRuntime:E', '*:S'))
        print(auto_result, flush=True)
        if 'OK (2 tests)' not in auto_result or 'FAILURES' in auto_result:
            raise RuntimeError('Android Auto security/UI regression tests failed')

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

    adb('shell', 'am', 'force-stop', package)

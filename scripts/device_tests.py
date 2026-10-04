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
for edition in ('normal', 'strong'):
    package = 'it.gameshield' + ('.strong' if edition == 'strong' else '')
    for name in (f'app-{edition}-debug.apk', f'app-{edition}-debug-androidTest.apk'):
        matches = list(apks.rglob(name))
        if len(matches) != 1: raise RuntimeError(f'Expected one {name}, got {matches}')
        adb('install', '-r', str(matches[0]), timeout=120)
    adb('shell', 'appops', 'set', package, 'ACTIVATE_VPN', 'allow')
    for mode in ('off', 'opportunistic'):
        adb('shell', 'settings', 'put', 'global', 'private_dns_mode', mode)
        adb('logcat', '-c')
        result = adb('shell', 'am', 'instrument', '-w', '-r',
            package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=900)
        (reports / f'{edition}-{mode}.txt').write_text(result)
        (reports / f'{edition}-{mode}-logcat.txt').write_text(adb('logcat', '-d', '-s', 'GameShieldDeviceTest:I', 'GameShieldTransport:I', 'GameShieldConnectivity:I', 'AndroidRuntime:E', '*:S'))
        for extension in ('png', 'xml'):
            subprocess.run(['adb', 'pull', '/sdcard/gameshield-store.' + extension,
                str(reports / f'{edition}-{mode}-store.{extension}')], check=False, capture_output=True)
        print(f'{edition}/{mode}:\n{result}', flush=True)
        if 'OK (5 tests)' not in result or 'FAILURES' in result: raise RuntimeError('Native tunnel instrumentation failed')
    adb('shell', 'am', 'force-stop', package)

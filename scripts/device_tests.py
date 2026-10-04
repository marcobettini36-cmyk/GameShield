#!/usr/bin/env python3
"""Dedicated Android emulator only. Exercise real TUN with Private DNS Off/Automatic."""
import pathlib, subprocess, sys
apks = pathlib.Path(sys.argv[1])
reports = pathlib.Path('device-reports'); reports.mkdir(exist_ok=True)
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
            package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=420)
        (reports / f'{edition}-{mode}.txt').write_text(result)
        (reports / f'{edition}-{mode}-logcat.txt').write_text(adb('logcat', '-d', '-s', 'GameShieldDeviceTest:I', 'GameShieldTransport:W', 'AndroidRuntime:E', '*:S'))
        print(f'{edition}/{mode}:\n{result}', flush=True)
        if 'OK (3 tests)' not in result or 'FAILURES' in result: raise RuntimeError('Native tunnel instrumentation failed')
    adb('shell', 'am', 'force-stop', package)

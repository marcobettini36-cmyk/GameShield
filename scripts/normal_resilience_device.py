"""Run new Normal device tests exclusively on a disposable Android emulator."""
from pathlib import Path
import os
import subprocess
import sys

serial = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
if not serial.startswith('emulator-'):
    raise SystemExit('These tests may reset Normal test preferences; physical devices are excluded.')
apkdir = Path(sys.argv[1])
out = Path('normal-device-reports')
out.mkdir(exist_ok=True)

def adb(*args, timeout=600):
    p = subprocess.run(['adb', '-s', serial, *args], text=True, capture_output=True, timeout=timeout)
    if p.returncode:
        raise RuntimeError(p.stderr[-2000:] + p.stdout[-2000:])
    return p.stdout

app = next(p for p in apkdir.rglob('*.apk') if p.name == 'app-normal-debug.apk')
tests = next(p for p in apkdir.rglob('*.apk') if 'androidTest' in p.name)
adb('install', '-r', str(app))
adb('install', '-r', str(tests))
adb('shell', 'appops', 'set', 'it.gameshield', 'ACTIVATE_VPN', 'allow')
sdk = int(adb('shell', 'getprop', 'ro.build.version.sdk').strip())
if sdk >= 33:
    adb('shell', 'pm', 'grant', 'it.gameshield', 'android.permission.POST_NOTIFICATIONS')
for name in ('NormalResilienceDeviceTest', 'NormalDisableDeviceTest'):
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'it.gameshield.'+name,
                 'it.gameshield.test/androidx.test.runner.AndroidJUnitRunner', timeout=1000)
    (out/(name+'.txt')).write_text(result, encoding='utf-8')
    print(result)
    if 'FAILURES!!!' in result or 'INSTRUMENTATION_FAILED' in result or 'OK (' not in result:
        raise RuntimeError(name+' did not pass')

# The shell fixture is emulator-only. No Device Owner is ever provisioned.
activation = subprocess.run(['adb', '-s', serial, 'shell', 'dpm', 'set-active-admin',
                             'it.gameshield/it.gameshield.NormalAdminReceiver'], text=True, capture_output=True, timeout=30)
(out/'NormalAdminActivation.txt').write_text(activation.stdout+activation.stderr, encoding='utf-8')
result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'it.gameshield.NormalAdminDeviceTest',
             'it.gameshield.test/androidx.test.runner.AndroidJUnitRunner')
(out/'NormalAdminDeviceTest.txt').write_text(result, encoding='utf-8')
print(result)
if 'FAILURES!!!' in result or 'OK (' not in result:
    raise RuntimeError('Normal administrator release failed')


"""Run new Normal device tests exclusively on a disposable Android emulator."""
from pathlib import Path
import os
import subprocess
import sys
import time

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
fixtures = list(Path('fixtures/normal-app/build/outputs/apk/debug').glob('*.apk'))
if len(fixtures) != 1:
    raise RuntimeError('Build the disposable :normalFixture APK before running layer tests')
adb('install', '-r', str(fixtures[0]))
for name in ('NormalUiDeviceTest', 'NormalResilienceDeviceTest', 'NormalDisableDeviceTest', 'NormalLayersDeviceTest'):
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'it.gameshield.'+name,
                 'it.gameshield.test/androidx.test.runner.AndroidJUnitRunner', timeout=1000)
    (out/(name+'.txt')).write_text(result, encoding='utf-8')
    print(result)
    if 'FAILURES!!!' in result or 'INSTRUMENTATION_FAILED' in result or 'OK (' not in result:
        raise RuntimeError(name+' did not pass')

# Screenshot additional small layout with enlarged system text, on emulator only.
adb('shell', 'wm', 'size', '720x1280')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.5')
try:
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                 'it.gameshield.NormalUiDeviceTest#smallScreenAndLargeText',
                 'it.gameshield.test/androidx.test.runner.AndroidJUnitRunner')
    (out/'SmallLayout.txt').write_text(result, encoding='utf-8')
    if 'FAILURES!!!' in result or 'OK (' not in result:
        raise RuntimeError('Small layout validation failed')
    adb('pull', '/sdcard/Android/data/it.gameshield/files/screenshots', str(out))
finally:
    adb('shell', 'wm', 'size', 'reset')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')

# The shell fixture is emulator-only. No Device Owner is ever provisioned.
adb('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', 'null')
adb('shell', 'settings', 'put', 'secure', 'accessibility_enabled', '0')
try:
    adb('shell', 'settings', 'put', 'global', 'private_dns_specifier', 'clean.dnsforge.de')
    adb('shell', 'settings', 'put', 'global', 'private_dns_mode', 'hostname')
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'it.gameshield.NormalDnsProviderDeviceTest',
                 'it.gameshield.test/androidx.test.runner.AndroidJUnitRunner', timeout=180)
    (out/'NormalDnsProviderDeviceTest.txt').write_text(result, encoding='utf-8')
    print(result)
    if 'FAILURES!!!' in result or 'OK (' not in result:
        raise RuntimeError('Private DNS measurement failed')
finally:
    adb('shell', 'settings', 'put', 'global', 'private_dns_mode', 'opportunistic')
    adb('shell', 'settings', 'delete', 'global', 'private_dns_specifier')

activation = subprocess.run(['adb', '-s', serial, 'shell', 'dpm', 'set-active-admin',
                             'it.gameshield/it.gameshield.NormalAdminReceiver'], text=True, capture_output=True, timeout=30)
(out/'NormalAdminActivation.txt').write_text(activation.stdout+activation.stderr, encoding='utf-8')
if activation.returncode == 0:
    uninstall = subprocess.run(['adb', '-s', serial, 'shell', 'pm', 'uninstall', 'it.gameshield'],
                               text=True, capture_output=True, timeout=30)
    attempt = uninstall.stdout+uninstall.stderr
    (out/'UninstallWhileAdmin.txt').write_text(attempt, encoding='utf-8')
    if 'DELETE_FAILED_DEVICE_POLICY_MANAGER' not in attempt:
        raise RuntimeError('Expected Android administrator uninstall restriction: '+attempt)
result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'it.gameshield.NormalAdminDeviceTest',
             'it.gameshield.test/androidx.test.runner.AndroidJUnitRunner')
(out/'NormalAdminDeviceTest.txt').write_text(result, encoding='utf-8')
print(result)
if 'FAILURES!!!' in result or 'OK (' not in result:
    raise RuntimeError('Normal administrator release failed')

def boot_test(method):
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                 'it.gameshield.NormalRebootDeviceTest#'+method,
                 'it.gameshield.test/androidx.test.runner.AndroidJUnitRunner', timeout=1000)
    (out/(method+'.txt')).write_text(result, encoding='utf-8')
    print(result)
    if 'FAILURES!!!' in result or 'OK (' not in result:
        raise RuntimeError('Actual reboot phase failed: '+method)

adb('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services',
    'it.gameshield/it.gameshield.NormalContentService')
adb('shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1')
boot_test('prepareBootCommitment')
adb('reboot')
adb('wait-for-device', timeout=180)
boot_deadline = time.monotonic()+180
while adb('shell', 'getprop', 'sys.boot_completed').strip() != '1':
    if time.monotonic()>boot_deadline:
        raise RuntimeError('Emulator reboot timeout')
    time.sleep(1)
boot_test('verifyAfterActualReboot')
removed = adb('shell', 'pm', 'uninstall', 'it.gameshield')
(out/'UninstallAfterRelease.txt').write_text(removed, encoding='utf-8')
if 'Success' not in removed:
    raise RuntimeError('Normal must remain removable after administrator release')


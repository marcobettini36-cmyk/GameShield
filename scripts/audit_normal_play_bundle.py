import hashlib, json, struct, sys, zipfile
from pathlib import Path
bundle = Path(sys.argv[1])
source = Path(sys.argv[2])
strings = set()
jni = {}
def uleb(data, pos):
    value = shift = 0
    while True:
        b = data[pos]; pos += 1
        value |= (b & 127) << shift
        if b < 128: return value, pos
        shift += 7
with zipfile.ZipFile(bundle) as z:
    names = z.namelist()
    for name in names:
        if name.startswith('base/dex/') and name.endswith('.dex'):
            data = z.read(name)
            dex_strings = []
            count, offset = struct.unpack_from('<II', data, 56)
            for i in range(count):
                pos = struct.unpack_from('<I', data, offset + i * 4)[0]
                _, pos = uleb(data, pos)
                end = data.index(b'\0', pos)
                s = data[pos:end].decode('utf-8', errors='replace')
                strings.add(s); dex_strings.append(s)
            type_count, type_offset = struct.unpack_from('<II', data, 64)
            types = [dex_strings[struct.unpack_from('<I', data, type_offset + i * 4)[0]] for i in range(type_count)]
            _, proto_offset = struct.unpack_from('<II', data, 72)
            _, method_offset = struct.unpack_from('<II', data, 88)
            class_count, class_offset = struct.unpack_from('<II', data, 96)
            for i in range(class_count):
                cp = class_offset + i * 32
                if types[struct.unpack_from('<I', data, cp)[0]] != 'Lhev/htproxy/TProxyService;': continue
                pos = struct.unpack_from('<I', data, cp + 24)[0]
                counts = []
                for _ in range(4):
                    value, pos = uleb(data, pos); counts.append(value)
                for _ in range(counts[0] + counts[1]):
                    _, pos = uleb(data, pos); _, pos = uleb(data, pos)
                for count in counts[2:]:
                    method_index = 0
                    for _ in range(count):
                        delta, pos = uleb(data, pos); method_index += delta
                        flags, pos = uleb(data, pos); _, pos = uleb(data, pos)
                        if not flags & 0x100: continue
                        _, proto, string_index = struct.unpack_from('<HHI', data, method_offset + method_index * 8)
                        _, return_type, parameters = struct.unpack_from('<III', data, proto_offset + proto * 12)
                        args = []
                        if parameters:
                            n = struct.unpack_from('<I', data, parameters)[0]
                            args = [types[struct.unpack_from('<H', data, parameters + 4 + j * 2)[0]] for j in range(n)]
                        jni[dex_strings[string_index]] = '(' + ''.join(args) + ')' + types[return_type]
    forbidden = ('setDeviceOwner', 'isDeviceOwnerApp', 'isProfileOwnerApp', 'sendMultipartTextMessage', 'sendTextMessage',
        'setUninstallBlocked', 'isUninstallBlocked', 'setAlwaysOnVpnPackage', 'clearDeviceOwnerApp',
        'setUserControlDisabledPackages', 'setGlobalPrivateDnsModeOpportunistic', 'Codice custode', 'Protezioni Strong')
    hits = sorted(s for s in strings if any(x in s for x in forbidden))
    asset = z.read('base/assets/gambling.txt')
    assert asset == (source / 'app/src/main/assets/gambling.txt').read_bytes()
    native = {}
    for name in names:
        if name.startswith('base/lib/') and name.endswith('.so'):
            data = z.read(name)
            assert data[:4] == b'\x7fELF'
            assert b'hev/htproxy/TProxyService' in data
            assert all(x.encode('ascii') in data for x in jni)
            bits = data[4]
            phoff = struct.unpack_from('<Q' if bits == 2 else '<I', data, 32 if bits == 2 else 28)[0]
            size, count = struct.unpack_from('<HH', data, 54 if bits == 2 else 42)
            aligns = []
            for i in range(count):
                p = phoff + i * size
                if struct.unpack_from('<I', data, p)[0] == 1:
                    aligns.append(struct.unpack_from('<Q' if bits == 2 else '<I', data, p + (48 if bits == 2 else 28))[0])
            native[name] = aligns
            assert all(a >= 16384 for a in aligns), (name, aligns)
    assert jni == {'TProxyStartService':'(Ljava/lang/String;I)Z', 'TProxyStopService':'()Z', 'TProxyIsRunning':'()Z', 'TProxyGetStats':'()[J'}, jni
    report = dict(bundle=str(bundle.resolve()), sha256=hashlib.sha256(bundle.read_bytes()).hexdigest(),
        bytes=bundle.stat().st_size, blacklist_sha256=hashlib.sha256(asset).hexdigest(),
        blacklist_lines=len(asset.splitlines()), strong_dex_hits=hits, native_segment_alignment=native,
        signature_entries=[n for n in names if n.startswith('META-INF/') and n.endswith(('.RSA','.DSA','.EC','.SF'))],
        admin_resource_entries=[n for n in names if 'device_admin' in n], dex_string_count=len(strings), jni_native_methods=jni)
print(json.dumps(report, indent=2))
if hits or report['admin_resource_entries']: sys.exit(2)

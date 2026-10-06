# Normal Play release bundle

Build from the current source and pinned native submodules. Do not reuse APKs or
old bundles. The `normalPlayRelease` profile selects API 36, enables R8/resource
shrinking to remove unreachable Strong code, and rejects explicit Strong tasks.
Normal's manifest removes the shared Device Admin receiver. JNI entry points
remain protected from renaming. The VPN implementation is unchanged.

```powershell
./gradlew.bat -PnormalPlayRelease=true -PnormalVersionCode=6 clean testNormalDebugUnitTest lintNormalRelease bundleNormalRelease
```

The output is `app/build/outputs/bundle/normalRelease/app-normal-release.aab`.
Normal retains versionName `0.3.2`. Its new default versionCode is `6`, above the
previous repository code `5`. Before upload, check Play Console's highest code
and override `normalVersionCode` with a larger number when required.

## Existing upload key only

Do not generate a replacement key or use the debug key for Play. Configure the
existing GameShield upload identity using either environment variables:

- `GAMESHIELD_UPLOAD_KEYSTORE`
- `GAMESHIELD_UPLOAD_ALIAS`
- `GAMESHIELD_UPLOAD_STORE_PASSWORD`
- `GAMESHIELD_UPLOAD_KEY_PASSWORD`

or a private `.local-signing/release.properties` file with the corresponding
`storeFile`, `keyAlias`, `storePassword`, and `keyPassword` properties. This folder
is excluded from Git. Use forward slashes in a Windows properties path. Never
commit or log credentials. Without this configuration, Gradle produces an
**unsigned** release bundle, which cannot be uploaded to Google Play. Verify the
signing certificate against the intended upload certificate before delivery.

Validate the resulting bundle with Google's bundletool and inspect its merged
manifest, DEX, blacklist asset and native libraries. A successful build alone
does not certify a signature, Play Console versionCode availability, or device
runtime behavior.

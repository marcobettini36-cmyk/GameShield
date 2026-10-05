# Third-party components

- HEV SOCKS5 tunnel (`heiher/hev-socks5-tunnel`), pinned commit 2cdc169a248ced7097a7931aea5bf81540dc7759: MIT, license in native/LICENSE. Includes pinned HEV task system (MIT), HEV SOCKS5 core (MIT), lwIP (BSD) and YAML; their license texts remain in their respective source submodules. The JNI binding name hev.htproxy.TProxyService follows upstream's documented ABI.
- HaGeZi DNS Blocklists, gambling-onlydomains feed: GPL-3.0, original license in licenses/HaGeZi-GPL-3.0.txt, source https://github.com/hagezi/dns-blocklists. Updated snapshots retain their source in feeds/metadata.json. Redistribution must retain the license and attribution. There is no affiliation or endorsement.
- ADM public gambling domain lists: source https://www.adm.gov.it/portale/siti-web-inibiti-giochi and authorized-operator directory. Extracted factual domain names; availability and source warnings are recorded in feed metadata. Curated entries do not claim a license classification.
- AndroidX Car App 1.7.0 and its AndroidX/Kotlin runtime dependencies: Apache-2.0. License and attribution are retained in licenses/AndroidX-* and bundled in APK assets/licenses/. Source: https://github.com/androidx/androidx and https://github.com/JetBrains/kotlin.
- JUnit 4.13.2: EPL-1.0, test dependency only. Android SDK/NDK and Gradle are build tools, not redistributed in the app.

APK recipients must also have access to the corresponding complete GameShield source and pinned native submodules under their licenses. Source archives include the native sources, not only gitlink placeholders.

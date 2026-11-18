# Java4Shell (CVE-2026-114938) — JVM security

The issue is in specific OpenJDK **HotSpot Base64 JIT implementations**,
rather than in Java source files, Maven dependencies, or the application
code. This repository's GitHub Actions build temporarily disables the
affected intrinsic and verifies its effective JVM setting.

**For builds or execution outside GitHub Actions:** use an updated,
vendor-confirmed fixed JDK/JRE (Java 17, 21, 25 or newer as applicable).
If immediate updates are impossible, set `JAVA_TOOL_OPTIONS=-XX:-UseBASE64Intrinsics`
before launching each affected JVM, restart any running Java processes,
and confirm `UseBASE64Intrinsics = false` using
`java -XX:+PrintFlagsFinal -version`. In PowerShell use
`$env:JAVA_TOOL_OPTIONS = '-XX:-UseBASE64Intrinsics'`.
CI settings do **not** affect other deployment environments.

See [CERT-EU's advisory](https://www.cert.europa.eu/publications/security-advisories/2026-021/)
and [Adoptium's Temurin update guidance](https://adoptium.net/news/2026/11/temurin-november-2026-cspu-java4shell/).

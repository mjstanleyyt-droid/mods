# Verified dependencies

Verified from published metadata on 2026-11-10 UTC.

| Dependency | Verified selection |
|---|---|
| Minecraft | 26.3 release (2026-09-15) |
| Java | 25, required by Minecraft version manifest |
| Mappings | Unobfuscated Mojang names; Yarn endpoint returned [] |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.166.0+26.3 |
| Loom | net.fabricmc.fabric-loom 1.19.4 |
| Gradle | 9.9.1; Loom module declares minimum plugin API 9.8.0 |

Sources:

- [Minecraft manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [Fabric loader](https://meta.fabricmc.net/v2/versions/loader/26.3)
- [Yarn](https://meta.fabricmc.net/v2/versions/yarn/26.3)
- [Fabric API POM](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.166.0+26.3/fabric-api-0.166.0+26.3.pom)
- [Loom module](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.4/fabric-loom-1.19.4.module)
- [Gradle](https://services.gradle.org/versions/current)

The Minecraft version JSON and selected loader and Loom metadata are checked in alongside this report. Minecraft client JAR SHA-1 from Mojang metadata: `e877b6a07acd633fb3bb475002175cec036e7b87`. Java 25 was installed into the workspace from Azul; no system Java changes were made.

The build environment needed its inherited HTTP proxy and CA trust passed explicitly to Gradle. These environment-specific paths are not embedded in the project. On an ordinary development machine, `./gradlew build` is sufficient.

# Fabric 26.2 port

Original port version: `1.0.1-mc26.2`. Current fast update: `1.0.2-mc26.2`; see README.md and TESTING.md for the timing changes and additional tests.

- Verified the exact Minecraft 26.2 release and Java 25 requirement using Mojang's version manifest.
- Verified and compiled against Fabric Loader 0.20.1, Fabric API 0.161.0+26.2, and Loom 1.19.4.
- Made the Minecraft, Fabric API, loader, Java and mod versions explicit Gradle properties.
- Confirmed the existing Java sources compile against the 26.2 APIs, including the inventory packet, screen widgets, registry/component codecs and synchronization mixin.
- Completed a clean build and reran all 14 automated tests, including 2,000 randomized planner conservation cases: zero failures, errors or skipped tests.
- Kept configuration schema 1 and the same local configuration directory. No player kit migration or reset is performed. Component templates originating in a newer Minecraft version may be skipped if 26.2 cannot decode them; the stored JSON is preserved.

The earlier 26.3 jar is separate. Remove it from `mods/` before installing the 26.2 jar; both use the same mod ID. Use the Fabric API jar for 26.2. Commands and controls are unchanged. See TESTING.md for pending manual tests and RUNTIME.md for the version-specific startup result.

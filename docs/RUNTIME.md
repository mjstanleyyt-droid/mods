# Minecraft 26.2 startup smoke test

Run on 2026-11-10 using JDK 25, Fabric Loader 0.20.1, Fabric API 0.161.0+26.2 and the 1.0.1-mc26.2 mod.

`gradle runClient -x downloadAssets` successfully loaded the mod and mixins, initialized OpenGL with Mesa llvmpipe, reloaded the resource manager, and rendered Minecraft's initial Welcome/accessibility screen. [Startup screenshot](runtime-26.2-startup.png).

Observed log entries:

```
Loading Minecraft 26.2 with Fabric Loader 0.20.1
fabric-api 0.161.0+26.2
shulker_autorestock 1.0.1-mc26.2
Using graphics backend OpenGL, using drivers: 4.5 (Core Profile) Mesa 25.0.7-2+deb13u1
Using graphics device: llvmpipe (LLVM 19.1.7, 256 bits) (Mesa)
Created: 512x512x0 minecraft:textures/atlas/shulker_boxes.png-atlas
```

The mod initializer generated settings.json, kits.json and profiles.json under the development run's config directory. All three parsed as valid JSON with schema 1. No mod initialization or mixin error was observed.

This used the available cached asset subset rather than downloading all assets. Missing window-icon assets, unavailable audio devices, and unauthenticated Minecraft service requests produced warnings. These did not prevent rendering the Welcome screen. The client and test display were stopped after recording the result.

No world was entered. Kit editor interaction, actual inventory transfers, armor replacement, Survival gameplay and multiplayer synchronization remain untested in game. All manual checklist rows remain NOT RUN; startup success is not inventory safety certification.

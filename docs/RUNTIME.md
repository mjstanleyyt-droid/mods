# Development-client smoke attempt (2026-11-10 UTC)

Attempted `gradle build runClient` with Java 25 and an Xorg dummy display. The build and all 14 automated tests passed. Asset downloading was slow and incomplete; a second smoke attempt used `runClient -x downloadAssets` to test the executable startup with the cached asset subset. This is not a complete game test.

Observed in the client log:

```
Loading Minecraft 26.3 with Fabric Loader 0.20.1
fabric-api 0.166.0+26.3
shulker_autorestock 1.0.0
Initializing MixinExtras ...
Backend library: LWJGL version 3.4.3-snapshot
Failed to create backend OpenGL
Failed to create window for OpenGL context: Couldn't find matching GLX visual
Failed to create backend Vulkan
Vulkan is not supported: Installed Vulkan doesn't implement the VK_KHR_surface extension
```

The mod initializer ran and generated valid `settings.json`, `kits.json` and `profiles.json` under the development run's config directory. No mod initializer or mixin failure appeared before the graphics failure. A Minecraft graphics error dialog was displayed; [screenshot](runtime-graphics-blocker.png).

Minecraft service discovery also failed in the unauthenticated development run. This run did not reach the title screen or enter a world. The Minecraft editor, inventory actions, armor staging and multiplayer behavior have **not** been tested in game. All manual release checklist rows remain NOT RUN. The dummy X display was stopped after recording the failure.

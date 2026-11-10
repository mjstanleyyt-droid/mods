# Shulker Auto-Restock

**[Download the Fabric 26.2 jar](https://github.com/mjstanleyyt-droid/mods/raw/refs/heads/main/downloads/shulker-autorestock-1.0.2-mc26.2.jar)** · **[Complete 26.2 project ZIP](https://github.com/mjstanleyyt-droid/mods/raw/refs/heads/main/downloads/shulker-autorestock-1.0.2-mc26.2-project.zip)**

Prerelease: the 26.2 clean build and all 29 automated tests passed, and the client rendered the initial Welcome screen. Actual inventory transfers and multiplayer behavior remain unverified.

Remove the 26.3 jar before installing this version. Existing local kits use the same schema and configuration directory. Back up that directory before changing versions. The earlier 26.3 download remains available in `downloads/`.


Client-only Fabric inventory kit editor, inventory layout switching, and restocking from a deliberately opened shulker. Targets **Minecraft Java 26.2**, Java 25, Fabric Loader 0.20.1+, and Fabric API 0.161.0+26.2. No server mod, cheats, commands on the server, or operator permissions are needed. Servers may restrict automation; use only where permitted. Multiplayer behavior still requires the manual validation described in [TESTING.md](TESTING.md).

## Install

Install Java 25 and the Fabric Loader profile for **26.2**. Put `shulker-autorestock-1.0.2-mc26.2.jar` and the Fabric API jar for 26.2 in your Minecraft installation's `mods/` directory. Launch the Fabric profile. Install on your client only. Make a world backup before testing a new inventory mod.

## Create a kit

1. Enter a Survival world and type `/restock inv`.
2. Click **New**, enter a name and click **Rename**.
3. Select a slot in the virtual inventory: the top three rows are main inventory, the bottom row is the hotbar.
4. Search by localized item name or registry identifier. All registered items, including installed mod items, are searched. Scroll the results.
5. Click a search result to assign it to the selected slot. Edit **Qty** (clamped to that item's stack limit). **Clear** removes the template target.
6. Configure settings on both sides and click **Save**. Changes also save automatically.

Keyboard: Tab cycles buttons and text fields; Alt + arrow keys selects template slots, Alt + Enter assigns the first visible search result, and Alt + Backspace clears the selected slot.

Selecting a virtual item never gives it to you. Use **Capture** to capture your current inventory, including custom components and enchantments. **Exact** toggles matching all components for the selected slot; its green underline indicates enabled. With exact matching disabled, enchantments are still matched when the Enchants setting is enabled. Registry-created templates contain default components; use Capture for custom or enchanted items, or disable Enchants to accept any enchantments. Items with different components are never merged even when both match a template.

**Kit: name** opens the searchable, scrollable kit list. **Copy** duplicates the selected kit, **Delete** asks for confirmation, and **New** creates another independent layout. There is no fixed kit-count limit. Duplicate names are allowed; unique UUIDs identify stored kits. For unambiguous command selection use a unique name or a UUID from kits.json.

## Switch layouts in the normal inventory

Press **E**, click **Kits** to the right of the vanilla inventory, and select a kit. The normal inventory remains vanilla. Matching items already in your 36 inventory slots move, merge or split into kit positions. No chest or nearby shulker is searched. Missing items stay missing; extra items remain in your inventory. Whole-stack swaps resolve full-inventory cycles when possible. A blocked partial-stack move may be skipped rather than risking an item. Incomplete slots are reported using player inventory indices 1–36 (1–9 hotbar).

## Fast restocking (1.0.2)

Fast (0 ms) is the default. Saved kits with the old 150 ms default upgrade automatically; other custom delays are retained. Choose **Fast (0 ms)** in the editor for maximum speed. A timing revision flag ensures that deliberately choosing 150 ms again is preserved on restart.

Eligible full-stack moves and swaps involving a hotbar slot use one vanilla hotbar-swap interaction. Partial transfers use a short sequence of normal left/right clicks rather than depositing every item separately. Each click still waits for the exact server-returned inventory and cursor state; once confirmed, the next click can be sent directly from the incoming packet handler, without an extra client tick. No overlapping clicks, local prediction or unchecked packet bursts are used.

Examples of required clicks: 32 from a 64 stack takes 2 instead of 34; 16 from 64 takes 4 instead of 18; 63 from 64 takes 3 instead of 65. These are verified click counts, not measured multiplayer timings. Server ping, tick rate and server restrictions still determine real speed. Armor replacement includes extra screen transitions. If necessary, raise the delay for a server permitting automation that requires slower interactions.

A split sequence can temporarily stage extra items in its source or destination before returning the remainder. Escape waits until the entire planned move has reached its final quantities, even if an intermediate click leaves the cursor empty. Forced closure or disconnect still stops immediately and preserves the server's actual state; inspect your items afterward.

## Restock

Select your kit, place a shulker containing supplies, then **Shift + right-click** it. The mod briefly releases sneak using normal input packets so vanilla can open the box even while you hold an item. It waits for the initial full container contents packet, plans against that box and your player inventory, and transfers only target deficits. Empty or partially filled shulkers work; unavailable items are skipped. Ordinary right-click opens normally without restocking.

While the queue runs, manual inventory clicks and keys are blocked. Escape requests cancellation at the next empty-cursor boundary; then press Escape again to close. This avoids closing manually while carrying a transaction stack. Wait for completion before interacting. A server-forced screen close also cancels the queue. On timeout, interruption, or a changed inventory, the queue stops without retrying. Check the cursor after an interrupted operation: it may hold an actual stack from the last server-accepted click. Vanilla returns cursor items when closing a container; this mod never sends a throw, creative, spawn, or delete interaction.

Armor replacement defaults to ON at **less than 35% remaining durability**. It examines helmet, chestplate, leggings and boots. Replacements must have at least the original maximum durability, armor, toughness and knockback resistance, better remaining durability, and at least every original enchantment level. Introducing new enchantment types is conservatively rejected, including curses. This supports ordinary vanilla upgrades and skips ambiguous modded equipment rather than guessing material rankings. Binding enchantments and server slot restrictions are honored.

A replacement needs one empty player inventory slot. The mod takes the new armor, closes the shulker, uses the normal armor slot in the player inventory, reopens the same placed box, and deposits the old piece in the vacated source slot. If that slot changed, the old piece remains in the player inventory. It skips replacement when staging space is unavailable. Do not move, close the screen or interact with another block during this sequence. Custom data stays on the original physical stacks.

## Settings (per kit)

| Control | Default | Behavior |
|---|---|---|
| Auto Restock | ON | Enables Shift + right-click trigger |
| Armor | ON | Enables conservative armor upgrades |
| Weapons | OFF | Swaps damaged configured vanilla swords, axes, mace, trident and bows for the identical item/components except lower damage; old weapon returns to source slot |
| Fill Empty | OFF | With Exact Slots OFF, permits empty unconfigured slots as fallback; never takes unrelated supplies |
| Exact Slots | ON | Target saved positions; OFF can retain matching stacks elsewhere |
| Enchants | ON | Match template enchantments unless exact components are already required |
| Rearrange | ON | Move existing player items during shulker restocking; kit switching always arranges |
| Notify | ON | Completion and missing-slot messages; safety errors always show |
| Armor 35% | 35% | Click to cycle threshold in 5% steps |
| Delay | Fast (0 ms) | No artificial delay; click to cycle 0, 25, 50, 100, 150, 250, 500, 1000 ms. JSON allows 0–5000 ms |

Green settings are enabled, red settings disabled; hover for explanations. Quantities already exceeding the target are preserved, never deleted or unnecessarily returned. Unconfigured slots are storage for displaced items only when necessary.

## Client commands

| Command | Action |
|---|---|
| `/restock inv` | Virtual kit editor |
| `/restock kits` | Searchable kit manager; select a kit to edit |
| `/restock select <kit>` | Select a saved kit by case-insensitive name or exact UUID; no inventory action outside the Kits inventory selector |
| `/restock save` | Capture current 36 inventory slots as a **new** kit |
| `/restock reload` | Validate and reload local configuration while idle |
| `/restock help` | Show help |

`/shulkerrestock` is an alias with the same subcommands if another mod claims `/restock`. Fabric client commands are handled locally; a same-name server command may be shadowed by the client root. No command is sent to the server to move items.

## Persistence

Local files under `config/shulker-autorestock/` are shared across worlds and servers:

* `kits.json`: schema version, named UUID kits, 36 targets, encoded item components and per-kit settings.
* `settings.json`: schema version and defaults for newly created kits.
* `profiles.json`: schema version and active kit UUID.

Each file is written via a forced temporary file and atomic rename. An unsupported atomic filesystem produces a visible save error rather than a risky overwrite. Files with malformed data or unsupported schema are copied to a uniquely named `.invalid-….bak` before recovery. Kits are never replaced just because the mod updates. Back up this directory before changing Minecraft versions. Unknown modded items are retained in JSON and skipped until their registry entries are available. File validation is independent; a broken profiles file cannot erase valid kits. The three documents are not a single cross-file transaction: stale active UUIDs recover to the first valid kit.

## Development

Install a JDK 25; run `./gradlew build` (Windows: `gradlew.bat build`). The wrapper pins Gradle 9.9.1 with the verified distribution SHA-256. Output is in `build/libs/`; install the main jar, not the `-sources.jar`. `./gradlew runClient` starts a development client. Dependency verification evidence and URLs are in [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md).

The pure `plan/Planner` has no Minecraft dependencies. `inventory/Transactions` translates planned moves into checked ordinary PICKUP or hotbar SWAP clicks and waits for actual server-returned state; it deliberately does **not** locally predict inventory changes. It sends genuine pre-click cursor hashes with no predicted changed-slot map, so vanilla sends authoritative changes. No state IDs are fabricated. A bounded count-only planner searches for short split/merge sequences and falls back to returning the smaller remainder for unusually large modded stacks. Every click checks the entire menu and cursor before and after, the same handler identity, valid slot admission, stack sizes and pickup permissions. Five seconds without the expected state stops the queue. No overlapping queue can start.

`compat/Stacks` isolates registry and component serialization; future version ports should start there, the transaction packet adapter, GUI extractor names and the synchronization mixin. Exact slot mappings are resolved from slot inventory identity plus inventory index, with strict handler-type checks. Armor staging remains in its own handler. Model and schema changes require explicit migration rather than silently changing existing kits.

See [TESTING.md](TESTING.md) for actual results and required in-game validation. A successful compile does not establish production multiplayer safety.

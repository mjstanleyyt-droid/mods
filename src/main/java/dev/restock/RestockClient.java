package dev.restock;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.restock.compat.Stacks;
import dev.restock.config.ConfigStore;
import dev.restock.gui.*;
import dev.restock.inventory.*;
import dev.restock.model.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.BlockHitResult;

public final class RestockClient implements ClientModInitializer {
  public static ConfigStore store;
  public static final Transactions transactions = new Transactions();
  public static BlockHitResult shulkerHit;
  private static boolean opening;
  private static long armedUntil;
  private static int synced = -1;
  private static Runnable deferred;

  public void onInitializeClient() {
    store =
        new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("shulker-autorestock"));
    try {
      store.load();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot load safe local configuration", e);
    }
    ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
      var root = literal("restock");
      root.then(
          literal("inv")
              .executes(c -> {
                deferred = () -> Minecraft.getInstance().gui.setScreen(new KitEditorScreen());
                return 1;
              }));
      root.then(
          literal("kits")
              .executes(c -> {
                deferred =
                    () -> Minecraft.getInstance().gui.setScreen(new KitListScreen(null, false));
                return 1;
              }));
      root.then(
          literal("select")
              .then(
                  argument("kit", StringArgumentType.greedyString())
                      .suggests((c, b) -> {
                        store.kits.forEach(k -> b.suggest(k.name));
                        return b.buildFuture();
                      })
                      .executes(c -> {
                        String name = StringArgumentType.getString(c, "kit");
                        var k =
                            store.kits.stream()
                                .filter(v -> v.name.equalsIgnoreCase(name) || v.id.equals(name))
                                .findFirst();
                        if (k.isEmpty()) {
                          notice("Unknown kit: " + name);
                          return 0;
                        }
                        if (transactions.busy()) {
                          notice("Wait for the current inventory operation.");
                          return 0;
                        }
                        store.active = k.get().id;
                        save();
                        notice("Active kit: " + k.get().name);
                        return 1;
                      })));
      root.then(
          literal("save")
              .executes(c -> {
                capture(true);
                return 1;
              }));
      root.then(
          literal("reload")
              .executes(c -> {
                if (transactions.busy()) {
                  notice("Finish the current operation first.");
                  return 0;
                }
                try {
                  store.load();
                  notice("Configuration reloaded.");
                  store.warnings.forEach(RestockClient::notice);
                } catch (Exception e) {
                  notice("Reload failed: " + e.getMessage());
                }
                return 1;
              }));
      root.then(
          literal("help")
              .executes(c -> {
                notice(
                    "/restock inv — virtual editor; /restock kits — kit manager; /restock select"
                        + " <name> — active kit; /restock save — capture as a NEW kit; /restock"
                        + " reload — reload JSON; Shift + right-click a shulker to restock."
                        + " Inventory: Kits selects and rearranges. Alias: /shulkerrestock.");
                return 1;
              }));
      var node = dispatcher.register(root);
      dispatcher.register(literal("shulkerrestock").redirect(node));
    });
    ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
      if (screen instanceof AbstractContainerScreen) {
        ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !transactions.busy());
        ScreenKeyboardEvents.allowKeyPress(screen)
            .register((s, e) -> {
              if (!transactions.busy()) return true;
              if (e.isEscape()) transactions.requestCancel();
              return false;
            });
      }
      if (screen instanceof InventoryScreen inv) {
        var geometry = (dev.restock.mixin.ScreenGeometry) inv;
        Button kits =
            Button.builder(
                    Component.literal("Kits"),
                    b -> {
                      if (!transactions.busy()) mc.gui.setScreen(new KitListScreen(screen, true));
                    })
                .bounds(
                    Math.min(w - 54, geometry.restock$left() + geometry.restock$width() + 4),
                    geometry.restock$top(),
                    52,
                    20)
                .build();
        Screens.getWidgets(screen).add(kits);
        ScreenEvents.beforeTick(screen)
            .register(s -> {
              kits.setX(
                  Math.min(s.width - 54, geometry.restock$left() + geometry.restock$width() + 4));
              kits.setY(geometry.restock$top());
              kits.active = !transactions.busy();
            });
      }
    });
    UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
      if (level.isClientSide() && !opening && armedUntil > 0) {
        armedUntil = 0;
        ArmorReplacement.abort();
      }
      if (!level.isClientSide()
          || opening
          || hand != InteractionHand.MAIN_HAND
          || !player.isShiftKeyDown()
          || !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof ShulkerBoxBlock)
          || !store.current().settings.autoRestock) return InteractionResult.PASS;
      if (transactions.busy()) return InteractionResult.FAIL;
      shulkerHit = hit;
      armedUntil = System.currentTimeMillis() + 5000;
      synced = -1;
      openShulker();
      return InteractionResult.SUCCESS;
    });
    ClientTickEvents.END_CLIENT_TICK.register(mc -> {
      if (deferred != null) {
        Runnable r = deferred;
        deferred = null;
        if (mc.player != null && !transactions.busy()) r.run();
        else notice("Wait for the current inventory operation.");
      }
      if (mc.player != null && !store.warnings.isEmpty()) {
        java.util.List.copyOf(store.warnings).forEach(RestockClient::notice);
        store.warnings.clear();
      }
      transactions.tick();
      ArmorReplacement.tick();
      if (armedUntil > 0 && System.currentTimeMillis() > armedUntil) {
        armedUntil = 0;
        notice("Shulker did not synchronize; no items moved.");
      }
      if (armedUntil > 0
          && mc.player != null
          && mc.player.containerMenu instanceof ShulkerBoxMenu m
          && synced == m.containerId
          && !transactions.busy()) {
        armedUntil = 0;
        if (!ArmorReplacement.onOpen()) RestockEngine.run(true);
      }
    });
  }

  public static void synchronizedMenu(int id) {
    synced = id;
  }

  public static void openShulker() {
    var mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null || mc.gameMode == null || shulkerHit == null) return;
    // Normal brief release of sneak allows vanilla block activation even with held items.
    Input previous = mc.player.input.keyPresses;
    boolean shift = mc.player.isShiftKeyDown();
    opening = true;
    try {
      if (shift) {
        mc.player.input.keyPresses =
            new Input(
                previous.forward(),
                previous.backward(),
                previous.left(),
                previous.right(),
                previous.jump(),
                false,
                previous.sprint());
        mc.player.setShiftKeyDown(false);
        mc.getConnection().send(new ServerboundPlayerInputPacket(mc.player.input.keyPresses));
      }
      mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, shulkerHit);
      armedUntil = System.currentTimeMillis() + 5000;
      synced = -1;
    } finally {
      if (shift) {
        mc.player.input.keyPresses = previous;
        mc.player.setShiftKeyDown(true);
        mc.getConnection().send(new ServerboundPlayerInputPacket(previous));
      }
      opening = false;
    }
  }

  public static void capture(boolean create) {
    var mc = Minecraft.getInstance();
    if (mc.player == null || transactions.busy()) return;
    Kit k = create ? new Kit() : store.current();
    if (create) {
      k.name = "Inventory " + (store.kits.size() + 1);
      store.kits.add(k);
      store.active = k.id;
    }
    for (int i = 0; i < 36; i++) {
      var s = mc.player.getInventory().getItem(i);
      k.slots.set(i, s.isEmpty() ? null : Stacks.encode(s));
    }
    save();
    notice("Saved inventory as " + k.name);
  }

  public static void save() {
    try {
      store.save();
    } catch (Exception e) {
      notice("Configuration save failed: " + e.getMessage());
    }
  }

  public static void notice(String text) {
    var mc = Minecraft.getInstance();
    if (mc.player != null)
      mc.gui.chatListener().handleSystemMessage(Component.literal("[Restock] " + text), false);
  }
}

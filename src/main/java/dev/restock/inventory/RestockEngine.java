package dev.restock.inventory;

import dev.restock.*;
import dev.restock.compat.Stacks;
import dev.restock.model.*;
import dev.restock.plan.Planner;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class RestockEngine {
  private static void replaceWeapons(Settings settings) {
    var mc = Minecraft.getInstance();
    var m = mc.player.containerMenu;
    if (settings.weaponReplacement && m instanceof ShulkerBoxMenu) {
      for (int inv = 0; inv < 36; inv++) {
        var target = RestockClient.store.current().slots.get(inv);
        if (target == null) continue;
        int dest = -1;
        for (int j = 27; j < m.slots.size(); j++)
          if (m.getSlot(j).getContainerSlot() == inv) dest = j;
        if (dest < 0) continue;
        ItemStack old = m.getSlot(dest).getItem();
        if (!old.isDamageableItem() || old.getCount() != 1 || old.getDamageValue() == 0) continue;
        String id =
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(old.getItem()).getPath();
        boolean weapon =
            id.endsWith("sword")
                || id.endsWith("axe")
                || id.equals("mace")
                || id.equals("trident")
                || id.endsWith("bow");
        if (!weapon) continue;
        for (int src = 0; src < 27; src++) {
          ItemStack candidate = m.getSlot(src).getItem();
          if (candidate.isEmpty()
              || candidate.getCount() != 1
              || candidate.getDamageValue() >= old.getDamageValue()) continue;
          ItemStack normalized = candidate.copy();
          normalized.setDamageValue(old.getDamageValue());
          if (!ItemStack.isSameItemSameComponents(normalized, old)) continue;
          RestockClient.transactions.start(
              m,
              List.of(new Planner.Move(src, dest, 1, true)),
              settings.actionDelay,
              () -> replaceWeapons(settings));
          return;
        }
      }
    }
    if (settings.armorReplacement) ArmorReplacement.begin();
  }

  public static void run(boolean shulker) {
    var mc = Minecraft.getInstance();
    if (mc.player == null) return;
    try {
      var menu = mc.player.containerMenu;
      Kit kit = RestockClient.store.current();
      Settings settings = kit.settings;
      if (shulker && !(menu instanceof ShulkerBoxMenu)
          || !shulker && menu != mc.player.inventoryMenu)
        throw new IllegalStateException("Unexpected inventory screen");
      if (mc.player.isCreative() || mc.player.isSpectator())
        throw new IllegalStateException("Use Survival or Adventure inventory");
      List<Integer> player = new ArrayList<>(), supply = new ArrayList<>();
      Map<Integer, Planner.Target> targets = new LinkedHashMap<>();
      List<Planner.Stack> snapshot = new ArrayList<>();
      Map<String, ItemStack> keys = new HashMap<>();
      // UUID-like sequence keys distinguish all components, without relying on hash collisions.
      for (var slot : menu.slots) {
        ItemStack s = slot.getItem();
        String key = null;
        if (!s.isEmpty()) {
          for (var e : keys.entrySet())
            if (ItemStack.isSameItemSameComponents(e.getValue(), s)) {
              key = e.getKey();
              break;
            }
          if (key == null) {
            key = Integer.toString(keys.size());
            keys.put(key, s.copy());
          }
        }
        snapshot.add(
            s.isEmpty() ? null : new Planner.Stack(key, s.getCount(), s.getMaxStackSize()));
      }
      for (int inv = 0; inv < 36; inv++)
        for (int j = 0; j < menu.slots.size(); j++) {
          var slot = menu.getSlot(j);
          if (slot.container != mc.player.getInventory() || slot.getContainerSlot() != inv)
            continue;
          player.add(j);
          Kit.Target t = kit.slots.get(inv);
          if (t != null && !Stacks.decode(t).isEmpty())
            targets.put(
                j,
                new Planner.Target(
                    s -> Stacks.matches(keys.get(s.key()), t, settings),
                    Math.min(t.count, Stacks.decode(t).getMaxStackSize())));
        }
      if (!settings.exactSlots) {
        Map<Integer, Planner.Target> flexible = new LinkedHashMap<>();
        for (var entry : targets.entrySet()) {
          int dest = entry.getKey();
          var target = entry.getValue();
          if (snapshot.get(dest) == null || !target.matches().test(snapshot.get(dest))) {
            for (int j : player)
              if (!flexible.containsKey(j)
                  && !targets.containsKey(j)
                  && snapshot.get(j) != null
                  && target.matches().test(snapshot.get(j))) {
                dest = j;
                break;
              }
            if (dest == entry.getKey() && settings.fillEmpty && snapshot.get(dest) != null) {
              for (int j : player)
                if (snapshot.get(j) == null
                    && !flexible.containsKey(j)
                    && !targets.containsKey(j)) {
                  dest = j;
                  break;
                }
            }
          }
          flexible.put(dest, target);
        }
        targets = flexible;
      }
      if (player.size() != 36) throw new IllegalStateException("Unsupported player slot mapping");
      if (shulker) {
        if (menu.slots.size() != 63)
          throw new IllegalStateException("Unsupported shulker slot mapping");
        for (int i = 0; i < 27; i++) supply.add(i);
      }
      var plan = Planner.plan(snapshot, player, supply, targets, !shulker || settings.rearrange);
      RestockClient.transactions.start(
          menu,
          plan.moves(),
          settings.actionDelay,
          () -> {
            if (settings.notifications)
              RestockClient.notice(
                  plan.incomplete().isEmpty()
                      ? "Kit complete."
                      : "Incomplete inventory slots: "
                          + plan.incomplete().stream()
                              .map(i -> Integer.toString(menu.getSlot(i).getContainerSlot() + 1))
                              .toList());
            if (shulker) replaceWeapons(settings);
          });
    } catch (RuntimeException e) {
      RestockClient.notice("Cannot restock: " + e.getMessage());
    }
  }
}

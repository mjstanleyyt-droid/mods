package dev.restock.inventory;

import dev.restock.*;
import dev.restock.compat.Stacks;
import dev.restock.plan.Planner;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/**
 * Armor slots are NOT exposed by a shulker menu: stage through one empty inventory slot and reopen
 * the same box.
 */
public final class ArmorReplacement {
  private static int phase, inventorySlot, sourceSlot;
  private static ItemStack old = ItemStack.EMPTY;
  private static EquipmentSlot equipment;
  private static long deadline;

  public static void abort() {
    reset();
  }

  public static void begin() {
    if (phase != 0) return;
    var mc = Minecraft.getInstance();
    if (mc.player == null || !(mc.player.containerMenu instanceof ShulkerBoxMenu m)) return;
    var settings = RestockClient.store.current().settings;
    int empty = mc.player.getInventory().getFreeSlot();
    if (empty < 0) {
      RestockClient.notice("Armor replacement skipped: needs one empty player inventory slot.");
      return;
    }
    for (EquipmentSlot eq :
        List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
      ItemStack worn = mc.player.getItemBySlot(eq);
      if (!worn.isDamageableItem() || remaining(worn) * 100 >= settings.armorThreshold) continue;
      for (int i = 0; i < 27; i++) {
        ItemStack candidate = m.getSlot(i).getItem();
        if (!better(candidate, worn, eq)) continue;
        inventorySlot = empty;
        sourceSlot = i;
        old = worn.copy();
        equipment = eq;
        phase = 1;
        deadline = System.currentTimeMillis() + 15000;
        int dest = findPlayer(m, empty);
        if (dest < 0) {
          reset();
          return;
        }
        start(
            m,
            List.of(new Planner.Move(i, dest, 1, false)),
            settings.actionDelay,
            () -> {
              if (phase != 1) return;
              mc.player.closeContainer();
              mc.gui.setScreen(new InventoryScreen(mc.player));
              phase = 2;
            });
        return;
      }
    }
  }

  private static double remaining(ItemStack s) {
    return s.getMaxDamage() == 0
        ? 1
        : (s.getMaxDamage() - s.getDamageValue()) / (double) s.getMaxDamage();
  }

  private static boolean better(ItemStack a, ItemStack b, EquipmentSlot eq) {
    if (a.isEmpty()
        || !a.isDamageableItem()
        || remaining(a) <= remaining(b)
        || a.getMaxDamage() < b.getMaxDamage()) return false;
    var equip = a.get(DataComponents.EQUIPPABLE);
    if (equip == null || equip.slot() != eq) return false;
    var aa =
        a.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS,
            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
    var bb =
        b.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS,
            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
    for (var attr :
        List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE))
      if (aa.compute(attr, 0, eq) < bb.compute(attr, 0, eq)) return false;
    // Reject introducing any new enchantment, including curses; every existing enchantment must be
    // >=.
    for (var e : b.getEnchantments().entrySet())
      if (a.getEnchantments().getLevel(e.getKey()) < e.getIntValue()) return false;
    for (var e : a.getEnchantments().entrySet())
      if (!b.getEnchantments().keySet().contains(e.getKey())) return false;
    return true;
  }

  public static void tick() {
    if (phase == 0) return;
    var mc = Minecraft.getInstance();
    if (mc.player == null || System.currentTimeMillis() > deadline) {
      if (RestockClient.transactions.busy())
        RestockClient.transactions.cancel(
            "Armor operation interrupted; all items remain in inventory or shulker.");
      reset();
      return;
    }
    if (phase == 2 && !RestockClient.transactions.busy()) {
      if (mc.player.containerMenu != mc.player.inventoryMenu
          || !(mc.gui.screen() instanceof InventoryScreen)) {
        reset();
        return;
      }
      var m = mc.player.inventoryMenu;
      int armor = -1;
      for (int i = 5; i <= 8; i++) {
        var s = m.getSlot(i).getItem();
        if (Stacks.same(s, old)) {
          var e = s.get(DataComponents.EQUIPPABLE);
          if (e != null && e.slot() == equipment) {
            armor = i;
            break;
          }
        }
      }
      int source = findPlayer(m, inventorySlot);
      if (armor < 0 || source < 0 || !better(m.getSlot(source).getItem(), old, equipment)) {
        reset();
        return;
      }
      phase = 3;
      start(
          m,
          List.of(new Planner.Move(source, armor, 1, true)),
          RestockClient.store.current().settings.actionDelay,
          () -> {
            if (phase != 3) return;
            phase = 4;
            deadline = System.currentTimeMillis() + 7000;
            RestockClient.openShulker();
          });
    }
  }

  public static boolean onOpen() {
    if (phase != 4) return false;
    var mc = Minecraft.getInstance();
    var m = mc.player.containerMenu;
    int source = findPlayer(m, inventorySlot), dest = sourceSlot;
    if (source < 0
        || !Stacks.same(m.getSlot(source).getItem(), old)
        || !m.getSlot(dest).getItem().isEmpty()) {
      RestockClient.notice("Old armor preserved in player inventory: shulker slot changed.");
      reset();
      return true;
    }
    phase = 5;
    start(
        m,
        List.of(new Planner.Move(source, dest, 1, false)),
        RestockClient.store.current().settings.actionDelay,
        () -> {
          reset();
          begin();
        });
    return true;
  }

  private static int findPlayer(AbstractContainerMenu m, int inv) {
    var mc = Minecraft.getInstance();
    for (int i = 0; i < m.slots.size(); i++) {
      var s = m.getSlot(i);
      if (s.container == mc.player.getInventory() && s.getContainerSlot() == inv) return i;
    }
    return -1;
  }

  private static void start(
      AbstractContainerMenu menu, List<Planner.Move> moves, int delay, Runnable done) {
    try {
      RestockClient.transactions.start(menu, moves, delay, done);
    } catch (RuntimeException e) {
      reset();
      RestockClient.notice("Armor replacement stopped: " + e.getMessage());
    }
  }

  private static void reset() {
    phase = 0;
    old = ItemStack.EMPTY;
  }
}

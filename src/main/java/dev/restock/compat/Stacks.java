package dev.restock.compat;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.restock.model.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

public final class Stacks {
  public static ItemStack decode(Kit.Target t) {
    if (t == null) return ItemStack.EMPTY;
    try {
      var mc = Minecraft.getInstance();
      ItemStack s =
          t.stack == null
              ? new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(t.item)))
              : ItemStack.CODEC
                  .parse(
                      mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                      t.stack)
                  .getOrThrow();
      if (s.isEmpty()) return ItemStack.EMPTY;
      s.setCount(Math.min(t.count, s.getMaxStackSize()));
      return s;
    } catch (Exception e) {
      return ItemStack.EMPTY;
    }
  }

  public static Kit.Target encode(ItemStack s) {
    Kit.Target t = new Kit.Target();
    t.item = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    t.count = s.getCount();
    t.stack =
        ItemStack.CODEC
            .encodeStart(
                Minecraft.getInstance()
                    .level
                    .registryAccess()
                    .createSerializationContext(JsonOps.INSTANCE),
                s)
            .getOrThrow();
    return t;
  }

  public static boolean matches(ItemStack s, Kit.Target t, Settings settings) {
    ItemStack wanted = decode(t);
    if (s.isEmpty() || wanted.isEmpty() || !s.is(wanted.getItem())) return false;
    if (t.exactComponents) return ItemStack.isSameItemSameComponents(s, wanted);
    return !settings.enchantments
        || !t.matchEnchantments
        || s.getEnchantments().equals(wanted.getEnchantments());
  }

  public static boolean same(ItemStack a, ItemStack b) {
    return a.getCount() == b.getCount()
        && (a.isEmpty() && b.isEmpty() || ItemStack.isSameItemSameComponents(a, b));
  }
}

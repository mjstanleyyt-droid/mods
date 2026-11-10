package dev.restock.model;

public class Settings {
  public boolean autoRestock = true,
      armorReplacement = true,
      weaponReplacement = false,
      fillEmpty = false,
      exactSlots = true,
      enchantments = true,
      rearrange = true,
      notifications = true;
  public int armorThreshold = 35, actionDelay = 150;

  public void validate() {
    if (armorThreshold < 1 || armorThreshold > 100 || actionDelay < 50 || actionDelay > 5000)
      throw new IllegalArgumentException("Invalid settings range");
  }
}

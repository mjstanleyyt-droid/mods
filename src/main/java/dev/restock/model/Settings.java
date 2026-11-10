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
  public int armorThreshold = 35, actionDelay = 0;

  public int timingRevision;

  public void validate() {
    if (armorThreshold < 1 || armorThreshold > 100 || actionDelay < 0 || actionDelay > 5000)
      throw new IllegalArgumentException("Invalid settings range");
    if (timingRevision < 0 || timingRevision > 1)
      throw new IllegalArgumentException("Unsupported timing revision");
    if (timingRevision == 0 && actionDelay == 150) actionDelay = 0;
    timingRevision = 1;
  }
}

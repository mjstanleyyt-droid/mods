package dev.restock.model;

import com.google.gson.JsonElement;
import java.util.*;

public class Kit {
  public String id = UUID.randomUUID().toString(), name = "New Kit";
  public Settings settings = new Settings();
  public List<Target> slots = new ArrayList<>(Collections.nCopies(36, null));

  public static class Target {
    public String item;
    public int count = 1;
    public JsonElement stack;
    public boolean exactComponents = false, matchEnchantments = true;
  }

  public void validate() {
    if (id == null
        || id.isBlank()
        || name == null
        || name.isBlank()
        || name.length() > 128
        || settings == null
        || slots == null
        || slots.size() != 36) throw new IllegalArgumentException("Invalid kit");
    settings.validate();
    for (Target t : slots)
      if (t != null
          && (t.item == null || !t.item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || t.count < 1))
        throw new IllegalArgumentException("Invalid target");
  }
}

package dev.restock;

import static org.junit.jupiter.api.Assertions.*;

import dev.restock.config.*;
import dev.restock.model.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {
  @TempDir Path dir;

  @Test
  void firstLaunchAndRestart() throws Exception {
    var a = new ConfigStore(dir);
    a.load();
    Kit k = new Kit();
    k.name = "Crystal PvP";
    Kit.Target t = new Kit.Target();
    t.item = "minecraft:ender_pearl";
    t.count = 16;
    k.slots.set(2, t);
    k.settings.armorThreshold = 40;
    a.kits.add(k);
    a.active = k.id;
    a.save();
    var b = new ConfigStore(dir);
    b.load();
    assertEquals(2, b.kits.size());
    assertEquals(k.id, b.active);
    assertEquals(16, b.current().slots.get(2).count);
    assertEquals(40, b.current().settings.armorThreshold);
  }

  @Test
  void corruptedFileIsBackedUp() throws Exception {
    Files.writeString(dir.resolve("kits.json"), "broken data");
    var a = new ConfigStore(dir);
    a.load();
    assertEquals(1, a.kits.size());
    assertEquals(1, a.warnings.size());
    try (var files = Files.list(dir)) {
      assertTrue(files.anyMatch(p -> p.getFileName().toString().endsWith(".bak")));
    }
  }

  @Test
  void invalidSchemaIsPreserved() throws Exception {
    Files.writeString(dir.resolve("kits.json"), "{\"schema\":999,\"kits\":[]}");
    var a = new ConfigStore(dir);
    a.load();
    assertEquals(1, a.warnings.size());
  }

  @Test
  void duplicateIsIndependent() throws Exception {
    var a = new ConfigStore(dir);
    a.load();
    Kit b = a.duplicate(a.current());
    b.settings.actionDelay = 500;
    assertNotEquals(b.id, a.current().id);
    assertEquals(0, a.current().settings.actionDelay);
  }

  @Test
  void invalidSaveDoesNotReplaceFile() throws Exception {
    var a = new ConfigStore(dir);
    a.load();
    String before = Files.readString(dir.resolve("kits.json"));
    a.current().slots.clear();
    assertThrows(IllegalArgumentException.class, a::save);
    assertEquals(before, Files.readString(dir.resolve("kits.json")));
  }
}

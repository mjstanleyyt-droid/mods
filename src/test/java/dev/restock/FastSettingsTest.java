package dev.restock;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.restock.config.ConfigStore;
import dev.restock.model.Settings;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FastSettingsTest {
  @TempDir Path dir;

  @Test
  void newSettingsAreFastAndZeroIsValid() {
    Settings settings = new Settings();
    settings.validate();
    assertEquals(0, settings.actionDelay);
    assertEquals(1, settings.timingRevision);
  }

  @Test
  void legacyDefaultMigratesAndPersistsAcrossReload() throws Exception {
    Files.writeString(
        dir.resolve("settings.json"), "{\"schema\":1,\"settings\":{\"actionDelay\":150}}");
    Files.writeString(
        dir.resolve("kits.json"),
        "{\"schema\":1,\"kits\":[{\"id\":\"legacy\",\"name\":\"PvP\",\"settings\":{\"actionDelay\":150},\"slots\":[null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null]}]}");
    ConfigStore store = new ConfigStore(dir);
    store.load();
    assertEquals(0, store.current().settings.actionDelay);
    assertEquals("PvP", store.current().name);
    assertEquals(0, store.defaults.actionDelay);
    ConfigStore reload = new ConfigStore(dir);
    reload.load();
    assertEquals(0, reload.current().settings.actionDelay);
  }

  @Test
  void existingCustomDelayIsPreserved() {
    Settings settings = new Gson().fromJson("{\"actionDelay\":500}", Settings.class);
    settings.validate();
    assertEquals(500, settings.actionDelay);
  }

  @Test
  void explicitlyRechosen150msIsPreserved() {
    Settings settings = new Settings();
    settings.validate();
    settings.actionDelay = 150;
    settings.validate();
    assertEquals(150, settings.actionDelay);
  }

  @Test
  void negativeDelayIsRejected() {
    Settings settings = new Settings();
    settings.actionDelay = -1;
    assertThrows(IllegalArgumentException.class, settings::validate);
  }
}

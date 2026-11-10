package dev.restock.config;

import com.google.gson.*;
import dev.restock.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Each document is independently atomic. Profiles reference immutable UUIDs, never list indices.
 */
public final class ConfigStore {
  public static final int SCHEMA = 1;
  private final Path dir;
  public final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  public List<Kit> kits = new ArrayList<>();
  public Settings defaults = new Settings();
  public String active;
  public final List<String> warnings = new ArrayList<>();

  public ConfigStore(Path dir) {
    this.dir = dir;
  }

  public void load() throws IOException {
    Files.createDirectories(dir);
    warnings.clear();
    defaults = read("settings.json", SettingsDoc.class, new SettingsDoc()).settings;
    kits = read("kits.json", KitsDoc.class, new KitsDoc()).kits;
    active = read("profiles.json", ProfilesDoc.class, new ProfilesDoc()).active;
    if (kits.isEmpty()) {
      Kit k = new Kit();
      k.name = "Survival";
      kits.add(k);
    }
    if (kits.stream().noneMatch(k -> k.id.equals(active))) active = kits.getFirst().id;
    save();
  }

  private <T extends Document> T read(String name, Class<T> type, T fallback) throws IOException {
    Path path = dir.resolve(name);
    if (!Files.exists(path)) return fallback;
    try {
      T doc = gson.fromJson(Files.readString(path), type);
      if (doc == null || doc.schema != SCHEMA)
        throw new IllegalArgumentException("Unsupported schema");
      doc.validate();
      return doc;
    } catch (RuntimeException e) {
      Path backup = dir.resolve(name + ".invalid-" + UUID.randomUUID() + ".bak");
      Files.copy(path, backup);
      warnings.add(name + ": preserved as " + backup.getFileName());
      return fallback;
    }
  }

  public synchronized void save() throws IOException {
    defaults.validate();
    Set<String> ids = new HashSet<>();
    for (Kit k : kits) {
      k.validate();
      if (!ids.add(k.id)) throw new IOException("Duplicate kit UUID");
    }
    SettingsDoc s = new SettingsDoc();
    s.settings = defaults;
    KitsDoc k = new KitsDoc();
    k.kits = kits;
    ProfilesDoc p = new ProfilesDoc();
    p.active = active;
    atomic("kits.json", k);
    atomic("settings.json", s);
    atomic("profiles.json", p);
  }

  private void atomic(String name, Object data) throws IOException {
    Files.createDirectories(dir);
    Path tmp = Files.createTempFile(dir, name, ".tmp");
    try {
      byte[] bytes = gson.toJson(data).getBytes(StandardCharsets.UTF_8);
      try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
        ByteBuffer b = ByteBuffer.wrap(bytes);
        while (b.hasRemaining()) ch.write(b);
        ch.force(true);
      }
      // Do not replace the original with a non-atomic fallback.
      Files.move(
          tmp,
          dir.resolve(name),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  public Kit current() {
    return kits.stream().filter(k -> k.id.equals(active)).findFirst().orElse(kits.getFirst());
  }

  public Kit duplicate(Kit kit) {
    Kit copy = gson.fromJson(gson.toJson(kit), Kit.class);
    copy.id = UUID.randomUUID().toString();
    copy.name = kit.name + " copy";
    kits.add(copy);
    return copy;
  }

  public static class Document {
    public int schema = SCHEMA;

    void validate() {}
  }

  public static class SettingsDoc extends Document {
    public Settings settings = new Settings();

    @Override
    void validate() {
      Objects.requireNonNull(settings).validate();
    }
  }

  public static class KitsDoc extends Document {
    public List<Kit> kits = new ArrayList<>();

    @Override
    void validate() {
      Set<String> ids = new HashSet<>();
      for (Kit k : Objects.requireNonNull(kits)) {
        k.validate();
        if (!ids.add(k.id)) throw new IllegalArgumentException("Duplicate UUID");
      }
    }
  }

  public static class ProfilesDoc extends Document {
    public String active;
  }
}

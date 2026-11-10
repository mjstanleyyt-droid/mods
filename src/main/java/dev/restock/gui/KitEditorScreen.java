package dev.restock.gui;

import dev.restock.*;
import dev.restock.compat.Stacks;
import dev.restock.model.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class KitEditorScreen extends Screen {
  private Kit kit;
  private int selected, offset, cx;
  private EditBox search, name, quantity;
  private List<Item> results = List.of();
  private String query = "";

  public KitEditorScreen() {
    super(Component.literal("Shulker Auto-Restock"));
    kit = RestockClient.store.current();
  }

  private Button button(String text, int x, int y, int w, Runnable action, String tip) {
    Button b =
        Button.builder(Component.literal(text), v -> action.run()).bounds(x, y, w, 18).build();
    if (tip != null) b.setTooltip(Tooltip.create(Component.literal(tip)));
    return addRenderableWidget(b);
  }

  @Override
  protected void init() {
    cx = width / 2 - 81;
    int left = Math.max(2, cx - 78), right = cx + 166;
    button(
        "Kit: " + kit.name,
        cx,
        20,
        162,
        () -> {
          RestockClient.save();
          minecraft.gui.setScreen(new KitListScreen(this, false));
        },
        "Select the active kit");
    name = addRenderableWidget(new EditBox(font, cx, 41, 100, 18, Component.literal("Kit name")));
    name.setMaxLength(128);
    name.setValue(kit.name);
    button(
        "Rename",
        cx + 103,
        41,
        59,
        () -> {
          if (!name.getValue().isBlank()) {
            kit.name = name.getValue();
            RestockClient.save();
            rebuildWidgets();
          }
        },
        "Rename this kit");
    button(
        "New",
        cx,
        62,
        38,
        () -> {
          Kit k = new Kit();
          k.settings =
              RestockClient.store.gson.fromJson(
                  RestockClient.store.gson.toJson(RestockClient.store.defaults), Settings.class);
          RestockClient.store.kits.add(k);
          RestockClient.store.active = k.id;
          kit = k;
          RestockClient.save();
          rebuildWidgets();
        },
        "Create an empty virtual kit");
    button(
        "Copy",
        cx + 40,
        62,
        38,
        () -> {
          kit = RestockClient.store.duplicate(kit);
          RestockClient.store.active = kit.id;
          RestockClient.save();
          rebuildWidgets();
        },
        "Duplicate this kit");
    button(
        "Delete",
        cx + 80,
        62,
        40,
        () ->
            minecraft.gui.setScreen(
                new ConfirmScreen(
                    ok -> {
                      if (ok) {
                        RestockClient.store.kits.remove(kit);
                        if (RestockClient.store.kits.isEmpty())
                          RestockClient.store.kits.add(new Kit());
                        kit = RestockClient.store.kits.getFirst();
                        RestockClient.store.active = kit.id;
                        RestockClient.save();
                      }
                      minecraft.gui.setScreen(this);
                    },
                    Component.literal("Delete " + kit.name + "?"),
                    Component.literal("This removes only the virtual template."))),
        "Confirm before deleting");
    button(
        "Save",
        cx + 122,
        62,
        40,
        () -> {
          RestockClient.save();
          RestockClient.notice("Kit saved.");
        },
        "Save this kit locally");
    quantity =
        addRenderableWidget(new EditBox(font, cx + 40, 164, 36, 18, Component.literal("Quantity")));
    quantity.setMaxLength(10);
    quantity.setValue(
        kit.slots.get(selected) == null ? "1" : Integer.toString(kit.slots.get(selected).count));
    quantity.setResponder(v -> {
      try {
        var t = kit.slots.get(selected);
        if (t != null) {
          var s = Stacks.decode(t);
          if (!s.isEmpty()) {
            t.count = Math.max(1, Math.min(s.getMaxStackSize(), Integer.parseInt(v)));
            RestockClient.save();
          }
        }
      } catch (NumberFormatException ignored) {
      }
    });
    button(
        "Clear",
        cx + 80,
        164,
        38,
        () -> {
          kit.slots.set(selected, null);
          RestockClient.save();
        },
        "Clear the selected template slot");
    button(
        "Exact",
        cx + 121,
        164,
        41,
        () -> {
          var t = kit.slots.get(selected);
          if (t != null) {
            t.exactComponents = !t.exactComponents;
            RestockClient.save();
          }
        },
        "Toggle exact item components for the selected slot; green border means exact");
    search =
        addRenderableWidget(new EditBox(font, cx, 186, 162, 18, Component.literal("Search items")));
    search.setHint(Component.literal("Search registered items…"));
    search.setValue(query);
    search.setResponder(v -> {
      query = v;
      offset = 0;
      find();
    });
    find();
    toggle(
        "Auto Restock",
        left,
        40,
        () -> kit.settings.autoRestock,
        v -> kit.settings.autoRestock = v,
        "Restock only after Shift + right-click on a placed shulker.");
    toggle(
        "Armor",
        left,
        62,
        () -> kit.settings.armorReplacement,
        v -> kit.settings.armorReplacement = v,
        "Replace damaged armor with equivalent or better armor from this shulker.");
    toggle(
        "Weapons",
        left,
        84,
        () -> kit.settings.weaponReplacement,
        v -> kit.settings.weaponReplacement = v,
        "Replace damaged weapons matching configured kit targets, only with equivalent"
            + " components.");
    toggle(
        "Fill Empty",
        left,
        106,
        () -> kit.settings.fillEmpty,
        v -> kit.settings.fillEmpty = v,
        "Use an empty unconfigured slot as fallback when exact placement is OFF; never take beyond"
            + " kit quantities.");
    toggle(
        "Exact Slots",
        left,
        128,
        () -> kit.settings.exactSlots,
        v -> kit.settings.exactSlots = v,
        "Place targets in their saved slots; OFF permits an empty-slot fallback.");
    toggle(
        "Enchants",
        right,
        40,
        () -> kit.settings.enchantments,
        v -> kit.settings.enchantments = v,
        "Require the template enchantments when matching items.");
    toggle(
        "Rearrange",
        right,
        62,
        () -> kit.settings.rearrange,
        v -> kit.settings.rearrange = v,
        "Allow moving existing player inventory items during shulker restocking.");
    toggle(
        "Notify",
        right,
        84,
        () -> kit.settings.notifications,
        v -> kit.settings.notifications = v,
        "Report completed and incomplete slots; safety errors always display.");
    button(
        "Armor " + kit.settings.armorThreshold + "%",
        right,
        106,
        73,
        () -> {
          kit.settings.armorThreshold = kit.settings.armorThreshold % 100 + 5;
          if (kit.settings.armorThreshold > 100) kit.settings.armorThreshold = 5;
          RestockClient.save();
          rebuildWidgets();
        },
        "Click to increase remaining-durability threshold by 5%, cycling at 100%.");
    button(
        kit.settings.actionDelay + " ms",
        right,
        128,
        73,
        () -> {
          kit.settings.actionDelay =
              kit.settings.actionDelay >= 1000 ? 50 : kit.settings.actionDelay + 50;
          RestockClient.save();
          rebuildWidgets();
        },
        "Delay after each confirmed server action: 50–1000ms. JSON allows up to 5000ms.");
    button(
        "Capture",
        left,
        164,
        73,
        () -> {
          RestockClient.capture(false);
          kit = RestockClient.store.current();
          rebuildWidgets();
        },
        "Capture your current 36 slots, including components and enchantments.");
    button("Done", right, 164, 73, this::onClose, "Close the virtual editor");
  }

  private void toggle(
      String title, int x, int y, BooleanSupplier get, Consumer<Boolean> set, String tip) {
    button(
        (get.getAsBoolean() ? "§a" : "§c") + title,
        x,
        y,
        73,
        () -> {
          set.accept(!get.getAsBoolean());
          RestockClient.save();
          rebuildWidgets();
        },
        tip);
  }

  private void find() {
    String q = query.toLowerCase(Locale.ROOT).strip();
    results =
        BuiltInRegistries.ITEM.stream()
            .filter(i -> i != Items.AIR)
            .filter(i ->
                BuiltInRegistries.ITEM.getKey(i).toString().contains(q)
                    || new ItemStack(i)
                        .getHoverName()
                        .getString()
                        .toLowerCase(Locale.ROOT)
                        .contains(q))
            .toList();
  }

  private int index(int visual) {
    return visual < 27 ? visual + 9 : visual - 27;
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
    extractBackground(g, mx, my, dt);
    g.centeredText(font, title, width / 2, 7, 0xffffff);
    g.fill(cx - 4, 84, cx + 166, 161, 0xffc6c6c6);
    for (int v = 0; v < 36; v++) {
      int i = index(v), x = cx + v % 9 * 18, y = 87 + v / 9 * 18 + (v >= 27 ? 3 : 0);
      g.fill(x, y, x + 18, y + 18, i == selected ? 0xffe8d478 : 0xff373737);
      g.fill(x + 1, y + 1, x + 17, y + 17, 0xff8b8b8b);
      ItemStack s = Stacks.decode(kit.slots.get(i));
      g.item(s, x + 1, y + 1);
      g.itemDecorations(font, s, x + 1, y + 1);
      if (mx >= x && mx < x + 18 && my >= y && my < y + 18 && !s.isEmpty())
        g.setTooltipForNextFrame(font, s, mx, my);
    }
    var t = kit.slots.get(selected);
    g.text(font, "Qty", cx, 169, 0xffffff);
    if (t != null && t.exactComponents) g.fill(cx + 121, 182, cx + 162, 184, 0xff55ff55);
    int rows = Math.max(1, (height - 212) / 18), shown = rows * 9;
    offset = Math.min(offset, Math.max(0, results.size() - shown));
    for (int n = 0; n < shown && offset + n < results.size(); n++) {
      int x = cx + n % 9 * 18, y = 210 + n / 9 * 18;
      ItemStack s = new ItemStack(results.get(offset + n));
      g.fill(x, y, x + 17, y + 17, 0xff8b8b8b);
      g.item(s, x, y);
      if (mx >= x && mx < x + 18 && my >= y && my < y + 18)
        g.setTooltipForNextFrame(font, s, mx, my);
    }
    super.extractRenderState(g, mx, my, dt);
  }

  @Override
  public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
    if (e.hasAltDown()) {
      int v = selected < 9 ? selected + 27 : selected - 9;
      if (e.isLeft()) v = (v + 35) % 36;
      else if (e.isRight()) v = (v + 1) % 36;
      else if (e.isUp()) v = (v + 27) % 36;
      else if (e.isDown()) v = (v + 9) % 36;
      else if (e.isConfirmation() && !results.isEmpty()) {
        ItemStack item = new ItemStack(results.get(Math.min(offset, results.size() - 1)));
        int count = 1;
        try {
          count = Integer.parseInt(quantity.getValue());
        } catch (NumberFormatException ignored) {
        }
        item.setCount(Math.max(1, Math.min(count, item.getMaxStackSize())));
        kit.slots.set(selected, Stacks.encode(item));
        RestockClient.save();
        return true;
      } else if (e.key() == 259) {
        kit.slots.set(selected, null);
        RestockClient.save();
        return true;
      } else return super.keyPressed(e);
      selected = index(v);
      var t = kit.slots.get(selected);
      quantity.setValue(t == null ? "1" : Integer.toString(t.count));
      return true;
    }
    return super.keyPressed(e);
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
    for (int v = 0; v < 36; v++) {
      int x = cx + v % 9 * 18, y = 87 + v / 9 * 18 + (v >= 27 ? 3 : 0);
      if (e.x() >= x && e.x() < x + 18 && e.y() >= y && e.y() < y + 18) {
        selected = index(v);
        var t = kit.slots.get(selected);
        quantity.setValue(t == null ? "1" : Integer.toString(t.count));
        return true;
      }
    }
    int col = (int) (e.x() - cx) / 18, row = (int) (e.y() - 210) / 18;
    if (e.x() >= cx && e.x() < cx + 162 && e.y() >= 210 && e.y() < height - 2 && col < 9) {
      int n = offset + row * 9 + col;
      if (n < results.size()) {
        ItemStack s = new ItemStack(results.get(n));
        int count = 1;
        try {
          count = Integer.parseInt(quantity.getValue());
        } catch (Exception ignored) {
        }
        s.setCount(Math.max(1, Math.min(count, s.getMaxStackSize())));
        kit.slots.set(selected, Stacks.encode(s));
        RestockClient.save();
        quantity.setValue(Integer.toString(s.getCount()));
        return true;
      }
    }
    return super.mouseClicked(e, dbl);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double a, double b) {
    offset =
        Math.max(0, Math.min(Math.max(0, results.size() - 9), offset - (int) Math.signum(b) * 9));
    return true;
  }

  @Override
  public void onClose() {
    RestockClient.save();
    minecraft.gui.setScreen(null);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}

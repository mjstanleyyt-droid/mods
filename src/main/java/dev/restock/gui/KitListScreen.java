package dev.restock.gui;

import dev.restock.*;
import dev.restock.inventory.RestockEngine;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class KitListScreen extends Screen {
  private final Screen parent;
  private final boolean arrange;
  private int offset;
  private String filter = "";

  public KitListScreen(Screen parent, boolean arrange) {
    super(Component.literal("Saved Kits"));
    this.parent = parent;
    this.arrange = arrange;
  }

  @Override
  protected void init() {
    EditBox search =
        new EditBox(font, width / 2 - 100, 24, 200, 20, Component.literal("Search kits"));
    search.setValue(filter);
    search.setResponder(v -> {
      filter = v;
      offset = 0;
      rebuildList();
    });
    addRenderableWidget(search);
    rebuildList();
  }

  private void rebuildList() {
    var all =
        RestockClient.store.kits.stream()
            .filter(k ->
                k.name
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains(filter.toLowerCase(java.util.Locale.ROOT)))
            .toList();
    // Retain search focus while rebuilding only list buttons.
    for (var widget : java.util.List.copyOf(children()))
      if (widget instanceof Button) removeWidget(widget);
    int rows = Math.max(1, (height - 84) / 22);
    offset = Math.max(0, Math.min(offset, Math.max(0, all.size() - rows)));
    for (int i = offset; i < Math.min(all.size(), offset + rows); i++) {
      var k = all.get(i);
      addRenderableWidget(
          Button.builder(
                  Component.literal((k.id.equals(RestockClient.store.active) ? "✓ " : "") + k.name),
                  b -> {
                    RestockClient.store.active = k.id;
                    RestockClient.save();
                    minecraft.gui.setScreen(parent);
                    if (arrange) RestockEngine.run(false);
                    else minecraft.gui.setScreen(new KitEditorScreen());
                  })
              .bounds(width / 2 - 100, 50 + (i - offset) * 22, 200, 20)
              .build());
    }
    addRenderableWidget(
        Button.builder(Component.literal("Back"), b -> minecraft.gui.setScreen(parent))
            .bounds(width / 2 - 45, height - 26, 90, 20)
            .build());
  }

  @Override
  public boolean mouseScrolled(double x, double y, double a, double b) {
    offset -= (int) Math.signum(b);
    rebuildList();
    return true;
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float d) {
    extractBackground(g, x, y, d);
    g.centeredText(font, title, width / 2, 8, 0xffffff);
    super.extractRenderState(g, x, y, d);
  }

  @Override
  public void onClose() {
    minecraft.gui.setScreen(parent);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}

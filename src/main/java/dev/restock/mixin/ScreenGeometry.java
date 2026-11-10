package dev.restock.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ScreenGeometry {
  @Accessor("leftPos")
  int restock$left();

  @Accessor("topPos")
  int restock$top();

  @Accessor("imageWidth")
  int restock$width();
}

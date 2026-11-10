package dev.restock.mixin;

import dev.restock.RestockClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class SyncMixin {
  @Inject(method = "handleContainerContent", at = @At("TAIL"))
  private void restock$synchronized(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
    RestockClient.synchronizedMenu(packet.containerId());
  }
}

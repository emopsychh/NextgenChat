package com.nextgenchat.mixin;

import com.nextgenchat.NextgenChatMod;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ChatMixin {
	@Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
	private void nextgenchat$handleChat(ServerboundChatPacket packet, CallbackInfo ci) {
		ServerGamePacketListenerImpl handler = (ServerGamePacketListenerImpl) (Object) this;
		NextgenChatMod.getInstance().chat().handleMessage(handler.player, packet.message());
		ci.cancel();
	}
}

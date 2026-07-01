package com.nextgenchat.mixin;

import com.nextgenchat.util.VanillaMessageFilter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin {
	@Inject(method = "broadcastSystemMessage", at = @At("HEAD"), cancellable = true)
	private void nextgenchat$filterVanillaJoinQuit(Component message, boolean overlay, CallbackInfo ci) {
		if (VanillaMessageFilter.isVanillaJoinOrQuit(message)) {
			ci.cancel();
		}
	}
}

package com.example.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Redirect(
        method = "onDisconnected",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V"
        )
    )
    private void redirectBroadcast(PlayerManager manager, Text message, boolean overlay) {
        // Просто ничего не делаем — стандартное сообщение не отправится
        System.out.println("[NextgenChat DEBUG] Blocked vanilla quit message");
    }
} 
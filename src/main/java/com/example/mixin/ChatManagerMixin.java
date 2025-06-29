package com.example.mixin;

import com.example.ChatManagerMod;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ChatManagerMixin {
    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        // Получаем игрока из handler
        ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
        String message = packet.chatMessage();
        
        // Вызываем кастомную обработку чата
        ChatManagerMod.CHAT_MANAGER.onChatMessage(handler.player, message);
        
        // Отменяем стандартную обработку чата
        ci.cancel();
    }
} 
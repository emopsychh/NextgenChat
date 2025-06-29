// Этот миксин отключен из-за проблем с сигнатурами методов в Minecraft 1.20.1
// Вместо этого мы полагаемся на Fabric API события для отправки кастомных сообщений
// и игнорируем дублирование стандартных сообщений

package com.example.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    
    @Inject(
        method = "broadcast",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onBroadcast(Text message, boolean overlay, CallbackInfo ci) {
        String messageStr = message.getString();
        
        // Логируем для отладки
        System.out.println("[NextgenChat DEBUG] PlayerManagerMixin: broadcast: " + messageStr);
        
        // Блокируем стандартные join/quit сообщения
        if (messageStr.contains("joined the game") || 
            messageStr.contains("left the game") ||
            messageStr.contains("зашел на сервер") ||
            messageStr.contains("покинул игру") ||
            messageStr.contains("присоединился к серверу") ||
            messageStr.contains("вышел с сервера") ||
            messageStr.contains("lost connection") ||
            messageStr.contains("disconnected")) {
            
            System.out.println("[NextgenChat DEBUG] PlayerManagerMixin: Blocking vanilla join/quit message");
            ci.cancel();
        }
    }
} 
package com.example;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatManager {
    private final ConcurrentHashMap<UUID, ChatMode> playerChatModes = new ConcurrentHashMap<>();
    
    public enum ChatMode {
        LOCAL,
        GLOBAL
    }
    
    public ChatManager() {
        // Конструктор
    }
    
    public void onChatMessage(ServerPlayerEntity player, String message) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        
        // Проверяем, не заблокирован ли игрок
        if (ChatManagerMod.MODERATION_MANAGER != null && 
            ChatManagerMod.MODERATION_MANAGER.isPlayerMuted(player.getUuid()) &&
            !ChatManagerMod.PERMISSION_MANAGER.canBypassMute(player)) {
            
            ModerationManager.MuteData muteData = ChatManagerMod.MODERATION_MANAGER.getMuteData(player.getUuid());
            if (muteData != null) {
                long remainingTime = muteData.muteTime + muteData.duration - System.currentTimeMillis();
                String remaining = ChatManagerMod.MODERATION_MANAGER.formatDuration(remainingTime);
                
                String muteMessage = ChatManagerMod.CONFIG.moderation.muteMessage
                    .replace("{duration}", remaining)
                    .replace("{reason}", muteData.reason);
                
                player.sendMessage(net.minecraft.text.Text.literal(muteMessage.replace("&", "§")), false);
            }
            return; // Блокируем отправку сообщения
        }
        
        // Определяем режим чата
        ChatMode chatMode = determineChatMode(player, message);
        
        // Проверяем права на конкретный режим чата
        if (chatMode == ChatMode.GLOBAL && !ChatManagerMod.PERMISSION_MANAGER.canUseGlobalChat(player)) {
            ChatManagerMod.PERMISSION_MANAGER.sendNoPermissionMessage(player, "nextgenchat.chat.global");
            return;
        }
        
        if (chatMode == ChatMode.LOCAL && !ChatManagerMod.PERMISSION_MANAGER.canUseLocalChat(player)) {
            ChatManagerMod.PERMISSION_MANAGER.sendNoPermissionMessage(player, "nextgenchat.chat.local");
            return;
        }
        
        // Обрабатываем сообщение в зависимости от режима
        String processedMessage = processMessage(player, message, chatMode);
        // Отправляем сообщение
        sendChatMessage(player, processedMessage, chatMode);
    }
    
    private ChatMode determineChatMode(ServerPlayerEntity player, String message) {
        ChatManagerConfig config = ChatManagerMod.CONFIG;
        // Проверяем специальные символы - только ! для глобального чата
        if (message.startsWith(config.chat.globalChatSymbol)) {
            return ChatMode.GLOBAL;
        }
        // По умолчанию - локальный чат
        return ChatMode.LOCAL;
    }
    
    private String processMessage(ServerPlayerEntity player, String message, ChatMode chatMode) {
        ChatManagerConfig config = ChatManagerMod.CONFIG;
        // Убираем специальные символы из начала сообщения только для глобального чата
        String cleanMessage = message;
        if (chatMode == ChatMode.GLOBAL && message.startsWith(config.chat.globalChatSymbol)) {
            cleanMessage = message.substring(config.chat.globalChatSymbol.length()).trim();
        }
        // Сохраняем режим чата для игрока
        playerChatModes.put(player.getUuid(), chatMode);
        return cleanMessage;
    }
    
    private void sendChatMessage(ServerPlayerEntity sender, String message, ChatMode chatMode) {
        ChatManagerConfig config = ChatManagerMod.CONFIG;
        MinecraftServer server = sender.getServer();
        if (server == null) return;
        // Формируем сообщение с новым форматом
        String formattedMessage = formatMessage(sender, message, chatMode);
        Text messageText = Text.literal(formattedMessage);
        if (chatMode == ChatMode.LOCAL) {
            // Отправляем локальное сообщение
            sendLocalMessage(sender, messageText, config.chat.localChatRadius);
        } else {
            // Отправляем глобальное сообщение
            sendGlobalMessage(sender, messageText);
        }
    }
    
    private void sendLocalMessage(ServerPlayerEntity sender, Text message, int radius) {
        MinecraftServer server = sender.getServer();
        if (server == null) return;
        Vec3d senderPos = sender.getPos();
        int radiusSquared = radius * radius;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld() == sender.getWorld()) {
                double distanceSquared = player.getPos().squaredDistanceTo(senderPos);
                if (distanceSquared <= radiusSquared) {
                    player.sendMessage(message, false);
                }
            }
        }
    }
    
    private void sendGlobalMessage(ServerPlayerEntity sender, Text message) {
        MinecraftServer server = sender.getServer();
        if (server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player == sender) {
                player.sendMessage(message, false);
                continue;
            }
            // Только отправитель инициирует рассылку глобального сообщения
            // (если вдруг вызовется несколько раз, остальные игроки не рассылают)
            // Вызов sendGlobalMessage только у отправителя
            player.sendMessage(message, false);
        }
    }
    
    private String formatMessage(ServerPlayerEntity player, String message, ChatMode chatMode) {
        ChatManagerConfig config = ChatManagerMod.CONFIG;
        // Получаем никнейм игрока
        String playerName = player.getName().getString();
        // Форматируем сообщение в зависимости от режима чата
        String format = chatMode == ChatMode.GLOBAL ? config.chat.globalChatFormat : config.chat.localChatFormat;
        
        // Заменяем плейсхолдеры
        String formatted = format
            .replace("{player}", playerName)
            .replace("{message}", message);
        
        // Заменяем плейсхолдеры LuckPerms
        formatted = replaceLuckPermsPlaceholders(formatted, player);
        
        // Применяем цвета
        return formatted.replace("&", "§");
    }
    
    public String replaceLuckPermsPlaceholders(String text, ServerPlayerEntity player) {
        try {
            // Получаем префикс и суффикс из LuckPerms
            String prefix = getLuckPermsPrefix(player);
            String suffix = getLuckPermsSuffix(player);
            String group = getLuckPermsGroup(player);
            
            return text
                .replace("%luckperms_prefix%", prefix)
                .replace("%luckperms_suffix%", suffix)
                .replace("%luckperms_group%", group)
                .replace("%player%", player.getName().getString())
                .replace("%player_name%", player.getName().getString())
                .replace("%player_displayname%", player.getDisplayName().getString());
        } catch (Exception e) {
            // Если LuckPerms недоступен, просто убираем плейсхолдеры
            return text
                .replace("%luckperms_prefix%", "")
                .replace("%luckperms_suffix%", "")
                .replace("%luckperms_group%", "")
                .replace("%player%", player.getName().getString())
                .replace("%player_name%", player.getName().getString())
                .replace("%player_displayname%", player.getDisplayName().getString());
        }
    }
    
    private String getLuckPermsPrefix(ServerPlayerEntity player) {
        try {
            Object luckPerms = Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUuid());
            if (user == null) return "";
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            String prefix = (String) metaData.getClass().getMethod("getPrefix").invoke(metaData);
            return prefix != null ? prefix : "";
        } catch (Exception e) {
            return "";
        }
    }
    
    private String getLuckPermsSuffix(ServerPlayerEntity player) {
        try {
            Object luckPerms = Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUuid());
            if (user == null) return "";
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            String suffix = (String) metaData.getClass().getMethod("getSuffix").invoke(metaData);
            return suffix != null ? suffix : "";
        } catch (Exception e) {
            return "";
        }
    }
    
    private String getLuckPermsGroup(ServerPlayerEntity player) {
        try {
            Object luckPerms = Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUuid());
            if (user == null) return "";
            String group = (String) user.getClass().getMethod("getPrimaryGroup").invoke(user);
            return group != null ? group : "";
        } catch (Exception e) {
            return "";
        }
    }
    
    // Методы для управления режимом чата
    public void setPlayerChatMode(UUID playerId, ChatMode mode) {
        playerChatModes.put(playerId, mode);
    }
    
    public ChatMode getPlayerChatMode(UUID playerId) {
        return playerChatModes.getOrDefault(playerId, 
            ChatManagerMod.CONFIG.chat.defaultChatMode.equals("global") ? ChatMode.GLOBAL : ChatMode.LOCAL);
    }
    
    public void clearPlayerData(UUID playerId) {
        playerChatModes.remove(playerId);
    }
    
    // Возвращает true, если сообщение обработано модом и стандартный вывод не нужен
    public boolean tryHandleChatMessage(ServerPlayerEntity player, String message) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        // Только инициатор сообщения должен рассылать его
        // Проверяем, что player — это отправитель (handler для этого игрока)
        // Если у игрока есть активный handler, то только он инициирует рассылку
        ServerPlayerEntity realSender = server.getPlayerManager().getPlayer(player.getUuid());
        if (realSender == null || !realSender.equals(player)) {
            return false;
        }
        // Обрабатываем и отправляем только если всё ок
        ChatMode chatMode = determineChatMode(player, message);
        String processedMessage = processMessage(player, message, chatMode);
        sendChatMessage(player, processedMessage, chatMode);
        return true; // Сообщение обработано модом, стандартный вывод не нужен
    }
} 
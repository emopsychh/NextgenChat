package com.example;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// LuckPerms imports
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;

public class PermissionManager {
    private static final ConcurrentHashMap<UUID, PlayerPermissions> playerPermissions = new ConcurrentHashMap<>();
    
    public static class PlayerPermissions {
        public final boolean canUseGlobalChat;
        public final boolean canUseLocalChat;
        public final boolean canMutePlayers;
        public final boolean canUnmutePlayers;
        public final boolean canReloadConfig;
        public final boolean canViewMutes;
        public final boolean canBypassAntiSpam;
        public final boolean canBypassMute;
        public final boolean canUseCommands;
        public final boolean canReceiveModerationNotifications;
        
        public PlayerPermissions(boolean canUseGlobalChat, boolean canUseLocalChat, 
                               boolean canMutePlayers, boolean canUnmutePlayers, 
                               boolean canReloadConfig, boolean canViewMutes,
                               boolean canBypassAntiSpam, boolean canBypassMute,
                               boolean canUseCommands, boolean canReceiveModerationNotifications) {
            this.canUseGlobalChat = canUseGlobalChat;
            this.canUseLocalChat = canUseLocalChat;
            this.canMutePlayers = canMutePlayers;
            this.canUnmutePlayers = canUnmutePlayers;
            this.canReloadConfig = canReloadConfig;
            this.canViewMutes = canViewMutes;
            this.canBypassAntiSpam = canBypassAntiSpam;
            this.canBypassMute = canBypassMute;
            this.canUseCommands = canUseCommands;
            this.canReceiveModerationNotifications = canReceiveModerationNotifications;
        }
    }
    
    public PermissionManager() {
        // Регистрируем слушатель LuckPerms для автоматической очистки кэша прав
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            EventBus eventBus = luckPerms.getEventBus();
            eventBus.subscribe(UserDataRecalculateEvent.class, event -> {
                UUID uuid = event.getUser().getUniqueId();
                clearPlayerPermissions(uuid);
                ChatManagerMod.LOGGER.info("[PERM] Кэш прав очищен для {} (UserDataRecalculateEvent)", uuid);
            });
            ChatManagerMod.LOGGER.info("[PERM] LuckPerms UserDataRecalculateEvent listener registered");
        } catch (Throwable t) {
            ChatManagerMod.LOGGER.warn("[PERM] Не удалось зарегистрировать слушатель LuckPerms: {}", t.toString());
        }
    }
    
    /**
     * Получает права игрока, проверяя LuckPerms
     */
    public PlayerPermissions getPlayerPermissions(ServerPlayerEntity player) {
        UUID playerUUID = player.getUuid();
        
        // Проверяем кэш
        PlayerPermissions cached = playerPermissions.get(playerUUID);
        if (cached != null) {
            return cached;
        }
        
        // Получаем права из LuckPerms
        PlayerPermissions permissions = loadPermissionsFromLuckPerms(player);
        
        // Кэшируем права
        playerPermissions.put(playerUUID, permissions);
        
        return permissions;
    }
    
    /**
     * Загружает права из LuckPerms (всегда через loadUser)
     */
    private PlayerPermissions loadPermissionsFromLuckPerms(ServerPlayerEntity player) {
        try {
            // Получаем API LuckPerms
            Object luckPerms = Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            // Всегда асинхронно загружаем пользователя
            Object future = userManager.getClass().getMethod("loadUser", UUID.class, String.class)
                    .invoke(userManager, player.getUuid(), player.getName().getString());
            Object user = future.getClass().getMethod("get").invoke(future);
            if (user == null) {
                ChatManagerMod.LOGGER.warn("LuckPerms: Не удалось загрузить пользователя {}", player.getName().getString());
                return getDefaultPermissions();
            }

            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);

            // Проверяем права через LuckPerms
            boolean canUseGlobalChat = hasPermission(permissionData, "nextgenchat.chat.global");
            boolean canUseLocalChat = hasPermission(permissionData, "nextgenchat.chat.local");
            boolean canMutePlayers = hasPermission(permissionData, "nextgenchat.moderate.mute");
            boolean canUnmutePlayers = hasPermission(permissionData, "nextgenchat.moderate.unmute");
            boolean canReloadConfig = hasPermission(permissionData, "nextgenchat.admin.reload");
            boolean canViewMutes = hasPermission(permissionData, "nextgenchat.moderate.view");
            boolean canBypassAntiSpam = hasPermission(permissionData, "nextgenchat.bypass.antispam");
            boolean canBypassMute = hasPermission(permissionData, "nextgenchat.bypass.mute");
            boolean canUseCommands = hasPermission(permissionData, "nextgenchat.commands");
            boolean canReceiveModerationNotifications = hasPermission(permissionData, "nextgenchat.notifications.moderation");

            if (ChatManagerMod.CONFIG.permissions.debugPermissionLogging) {
                ChatManagerMod.LOGGER.info("[PERM DEBUG] {}: global={}, local={}, mute={}, unmute={}, reload={}, viewMutes={}, bypassAS={}, bypassMute={}, commands={}, modNotify={}",
                        player.getName().getString(),
                        canUseGlobalChat, canUseLocalChat, canMutePlayers, canUnmutePlayers,
                        canReloadConfig, canViewMutes, canBypassAntiSpam, canBypassMute,
                        canUseCommands, canReceiveModerationNotifications
                );
            }

            return new PlayerPermissions(
                    canUseGlobalChat, canUseLocalChat, canMutePlayers, canUnmutePlayers,
                    canReloadConfig, canViewMutes, canBypassAntiSpam, canBypassMute,
                    canUseCommands, canReceiveModerationNotifications
            );

        } catch (Exception e) {
            // Если LuckPerms недоступен, используем права по умолчанию
            ChatManagerMod.LOGGER.warn("LuckPerms not available, using default permissions for player: {}", player.getName().getString());
            return getDefaultPermissions();
        }
    }
    
    /**
     * Проверяет наличие права через LuckPerms API с debug-логированием (универсально для Tristate и PermissionCheckResult)
     */
    private boolean hasPermission(Object permissionData, String permission) {
        try {
            Object result = permissionData.getClass().getMethod("checkPermission", String.class).invoke(permissionData, permission);
            boolean value;
            if (result.getClass().getSimpleName().equals("PermissionCheckResult")) {
                value = (boolean) result.getClass().getMethod("asBoolean").invoke(result);
                if (ChatManagerMod.CONFIG.permissions.debugPermissionLogging) {
                    ChatManagerMod.LOGGER.info("[PERM DEBUG] checkPermission('{}'): asBoolean={}", permission, value);
                }
            } else if (result.getClass().getSimpleName().equals("Tristate")) {
                String tristate = result.toString();
                value = tristate.equals("TRUE");
                if (ChatManagerMod.CONFIG.permissions.debugPermissionLogging) {
                    ChatManagerMod.LOGGER.info("[PERM DEBUG] checkPermission('{}'): Tristate={}, asBoolean={}", permission, tristate, value);
                }
            } else {
                value = false;
                if (ChatManagerMod.CONFIG.permissions.debugPermissionLogging) {
                    ChatManagerMod.LOGGER.warn("[PERM DEBUG] checkPermission('{}'): Unknown result type: {}", permission, result.getClass().getName());
                }
            }
            return value;
        } catch (Exception e) {
            if (ChatManagerMod.CONFIG.permissions.debugPermissionLogging) {
                ChatManagerMod.LOGGER.error("[PERM DEBUG] Exception in hasPermission('{}'): {}", permission, e.toString());
            }
            return false;
        }
    }
    
    /**
     * Возвращает права по умолчанию (когда LuckPerms недоступен)
     */
    private PlayerPermissions getDefaultPermissions() {
        ChatManagerConfig.PermissionSettings settings = ChatManagerMod.CONFIG.permissions;
        return new PlayerPermissions(
            settings.defaultCanUseGlobalChat,
            settings.defaultCanUseLocalChat,
            settings.defaultCanMutePlayers,
            settings.defaultCanUnmutePlayers,
            settings.defaultCanReloadConfig,
            settings.defaultCanViewMutes,
            settings.defaultCanBypassAntiSpam,
            settings.defaultCanBypassMute,
            settings.defaultCanUseCommands,
            settings.defaultCanReceiveModerationNotifications
        );
    }
    
    /**
     * Проверяет, имеет ли игрок право на использование глобального чата
     */
    public boolean canUseGlobalChat(ServerPlayerEntity player) {
        return getPlayerPermissions(player).canUseGlobalChat;
    }
    
    /**
     * Проверяет, имеет ли игрок право на использование локального чата
     */
    public boolean canUseLocalChat(ServerPlayerEntity player) {
        return getPlayerPermissions(player).canUseLocalChat;
    }
    
    /**
     * Проверяет, может ли игрок мутить других игроков
     */
    public boolean canMutePlayers(ServerPlayerEntity player) {
        return getPlayerPermissions(player).canMutePlayers;
    }
    
    /**
     * Проверяет, может ли игрок размучивать других игроков
     */
    public boolean canUnmutePlayers(ServerPlayerEntity player) {
        return getPlayerPermissions(player).canUnmutePlayers;
    }
    
    /**
     * Проверяет, может ли игрок перезагружать конфигурацию
     */
    public boolean canReloadConfig(ServerPlayerEntity player) {
        return getPlayerPermissions(player).canReloadConfig;
    }
    
    /**
     * Проверяет, может ли игрок просматривать список мутов
     */
    public boolean canViewMutes(ServerPlayerEntity player) {
        return getPlayerPermissions(player).canViewMutes;
    }
    
    /**
     * Проверяет, может ли игрок обходить анти-спам
     */
    public boolean canBypassAntiSpam(ServerPlayerEntity player) {
        return getPlayerPermissions(player).canBypassAntiSpam;
    }
    
    /**
     * Проверяет, может ли игрок обходить мут
     */
    public boolean canBypassMute(ServerPlayerEntity player) {
        return getPlayerPermissions(player).canBypassMute;
    }
    
    /**
     * Проверяет, может ли игрок использовать команды
     */
    public boolean canUseCommands(ServerPlayerEntity player) {
        return getPlayerPermissions(player).canUseCommands;
    }
    
    /**
     * Проверяет, может ли игрок получать уведомления о модерации
     */
    public boolean canReceiveModerationNotifications(ServerPlayerEntity player) {
        return getPlayerPermissions(player).canReceiveModerationNotifications;
    }
    
    /**
     * Отправляет сообщение об отсутствии прав
     */
    public void sendNoPermissionMessage(ServerPlayerEntity player, String permission) {
        String message = ChatManagerMod.CONFIG.permissions.noPermissionMessage
            .replace("{permission}", permission)
            .replace("{player}", player.getName().getString());
        player.sendMessage(Text.literal(message.replace("&", "§")), false);
    }
    
    /**
     * Очищает кэш прав игрока (вызывается при изменении прав в LuckPerms)
     */
    public void clearPlayerPermissions(UUID playerUUID) {
        playerPermissions.remove(playerUUID);
    }
    
    /**
     * Очищает весь кэш прав
     */
    public void clearAllPermissions() {
        playerPermissions.clear();
    }
    
    /**
     * Проверяет, доступен ли LuckPerms
     */
    public boolean isLuckPermsAvailable() {
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
} 
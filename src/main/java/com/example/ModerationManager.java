package com.example;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModerationManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MUTE_DATA_FILE = "nextgenchat_mutes.json";
    
    // Хранилище мутов: UUID игрока -> MuteData
    public final ConcurrentHashMap<UUID, MuteData> mutedPlayers = new ConcurrentHashMap<>();
    
    // Паттерн для парсинга времени: 1h, 30m, 1d, 2w
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([hdmw])");
    
    public ModerationManager() {
        loadMuteData();
    }
    
    /**
     * Мутит игрока на указанное время
     */
    public boolean mutePlayer(ServerPlayerEntity target, ServerPlayerEntity moderator, String duration, String reason) {
        if (!ChatManagerMod.CONFIG.moderation.enableModeration) {
            return false;
        }
        
        UUID targetUUID = target.getUuid();
        
        // Проверяем, не заблокирован ли уже игрок
        if (mutedPlayers.containsKey(targetUUID)) {
            String message = ChatManagerMod.CONFIG.moderation.alreadyMutedMessage
                .replace("{player}", target.getName().getString());
            moderator.sendMessage(Text.literal(message.replace("&", "§")), false);
            return false;
        }
        
        // Парсим время
        long muteDuration = parseDuration(duration);
        if (muteDuration <= 0) {
            String message = ChatManagerMod.CONFIG.moderation.invalidDurationMessage;
            moderator.sendMessage(Text.literal(message.replace("&", "§")), false);
            return false;
        }
        
        // Создаем данные о муте
        MuteData muteData = new MuteData(
            targetUUID,
            target.getName().getString(),
            moderator.getUuid(),
            moderator.getName().getString(),
            System.currentTimeMillis(),
            muteDuration,
            reason != null ? reason : "Нарушение правил"
        );
        
        // Добавляем в хранилище
        mutedPlayers.put(targetUUID, muteData);
        
        // Сохраняем данные
        if (ChatManagerMod.CONFIG.moderation.saveMuteData) {
            saveMuteData();
        }
        
        // Отправляем сообщения
        sendMuteMessages(target, moderator, muteData);
        
        // Логируем действие
        if (ChatManagerMod.CONFIG.moderation.logMuteActions) {
            ChatManagerMod.LOGGER.info("Player {} muted by {} for {} ({}). Reason: {}", 
                target.getName().getString(), 
                moderator.getName().getString(), 
                formatDuration(muteDuration), 
                duration, 
                reason != null ? reason : "Нарушение правил");
        }
        
        return true;
    }
    
    /**
     * Размучивает игрока
     */
    public boolean unmutePlayer(ServerPlayerEntity target, ServerPlayerEntity moderator) {
        if (!ChatManagerMod.CONFIG.moderation.enableModeration) {
            return false;
        }
        
        UUID targetUUID = target.getUuid();
        
        // Проверяем, заблокирован ли игрок
        if (!mutedPlayers.containsKey(targetUUID)) {
            String message = ChatManagerMod.CONFIG.moderation.notMutedMessage
                .replace("{player}", target.getName().getString());
            moderator.sendMessage(Text.literal(message.replace("&", "§")), false);
            return false;
        }
        
        // Убираем из хранилища
        MuteData muteData = mutedPlayers.remove(targetUUID);
        
        // Сохраняем данные
        if (ChatManagerMod.CONFIG.moderation.saveMuteData) {
            saveMuteData();
        }
        
        // Отправляем сообщения
        sendUnmuteMessages(target, moderator, muteData);
        
        // Логируем действие
        if (ChatManagerMod.CONFIG.moderation.logMuteActions) {
            ChatManagerMod.LOGGER.info("Player {} unmuted by {}", 
                target.getName().getString(), 
                moderator.getName().getString());
        }
        
        return true;
    }
    
    /**
     * Проверяет, заблокирован ли игрок
     */
    public boolean isPlayerMuted(UUID playerUUID) {
        if (!ChatManagerMod.CONFIG.moderation.enableModeration) {
            return false;
        }
        
        MuteData muteData = mutedPlayers.get(playerUUID);
        if (muteData == null) {
            return false;
        }
        
        // Проверяем, не истек ли мут
        if (System.currentTimeMillis() > muteData.muteTime + muteData.duration) {
            mutedPlayers.remove(playerUUID);
            if (ChatManagerMod.CONFIG.moderation.saveMuteData) {
                saveMuteData();
            }
            return false;
        }
        
        return true;
    }
    
    /**
     * Получает информацию о муте игрока
     */
    public MuteData getMuteData(UUID playerUUID) {
        return mutedPlayers.get(playerUUID);
    }
    
    /**
     * Очищает истекшие муты
     */
    public void cleanupExpiredMutes() {
        long currentTime = System.currentTimeMillis();
        mutedPlayers.entrySet().removeIf(entry -> {
            MuteData muteData = entry.getValue();
            boolean expired = currentTime > muteData.muteTime + muteData.duration;
            if (expired && ChatManagerMod.CONFIG.moderation.logMuteActions) {
                ChatManagerMod.LOGGER.info("Mute expired for player: {}", muteData.playerName);
            }
            return expired;
        });
        
        if (ChatManagerMod.CONFIG.moderation.saveMuteData) {
            saveMuteData();
        }
    }
    
    /**
     * Парсит строку времени в миллисекунды
     */
    private long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            duration = ChatManagerMod.CONFIG.moderation.defaultMuteDuration;
        }
        
        Matcher matcher = TIME_PATTERN.matcher(duration.toLowerCase());
        if (!matcher.matches()) {
            return -1;
        }
        
        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        
        switch (unit) {
            case "m": return value * 60 * 1000; // минуты
            case "h": return value * 60 * 60 * 1000; // часы
            case "d": return value * 24 * 60 * 60 * 1000; // дни
            case "w": return value * 7 * 24 * 60 * 60 * 1000; // недели
            default: return -1;
        }
    }
    
    /**
     * Форматирует время в читаемый вид
     */
    public String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + "д " + (hours % 24) + "ч";
        } else if (hours > 0) {
            return hours + "ч " + (minutes % 60) + "м";
        } else if (minutes > 0) {
            return minutes + "м";
        } else {
            return seconds + "с";
        }
    }
    
    /**
     * Отправляет сообщения при муте
     */
    private void sendMuteMessages(ServerPlayerEntity target, ServerPlayerEntity moderator, MuteData muteData) {
        // Сообщение заблокированному игроку
        String muteMsg = ChatManagerMod.CONFIG.moderation.muteMessage
            .replace("{duration}", formatDuration(muteData.duration))
            .replace("{reason}", muteData.reason);
        target.sendMessage(Text.literal(muteMsg.replace("&", "§")), false);
        
        // Уведомление модератору
        String notification = ChatManagerMod.CONFIG.moderation.muteNotification
            .replace("{player}", target.getName().getString())
            .replace("{duration}", formatDuration(muteData.duration));
        moderator.sendMessage(Text.literal(notification.replace("&", "§")), false);
        
        // Уведомление администрации
        if (ChatManagerMod.CONFIG.moderation.notifyStaffOnMute) {
            String staffNotification = "&e[Модерация] " + notification;
            for (ServerPlayerEntity player : moderator.getServer().getPlayerManager().getPlayerList()) {
                if (ChatManagerMod.PERMISSION_MANAGER.canReceiveModerationNotifications(player)) {
                    player.sendMessage(Text.literal(staffNotification.replace("&", "§")), false);
                }
            }
        }
    }
    
    /**
     * Отправляет сообщения при размуте
     */
    private void sendUnmuteMessages(ServerPlayerEntity target, ServerPlayerEntity moderator, MuteData muteData) {
        // Сообщение разблокированному игроку
        String unmuteMsg = ChatManagerMod.CONFIG.moderation.unmuteMessage;
        target.sendMessage(Text.literal(unmuteMsg.replace("&", "§")), false);
        
        // Уведомление модератору
        String notification = ChatManagerMod.CONFIG.moderation.unmuteNotification
            .replace("{player}", target.getName().getString());
        moderator.sendMessage(Text.literal(notification.replace("&", "§")), false);
        
        // Уведомление администрации
        if (ChatManagerMod.CONFIG.moderation.notifyStaffOnMute) {
            String staffNotification = "&e[Модерация] " + notification;
            for (ServerPlayerEntity player : moderator.getServer().getPlayerManager().getPlayerList()) {
                if (ChatManagerMod.PERMISSION_MANAGER.canReceiveModerationNotifications(player)) {
                    player.sendMessage(Text.literal(staffNotification.replace("&", "§")), false);
                }
            }
        }
    }
    
    /**
     * Загружает данные о мутах из файла
     */
    private void loadMuteData() {
        if (!ChatManagerMod.CONFIG.moderation.saveMuteData) {
            return;
        }
        
        Path dataPath = FabricLoader.getInstance().getGameDir().resolve(MUTE_DATA_FILE);
        File dataFile = dataPath.toFile();
        
        if (dataFile.exists()) {
            try (FileReader reader = new FileReader(dataFile)) {
                MuteData[] loadedMutes = GSON.fromJson(reader, MuteData[].class);
                if (loadedMutes != null) {
                    for (MuteData muteData : loadedMutes) {
                        // Проверяем, не истек ли мут
                        if (System.currentTimeMillis() <= muteData.muteTime + muteData.duration) {
                            mutedPlayers.put(muteData.playerUUID, muteData);
                        }
                    }
                }
                ChatManagerMod.LOGGER.info("Loaded {} active mutes", mutedPlayers.size());
            } catch (IOException e) {
                ChatManagerMod.LOGGER.error("Failed to load mute data", e);
            }
        }
    }
    
    /**
     * Сохраняет данные о мутах в файл
     */
    private void saveMuteData() {
        if (!ChatManagerMod.CONFIG.moderation.saveMuteData) {
            return;
        }
        
        Path dataPath = FabricLoader.getInstance().getGameDir().resolve(MUTE_DATA_FILE);
        File dataFile = dataPath.toFile();
        
        try {
            if (!dataFile.exists()) {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            }
            
            try (FileWriter writer = new FileWriter(dataFile)) {
                GSON.toJson(mutedPlayers.values().toArray(new MuteData[0]), writer);
            }
        } catch (IOException e) {
            ChatManagerMod.LOGGER.error("Failed to save mute data", e);
        }
    }
    
    /**
     * Класс для хранения данных о муте
     */
    public static class MuteData {
        public UUID playerUUID;
        public String playerName;
        public UUID moderatorUUID;
        public String moderatorName;
        public long muteTime;
        public long duration;
        public String reason;
        
        public MuteData(UUID playerUUID, String playerName, UUID moderatorUUID, String moderatorName, 
                       long muteTime, long duration, String reason) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.moderatorUUID = moderatorUUID;
            this.moderatorName = moderatorName;
            this.muteTime = muteTime;
            this.duration = duration;
            this.reason = reason;
        }
        
        // Конструктор для GSON
        public MuteData() {}
    }
} 
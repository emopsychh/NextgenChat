package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class ChatManagerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String CONFIG_FILE = "nextgenchat.json";
    
    // Настройки чата
    public ChatSettings chat = new ChatSettings();
    
    // Настройки анти-спама
    public AntiSpamSettings antiSpam = new AntiSpamSettings();
    
    // Настройки уведомлений
    public NotificationSettings notifications = new NotificationSettings();
    
    // Настройки автобродкаста
    public AutoBroadcastSettings autoBroadcast = new AutoBroadcastSettings();
    
    // Настройки модерации
    public ModerationSettings moderation = new ModerationSettings();
    
    // Настройки прав
    public PermissionSettings permissions = new PermissionSettings();
    
    public static class ChatSettings {
        public boolean enableLocalChat = true;
        public boolean enableGlobalChat = true;
        public int localChatRadius = 100;
        public String localChatPrefix = "&7[Локальный] ";
        public String globalChatPrefix = "&6[G] ";
        public String localChatSymbol = ""; // Пустая строка - локальный чат по умолчанию
        public String globalChatSymbol = "!"; // ! для глобального чата
        public String defaultChatMode = "local"; // "local" или "global"
        
        // Настройки цветов и форматов
        public String globalChatFormat = "&6[G] &f{player}: &7{message}";
        public String localChatFormat = "&7[Локальный] &f{player}: &7{message}";
        public String playerNameColor = "&f"; // Цвет никнейма
        public String messageColor = "&7"; // Цвет сообщения
        public String globalTagColor = "&6"; // Цвет тега [G]
        public String localTagColor = "&7"; // Цвет тега [Локальный]
    }
    
    public static class AntiSpamSettings {
        public boolean enableAntiSpam = true;
        public boolean enableAntiFlood = true;
        public int messageCooldown = 3; // секунды между сообщениями
        public int maxRepeatedMessages = 3; // максимальное количество повторяющихся сообщений
        public int floodThreshold = 5; // количество сообщений за короткое время
        public int floodTimeWindow = 10; // окно времени для проверки флуда (секунды)
    }
    
    public static class NotificationSettings {
        public boolean enableJoinMessages = true;
        public boolean enableQuitMessages = true;
        public String joinMessage = "&a+ {player} присоединился к серверу";
        public String quitMessage = "&c- {player} покинул сервер";
        public boolean playJoinSound = true;
        public boolean playQuitSound = false;
    }
    
    public static class AutoBroadcastSettings {
        public boolean enableAutoBroadcast = true;
        public int broadcastInterval = 300; // секунды между сообщениями
        public boolean randomizeMessages = false; // отправлять сообщения в случайном порядке
        public boolean showBroadcastPrefix = false; // показывать префикс [Автобродкаст]
        public String broadcastPrefix = "&d[Автобродкаст] &r"; // префикс для сообщений автобродкаста
        public String[] broadcastMessages = {
            "&6&lДобро пожаловать на сервер!",
            "&eНе забудьте прочитать правила!",
            "&aПриятной игры!",
            "&bОнлайн: &f{online}&b/&f{max_online}",
            "&dСервер работает стабильно!",
            "&7Использование памяти: &f{memory_used}MB&7/&f{memory_max}MB",
            "&3Время: &f{uptime_hours}&3ч &f{uptime_minutes}&3м",
            "&5Сервер: &f{server_name}"
        };
    }
    
    public static class ModerationSettings {
        public boolean enableModeration = true;
        public boolean saveMuteData = true; // сохранять данные о мутах в файл
        public String muteMessage = "&cВы заблокированы в чате на {duration}";
        public String unmuteMessage = "&aВы разблокированы в чате";
        public String muteNotification = "&c{player} заблокирован в чате на {duration}";
        public String unmuteNotification = "&a{player} разблокирован в чате";
        public String alreadyMutedMessage = "&c{player} уже заблокирован в чате";
        public String notMutedMessage = "&c{player} не заблокирован в чате";
        public String noPermissionMessage = "&cУ вас нет прав для использования этой команды";
        public String playerNotFoundMessage = "&cИгрок {player} не найден";
        public String invalidDurationMessage = "&cНеверный формат времени. Используйте: 1h, 30m, 1d";
        
        // Настройки по умолчанию для мутов
        public String defaultMuteDuration = "1h"; // 1 час по умолчанию
        public boolean notifyStaffOnMute = true; // уведомлять администрацию о мутах
        public boolean logMuteActions = true; // логировать действия модерации
    }
    
    public static class PermissionSettings {
        public boolean enablePermissionSystem = true;
        public boolean useLuckPerms = true;
        public String noPermissionMessage = "&cУ вас нет права {permission} для выполнения этого действия";
        public String permissionDeniedMessage = "&cДоступ запрещен";
        
        // Права по умолчанию (когда LuckPerms недоступен)
        public boolean defaultCanUseGlobalChat = false;
        public boolean defaultCanUseLocalChat = false;
        public boolean defaultCanMutePlayers = false;
        public boolean defaultCanUnmutePlayers = false;
        public boolean defaultCanReloadConfig = false;
        public boolean defaultCanViewMutes = false;
        public boolean defaultCanBypassAntiSpam = false;
        public boolean defaultCanBypassMute = false;
        public boolean defaultCanUseCommands = false;
        public boolean defaultCanReceiveModerationNotifications = false;
        
        // Настройки кэширования прав
        public boolean enablePermissionCache = true;
        public int permissionCacheTimeout = 300; // секунды (5 минут)
        public boolean debugPermissionLogging = false; // Включить подробное логирование прав
    }
    
    public void load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
        File configFile = configPath.toFile();
        
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                ChatManagerConfig loaded = GSON.fromJson(reader, ChatManagerConfig.class);
                this.chat = loaded.chat;
                this.antiSpam = loaded.antiSpam;
                this.notifications = loaded.notifications;
                this.autoBroadcast = loaded.autoBroadcast;
                this.moderation = loaded.moderation;
                this.permissions = loaded.permissions;
                ChatManagerMod.LOGGER.info("ChatManager config loaded successfully");
            } catch (IOException e) {
                ChatManagerMod.LOGGER.error("Failed to load ChatManager config", e);
            }
        } else {
            save();
            ChatManagerMod.LOGGER.info("Created default ChatManager config");
        }
    }
    
    public void save() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
        File configFile = configPath.toFile();
        
        try {
            if (!configFile.exists()) {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
            }
            
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(this, writer);
                ChatManagerMod.LOGGER.info("ChatManager config saved successfully");
            }
        } catch (IOException e) {
            ChatManagerMod.LOGGER.error("Failed to save ChatManager config", e);
        }
    }
} 
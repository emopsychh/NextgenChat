package com.nextgenchat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nextgenchat.NextgenChatMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class NextgenChatConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String CONFIG_FILE = "nextgenchat.json";

	public ChatSettings chat = new ChatSettings();
	public AntiSpamSettings antiSpam = new AntiSpamSettings();
	public NotificationSettings notifications = new NotificationSettings();
	public TimingSettings timing = new TimingSettings();
	public AutoBroadcastSettings autoBroadcast = new AutoBroadcastSettings();
	public ModerationSettings moderation = new ModerationSettings();
	public PermissionSettings permissions = new PermissionSettings();
	public CommandMessages commandMessages = new CommandMessages();

	public static class ChatSettings {
		public boolean enableLocalChat = true;
		public boolean enableGlobalChat = true;
		public int localChatRadius = 100;
		public String globalChatSymbol = "!";
		public String globalChatFormat = "&6[G] &f{player}: &7{message}";
		public String localChatFormat = "&7[Локальный] &f{player}: &7{message}";
	}

	public static class AntiSpamSettings {
		public boolean enableAntiSpam = true;
		public boolean enableAntiFlood = true;
		public int messageCooldown = 3;
		public int maxRepeatedMessages = 3;
		public int floodThreshold = 5;
		public int floodTimeWindow = 10;
		public String cooldownMessage = "&cПодождите перед отправкой следующего сообщения.";
		public String repeatMessage = "&cНе повторяйте одно и то же сообщение.";
		public String floodMessage = "&cВы отправляете сообщения слишком быстро.";
	}

	public static class NotificationSettings {
		public boolean enableJoinMessages = true;
		public boolean enableQuitMessages = true;
		public String joinMessage = "&a+ {player} присоединился к серверу";
		public String quitMessage = "&c- {player} покинул сервер";
	}

	public static class TimingSettings {
		public int joinMessageDelayTicks = 5;
		public int quitMessageDelayTicks = 3;
		public int muteCleanupIntervalTicks = 100;
		public int ticksPerSecond = 20;
	}

	public static class AutoBroadcastSettings {
		public boolean enableAutoBroadcast = true;
		public int broadcastInterval = 300;
		public boolean randomizeMessages = false;
		public boolean showBroadcastPrefix = false;
		public String broadcastPrefix = "&d[Автобродкаст] &r";
		public String[] broadcastMessages = {
			"&6&lДобро пожаловать на сервер!",
			"&eНе забудьте прочитать правила!",
			"&aПриятной игры!",
			"&bОнлайн: &f{online}&b/&f{max_online}",
			"&dСервер работает стабильно!",
			"&7Память: &f{memory_used}MB&7/&f{memory_max}MB",
			"&3Uptime: &f{uptime_hours}&3ч &f{uptime_minutes}&3м",
			"&5Сервер: &f{server_name}",
			"&2TPS: &f{tps}"
		};
	}

	public static class ModerationSettings {
		public boolean enableModeration = true;
		public boolean saveMuteData = true;
		public String defaultMuteReason = "Нарушение правил";
		public String staffNotificationPrefix = "&e[Модерация] ";
		public String muteMessage = "&cВы заблокированы в чате на {duration}. Причина: {reason}";
		public String unmuteMessage = "&aВы разблокированы в чате";
		public String muteNotification = "&c{player} заблокирован в чате на {duration}";
		public String unmuteNotification = "&a{player} разблокирован в чате";
		public String alreadyMutedMessage = "&c{player} уже заблокирован в чате";
		public String notMutedMessage = "&c{player} не заблокирован в чате";
		public String invalidDurationMessage = "&cНеверный формат времени. Используйте: 30m, 2h, 1d, 1w";
		public String defaultMuteDuration = "1h";
		public boolean notifyStaffOnMute = true;
		public boolean logMuteActions = true;
	}

	public static class PermissionSettings {
		public boolean enablePermissionSystem = true;
		public String noPermissionMessage = "&cУ вас нет права {permission} для выполнения этого действия";
		public boolean defaultCanUseGlobalChat = true;
		public boolean defaultCanUseLocalChat = true;
		public boolean defaultCanMutePlayers = false;
		public boolean defaultCanUnmutePlayers = false;
		public boolean defaultCanReloadConfig = false;
		public boolean defaultCanViewMutes = false;
		public boolean defaultCanBypassAntiSpam = false;
		public boolean defaultCanBypassMute = false;
		public boolean defaultCanUseCommands = false;
		public boolean defaultCanReceiveModerationNotifications = false;
		public boolean enablePermissionCache = true;
		public int permissionCacheTimeout = 300;
		public boolean debugPermissionLogging = false;
	}

	public static class CommandMessages {
		public String reloadSuccess = "&aКонфиг NextgenChat перезагружен!";
		public String permissionsCacheCleared = "&aКэш прав NextgenChat очищен!";
		public String luckPermsAvailable = "&aдоступен";
		public String luckPermsUnavailable = "&cнедоступен";
		public String luckPermsStatus = "&eLuckPerms: {status}";
		public String broadcastSent = "&aСообщение автобродкаста отправлено!";
		public String broadcastDisabled = "&cАвтобродкаст отключен или нет сообщений!";
		public String broadcastToggled = "&eАвтобродкаст {status}&e!";
		public String broadcastStatus = "&eАвтобродкаст: {status}&e, интервал: &f{interval}с&e, сообщений: &f{count}";
		public String statusEnabled = "&aвключен";
		public String statusDisabled = "&cвыключен";
		public String playersOnly = "&cЭта команда доступна только игрокам!";
		public String noMutedPlayers = "&eНет заблокированных игроков";
		public String mutedPlayersHeader = "&eЗаблокированные игроки:";
		public String mutedPlayerEntry = "&c{player} - {reason} (осталось: {remaining})";
		public String chatDisabled = "&cЭтот режим чата отключен на сервере.";
	}

	public void load() {
		Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
		File configFile = configPath.toFile();

		if (configFile.exists()) {
			try (FileReader reader = new FileReader(configFile)) {
				NextgenChatConfig loaded = GSON.fromJson(reader, NextgenChatConfig.class);
				if (loaded != null) {
					this.chat = loaded.chat != null ? loaded.chat : this.chat;
					this.antiSpam = loaded.antiSpam != null ? loaded.antiSpam : this.antiSpam;
					this.notifications = loaded.notifications != null ? loaded.notifications : this.notifications;
					this.timing = loaded.timing != null ? loaded.timing : this.timing;
					this.autoBroadcast = loaded.autoBroadcast != null ? loaded.autoBroadcast : this.autoBroadcast;
					this.moderation = loaded.moderation != null ? loaded.moderation : this.moderation;
					this.permissions = loaded.permissions != null ? loaded.permissions : this.permissions;
					this.commandMessages = loaded.commandMessages != null ? loaded.commandMessages : this.commandMessages;
				}
				NextgenChatMod.LOGGER.info("NextgenChat config loaded");
			} catch (IOException e) {
				NextgenChatMod.LOGGER.error("Failed to load NextgenChat config", e);
			}
		} else {
			save();
			NextgenChatMod.LOGGER.info("Created default NextgenChat config");
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
			}
		} catch (IOException e) {
			NextgenChatMod.LOGGER.error("Failed to save NextgenChat config", e);
		}
	}
}

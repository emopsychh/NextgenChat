package com.nextgenchat.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nextgenchat.NextgenChatMod;
import com.nextgenchat.config.NextgenChatConfig;
import com.nextgenchat.permission.PermissionService;
import com.nextgenchat.util.TextUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModerationService {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String MUTE_DATA_FILE = "nextgenchat_mutes.json";
	private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([hdmw])");
	public static final UUID CONSOLE_ACTOR_ID = new UUID(0, 0);

	private final NextgenChatConfig config;
	private final PermissionService permissions;
	private final ConcurrentHashMap<UUID, MuteData> mutedPlayers = new ConcurrentHashMap<>();

	public ModerationService(NextgenChatConfig config, PermissionService permissions) {
		this.config = config;
		this.permissions = permissions;
		loadMuteData();
	}

	public boolean mutePlayer(ServerPlayer target, Actor actor, String duration, String reason) {
		if (!config.moderation.enableModeration) {
			return false;
		}

		UUID targetId = target.getUUID();
		if (mutedPlayers.containsKey(targetId)) {
			actor.sendMessage(config.moderation.alreadyMutedMessage.replace("{player}", target.getName().getString()));
			return false;
		}

		long muteDuration = parseDuration(duration);
		if (muteDuration <= 0) {
			actor.sendMessage(config.moderation.invalidDurationMessage);
			return false;
		}

		String resolvedReason = reason != null && !reason.isBlank()
			? reason
			: config.moderation.defaultMuteReason;

		long now = System.currentTimeMillis();
		MuteData muteData = new MuteData(
			targetId,
			target.getName().getString(),
			actor.id(),
			actor.name(),
			now,
			muteDuration,
			resolvedReason
		);

		mutedPlayers.put(targetId, muteData);
		persistIfNeeded();

		notifyMute(target, actor, muteData);

		if (config.moderation.logMuteActions) {
			NextgenChatMod.LOGGER.info("Player {} muted by {} for {}. Reason: {}",
				target.getName().getString(), actor.name(), formatDuration(muteDuration), resolvedReason);
		}

		return true;
	}

	public boolean unmutePlayer(ServerPlayer target, Actor actor) {
		if (!config.moderation.enableModeration) {
			return false;
		}

		UUID targetId = target.getUUID();
		if (!mutedPlayers.containsKey(targetId)) {
			actor.sendMessage(config.moderation.notMutedMessage.replace("{player}", target.getName().getString()));
			return false;
		}

		MuteData removed = mutedPlayers.remove(targetId);
		persistIfNeeded();
		notifyUnmute(target, actor, removed);

		if (config.moderation.logMuteActions) {
			NextgenChatMod.LOGGER.info("Player {} unmuted by {}", target.getName().getString(), actor.name());
		}

		return true;
	}

	public boolean isMuted(UUID playerId) {
		if (!config.moderation.enableModeration) {
			return false;
		}

		MuteData muteData = mutedPlayers.get(playerId);
		if (muteData == null) {
			return false;
		}

		if (System.currentTimeMillis() >= muteData.expiresAt()) {
			mutedPlayers.remove(playerId);
			persistIfNeeded();
			return false;
		}

		return true;
	}

	public MuteData getMuteData(UUID playerId) {
		MuteData muteData = mutedPlayers.get(playerId);
		if (muteData == null || System.currentTimeMillis() >= muteData.expiresAt()) {
			return null;
		}
		return muteData;
	}

	public Collection<MuteData> getActiveMutes() {
		long now = System.currentTimeMillis();
		return mutedPlayers.values().stream()
			.filter(mute -> now < mute.expiresAt())
			.toList();
	}

	public void cleanupExpiredMutes() {
		long now = System.currentTimeMillis();
		boolean removed = mutedPlayers.entrySet().removeIf(entry -> {
			boolean expired = now >= entry.getValue().expiresAt();
			if (expired && config.moderation.logMuteActions) {
				NextgenChatMod.LOGGER.info("Mute expired for player: {}", entry.getValue().playerName);
			}
			return expired;
		});

		if (removed) {
			persistIfNeeded();
		}
	}

	public String formatDuration(long milliseconds) {
		long seconds = Math.max(0, milliseconds) / 1000;
		long minutes = seconds / 60;
		long hours = minutes / 60;
		long days = hours / 24;

		if (days > 0) {
			return days + "д " + (hours % 24) + "ч";
		}
		if (hours > 0) {
			return hours + "ч " + (minutes % 60) + "м";
		}
		if (minutes > 0) {
			return minutes + "м";
		}
		return seconds + "с";
	}

	private long parseDuration(String duration) {
		String value = duration == null || duration.isBlank()
			? config.moderation.defaultMuteDuration
			: duration;

		Matcher matcher = TIME_PATTERN.matcher(value.toLowerCase());
		if (!matcher.matches()) {
			return -1;
		}

		long amount = Long.parseLong(matcher.group(1));
		return switch (matcher.group(2)) {
			case "m" -> amount * 60_000L;
			case "h" -> amount * 3_600_000L;
			case "d" -> amount * 86_400_000L;
			case "w" -> amount * 604_800_000L;
			default -> -1;
		};
	}

	private void notifyMute(ServerPlayer target, Actor actor, MuteData muteData) {
		long durationMs = muteData.duration;
		target.sendSystemMessage(TextUtils.toComponent(config.moderation.muteMessage
			.replace("{duration}", formatDuration(durationMs))
			.replace("{reason}", muteData.reason)));

		String notification = config.moderation.muteNotification
			.replace("{player}", target.getName().getString())
			.replace("{duration}", formatDuration(durationMs));
		actor.sendMessage(notification);
		broadcastStaffNotification(notification, target.level().getServer());
	}

	private void notifyUnmute(ServerPlayer target, Actor actor, MuteData muteData) {
		target.sendSystemMessage(TextUtils.toComponent(config.moderation.unmuteMessage));

		String notification = config.moderation.unmuteNotification
			.replace("{player}", target.getName().getString());
		actor.sendMessage(notification);
		broadcastStaffNotification(notification, target.level().getServer());
	}

	private void broadcastStaffNotification(String notification, net.minecraft.server.MinecraftServer server) {
		if (!config.moderation.notifyStaffOnMute || server == null) {
			return;
		}

		String staffMessage = config.moderation.staffNotificationPrefix + notification;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (permissions.canReceiveModerationNotifications(player)) {
				player.sendSystemMessage(TextUtils.toComponent(staffMessage));
			}
		}
	}

	private void persistIfNeeded() {
		if (config.moderation.saveMuteData) {
			saveMuteData();
		}
	}

	private void loadMuteData() {
		if (!config.moderation.saveMuteData) {
			return;
		}

		Path dataPath = FabricLoader.getInstance().getGameDir().resolve(MUTE_DATA_FILE);
		File dataFile = dataPath.toFile();
		if (!dataFile.exists()) {
			return;
		}

		try (FileReader reader = new FileReader(dataFile)) {
			MuteData[] loadedMutes = GSON.fromJson(reader, MuteData[].class);
			if (loadedMutes == null) {
				return;
			}

			long now = System.currentTimeMillis();
			for (MuteData muteData : loadedMutes) {
				if (now < muteData.expiresAt()) {
					mutedPlayers.put(muteData.playerUUID, muteData);
				}
			}
			NextgenChatMod.LOGGER.info("Loaded {} active mutes", mutedPlayers.size());
		} catch (IOException e) {
			NextgenChatMod.LOGGER.error("Failed to load mute data", e);
		}
	}

	private void saveMuteData() {
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
			NextgenChatMod.LOGGER.error("Failed to save mute data", e);
		}
	}

	public static class MuteData {
		public UUID playerUUID;
		public String playerName;
		public UUID moderatorUUID;
		public String moderatorName;
		public long muteTime;
		public long duration;
		public String reason;

		public MuteData() {
		}

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

		public long expiresAt() {
			return muteTime + duration;
		}
	}

	public interface Actor {
		UUID id();

		String name();

		void sendMessage(String message);

		static Actor fromPlayer(ServerPlayer player) {
			return new Actor() {
				@Override
				public UUID id() {
					return player.getUUID();
				}

				@Override
				public String name() {
					return player.getName().getString();
				}

				@Override
				public void sendMessage(String message) {
					player.sendSystemMessage(TextUtils.toComponent(message));
				}
			};
		}

		static Actor console() {
			return new Actor() {
				@Override
				public UUID id() {
					return CONSOLE_ACTOR_ID;
				}

				@Override
				public String name() {
					return "Console";
				}

				@Override
				public void sendMessage(String message) {
					NextgenChatMod.LOGGER.info("[Console] {}", TextUtils.applyColorCodes(message).replace("§", "&"));
				}
			};
		}
	}
}

package com.nextgenchat.permission;

import com.nextgenchat.NextgenChatMod;
import com.nextgenchat.config.NextgenChatConfig;
import com.nextgenchat.luckperms.LuckPermsBridge;
import com.nextgenchat.util.TextUtils;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionService {
	private final NextgenChatConfig config;
	private final LuckPermsBridge luckPerms;
	private final ConcurrentHashMap<UUID, CachedPermissions> cache = new ConcurrentHashMap<>();

	public PermissionService(NextgenChatConfig config, LuckPermsBridge luckPerms) {
		this.config = config;
		this.luckPerms = luckPerms;
		this.luckPerms.setCacheInvalidator(this::clearPlayer);
	}

	public boolean canUseGlobalChat(ServerPlayer player) {
		return resolve(player).canUseGlobalChat;
	}

	public boolean canUseLocalChat(ServerPlayer player) {
		return resolve(player).canUseLocalChat;
	}

	public boolean canMutePlayers(ServerPlayer player) {
		return resolve(player).canMutePlayers;
	}

	public boolean canUnmutePlayers(ServerPlayer player) {
		return resolve(player).canUnmutePlayers;
	}

	public boolean canViewMutes(ServerPlayer player) {
		return resolve(player).canViewMutes;
	}

	public boolean canBypassAntiSpam(ServerPlayer player) {
		return resolve(player).canBypassAntiSpam;
	}

	public boolean canBypassMute(ServerPlayer player) {
		return resolve(player).canBypassMute;
	}

	public boolean canReceiveModerationNotifications(ServerPlayer player) {
		return resolve(player).canReceiveModerationNotifications;
	}

	public boolean canUseReloadCommand(ServerPlayer player) {
		return hasCommandPermission(player, "nextgenchat.command.reload", resolve(player).canReloadConfig);
	}

	public boolean canUseBroadcastCommand(ServerPlayer player) {
		return hasCommandPermission(player, "nextgenchat.command.broadcast", resolve(player).canUseCommands);
	}

	public boolean canUsePermissionsCommand(ServerPlayer player) {
		return hasCommandPermission(player, "nextgenchat.command.permissions", resolve(player).canUseCommands);
	}

	public void sendNoPermissionMessage(ServerPlayer player, String permission) {
		String message = config.permissions.noPermissionMessage
			.replace("{permission}", permission)
			.replace("{player}", player.getName().getString());
		player.sendSystemMessage(TextUtils.toComponent(message));
	}

	public void clearPlayer(UUID playerId) {
		cache.remove(playerId);
	}

	public void clearAll() {
		cache.clear();
	}

	private boolean hasCommandPermission(ServerPlayer player, String permissionNode, boolean fallbackPermission) {
		if (!config.permissions.enablePermissionSystem) {
			return true;
		}

		CachedPermissions permissions = resolve(player);
		if (permissions.canUseCommands) {
			return true;
		}

		if (luckPerms.isAvailable()) {
			return luckPerms.hasPermission(player, permissionNode);
		}

		return fallbackPermission;
	}

	private CachedPermissions resolve(ServerPlayer player) {
		if (!config.permissions.enablePermissionSystem) {
			return CachedPermissions.allAllowed();
		}

		if (!config.permissions.enablePermissionCache) {
			return loadPermissions(player);
		}

		long now = System.currentTimeMillis();
		CachedPermissions cached = cache.get(player.getUUID());
		if (cached != null && now - cached.cachedAtMs < config.permissions.permissionCacheTimeout * 1000L) {
			return cached;
		}

		CachedPermissions loaded = loadPermissions(player);
		cache.put(player.getUUID(), loaded);
		return loaded;
	}

	private CachedPermissions loadPermissions(ServerPlayer player) {
		if (luckPerms.isAvailable()) {
			CachedPermissions permissions = new CachedPermissions(
				luckPerms.hasPermission(player, "nextgenchat.chat.global"),
				luckPerms.hasPermission(player, "nextgenchat.chat.local"),
				luckPerms.hasPermission(player, "nextgenchat.moderate.mute"),
				luckPerms.hasPermission(player, "nextgenchat.moderate.unmute"),
				luckPerms.hasPermission(player, "nextgenchat.admin.reload"),
				luckPerms.hasPermission(player, "nextgenchat.moderate.view"),
				luckPerms.hasPermission(player, "nextgenchat.bypass.antispam"),
				luckPerms.hasPermission(player, "nextgenchat.bypass.mute"),
				luckPerms.hasPermission(player, "nextgenchat.commands"),
				luckPerms.hasPermission(player, "nextgenchat.notifications.moderation"),
				System.currentTimeMillis()
			);

			if (config.permissions.debugPermissionLogging) {
				NextgenChatMod.LOGGER.info("[PERM] {} -> global={}, local={}, mute={}, bypassMute={}",
					player.getName().getString(),
					permissions.canUseGlobalChat,
					permissions.canUseLocalChat,
					permissions.canMutePlayers,
					permissions.canBypassMute);
			}

			return permissions;
		}

		NextgenChatConfig.PermissionSettings defaults = config.permissions;
		return new CachedPermissions(
			defaults.defaultCanUseGlobalChat,
			defaults.defaultCanUseLocalChat,
			defaults.defaultCanMutePlayers,
			defaults.defaultCanUnmutePlayers,
			defaults.defaultCanReloadConfig,
			defaults.defaultCanViewMutes,
			defaults.defaultCanBypassAntiSpam,
			defaults.defaultCanBypassMute,
			defaults.defaultCanUseCommands,
			defaults.defaultCanReceiveModerationNotifications,
			System.currentTimeMillis()
		);
	}

	public record CachedPermissions(
		boolean canUseGlobalChat,
		boolean canUseLocalChat,
		boolean canMutePlayers,
		boolean canUnmutePlayers,
		boolean canReloadConfig,
		boolean canViewMutes,
		boolean canBypassAntiSpam,
		boolean canBypassMute,
		boolean canUseCommands,
		boolean canReceiveModerationNotifications,
		long cachedAtMs
	) {
		static CachedPermissions allAllowed() {
			return new CachedPermissions(true, true, true, true, true, true, true, true, true, true, System.currentTimeMillis());
		}
	}
}

package com.nextgenchat.chat;

import com.nextgenchat.config.NextgenChatConfig;
import com.nextgenchat.luckperms.LuckPermsBridge;
import com.nextgenchat.moderation.ModerationService;
import com.nextgenchat.permission.PermissionService;
import com.nextgenchat.util.TextUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class ChatService {
	public enum ChatMode {
		LOCAL,
		GLOBAL
	}

	private final NextgenChatConfig config;
	private final PermissionService permissions;
	private final ModerationService moderation;
	private final AntiSpamService antiSpam;
	private final LuckPermsBridge luckPerms;

	public ChatService(
		NextgenChatConfig config,
		PermissionService permissions,
		ModerationService moderation,
		AntiSpamService antiSpam,
		LuckPermsBridge luckPerms
	) {
		this.config = config;
		this.permissions = permissions;
		this.moderation = moderation;
		this.antiSpam = antiSpam;
		this.luckPerms = luckPerms;
	}

	public void handleMessage(ServerPlayer player, String message) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}

		if (moderation.isMuted(player.getUUID()) && !permissions.canBypassMute(player)) {
			sendMuteNotice(player);
			return;
		}

		ChatMode mode = resolveMode(message);
		if (mode == ChatMode.GLOBAL && !config.chat.enableGlobalChat) {
			player.sendSystemMessage(TextUtils.toComponent(config.commandMessages.chatDisabled));
			return;
		}
		if (mode == ChatMode.LOCAL && !config.chat.enableLocalChat) {
			player.sendSystemMessage(TextUtils.toComponent(config.commandMessages.chatDisabled));
			return;
		}

		if (mode == ChatMode.GLOBAL && !permissions.canUseGlobalChat(player)) {
			permissions.sendNoPermissionMessage(player, "nextgenchat.chat.global");
			return;
		}
		if (mode == ChatMode.LOCAL && !permissions.canUseLocalChat(player)) {
			permissions.sendNoPermissionMessage(player, "nextgenchat.chat.local");
			return;
		}

		Optional<String> spamMessage = antiSpam.validateMessage(
			player.getUUID(),
			message,
			permissions.canBypassAntiSpam(player)
		);
		if (spamMessage.isPresent()) {
			player.sendSystemMessage(TextUtils.toComponent(spamMessage.get()));
			return;
		}

		String content = stripModePrefix(message, mode);
		Component formatted = TextUtils.toComponent(formatMessage(player, content, mode));
		dispatchMessage(player, formatted, mode);
	}

	public void onPlayerDisconnect(ServerPlayer player) {
		antiSpam.clearPlayer(player.getUUID());
		permissions.clearPlayer(player.getUUID());
	}

	private ChatMode resolveMode(String message) {
		if (!config.chat.globalChatSymbol.isEmpty() && message.startsWith(config.chat.globalChatSymbol)) {
			return ChatMode.GLOBAL;
		}
		return ChatMode.LOCAL;
	}

	private String stripModePrefix(String message, ChatMode mode) {
		if (mode == ChatMode.GLOBAL && message.startsWith(config.chat.globalChatSymbol)) {
			return message.substring(config.chat.globalChatSymbol.length()).trim();
		}
		return message;
	}

	private void dispatchMessage(ServerPlayer sender, Component message, ChatMode mode) {
		MinecraftServer server = sender.level().getServer();
		if (server == null) {
			return;
		}

		if (mode == ChatMode.GLOBAL) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.sendSystemMessage(message);
			}
			return;
		}

		Vec3 senderPos = sender.position();
		double radiusSquared = (double) config.chat.localChatRadius * config.chat.localChatRadius;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level() == sender.level() && player.position().distanceToSqr(senderPos) <= radiusSquared) {
				player.sendSystemMessage(message);
			}
		}
	}

	private String formatMessage(ServerPlayer player, String message, ChatMode mode) {
		String format = mode == ChatMode.GLOBAL ? config.chat.globalChatFormat : config.chat.localChatFormat;
		String formatted = format
			.replace("{player}", player.getName().getString())
			.replace("{message}", message);
		return luckPerms.replacePlaceholders(formatted, player);
	}

	private void sendMuteNotice(ServerPlayer player) {
		ModerationService.MuteData muteData = moderation.getMuteData(player.getUUID());
		if (muteData == null) {
			return;
		}

		long remainingMs = muteData.expiresAt() - System.currentTimeMillis();
		String text = config.moderation.muteMessage
			.replace("{duration}", moderation.formatDuration(remainingMs))
			.replace("{reason}", muteData.reason);
		player.sendSystemMessage(TextUtils.toComponent(text));
	}
}

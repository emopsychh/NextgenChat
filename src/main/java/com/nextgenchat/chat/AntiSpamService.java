package com.nextgenchat.chat;

import com.nextgenchat.config.NextgenChatConfig;
import com.nextgenchat.permission.PermissionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiSpamService {
	private final NextgenChatConfig config;
	private final PermissionService permissions;
	private final Map<UUID, PlayerSpamState> states = new ConcurrentHashMap<>();

	public AntiSpamService(NextgenChatConfig config, PermissionService permissions) {
		this.config = config;
		this.permissions = permissions;
	}

	public Optional<String> validateMessage(UUID playerId, String message, boolean bypassPermissions) {
		if (!config.antiSpam.enableAntiSpam) {
			return Optional.empty();
		}

		PlayerSpamState state = states.computeIfAbsent(playerId, ignored -> new PlayerSpamState());
		long now = System.currentTimeMillis();

		if (!bypassPermissions && config.antiSpam.messageCooldown > 0) {
			long cooldownMs = config.antiSpam.messageCooldown * 1000L;
			if (now - state.lastMessageAtMs < cooldownMs) {
				return Optional.of(config.antiSpam.cooldownMessage);
			}
		}

		String normalized = message.trim();
		if (!normalized.isEmpty() && normalized.equals(state.lastMessage)) {
			state.repeatCount++;
			if (state.repeatCount >= config.antiSpam.maxRepeatedMessages) {
				return Optional.of(config.antiSpam.repeatMessage);
			}
		} else {
			state.lastMessage = normalized;
			state.repeatCount = 1;
		}

		if (config.antiSpam.enableAntiFlood) {
			state.recentMessages.removeIf(timestamp -> now - timestamp > config.antiSpam.floodTimeWindow * 1000L);
			state.recentMessages.add(now);
			if (state.recentMessages.size() >= config.antiSpam.floodThreshold) {
				return Optional.of(config.antiSpam.floodMessage);
			}
		}

		state.lastMessageAtMs = now;
		return Optional.empty();
	}

	public void clearPlayer(UUID playerId) {
		states.remove(playerId);
	}

	private static final class PlayerSpamState {
		private long lastMessageAtMs;
		private String lastMessage = "";
		private int repeatCount;
		private final List<Long> recentMessages = new ArrayList<>();
	}
}

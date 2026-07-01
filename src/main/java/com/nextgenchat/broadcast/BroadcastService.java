package com.nextgenchat.broadcast;

import com.nextgenchat.config.NextgenChatConfig;
import com.nextgenchat.util.TextUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public class BroadcastService {
	private final NextgenChatConfig config;
	private int tickCounter;
	private int currentIndex;

	public BroadcastService(NextgenChatConfig config) {
		this.config = config;
	}

	public void reset() {
		tickCounter = 0;
		currentIndex = 0;
	}

	public void tick(MinecraftServer server, long serverStartedAtMs) {
		if (!config.autoBroadcast.enableAutoBroadcast || config.autoBroadcast.broadcastMessages.length == 0) {
			return;
		}

		tickCounter++;
		int intervalTicks = config.autoBroadcast.broadcastInterval * config.timing.ticksPerSecond;
		if (tickCounter < intervalTicks) {
			return;
		}

		sendNext(server, serverStartedAtMs);
		tickCounter = 0;
	}

	public boolean sendNext(MinecraftServer server, long serverStartedAtMs) {
		if (!config.autoBroadcast.enableAutoBroadcast || config.autoBroadcast.broadcastMessages.length == 0) {
			return false;
		}

		String message = pickMessage();
		server.getPlayerList().broadcastSystemMessage(buildMessage(message, server, serverStartedAtMs), false);
		return true;
	}

	private String pickMessage() {
		if (config.autoBroadcast.randomizeMessages) {
			int index = (int) (Math.random() * config.autoBroadcast.broadcastMessages.length);
			return config.autoBroadcast.broadcastMessages[index];
		}

		String message = config.autoBroadcast.broadcastMessages[currentIndex];
		currentIndex = (currentIndex + 1) % config.autoBroadcast.broadcastMessages.length;
		return message;
	}

	public Component buildMessage(String template, MinecraftServer server, long serverStartedAtMs) {
		String resolved = replacePlaceholders(template, server, serverStartedAtMs);
		if (config.autoBroadcast.showBroadcastPrefix) {
			resolved = config.autoBroadcast.broadcastPrefix + resolved;
		}
		return TextUtils.toComponent(resolved);
	}

	public String replacePlaceholders(String message, MinecraftServer server, long serverStartedAtMs) {
		int onlinePlayers = server.getPlayerList().getPlayerCount();
		int maxPlayers = server.getPlayerList().getMaxPlayers();
		String serverName = server.getMotd() != null ? server.getMotd() : server.getServerModName();

		long uptimeMs = Math.max(0, System.currentTimeMillis() - serverStartedAtMs);
		long uptimeHours = uptimeMs / (1000L * 60 * 60);
		long uptimeMinutes = (uptimeMs / (1000L * 60)) % 60;

		long usedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
		long maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);

		double tps = calculateTps(server, serverStartedAtMs);

		return message
			.replace("{online}", String.valueOf(onlinePlayers))
			.replace("{max_online}", String.valueOf(maxPlayers))
			.replace("{server_name}", serverName)
			.replace("{uptime_hours}", String.valueOf(uptimeHours))
			.replace("{uptime_minutes}", String.valueOf(uptimeMinutes))
			.replace("{memory_used}", String.valueOf(usedMb))
			.replace("{memory_max}", String.valueOf(maxMb))
			.replace("{tps}", String.format("%.1f", tps));
	}

	private double calculateTps(MinecraftServer server, long serverStartedAtMs) {
		long uptimeMs = Math.max(1, System.currentTimeMillis() - serverStartedAtMs);
		double seconds = uptimeMs / 1000.0;
		double measured = server.getTickCount() / seconds;
		return Math.min(config.timing.ticksPerSecond, measured);
	}
}

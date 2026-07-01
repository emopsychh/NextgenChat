package com.nextgenchat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nextgenchat.broadcast.BroadcastService;
import com.nextgenchat.chat.AntiSpamService;
import com.nextgenchat.chat.ChatService;
import com.nextgenchat.command.HelpService;
import com.nextgenchat.config.NextgenChatConfig;
import com.nextgenchat.luckperms.LuckPermsBridge;
import com.nextgenchat.moderation.ModerationService;
import com.nextgenchat.permission.PermissionService;
import com.nextgenchat.util.TextUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class NextgenChatMod implements ModInitializer {
	public static final String MOD_ID = "nextgenchat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static NextgenChatMod instance;

	private final NextgenChatConfig config = new NextgenChatConfig();
	private LuckPermsBridge luckPerms;
	private PermissionService permissions;
	private ModerationService moderation;
	private AntiSpamService antiSpam;
	private ChatService chat;
	private BroadcastService broadcast;

	private long serverStartedAtMs;
	private final ConcurrentLinkedQueue<DelayedMessage> delayedMessages = new ConcurrentLinkedQueue<>();

	@Override
	public void onInitialize() {
		instance = this;
		config.load();

		luckPerms = new LuckPermsBridge();
		permissions = new PermissionService(config, luckPerms);
		moderation = new ModerationService(config, permissions);
		antiSpam = new AntiSpamService(config, permissions);
		chat = new ChatService(config, permissions, moderation, antiSpam, luckPerms);
		broadcast = new BroadcastService(config);

		registerLifecycleEvents();
		registerConnectionEvents();
		registerCommands();

		LOGGER.info("NextgenChat initialized");
	}

	public static NextgenChatMod getInstance() {
		return instance;
	}

	public NextgenChatConfig config() {
		return config;
	}

	public LuckPermsBridge luckPerms() {
		return luckPerms;
	}

	public PermissionService permissions() {
		return permissions;
	}

	public ModerationService moderation() {
		return moderation;
	}

	public ChatService chat() {
		return chat;
	}

	public BroadcastService broadcast() {
		return broadcast;
	}

	public String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("dev");
	}

	private void registerLifecycleEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			serverStartedAtMs = System.currentTimeMillis();
			broadcast.reset();
			LOGGER.info("NextgenChat ready on server");
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			processDelayedMessages(server.getPlayerList()::broadcastSystemMessage);

			if (serverStartedAtMs > 0) {
				broadcast.tick(server, serverStartedAtMs);
			}

			if (server.getTickCount() % config.timing.muteCleanupIntervalTicks == 0) {
				moderation.cleanupExpiredMutes();
			}
		});
	}

	private void registerConnectionEvents() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!config.notifications.enableJoinMessages) {
				return;
			}

			ServerPlayer player = handler.getPlayer();
			String message = luckPerms.replacePlaceholders(
				config.notifications.joinMessage.replace("{player}", player.getName().getString()),
				player
			);
			delayedMessages.offer(new DelayedMessage(TextUtils.toComponent(message), config.timing.joinMessageDelayTicks));
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.getPlayer();
			chat.onPlayerDisconnect(player);

			if (!config.notifications.enableQuitMessages) {
				return;
			}

			String message = luckPerms.replacePlaceholders(
				config.notifications.quitMessage.replace("{player}", player.getName().getString()),
				player
			);
			delayedMessages.offer(new DelayedMessage(TextUtils.toComponent(message), config.timing.quitMessageDelayTicks));
		});
	}

	private void processDelayedMessages(BroadcastConsumer consumer) {
		DelayedMessage current;
		while ((current = delayedMessages.peek()) != null) {
			if (current.ticksRemaining > 0) {
				current.ticksRemaining--;
				break;
			}

			delayedMessages.poll();
			consumer.broadcast(current.message, false);
		}
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("nextgenchat")
				.executes(ctx -> {
					HelpService.sendHelp(ctx.getSource(), this);
					return Command.SINGLE_SUCCESS;
				})
				.then(literal("help").executes(ctx -> {
					HelpService.sendHelp(ctx.getSource(), this);
					return Command.SINGLE_SUCCESS;
				}))
				.then(literal("reload")
					.requires(source -> canReload(source))
					.executes(ctx -> {
						config.load();
						permissions.clearAll();
						ctx.getSource().sendSuccess(() -> TextUtils.toComponent(config.commandMessages.reloadSuccess), false);
						return Command.SINGLE_SUCCESS;
					}))
				.then(literal("permissions")
					.requires(source -> canManagePermissions(source))
					.then(literal("reload").executes(ctx -> {
						permissions.clearAll();
						ctx.getSource().sendSuccess(() -> TextUtils.toComponent(config.commandMessages.permissionsCacheCleared), false);
						return Command.SINGLE_SUCCESS;
					}))
					.then(literal("status").executes(ctx -> {
						String status = luckPerms.isAvailable()
							? config.commandMessages.luckPermsAvailable
							: config.commandMessages.luckPermsUnavailable;
						String message = config.commandMessages.luckPermsStatus.replace("{status}", status);
						ctx.getSource().sendSuccess(() -> TextUtils.toComponent(message), false);
						return Command.SINGLE_SUCCESS;
					})))
				.then(literal("broadcast")
					.requires(source -> canBroadcast(source))
					.executes(ctx -> {
						boolean sent = broadcast.sendNext(ctx.getSource().getServer(), serverStartedAtMs);
						String message = sent ? config.commandMessages.broadcastSent : config.commandMessages.broadcastDisabled;
						ctx.getSource().sendSuccess(() -> TextUtils.toComponent(message), false);
						return Command.SINGLE_SUCCESS;
					})
					.then(literal("toggle").executes(ctx -> {
						config.autoBroadcast.enableAutoBroadcast = !config.autoBroadcast.enableAutoBroadcast;
						String status = config.autoBroadcast.enableAutoBroadcast
							? config.commandMessages.statusEnabled
							: config.commandMessages.statusDisabled;
						ctx.getSource().sendSuccess(() -> TextUtils.toComponent(
							config.commandMessages.broadcastToggled.replace("{status}", status)), false);
						config.save();
						return Command.SINGLE_SUCCESS;
					}))
					.then(literal("status").executes(ctx -> {
						String status = config.autoBroadcast.enableAutoBroadcast
							? config.commandMessages.statusEnabled
							: config.commandMessages.statusDisabled;
						String message = config.commandMessages.broadcastStatus
							.replace("{status}", status)
							.replace("{interval}", String.valueOf(config.autoBroadcast.broadcastInterval))
							.replace("{count}", String.valueOf(config.autoBroadcast.broadcastMessages.length));
						ctx.getSource().sendSuccess(() -> TextUtils.toComponent(message), false);
						return Command.SINGLE_SUCCESS;
					}))));

			dispatcher.register(literal("mute")
				.then(argument("player", EntityArgument.player())
					.executes(ctx -> executeMute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), null, null))
					.then(argument("duration", StringArgumentType.word())
						.executes(ctx -> executeMute(
							ctx.getSource(),
							EntityArgument.getPlayer(ctx, "player"),
							StringArgumentType.getString(ctx, "duration"),
							null))
						.then(argument("reason", StringArgumentType.greedyString())
							.executes(ctx -> executeMute(
								ctx.getSource(),
								EntityArgument.getPlayer(ctx, "player"),
								StringArgumentType.getString(ctx, "duration"),
								StringArgumentType.getString(ctx, "reason")))))));

			dispatcher.register(literal("unmute")
				.then(argument("player", EntityArgument.player())
					.executes(ctx -> executeUnmute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));

			dispatcher.register(literal("mutelist")
				.requires(source -> canViewMutes(source))
				.executes(ctx -> {
					var mutes = moderation.getActiveMutes();
					if (mutes.isEmpty()) {
						ctx.getSource().sendSuccess(() -> TextUtils.toComponent(config.commandMessages.noMutedPlayers), false);
						return Command.SINGLE_SUCCESS;
					}

					ctx.getSource().sendSuccess(() -> TextUtils.toComponent(config.commandMessages.mutedPlayersHeader), false);
					for (ModerationService.MuteData muteData : mutes) {
						long remaining = muteData.expiresAt() - System.currentTimeMillis();
						String line = config.commandMessages.mutedPlayerEntry
							.replace("{player}", muteData.playerName)
							.replace("{reason}", muteData.reason)
							.replace("{remaining}", moderation.formatDuration(remaining));
						ctx.getSource().sendSuccess(() -> TextUtils.toComponent(line), false);
					}
					return Command.SINGLE_SUCCESS;
				}));
		});
	}

	private int executeMute(CommandSourceStack source, ServerPlayer target, String duration, String reason) {
		ModerationService.Actor actor = resolveActor(source);
		if (actor == null) {
			return 0;
		}

		if (source.getEntity() instanceof ServerPlayer player && !permissions.canMutePlayers(player)) {
			permissions.sendNoPermissionMessage(player, "nextgenchat.moderate.mute");
			return 0;
		}

		return moderation.mutePlayer(target, actor, duration, reason) ? Command.SINGLE_SUCCESS : 0;
	}

	private int executeUnmute(CommandSourceStack source, ServerPlayer target) {
		ModerationService.Actor actor = resolveActor(source);
		if (actor == null) {
			return 0;
		}

		if (source.getEntity() instanceof ServerPlayer player && !permissions.canUnmutePlayers(player)) {
			permissions.sendNoPermissionMessage(player, "nextgenchat.moderate.unmute");
			return 0;
		}

		return moderation.unmutePlayer(target, actor) ? Command.SINGLE_SUCCESS : 0;
	}

	private ModerationService.Actor resolveActor(CommandSourceStack source) {
		if (source.getEntity() instanceof ServerPlayer player) {
			return ModerationService.Actor.fromPlayer(player);
		}
		if (source.getEntity() == null) {
			return ModerationService.Actor.console();
		}

		source.sendFailure(TextUtils.toComponent(config.commandMessages.playersOnly));
		return null;
	}

	private boolean canReload(CommandSourceStack source) {
		if (source.getEntity() == null) {
			return true;
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			return permissions.canUseReloadCommand(player);
		}
		return false;
	}

	private boolean canManagePermissions(CommandSourceStack source) {
		if (source.getEntity() == null) {
			return true;
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			return permissions.canUsePermissionsCommand(player);
		}
		return false;
	}

	private boolean canBroadcast(CommandSourceStack source) {
		if (source.getEntity() == null) {
			return true;
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			return permissions.canUseBroadcastCommand(player);
		}
		return false;
	}

	private boolean canViewMutes(CommandSourceStack source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return true;
		}
		return permissions.canViewMutes(player);
	}

	@FunctionalInterface
	private interface BroadcastConsumer {
		void broadcast(Component message, boolean overlay);
	}

	private static final class DelayedMessage {
		private final Component message;
		private int ticksRemaining;

		private DelayedMessage(Component message, int ticksRemaining) {
			this.message = message;
			this.ticksRemaining = ticksRemaining;
		}
	}
}

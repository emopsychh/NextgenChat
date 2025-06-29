package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.Command;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;
import net.minecraft.command.argument.EntityArgumentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.concurrent.ConcurrentLinkedQueue;


public class ChatManagerMod implements ModInitializer {
	public static final String MOD_ID = "nextgenchat";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	public static ChatManagerConfig CONFIG;
	public static ChatManager CHAT_MANAGER;
	public static ModerationManager MODERATION_MANAGER;
	
	// Очередь для отложенных сообщений
	private static final ConcurrentLinkedQueue<DelayedMessage> messageQueue = new ConcurrentLinkedQueue<>();
	
	// Переменные для автобродкаста
	private static int broadcastTicks = 0;
	private static int currentBroadcastIndex = 0;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("ChatManager mod is initializing...");
		
		// Инициализируем конфигурацию
		CONFIG = new ChatManagerConfig();
		CONFIG.load();
		
		// Инициализируем менеджер чата
		CHAT_MANAGER = new ChatManager();
		
		// Инициализируем менеджер модерации
		MODERATION_MANAGER = new ModerationManager();
		
		// Регистрируем обработчики событий
		registerEvents();
		registerCommands();
		
		LOGGER.info("ChatManager mod initialized successfully!");
	}
	
	private void registerEvents() {
		// Обработчик join - отправляем кастомное сообщение с задержкой
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			String joinMsg = CONFIG.notifications.joinMessage
				.replace("{player}", player.getName().getString());
			// Поддержка префикса LuckPerms
			joinMsg = ChatManagerMod.CHAT_MANAGER.replaceLuckPermsPlaceholders(joinMsg, player);
			Text message = Text.literal(joinMsg.replace("&", "§"));
			
			// Добавляем сообщение в очередь с задержкой в 5 тиков (0.25 секунды)
			messageQueue.offer(new DelayedMessage(message, 5));
		});

		// Обработчик quit - отправляем кастомное сообщение с задержкой
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			String quitMsg = CONFIG.notifications.quitMessage
				.replace("{player}", player.getName().getString());
			quitMsg = ChatManagerMod.CHAT_MANAGER.replaceLuckPermsPlaceholders(quitMsg, player);
			Text message = Text.literal(quitMsg.replace("&", "§"));
			
			// Добавляем сообщение в очередь с задержкой в 3 тика (0.15 секунды)
			messageQueue.offer(new DelayedMessage(message, 3));
		});

		// Обработчик тиков сервера для отправки отложенных сообщений и автобродкаста
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Обрабатываем очередь сообщений
			DelayedMessage message;
			while ((message = messageQueue.peek()) != null) {
				if (message.ticksRemaining <= 0) {
					messageQueue.poll(); // Убираем сообщение из очереди
					server.getPlayerManager().broadcast(message.text, false);
				} else {
					message.ticksRemaining--;
					break; // Выходим из цикла, так как остальные сообщения еще не готовы
				}
			}
			
			// Обрабатываем автобродкаст
			handleAutoBroadcast(server);
			
			// Очищаем истекшие муты (каждые 100 тиков = 5 секунд)
			if (server.getTicks() % 100 == 0) {
				MODERATION_MANAGER.cleanupExpiredMutes();
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("ChatManager: Server started, chat system is ready!");
			// Сбрасываем счетчики автобродкаста при запуске сервера
			broadcastTicks = 0;
			currentBroadcastIndex = 0;
		});
	}
	
	private void handleAutoBroadcast(net.minecraft.server.MinecraftServer server) {
		if (!CONFIG.autoBroadcast.enableAutoBroadcast || CONFIG.autoBroadcast.broadcastMessages.length == 0) {
			return;
		}
		
		// Увеличиваем счетчик тиков (20 тиков = 1 секунда)
		broadcastTicks++;
		
		// Проверяем, нужно ли отправить сообщение
		int ticksPerBroadcast = CONFIG.autoBroadcast.broadcastInterval * 20;
		if (broadcastTicks >= ticksPerBroadcast) {
			// Выбираем сообщение
			String broadcastMessage;
			if (CONFIG.autoBroadcast.randomizeMessages) {
				// Случайное сообщение
				int randomIndex = (int) (Math.random() * CONFIG.autoBroadcast.broadcastMessages.length);
				broadcastMessage = CONFIG.autoBroadcast.broadcastMessages[randomIndex];
			} else {
				// Последовательное сообщение
				broadcastMessage = CONFIG.autoBroadcast.broadcastMessages[currentBroadcastIndex];
				// Переходим к следующему сообщению
				currentBroadcastIndex = (currentBroadcastIndex + 1) % CONFIG.autoBroadcast.broadcastMessages.length;
			}
			
			// Обрабатываем плейсхолдеры автобродкаста
			broadcastMessage = replaceBroadcastPlaceholders(broadcastMessage, server);
			
			// Добавляем префикс если включен
			if (CONFIG.autoBroadcast.showBroadcastPrefix) {
				broadcastMessage = CONFIG.autoBroadcast.broadcastPrefix + broadcastMessage;
			}
			
			Text messageText = Text.literal(broadcastMessage.replace("&", "§"));
			server.getPlayerManager().broadcast(messageText, false);
			
			// Сбрасываем счетчик тиков
			broadcastTicks = 0;
			
			LOGGER.debug("AutoBroadcast: Sent message " + (CONFIG.autoBroadcast.randomizeMessages ? "random" : currentBroadcastIndex) + 
				" of " + CONFIG.autoBroadcast.broadcastMessages.length);
		}
	}
	
	private String replaceBroadcastPlaceholders(String message, net.minecraft.server.MinecraftServer server) {
		int onlinePlayers = server.getPlayerManager().getPlayerList().size();
		int maxPlayers = server.getPlayerManager().getMaxPlayerCount();
		String serverName = server.getServerMotd();
		
		// Используем текущее время вместо uptime сервера
		long currentTime = System.currentTimeMillis();
		long uptimeHours = currentTime / (1000 * 60 * 60) % 24; // Часы с полуночи
		long uptimeMinutes = (currentTime / (1000 * 60)) % 60; // Минуты
		
		return message
			.replace("{online}", String.valueOf(onlinePlayers))
			.replace("{max_online}", String.valueOf(maxPlayers))
			.replace("{server_name}", serverName)
			.replace("{uptime_hours}", String.valueOf(uptimeHours))
			.replace("{uptime_minutes}", String.valueOf(uptimeMinutes))
			.replace("{tps}", "20.0") // Стандартный TPS
			.replace("{memory_used}", String.valueOf(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()))
			.replace("{memory_max}", String.valueOf(Runtime.getRuntime().maxMemory()));
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("nextgenchat")
				.executes(context -> {
					// Показываем основную справку
					showHelp(context.getSource());
					return Command.SINGLE_SUCCESS;
				})
				.then(literal("help")
					.executes(context -> {
						showHelp(context.getSource());
						return Command.SINGLE_SUCCESS;
					})
				)
				.then(literal("reload")
					.executes(context -> {
						CONFIG.load();
						LOGGER.info("NextgenChat config reloaded!");
						context.getSource().sendFeedback(() -> net.minecraft.text.Text.literal("§aКонфиг NextgenChat перезагружен!"), false);
						return Command.SINGLE_SUCCESS;
					})
				)
				.then(literal("broadcast")
					.executes(context -> {
						// Отправляем следующее сообщение автобродкаста немедленно
						if (CONFIG.autoBroadcast.enableAutoBroadcast && CONFIG.autoBroadcast.broadcastMessages.length > 0) {
							String broadcastMessage;
							if (CONFIG.autoBroadcast.randomizeMessages) {
								// Случайное сообщение
								int randomIndex = (int) (Math.random() * CONFIG.autoBroadcast.broadcastMessages.length);
								broadcastMessage = CONFIG.autoBroadcast.broadcastMessages[randomIndex];
							} else {
								// Последовательное сообщение
								broadcastMessage = CONFIG.autoBroadcast.broadcastMessages[currentBroadcastIndex];
								// Переходим к следующему сообщению
								currentBroadcastIndex = (currentBroadcastIndex + 1) % CONFIG.autoBroadcast.broadcastMessages.length;
							}
							
							// Обрабатываем плейсхолдеры автобродкаста
							broadcastMessage = replaceBroadcastPlaceholders(broadcastMessage, context.getSource().getServer());
							
							// Добавляем префикс если включен
							if (CONFIG.autoBroadcast.showBroadcastPrefix) {
								broadcastMessage = CONFIG.autoBroadcast.broadcastPrefix + broadcastMessage;
							}
							
							Text messageText = Text.literal(broadcastMessage.replace("&", "§"));
							context.getSource().getServer().getPlayerManager().broadcast(messageText, false);
							
							context.getSource().sendFeedback(() -> net.minecraft.text.Text.literal("§aСообщение автобродкаста отправлено!"), false);
						} else {
							context.getSource().sendFeedback(() -> net.minecraft.text.Text.literal("§cАвтобродкаст отключен или нет сообщений!"), false);
						}
						return Command.SINGLE_SUCCESS;
					})
				)
				.then(literal("broadcast")
					.then(literal("toggle")
						.executes(context -> {
							CONFIG.autoBroadcast.enableAutoBroadcast = !CONFIG.autoBroadcast.enableAutoBroadcast;
							String status = CONFIG.autoBroadcast.enableAutoBroadcast ? "§aвключен" : "§cвыключен";
							context.getSource().sendFeedback(() -> net.minecraft.text.Text.literal("§eАвтобродкаст " + status + "§e!"), false);
							CONFIG.save();
							return Command.SINGLE_SUCCESS;
						})
					)
					.then(literal("status")
						.executes(context -> {
							String status = CONFIG.autoBroadcast.enableAutoBroadcast ? "§aвключен" : "§cвыключен";
							String interval = String.valueOf(CONFIG.autoBroadcast.broadcastInterval);
							String messageCount = String.valueOf(CONFIG.autoBroadcast.broadcastMessages.length);
							context.getSource().sendFeedback(() -> net.minecraft.text.Text.literal("§eАвтобродкаст: " + status + "§e, интервал: §f" + interval + "с§e, сообщений: §f" + messageCount), false);
							return Command.SINGLE_SUCCESS;
						})
					)
				)
			);
			
			// Команды модерации
			dispatcher.register(literal("mute")
				.requires(source -> source.hasPermissionLevel(2)) // Требует OP уровень 2+
				.then(argument("player", EntityArgumentType.player())
					.executes(context -> {
						// Мут без указания времени (использует значение по умолчанию)
						ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
						ServerPlayerEntity moderator = context.getSource().getPlayer();
						
						if (moderator == null) {
							context.getSource().sendError(Text.literal("§cЭта команда может использоваться только игроками!"));
							return 0;
						}
						
						boolean success = MODERATION_MANAGER.mutePlayer(target, moderator, null, null);
						return success ? Command.SINGLE_SUCCESS : 0;
					})
					.then(argument("duration", com.mojang.brigadier.arguments.StringArgumentType.word())
						.executes(context -> {
							// Мут с указанием времени
							ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
							String duration = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "duration");
							ServerPlayerEntity moderator = context.getSource().getPlayer();
							
							if (moderator == null) {
								context.getSource().sendError(Text.literal("§cЭта команда может использоваться только игроками!"));
								return 0;
							}
							
							boolean success = MODERATION_MANAGER.mutePlayer(target, moderator, duration, null);
							return success ? Command.SINGLE_SUCCESS : 0;
						})
						.then(argument("reason", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
							.executes(context -> {
								// Мут с указанием времени и причины
								ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
								String duration = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "duration");
								String reason = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "reason");
								ServerPlayerEntity moderator = context.getSource().getPlayer();
								
								if (moderator == null) {
									context.getSource().sendError(Text.literal("§cЭта команда может использоваться только игроками!"));
									return 0;
								}
								
								boolean success = MODERATION_MANAGER.mutePlayer(target, moderator, duration, reason);
								return success ? Command.SINGLE_SUCCESS : 0;
							})
						)
					)
				)
			);
			
			dispatcher.register(literal("unmute")
				.requires(source -> source.hasPermissionLevel(2)) // Требует OP уровень 2+
				.then(argument("player", EntityArgumentType.player())
					.executes(context -> {
						ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
						ServerPlayerEntity moderator = context.getSource().getPlayer();
						
						if (moderator == null) {
							context.getSource().sendError(Text.literal("§cЭта команда может использоваться только игроками!"));
							return 0;
						}
						
						boolean success = MODERATION_MANAGER.unmutePlayer(target, moderator);
						return success ? Command.SINGLE_SUCCESS : 0;
					})
				)
			);
			
			dispatcher.register(literal("mutelist")
				.requires(source -> source.hasPermissionLevel(2)) // Требует OP уровень 2+
				.executes(context -> {
					// Показывает список заблокированных игроков
					java.util.List<ModerationManager.MuteData> mutedPlayers = new java.util.ArrayList<>();
					for (ModerationManager.MuteData muteData : MODERATION_MANAGER.mutedPlayers.values()) {
						if (System.currentTimeMillis() <= muteData.muteTime + muteData.duration) {
							mutedPlayers.add(muteData);
						}
					}
					
					if (mutedPlayers.isEmpty()) {
						context.getSource().sendFeedback(() -> Text.literal("§eНет заблокированных игроков"), false);
					} else {
						context.getSource().sendFeedback(() -> Text.literal("§eЗаблокированные игроки:"), false);
						for (ModerationManager.MuteData muteData : mutedPlayers) {
							long remainingTime = muteData.muteTime + muteData.duration - System.currentTimeMillis();
							String remaining = MODERATION_MANAGER.formatDuration(remainingTime);
							String message = String.format("§c%s - %s (осталось: %s)", 
								muteData.playerName, muteData.reason, remaining);
							context.getSource().sendFeedback(() -> Text.literal(message), false);
						}
					}
					return Command.SINGLE_SUCCESS;
				})
			);
		});
	}
	
	private void showHelp(net.minecraft.server.command.ServerCommandSource source) {
		// Заголовок
		source.sendFeedback(() -> Text.literal("§6§l=== NextgenChat - Справка по командам ==="), false);
		source.sendFeedback(() -> Text.literal(""), false);
		
		// Основные команды
		source.sendFeedback(() -> Text.literal("§e§lОсновные команды:"), false);
		source.sendFeedback(() -> Text.literal("§f/nextgenchat §7- показать эту справку"), false);
		source.sendFeedback(() -> Text.literal("§f/nextgenchat help §7- показать эту справку"), false);
		source.sendFeedback(() -> Text.literal("§f/nextgenchat reload §7- перезагрузить конфигурацию"), false);
		source.sendFeedback(() -> Text.literal(""), false);
		
		// Команды автобродкаста
		source.sendFeedback(() -> Text.literal("§e§lАвтобродкаст:"), false);
		source.sendFeedback(() -> Text.literal("§f/nextgenchat broadcast §7- отправить следующее сообщение автобродкаста"), false);
		source.sendFeedback(() -> Text.literal("§f/nextgenchat broadcast toggle §7- включить/выключить автобродкаст"), false);
		source.sendFeedback(() -> Text.literal("§f/nextgenchat broadcast status §7- показать статус автобродкаста"), false);
		source.sendFeedback(() -> Text.literal(""), false);
		
		// Команды модерации (только для OP)
		if (source.hasPermissionLevel(2)) {
			source.sendFeedback(() -> Text.literal("§e§lМодерация (требует OP):"), false);
			source.sendFeedback(() -> Text.literal("§f/mute <игрок> [время] [причина] §7- заблокировать игрока в чате"), false);
			source.sendFeedback(() -> Text.literal("§f/unmute <игрок> §7- разблокировать игрока в чате"), false);
			source.sendFeedback(() -> Text.literal("§f/mutelist §7- показать список заблокированных игроков"), false);
			source.sendFeedback(() -> Text.literal(""), false);
			
			// Примеры команд модерации
			source.sendFeedback(() -> Text.literal("§e§lПримеры команд модерации:"), false);
			source.sendFeedback(() -> Text.literal("§7/mute PlayerName §8- заблокировать на 1 час (по умолчанию)"), false);
			source.sendFeedback(() -> Text.literal("§7/mute PlayerName 30m §8- заблокировать на 30 минут"), false);
			source.sendFeedback(() -> Text.literal("§7/mute PlayerName 2h Спам §8- заблокировать на 2 часа с причиной"), false);
			source.sendFeedback(() -> Text.literal("§7/mute PlayerName 1d Нарушение правил §8- заблокировать на 1 день"), false);
			source.sendFeedback(() -> Text.literal(""), false);
			
			// Форматы времени
			source.sendFeedback(() -> Text.literal("§e§lФорматы времени:"), false);
			source.sendFeedback(() -> Text.literal("§7m §8- минуты (30m = 30 минут)"), false);
			source.sendFeedback(() -> Text.literal("§7h §8- часы (2h = 2 часа)"), false);
			source.sendFeedback(() -> Text.literal("§7d §8- дни (1d = 1 день)"), false);
			source.sendFeedback(() -> Text.literal("§7w §8- недели (1w = 1 неделя)"), false);
			source.sendFeedback(() -> Text.literal(""), false);
		}
		
		// Информация о чате
		source.sendFeedback(() -> Text.literal("§e§lСистема чата:"), false);
		source.sendFeedback(() -> Text.literal("§7Локальный чат §8- сообщения видны только в радиусе " + CONFIG.chat.localChatRadius + " блоков"), false);
		source.sendFeedback(() -> Text.literal("§7Глобальный чат §8- используйте '" + CONFIG.chat.globalChatSymbol + "' в начале сообщения"), false);
		source.sendFeedback(() -> Text.literal(""), false);
		
		// Плейсхолдеры автобродкаста
		source.sendFeedback(() -> Text.literal("§e§lПлейсхолдеры автобродкаста:"), false);
		source.sendFeedback(() -> Text.literal("§7{online} §8- количество игроков онлайн"), false);
		source.sendFeedback(() -> Text.literal("§7{max_online} §8- максимальное количество игроков"), false);
		source.sendFeedback(() -> Text.literal("§7{server_name} §8- название сервера"), false);
		source.sendFeedback(() -> Text.literal("§7{memory_used} §8- используемая память"), false);
		source.sendFeedback(() -> Text.literal("§7{memory_max} §8- максимальная память"), false);
		source.sendFeedback(() -> Text.literal(""), false);
		
		// Информация о конфигурации
		source.sendFeedback(() -> Text.literal("§e§lКонфигурация:"), false);
		source.sendFeedback(() -> Text.literal("§7Файл конфигурации §8- config/nextgenchat.json"), false);
		source.sendFeedback(() -> Text.literal("§7Данные мутов §8- nextgenchat_mutes.json"), false);
		source.sendFeedback(() -> Text.literal(""), false);
		
		// Статус систем
		source.sendFeedback(() -> Text.literal("§e§lСтатус систем:"), false);
		String chatStatus = CONFIG.chat.enableLocalChat && CONFIG.chat.enableGlobalChat ? "§aвключена" : "§cвыключена";
		String broadcastStatus = CONFIG.autoBroadcast.enableAutoBroadcast ? "§aвключен" : "§cвыключен";
		String moderationStatus = CONFIG.moderation.enableModeration ? "§aвключена" : "§cвыключена";
		
		source.sendFeedback(() -> Text.literal("§7Система чата: " + chatStatus), false);
		source.sendFeedback(() -> Text.literal("§7Автобродкаст: " + broadcastStatus), false);
		source.sendFeedback(() -> Text.literal("§7Модерация: " + moderationStatus), false);
		source.sendFeedback(() -> Text.literal(""), false);
		
		// Подвал
		source.sendFeedback(() -> Text.literal("§6§l=== NextgenChat v1.0.0 ==="), false);
		source.sendFeedback(() -> Text.literal("§7Для подробной информации см. README.md"), false);
	}
	
	// Класс для хранения отложенных сообщений
	private static class DelayedMessage {
		final Text text;
		int ticksRemaining;
		
		DelayedMessage(Text text, int delayTicks) {
			this.text = text;
			this.ticksRemaining = delayTicks;
		}
	}
}
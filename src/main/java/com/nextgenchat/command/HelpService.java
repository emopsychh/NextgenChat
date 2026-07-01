package com.nextgenchat.command;

import com.nextgenchat.NextgenChatMod;
import com.nextgenchat.config.NextgenChatConfig;
import com.nextgenchat.luckperms.LuckPermsBridge;
import com.nextgenchat.permission.PermissionService;
import com.nextgenchat.util.TextUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class HelpService {
	private HelpService() {
	}

	public static void sendHelp(CommandSourceStack source, NextgenChatMod mod) {
		NextgenChatConfig config = mod.config();
		LuckPermsBridge luckPerms = mod.luckPerms();

		line(source, "&6&l=== NextgenChat - Справка ===");
		line(source, "");
		line(source, "&eЛокальный чат: радиус &f" + config.chat.localChatRadius + " &7блоков");
		line(source, "&eГлобальный чат: символ &f'" + config.chat.globalChatSymbol + "'&7 в начале сообщения");
		line(source, "");
		line(source, "&eКоманды:");
		line(source, "&f/nextgenchat help &7- эта справка");
		line(source, "&f/nextgenchat reload &7- перезагрузить конфиг");
		line(source, "&f/nextgenchat permissions reload|status");
		line(source, "&f/nextgenchat broadcast|broadcast toggle|broadcast status");
		line(source, "&f/mute &7| &f/unmute &7| &f/mutelist");
		line(source, "");
		line(source, "&eПлейсхолдеры автобродкаста:");
		line(source, "&7{online}, {max_online}, {server_name}, {memory_used}, {memory_max}");
		line(source, "&7{uptime_hours}, {uptime_minutes}, {tps}");
		line(source, "");
		line(source, "&eФайлы:");
		line(source, "&7config/nextgenchat.json");
		line(source, "&7nextgenchat_mutes.json");
		line(source, "");
		line(source, "&eСтатус:");
		line(source, statusLine("Чат", config.chat.enableLocalChat || config.chat.enableGlobalChat));
		line(source, statusLine("Автобродкаст", config.autoBroadcast.enableAutoBroadcast));
		line(source, statusLine("Модерация", config.moderation.enableModeration));
		line(source, statusLine("Права", config.permissions.enablePermissionSystem));
		line(source, statusLine("LuckPerms", luckPerms.isAvailable()));
		line(source, "");
		line(source, "&6NextgenChat &7v" + mod.version());
	}

	private static void line(CommandSourceStack source, String text) {
		Component component = TextUtils.toComponent(text);
		source.sendSuccess(() -> component, false);
	}

	private static String statusLine(String label, boolean enabled) {
		String status = enabled ? "&aвключено" : "&cвыключено";
		return "&7" + label + ": " + status;
	}
}

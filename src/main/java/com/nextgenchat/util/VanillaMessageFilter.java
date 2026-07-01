package com.nextgenchat.util;

import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

/**
 * Filters only known vanilla join/quit system messages without blocking unrelated broadcasts.
 */
public final class VanillaMessageFilter {
	private static final Pattern[] VANILLA_PATTERNS = {
		Pattern.compile("^.+ joined the game$"),
		Pattern.compile("^.+ left the game$"),
		Pattern.compile("^.+ присоединился к серверу$"),
		Pattern.compile("^.+ покинул сервер$"),
		Pattern.compile("^.+ зашел на сервер$"),
		Pattern.compile("^.+ вышел с сервера$"),
		Pattern.compile("^.+ lost connection: .+$"),
	};

	private VanillaMessageFilter() {
	}

	public static boolean isVanillaJoinOrQuit(Component message) {
		String plain = message.getString();
		for (Pattern pattern : VANILLA_PATTERNS) {
			if (pattern.matcher(plain).matches()) {
				return true;
			}
		}
		return false;
	}
}

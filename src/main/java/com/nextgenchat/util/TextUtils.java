package com.nextgenchat.util;

import net.minecraft.network.chat.Component;

public final class TextUtils {
	private TextUtils() {
	}

	public static String applyColorCodes(String text) {
		return text.replace("&", "§");
	}

	public static Component toComponent(String text) {
		return Component.literal(applyColorCodes(text));
	}
}

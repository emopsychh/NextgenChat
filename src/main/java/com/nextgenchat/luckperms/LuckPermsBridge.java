package com.nextgenchat.luckperms;

import com.nextgenchat.NextgenChatMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Optional LuckPerms integration. This class must not import LuckPerms API types so the mod
 * can run without LuckPerms installed at runtime.
 */
public class LuckPermsBridge {
	private static final String LUCKPERMS_MOD_ID = "luckperms";
	private static final String PROVIDER_CLASS = "net.luckperms.api.LuckPermsProvider";

	private final boolean available;
	private Consumer<UUID> cacheInvalidator = uuid -> {};

	public LuckPermsBridge() {
		this.available = FabricLoader.getInstance().isModLoaded(LUCKPERMS_MOD_ID);
		if (available) {
			registerCacheInvalidationListener();
			NextgenChatMod.LOGGER.info("LuckPerms integration enabled");
		} else {
			NextgenChatMod.LOGGER.info("LuckPerms not installed, using default permissions from config");
		}
	}

	public void setCacheInvalidator(Consumer<UUID> cacheInvalidator) {
		this.cacheInvalidator = cacheInvalidator != null ? cacheInvalidator : uuid -> {};
	}

	public boolean isAvailable() {
		return available;
	}

	public boolean hasPermission(ServerPlayer player, String permission) {
		if (!available) {
			return false;
		}

		Object user = getUser(player.getUUID());
		if (user == null) {
			return false;
		}

		try {
			Object cachedData = invoke(user, "getCachedData");
			Object permissionData = invoke(cachedData, "getPermissionData");
			Object result = invoke(permissionData, "checkPermission", permission);
			return asBoolean(result);
		} catch (ReflectiveOperationException e) {
			NextgenChatMod.LOGGER.debug("LuckPerms permission check failed for {} on {}", permission, player.getName().getString());
			return false;
		}
	}

	public String getPrefix(ServerPlayer player) {
		return getMetaValue(player, "getPrefix");
	}

	public String getSuffix(ServerPlayer player) {
		return getMetaValue(player, "getSuffix");
	}

	public String getPrimaryGroup(ServerPlayer player) {
		if (!available) {
			return "";
		}

		Object user = getUser(player.getUUID());
		if (user == null) {
			return "";
		}

		try {
			Object group = invoke(user, "getPrimaryGroup");
			return group != null ? group.toString() : "";
		} catch (ReflectiveOperationException e) {
			return "";
		}
	}

	public String replacePlaceholders(String text, ServerPlayer player) {
		String playerName = player.getName().getString();
		String displayName = player.getDisplayName().getString();

		if (!available) {
			return text
				.replace("%luckperms_prefix%", "")
				.replace("%luckperms_suffix%", "")
				.replace("%luckperms_group%", "")
				.replace("%player%", playerName)
				.replace("%player_name%", playerName)
				.replace("%player_displayname%", displayName);
		}

		return text
			.replace("%luckperms_prefix%", getPrefix(player))
			.replace("%luckperms_suffix%", getSuffix(player))
			.replace("%luckperms_group%", getPrimaryGroup(player))
			.replace("%player%", playerName)
			.replace("%player_name%", playerName)
			.replace("%player_displayname%", displayName);
	}

	private String getMetaValue(ServerPlayer player, String accessor) {
		if (!available) {
			return "";
		}

		Object user = getUser(player.getUUID());
		if (user == null) {
			return "";
		}

		try {
			Object cachedData = invoke(user, "getCachedData");
			Object metaData = invoke(cachedData, "getMetaData");
			Object value = invoke(metaData, accessor);
			return value != null ? value.toString() : "";
		} catch (ReflectiveOperationException e) {
			return "";
		}
	}

	private Object getUser(UUID playerId) {
		try {
			Object api = getProvider();
			Object userManager = invoke(api, "getUserManager");
			return invoke(userManager, "getUser", playerId);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private Object getProvider() throws ReflectiveOperationException {
		Class<?> providerClass = Class.forName(PROVIDER_CLASS);
		Method get = providerClass.getMethod("get");
		return get.invoke(null);
	}

	private void registerCacheInvalidationListener() {
		try {
			Object api = getProvider();
			Object eventBus = invoke(api, "getEventBus");
			Class<?> eventClass = Class.forName("net.luckperms.api.event.user.UserDataRecalculateEvent");

			Method subscribe = eventBus.getClass().getMethod("subscribe", Class.class, Consumer.class);
			subscribe.invoke(eventBus, eventClass, (Consumer<Object>) event -> {
				try {
					Object user = invoke(event, "getUser");
					UUID uuid = (UUID) invoke(user, "getUniqueId");
					cacheInvalidator.accept(uuid);
				} catch (ReflectiveOperationException ignored) {
				}
			});
		} catch (ReflectiveOperationException e) {
			NextgenChatMod.LOGGER.warn("Failed to register LuckPerms cache invalidation listener", e);
		}
	}

	private static Object invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
		for (Method method : target.getClass().getMethods()) {
			if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
				continue;
			}

			try {
				return method.invoke(target, args);
			} catch (IllegalArgumentException ignored) {
				// Try next overload
			}
		}

		throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName);
	}

	private static boolean asBoolean(Object result) throws ReflectiveOperationException {
		if (result == null) {
			return false;
		}

		try {
			Method asBoolean = result.getClass().getMethod("asBoolean");
			return (boolean) asBoolean.invoke(result);
		} catch (NoSuchMethodException ignored) {
			return "TRUE".equalsIgnoreCase(result.toString());
		}
	}
}

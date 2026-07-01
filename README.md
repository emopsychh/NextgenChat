# NextgenChat

Fabric-мод для Minecraft **26.1** (серверный, Java 25): локальный/глобальный чат, анти-спам, автобродкаст, муты. LuckPerms **опционален** — без него работают права из конфига.

## Установка

1. Minecraft **26.1** + Fabric Loader **0.19.3+** + Fabric API
2. JAR в `mods/` (только сервер)
3. Конфиг: `config/nextgenchat.json` (создаётся автоматически)
4. `/nextgenchat reload` после изменений

## Чат

- **Локальный** — по умолчанию, радиус в конфиге
- **Глобальный** — префикс `!` (настраивается)
- **Анти-спам** — кулдаун, повторы, флуд (секция `antiSpam`)
- Флаги `enableLocalChat` / `enableGlobalChat` реально отключают режимы

## Команды

| Команда | Описание |
|---------|----------|
| `/nextgenchat help` | Справка |
| `/nextgenchat reload` | Перезагрузка конфига |
| `/nextgenchat permissions reload\|status` | Кэш прав / статус LuckPerms |
| `/nextgenchat broadcast\|toggle\|status` | Автобродкаст |
| `/mute`, `/unmute`, `/mutelist` | Модерация (консоль поддерживается) |

## Плейсхолдеры автобродкаста

`{online}`, `{max_online}`, `{server_name}`, `{memory_used}`, `{memory_max}`, `{uptime_hours}`, `{uptime_minutes}`, `{tps}`

- `{uptime_*}` — реальный uptime сервера
- `{memory_*}` — мегабайты JVM
- `{tps}` — расчётный TPS (cap 20)

## Структура кода

```
com.nextgenchat/
  NextgenChatMod.java       — точка входа
  config/NextgenChatConfig  — конфигурация
  chat/ChatService          — логика чата
  chat/AntiSpamService      — анти-спам
  broadcast/BroadcastService
  moderation/ModerationService
  permission/PermissionService
  luckperms/LuckPermsBridge — опциональная интеграция (reflection, без hard-dependency)
  command/HelpService
  mixin/
```

## Сборка

```bash
./gradlew build
```

## Лицензия

MIT — см. [LICENSE](LICENSE).

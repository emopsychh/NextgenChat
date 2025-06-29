# Система прав NextgenChat

NextgenChat интегрируется с LuckPerms для управления правами игроков. Система поддерживает как работу с LuckPerms, так и права по умолчанию, когда LuckPerms недоступен.

## Права

### Права чата
- `nextgenchat.chat.global` - право на использование глобального чата
- `nextgenchat.chat.local` - право на использование локального чата

### Права модерации
- `nextgenchat.moderate.mute` - право на блокировку игроков в чате
- `nextgenchat.moderate.unmute` - право на разблокировку игроков в чате
- `nextgenchat.moderate.view` - право на просмотр списка заблокированных игроков

### Права администратора
- `nextgenchat.admin.reload` - право на перезагрузку конфигурации и очистку кэша прав

### Права обхода
- `nextgenchat.bypass.antispam` - обход системы анти-спама
- `nextgenchat.bypass.mute` - обход блокировки в чате

### Права уведомлений
- `nextgenchat.notifications.moderation` - получение уведомлений о действиях модерации

### Общие права
- `nextgenchat.commands` - право на использование команд мода

## Настройка прав в LuckPerms

### Примеры команд LuckPerms

```bash
# Дать права модератора
/lp user <player> permission set nextgenchat.moderate.mute true
/lp user <player> permission set nextgenchat.moderate.unmute true
/lp user <player> permission set nextgenchat.moderate.view true
/lp user <player> permission set nextgenchat.notifications.moderation true

# Дать права администратора
/lp user <player> permission set nextgenchat.admin.reload true

# Дать права обхода
/lp user <player> permission set nextgenchat.bypass.antispam true
/lp user <player> permission set nextgenchat.bypass.mute true

# Ограничить использование чата
/lp user <player> permission set nextgenchat.chat.global false
/lp user <player> permission set nextgenchat.chat.local false
```

### Создание групп

```bash
# Группа модераторов
/lp creategroup moderator
/lp group moderator permission set nextgenchat.moderate.mute true
/lp group moderator permission set nextgenchat.moderate.unmute true
/lp group moderator permission set nextgenchat.moderate.view true
/lp group moderator permission set nextgenchat.notifications.moderation true

# Группа администраторов
/lp creategroup admin
/lp group admin permission set nextgenchat.admin.reload true
/lp group admin permission set nextgenchat.bypass.antispam true
/lp group admin permission set nextgenchat.bypass.mute true

# Назначение игроков в группы
/lp user <player> parent set moderator
/lp user <player> parent set admin
```

## Настройки по умолчанию

Когда LuckPerms недоступен, используются следующие права по умолчанию:

```json
{
  "permissions": {
    "defaultCanUseGlobalChat": true,
    "defaultCanUseLocalChat": true,
    "defaultCanMutePlayers": false,
    "defaultCanUnmutePlayers": false,
    "defaultCanReloadConfig": false,
    "defaultCanViewMutes": false,
    "defaultCanBypassAntiSpam": false,
    "defaultCanBypassMute": false,
    "defaultCanUseCommands": true,
    "defaultCanReceiveModerationNotifications": false
  }
}
```

## Команды управления правами

- `/nextgenchat permissions reload` - очистить кэш прав (требует `nextgenchat.admin.reload`)
- `/nextgenchat permissions status` - показать статус LuckPerms (требует `nextgenchat.admin.reload`)

## Кэширование прав

Система кэширует права игроков для повышения производительности. Кэш автоматически очищается при:
- Перезагрузке конфигурации
- Выполнении команды `/nextgenchat permissions reload`
- Изменении прав в LuckPerms (требует ручной очистки кэша)

## Совместимость

- **С LuckPerms**: Полная интеграция с проверкой прав через API
- **Без LuckPerms**: Использование прав по умолчанию из конфигурации
- **Гибридный режим**: Автоматическое переключение между режимами

## Логирование

Система логирует:
- Предупреждения о недоступности LuckPerms
- Очистку кэша прав
- Ошибки при проверке прав

Логи можно найти в консоли сервера и файлах логов. 
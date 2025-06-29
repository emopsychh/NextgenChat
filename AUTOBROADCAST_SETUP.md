# Настройка автобродкаста в NextgenChat

## Быстрый старт

1. **Установите мод** в папку `mods/`
2. **Запустите сервер** - конфиг создастся автоматически
3. **Отредактируйте** файл `config/nextgenchat.json`
4. **Перезапустите сервер** или используйте `/nextgenchat reload`

## Основные настройки

### Включение/выключение
```json
"enableAutoBroadcast": true
```

### Интервал сообщений (в секундах)
```json
"broadcastInterval": 300  // 5 минут
```

### Порядок сообщений
```json
"randomizeMessages": false  // false = по порядку, true = случайно
```

### Префикс сообщений
```json
"showBroadcastPrefix": false,  // false = без префикса (по умолчанию), true = с префиксом
"broadcastPrefix": "&d[Автобродкаст] &r"
```

### Сообщения
```json
"broadcastMessages": [
  "&6&lДобро пожаловать на сервер!",
  "&bОнлайн: &f{online}&b/&f{max_online}",
  "&eНе забудьте прочитать правила!"
]
```

## Плейсхолдеры

Используйте эти переменные в сообщениях:

- `{online}` - игроков онлайн
- `{max_online}` - максимум игроков
- `{server_name}` - название сервера
- `{memory_used}` - используемая память
- `{memory_max}` - максимальная память
- `{uptime_hours}` - часы работы
- `{uptime_minutes}` - минуты работы

## Примеры конфигураций

### Простой автобродкаст
```json
{
  "autoBroadcast": {
    "enableAutoBroadcast": true,
    "broadcastInterval": 300,
    "randomizeMessages": false,
    "showBroadcastPrefix": false,
    "broadcastPrefix": "&d[Автобродкаст] &r",
    "broadcastMessages": [
      "&6&lДобро пожаловать на сервер!",
      "&eНе забудьте прочитать правила!",
      "&aПриятной игры!"
    ]
  }
}
```

### Информационный автобродкаст
```json
{
  "autoBroadcast": {
    "enableAutoBroadcast": true,
    "broadcastInterval": 600,
    "randomizeMessages": false,
    "showBroadcastPrefix": true,
    "broadcastPrefix": "&6[Инфо] &r",
    "broadcastMessages": [
      "&bОнлайн: &f{online}&b/&f{max_online}",
      "&7Память: &f{memory_used}MB&7/&f{memory_max}MB",
      "&3Время: &f{uptime_hours}&3ч &f{uptime_minutes}&3м"
    ]
  }
}
```

### Случайные сообщения
```json
{
  "autoBroadcast": {
    "enableAutoBroadcast": true,
    "broadcastInterval": 180,
    "randomizeMessages": true,
    "showBroadcastPrefix": false,
    "broadcastMessages": [
      "&aПриятной игры!",
      "&eНе забудьте прочитать правила!",
      "&bОнлайн: &f{online}&b/&f{max_online}",
      "&dСервер работает стабильно!"
    ]
  }
}
```

## Команды управления

- `/nextgenchat reload` - перезагрузить конфиг
- `/nextgenchat broadcast` - отправить сообщение сейчас
- `/nextgenchat broadcast toggle` - включить/выключить
- `/nextgenchat broadcast status` - показать статус 
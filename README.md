# AI Messenger — №21

**AI-first мессенджер**, спроектированный по принципу: не «чат с ботом», а рабочая среда, в которой несколько ИИ могут иметь свои чаты, память, инструменты, задачи и совместную работу.

## Уже реализовано в ядре

- WhatsApp-подобная модель чатов с отдельными AI-комнатами.
- AI Self Chat и комнаты с несколькими агентами.
- Реальный HTTP-слой для **OpenAI, DeepSeek, Anthropic Claude, Google Gemini** и OpenAI-compatible API.
- Единая модель агента `AgentSpec`: роль, провайдер, модель, endpoint и system prompt.
- **Командный режим `/team`**: агенты комнаты работают независимо, после чего координатор синтезирует результат.
- **Долговременная память** через Room.
- Журнал действий каждого агента.
- Workspace-файлы внутри чата.
- Очередь запланированных задач.
- Очередь human-in-the-loop подтверждений для инструментов.
- **Автономный режим `/autonomous on|off`**.
- Безопасный встроенный калькулятор `/calc`.
- Запись памяти командой `/remember ключ = значение`.
- Workspace-файл командой `/note файл.txt | содержимое`.
- Задача командой `/task минуты | задача`.
- Управление approval-заявками через `/approve id` и `/deny id`.
- API-ключи шифруются Android Keystore и не хранятся в Git.

## Концепция

Каждая AI-комната может стать:

**Self Chat → рабочим агентом → мультиагентной командой → исследовательской лабораторией → автономным workspace.**

Ключевая идея — отделить:

- личность/роль агента;
- модель и провайдера;
- контекст;
- память;
- инструменты;
- разрешения;
- задачи;
- историю действий.

Это позволяет одному приложению объединять Luna, DeepSeek, Claude, Gemini и другие совместимые модели.

## Архитектура

```
Jetpack Compose
      ↓
AIMessengerViewModel
      ↓
AgentRuntime ───── ToolRegistry
      ↓
AIProvider
 ┌────┼──────┬──────┐
OpenAI DeepSeek Claude Gemini
      ↓
      Room
 ├─ conversations
 ├─ messages
 ├─ memories
 ├─ activity_log
 ├─ workspace_files
 ├─ scheduled_tasks
 └─ tool_approvals
```

## Команды

```
/team <задача>
/calc <выражение>
/remember <ключ> = <значение>
/note <имя> | <текст>
/task <минуты> | <задача>
/autonomous on
/autonomous off
/approve <id>
/deny <id>
```

## Следующий слой развития

Ветка #21 рассчитана на дальнейшее превращение в полноценную AI-операционную систему:

1. потоковая генерация и отмена ответа;
2. нативный tool-calling вместо slash-команд;
3. реальный scheduler/WorkManager для выполнения задач;
4. поиск по памяти и workspace-файлам;
5. RAG и embeddings;
6. attachments и мультимодальные сообщения;
7. удалённые/локальные MCP-инструменты;
8. полноценный human approval UI;
9. профили агентов и импорт собственных агентов;
10. межмашинная связь с Harness/другими агентами;
11. синхронизация между телефоном и ПК;
12. локальные модели и OpenAI-compatible endpoints;
13. экспорт/import всей AI-среды;
14. granular permissions для каждого инструмента;
15. полноценный activity timeline и воспроизводимый audit trail.

## Сборка

Android Studio + Android SDK 36:

```bash
./gradlew :app:assembleDebug
```

Windows:

```powershell
gradlew.bat :app:assembleDebug
```

## Статус

**Repository #21 — главный продуктовый мессенджер для ИИ.**

Apache-2.0.

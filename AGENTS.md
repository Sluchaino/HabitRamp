# AGENTS.md — HabitRamp
Краткая инструкция для ИИ-агентов. Проект уже реализован: не пересоздавай его,
не меняй функциональность без явной задачи и не делай «попутных» рефакторингов.

## Инварианты

- Приложение полностью офлайн: без аккаунтов, сети, синхронизации и ИИ.
- `ApplicationId` / namespace: `com.habitramp`. MinSdk 26, target/compileSdk 36, JDK 17.
- Формула прогрессии неизменна: `Goal = Base + Step × (2 × Cycle + Day)`.
- Снижение цели на границе цикла (`Day` сбрасывается в 0, `Cycle` растёт) —
  намеренное поведение, а не дефект. Не «исправляй» его.
- Сохраняй данные, схему Room и структуру пакетов. Не удаляй и не переименовывай
  файлы/таблицы/поля без необходимости и без миграции.

## Карта проекта

```
app/src/main/java/com/habitramp/
  core/         модели, единицы, BigDecimal/даты, Room, уведомления
  domain/       доменные модели и чистые калькуляторы (без Android/UI)
  data/         Room-сущности, DAO, мапперы, репозиторий, DataStore
  presentation/ Compose-экраны, ViewModel/StateFlow, Navigation
```

- `data/repository/HabitRepository.kt` — единственная точка записи и дневной
  обработки (`runDailyProcessing`).
- `core/database/HabitDatabase.kt` — Room v1, 6 таблиц, `exportSchema = false`;
  менять схему только с миграцией и тестом.
- `domain/calculator/` — `ProgressionCalculator`, `MissCalculator`,
  `PauseCalculator`, `ResetCalculator`, `StatisticsCalculator`, `UnlockCalculator`.
- `core/notification/` — `NotificationScheduler`, `ReminderReceiver`,
  `BootReceiver`, `DailyMaintenanceWorker`.
- `presentation/navigation/HabitRampNavHost.kt` — маршруты и нижняя навигация.
- `HabitRampApp.kt` — ручной DI-контейнер, `initialize` и периодическая работа.
- `app/src/test/` — JUnit4 unit-тесты; `app/src/androidTest/` — Room/уведомления.

Поток данных: Room → `HabitRepository` (Flow) → ViewModel (`StateFlow`) → Compose.
UI не пишет в DAO напрямую. Время в репозитории — из инжектированного `clock`.

## Команды

```powershell
$env:JAVA_HOME="$env:USERPROFILE\.jdks\temurin-17"
$git = "C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\TeamFoundation\Team Explorer\Git\cmd\git.exe"

.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest --tests "com.habitramp.domain.calculator.ProgressionCalculatorTest"
.\gradlew.bat :app:connectedDebugAndroidTest   # нужен эмулятор/устройство
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Эталон: 89 unit-тестов, 15 инструментальных, debug APK собирается. Перед
передачей работы запускай unit-тесты и сборку; инструментальные — если доступен
эмулятор. Git не в PATH — путь к Git из Visual Studio задан выше.

## Бизнес-правила

### Прогрессия и цель

- Позиция: `Cycle` и `Day` с 0. `Day` растёт после каждого запланированного
  выполнения или пропуска; при `Day >= CycleLength`: `Day = 0`, `Cycle += 1`.
  Незапланированные дни и дни паузы позицию не меняют.
- `Goal` округляется до точности единицы (`HALF_UP`) и не ниже `unit.minGoal`.
  Фактическое значение может быть 0 и не поднимается до минимума.
- Цель: `ASK` — спросить при достижении; `MAINTAIN` — заморозить; `CONTINUE` —
  расти дальше; `FINISH` — завершить. Для `ASK/MAINTAIN/FINISH` цель сверху
  ограничивается target, для `CONTINUE` — нет.
- `ASK` выставляет `targetReached`; выбор применяется через `resolveTarget`.
- Изменение шага/длины цикла пересчитывает базу с сохранением цели; сброс и конец паузы начинают новый цикл.

### Пропуски

- `KEEP` — обычное продвижение на следующую позицию.
- `RESTART` — вернуться к `resetValue` и начать новый цикл.
- `REDUCE` — вычесть штраф из текущей цели (не ниже минимума) и начать новый цикл.
- `PARTIAL` (факт > 0, но < цели) и `MISSED` пишутся в историю, но прогрессия
  обрабатывается по выбранной miss-политике.
- Повторная запись за ту же дату игнорируется: уникальный индекс
  `(habitId, date)` и проверка в репозитории.

### Ручной сброс и пауза

- Ручной сброс: 50 % текущей цели; целые единицы — вверх (`21 → 11`), дробные —
  `HALF_UP`.
- Сброс применяется сразу в запланированный день без записи, иначе откладывается
  до следующего планового дня; действует последний план, `MANUAL_RESET` — при применении.
- Пауза: `WEEKLY` = 7 дней, −40 % за каждую полную неделю; `MONTHLY` =
  календарный месяц, −75 %. Конец паузы — исключающая дата (`[start, end)`).
- Снижение — от `currentGoal - base`, экспоненциально по полным периодам;
  `processedPeriods` даёт идемпотентность. В конце паузы `base = currentGoal`, цикл с 0.
- Дни паузы не становятся пропусками и не дают баллов.

### Баллы и слоты

- 21 балл на слот, максимум 8; уже открытые слоты не отзываются.
- За день: нет запланированных — счётчик не меняется; все выполнены — начисляется
  число выполненных; иначе счётчик сбрасывается в 0.
- Архивация и завершение освобождают активный слот и сохраняют историю.

### Даты и данные

- Даты — epoch-day (`Long`); конец паузы — исключающий.
- Измерения — точные десятичные строки (`BigDecimal`), не `Double`.
- На каждую привычку/дату — ровно одна запись.
- Порядок `runDailyProcessing` важен: паузы → пропущенные дни → отложенные
  сбросы → баллы. Не переставляй его.
- Догоняющая обработка — при старте, изменениях и раз в сутки воркером.

## Известные дефекты (audit labels)

Не исправляй их в подготовительном коммите. Перед фиксом воспроизведи дефект,
добавь регрессионный тест и меняй минимальный участок.

- **H1 — потеря обновлений прогрессии.** `HabitRepository.updateNotifications`
  читает `Habit` вне транзакции и пишет всю сущность — lost update при дневной обработке.
- **H2 — введённое фактическое значение игнорируется кнопкой «Выполнено».**
  `HomeViewModel.markDone()` вызывает `recordCompletion(..., null)`, игнорируя `actualInput`.
- **M1 — прошлые дни используют текущее расписание.** `HistoryViewModel` и
  `HomeViewModel.calculateStreak` берут текущие `weekdays`, а не снимки `HabitSettingsHistory`.
- **M2 — потенциальная статистика завышена на паузах.** `StatisticsCalculator.
  potentialGoals` не принимает паузы: за дни паузы начисляется потенциальная цель.
- **M3 — отложенный сброс показывается как применённый.** `manualReset` сразу
  возвращает новую цель, и UI пишет «Новая цель…», хотя сброс отложен.
- **M4 — удаление привычки не отменяет alarm.** `deleteHabit` удаляет запись, но
  не вызывает `NotificationScheduler.cancel`; возможен лишний broadcast.

## Правила безопасных изменений

- Одна задача — одна ветка/worktree/сессия. Не смешивай H1, H2 и M1–M4.
- Сначала тест, воспроизводящий дефект, затем минимальный фикс.
- Не меняй формулу, округление, формат хранения и публичные сигнатуры без
  согласования.
- Не удаляй данные и не добавляй destructive-миграции; не включай
  `fallbackToDestructiveMigration`.
- Сохраняй русскоязычный UI и офлайн-характер приложения.
- Не коммить `local.properties`, `build/`, `.gradle/`, APK/AAB, ключи и секреты.

## Параллельная работа через git worktree

Из корня основного репозитория (ветка `main` чистая):

```powershell
& $git worktree add ..\HabitRamp-agent-h1 -b fix/h1
& $git worktree add ..\HabitRamp-agent-h2 -b fix/h2
& $git worktree add ..\HabitRamp-agent-m  -b fix/m1-m4
```

Для каждого worktree скопируй `local.properties` или задай `ANDROID_HOME`;
Gradle запускай из его каталога. Удаление: `& $git worktree remove <path>`.

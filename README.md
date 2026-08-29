# Коннектор BSL LS для 1С:EDT

Плагин включает проверки [BSL LS](https://github.com/1c-syntax/bsl-language-server) в среде разработки [1С:EDT](https://edt.1c.ru/).
Каталог формируется из [v8std](https://v8std.ru/diagnostics/bslls/) и сейчас содержит 186 диагностик.

## Возможности

- [x] Диагностики BSL LS в редакторе и панели `Проблемы конфигурации`
- [x] Отдельная проверка EDT для каждого кода BSL LS
- [x] Запуск BSL LS в режимах native и JAR
- [x] Загрузка последних релизов BSL LS и индикация состояния подключения
- [x] Карточки диагностик EDT со статьями и ссылками v8std
- [x] Ручное подавление через регионы `// BSLLS:...-off/on`
- [ ] Быстрые исправления из LSP `codeAction`

## Установка

1. Откройте `Справка` -> `Установить новое ПО`.
2. Введите ссылку:
```
https://malikov-pro.github.io/bslls-connector-for-edt/update/bslls-connector-for-edt/latest/
```
3. Нажмите `Добавить`.
4. Установите флажок на `BSL LS connector for EDT`.
5. Убедитесь, что установлен флажок `Обращаться во время инсталляции ко всем сайтам обновления для поиска требуемого ПО`.
6. Нажмите `Далее` -> `Готово`.
7. Перезапустите 1С:EDT.

### Первый запуск

Плагин **не** кладёт BSL Language Server в p2 и **не** качает его при старте EDT. Дистрибутив native/jar берётся из рабочей папки `~/.bsl-connector-for-edt` (один слот на режим). Если слот пуст, в настройках показывается список из пяти последних релизов GitHub — загрузка только после выбора.

1. Откройте `Окно` → `Параметры` → `Коннектор BSL LS`.
2. Выберите режим запуска:
   * **Нативный** — zip под ОС (`_win.zip` / `_nix.zip` / `_mac.zip`), без отдельной Java.
   * **JAR** — `*-exec.jar`. **Команда Java** из PATH; для BSL LS 1.x нужна **Java 21+** (JVM EDT 17 не подходит).
3. Если кэша нет — выберите релиз и нажмите **Скачать**. Новая загрузка затирает предыдущий слот. Своего файла через «Обзор» нет.
4. **Включите проверки BSL LS** — без этого замечания не появятся, даже если LS запущен:
   * на уровне воркспейса: `Окно` → `Параметры` → `1C:Enterprise Development Tools` → `Проверки конфигурации` — отметьте категории `BSL LS` (или отдельные проверки с кодами BSL LS);
   * или на уровне проекта: ПКМ по проекту → `Свойства` → `Проверки`.
5. **Применить** — плагин перезапустит подключение к LS.
6. Проверка: сохраните BSL-модуль с нарушением (например, строку длиннее 120 символов) — в панели `Проблемы конфигурации` появится замечание с префиксом `[BSL LS]`. Если замечаний нет — см. [issue #2](https://github.com/malikov-pro/bslls-connector-for-edt/issues/2) и журнал ошибок (`Окно` → `Журнал ошибок`); stderr процесса LS пишется в `~/.bsl-connector-for-edt/logs/ls-stderr-<режим>.log`.

Для настройки проверки используется файл [.bsl-language-server.json](https://1c-syntax.github.io/bsl-language-server/features/ConfigurationFile/) (ключ `-c` / `--configuration` только для native и jar). Путь к файлу задаётся в настройках коннектора; если файл не найден, в журнал ошибок пишется предупреждение.

Шаблон файла `.bsl-language-server.json` можно взять [example/.bsl-language-server.json](/example/.bsl-language-server.json).

⚠️ Не включайте в конфиге `traceLog`: BSL LS пишет лог **внутрь анализируемого проекта** (относительно рабочего каталога), и эти файлы засоряют проект в EDT.

Конфигурационный файл должен содержать:
* Событие запуска анализа `computeTrigger` на `onSave`.
* Путь к метаданным проекта в свойстве `configurationRoot`.

### Отладка

В настройках коннектора есть группа **«Отладка»** с флажком **«Вести отладочный журнал»**. Для обычной работы включать не нужно.

* Ошибки и предупреждения плагина попадают в журнал **всегда**; флажок добавляет отладочные сообщения: команда запуска BSL LS, запуск LSP, LSP trace `verbose` вместо `off` (trace применяется при следующем запуске LS — кнопки «Применить»/«OK» перезапускают LS).
* Где смотреть отладочную информацию:
  * `Окно` → `Показать вид` → `Другое…` → `Общие` → `Журнал ошибок` — журнал Eclipse в интерфейсе;
  * файл `<воркспейс>/.metadata/.log` — тот же журнал на диске;
  * stderr процесса BSL LS: `~/.bsl-connector-for-edt/logs/ls-stderr-<режим>.log` — пишется всегда, независимо от флажка.

### Просмотр списка найденных проблем

Замечания BSL LS попадают в панель 1С:EDT `Проблемы конфигурации` и видны в `get_project_errors`.
Каждая диагностика LS — отдельная проверка EDT: код совпадает с ключом BSL LS (`LineLength`, `MethodSize`, …).

Проверки EDT и BSL LS идут **параллельно**, замечания не склеиваем. Текст LS в обоих каналах коннектора начинается с `[BSL LS]`, чтобы его нельзя было принять за типовое сообщение EDT. `ICheck` пишет в `Проблемы конфигурации` при обычной проверке проекта, как встроенные EDT. ПКМ **«Проверить»** — дорогой прогон Xtext EDT (надпись «Проверка Xтекст» коннектор не рисует); обычное сохранение тоже поднимает Xtext-канал.

* **Подавить** у проверки EDT пишет `//@skip-check <код EDT>` — для типовых проверок так и осталось.
* Для диагностик BSL LS коннектор убирает эту вставку и добавляет вместо неё регионы `// BSLLS:<Код>-off` / `// BSLLS:<Код>-on` вокруг диапазона ошибки из ответа LS (аналога диагностики в типовой EDT нет — строка осталась бы мусором). Если LS недоступен, вставка остаётся. Регионы можно писать и вручную: `// BSLLS:DeprecatedCurrentDate-off` … `// BSLLS:DeprecatedCurrentDate-on` и глобальные `// BSLLS:off` … `// BSLLS:on`.
* **Открыть проверку** открывает карточку диагностики: текст статьи с [v8std.ru](https://v8std.ru/diagnostics/bslls/LineLength/) (раздел `bslls`). В «См.» — URL карточки v8std и документации BSL LS (текст ссылки = сам URL), плюс ссылки из статьи (ИТС / std).

Каталог кодов: [v8std.ru/diagnostics](https://v8std.ru/diagnostics/) (раздел `bslls`). Список в плагине обновляется скриптом `scripts/generate-ls-checks.py`.

### Установка из архива

1. `Справка` → `Установить новое ПО` → `Добавить` → `Архив`.
2. Укажите zip:
   `connector/repositories/com.github.malikov-pro.dt.bsl.lsconnector.repository/target/com.github.malikov-pro.dt.bsl.lsconnector.repository-0.4.0-SNAPSHOT.zip`
3. **Снимите** флажок `Обращаться во время инсталляции ко всем сайтам обновления…`. Иначе p2 лезет на `services.1c.dev` (ошибка аутентификации) и потом не находит локальные артефакты (`No repository found containing`).
4. Каждая сборка даёт новый квалификатор (`0.4.0.v20260822191503`). Если EDT пишет **«Все элементы установлены»**, в zip тот же номер, что уже стоит: пересоберите (`mvn clean verify …`) или удалите фичу в `Справка` → `О программе` → `Сведения об установке` и ставьте заново.
5. Выберите `BSL LS connector for EDT` → `Далее` → `Готово` → перезапустите EDT.

### Публикация сайта обновления

Ссылка установки — это статические файлы p2-репозитория на GitHub Pages:

```
https://malikov-pro.github.io/bslls-connector-for-edt/update/bslls-connector-for-edt/latest/
```

Публикует workflow `deploy-update-site.yml` (запуск: публикация релиза или вручную
из вкладки Actions): собирает p2 и перезаписывает путь `…/latest/` — EDT видит
обновление по изменившемуся квалификатору. Для работы нужны:
1. Репозиторий под аккаунтом `malikov-pro`, в его настройках:
   `Settings → Pages → Source: GitHub Actions`.
2. Секреты `MAVEN_USERNAME` / `MAVEN_CENTRAL_TOKEN` (доступ к реджестри 1С).

## Разработчикам

### Требования

* JDK 17+
* Maven 3.9+ (Tycho 4.0.5 на 3.8.x падает «requires Maven version 3.9.0»)
* Доступ к репозиторию EDT (credentials в `connector/bom/settings.xml`)
* Сабмодуль [zeegin/v8std](https://github.com/zeegin/v8std): `git clone --recurse-submodules …` или `git submodule update --init third_party/v8std`

### Целевая платформа в EDT

`Reload Target Platform` есть только в **EDT для разработки плагинов** (PDE). В обычном EDT с конфигурациями 1С этого пункта нет: туда ставится уже собранный p2 (`Справка` → `Установить новое ПО`).

В PDE-workspace:

1. Вид **Project Explorer** (не Package Explorer).
2. Проект `default` (в `connector/targets/default/`) → файл `default.target` (EDT 2025.2 + Eclipse 2025-12). Для 2026.1 — `connector/targets/edt-2026.1/edt-2026.1.target`.
3. Либо **Окно → Параметры → Plug-in Development → Target Platform** → **Add** → **Workspace**.

Target Editor этой PDE не открывает `<location type="Maven">` (utils). Для компиляции в IDE используйте локальный таргет `connector/targets/local-edt-2025.2.target` (p2-локации + стабильный каталог `connector/targets/local-p2/`, который обновляет `compile.sh`) и «Set as Active Target Platform».

### Локальная сборка

> Канонический путь — скрипт в корне репозитория: он запускает сборку
> (entity-лимиты берёт из `connector/.mvn/jvm.config`), обновляет стабильный
> каталог `connector/targets/local-p2/` для IDE-таргета и печатает путь к p2-zip.

```bash
bash compile.sh
# профиль EDT 2026.1:
bash compile.sh --profile edt-2026.1
```

Ниже — те же шаги вручную (например, для Windows без bash):

> XML-entity лимиты уже заданы в `connector/.mvn/jvm.config` — Maven подхватывает их сам.

#### Linux / macOS

```bash
cd connector
mvn verify -Dtycho.localArtifacts=ignore
```

#### Windows

```bat
cd connector
mvn verify -Dtycho.localArtifacts=ignore
```

По умолчанию сборка идёт против EDT **2025.2**. Для EDT **2026.1**:

```bash
mvn verify -Dtycho.localArtifacts=ignore -Pedt-2026.1
```

Исходник один: версии пакетов 1С в `MANIFEST.MF` не зафиксированы. 2025.2 берёт LSP4J 0.23.1, 2026.1 — LSP4J 1.0.0.

Каталог диагностик (`plugin.xml`, `ls-diagnostics.tsv`) и карточки `check.descriptions/` собираются из сабмодуля [zeegin/v8std](https://github.com/zeegin/v8std) (`third_party/v8std`). Карточки в git не хранятся: их пишет `scripts/generate-ls-checks.py` и кладёт в плагин при сборке. Для каждого кода из `docs/diagnostics/bslls` и `docs/diagnostics/v8-code-style` исходная статья копируется как `<код>.md`, а рядом создаётся HTML-карточка EDT `<код>.html`. BSL LS использует CamelCase-коды (`LineLength`), EDT — dash-case (`module-unused-local-variable`).

```bash
git submodule update --init third_party/v8std
python3 scripts/generate-ls-checks.py
# или при Maven-сборке (если сабмодуль уже есть, профиль generate-checks включается сам):
mvn -pl bundles/com.github.malikov-pro.dt.bsl.lspconnector generate-resources   # из connector/
```

Чужой клон: `python3 scripts/generate-ls-checks.py --v8std-dir путь/к/v8std`.
Без сети: `--offline` (нужен сабмодуль или ранее скачанный `.cache/v8std`).
По желанию можно передать старый индекс BSL LS (markdown-таблица с ключами) первым аргументом.

Результат сборки — p2-репозиторий в `connector/repositories/com.github.malikov-pro.dt.bsl.lsconnector.repository/target/`.
Ставьте этот репозиторий в ту EDT, под которую собирали.

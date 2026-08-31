# Коннектор BSL LS для 1С:EDT

[![GitHub all releases](https://img.shields.io/github/downloads/malikov-pro/bslls-connector-for-edt/total)](https://github.com/malikov-pro/bslls-connector-for-edt/releases)
[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)

[![Непрерывная интеграция](https://github.com/malikov-pro/bslls-connector-for-edt/actions/workflows/ci.yml/badge.svg)](https://github.com/malikov-pro/bslls-connector-for-edt/actions/workflows/ci.yml)
[![Релиз](https://github.com/malikov-pro/bslls-connector-for-edt/actions/workflows/release.yml/badge.svg)](https://github.com/malikov-pro/bslls-connector-for-edt/actions/workflows/release.yml)
[![Deploy Update Site](https://github.com/malikov-pro/bslls-connector-for-edt/actions/workflows/deploy-update-site.yml/badge.svg)](https://github.com/malikov-pro/bslls-connector-for-edt/actions/workflows/deploy-update-site.yml)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=malikov-pro_bslls-connector-for-edt&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=malikov-pro_bslls-connector-for-edt)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=malikov-pro_bslls-connector-for-edt&metric=bugs)](https://sonarcloud.io/summary/new_code?id=malikov-pro_bslls-connector-for-edt)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=malikov-pro_bslls-connector-for-edt&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=malikov-pro_bslls-connector-for-edt)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=malikov-pro_bslls-connector-for-edt&metric=coverage)](https://sonarcloud.io/summary/new_code?id=malikov-pro_bslls-connector-for-edt)

Плагин включает проверки [BSL LS](https://github.com/1c-syntax/bsl-language-server) в среде разработки [1С:EDT](https://edt.1c.ru/), пишет ошибки в совместимый с [EDT-MCP](https://github.com/DitriXNew/EDT-MCP) стек, дополняет описания ошибок для пользователя из [v8std](https://v8std.ru/diagnostics/bslls/).

## Возможности

- [x] Добавляет диагностики BSL LS в панель `Ошибки конфигурации` — при сохранении модуля и при стандартной проверке
- [x] Каждая диагностика BSL LS — отдельная проверка EDT: включается и настраивается в стандартных настройках проверок, как типовые
- [x] Скачивает и запускает BSL LS без установки: вариант native (Java не нужна) или JAR
- [x] Показывает состояние подключения к BSL LS
- [x] Карточка диагностики со статьёй из v8std (кнопка «Открыть проверку»)
- [x] Подавление замечаний кнопкой «Подавить» или регионами `// BSLLS:Код-off/on`
- [ ] Быстрые исправления (quick fixes) — в планах

## Установка

1. Откройте `Справка` -> `Установить новое ПО`.
2. Введите ссылку:
```
https://malikov-pro.github.io/bslls-connector-for-edt/
```
3. Нажмите `Добавить`.
4. Установите флажок на `BSL LS connector for EDT`.
5. Убедитесь, что установлен флажок `Обращаться во время инсталляции ко всем сайтам обновления для поиска требуемого ПО`.
6. Нажмите `Далее` -> `Готово`.
7. Перезапустите 1С:EDT.

### Первый запуск

Плагин сам ничего не устанавливает: BSL Language Server скачивается один раз через панель настроек коннектора — в нужном вам варианте (native или JAR). Плагин сохраняет его в своей рабочей папке (`~/.bsl-connector-for-edt`) и запускает оттуда при работе с кодом.

1. Откройте `Окно` → `Параметры` → `Коннектор BSL LS`.
2. Выберите режим запуска:
   * **Нативный** — zip под ОС (`_win.zip` / `_nix.zip` / `_mac.zip`), без отдельной Java.
   * **JAR** — `*-exec.jar`. **Команда Java** из PATH; для BSL LS 1.x нужна **Java 21+** (JVM EDT 17 не подходит).
3. Если кэша нет — выберите релиз и нажмите **Скачать**. Новая загрузка затирает предыдущий слот. Своего файла через «Обзор» нет.
4. Замечания появляются сами — проверки BSL LS включаются автоматически при установке. Если их нет, убедитесь, что проверки не выключены:
   * на уровне воркспейса: `Окно` → `Параметры` → `1C:Enterprise Development Tools` → `Проверки конфигурации` — отметьте категории `BSL LS` (или отдельные проверки с кодами BSL LS);
   * или на уровне проекта: ПКМ по проекту → `Свойства` → `Проверки`.
5. **Применить** — плагин перезапустит подключение к LS.
6. Проверка: сохраните BSL-модуль с нарушением (например, строку длиннее 120 символов) — в панели `Ошибки конфигурации` появится замечание с префиксом `[BSL LS]`. Если замечаний нет — см. [issue #2](https://github.com/malikov-pro/bslls-connector-for-edt/issues/2) и журнал ошибок (`Окно` → `Журнал ошибок`); stderr процесса LS пишется в `~/.bsl-connector-for-edt/logs/ls-stderr-<режим>.log`.

### Дополнительные настройки BSL LS

Поведение проверок BSL LS задаётся файлом `.bsl-language-server.json`
([формат](https://1c-syntax.github.io/bsl-language-server/features/ConfigurationFile/)).
Файл кладётся в **корень воркспейса EDT** (рабочей папки с проектами) вручную:
коннектор сам находит его при запуске BSL LS и передаёт параметром `--configuration`
(поиск идёт и по подпапкам, берётся первый найденный). Если файла нет, BSL LS
работает со стандартными настройками.
Кнопка «Открыть рабочую папку» в настройках — в планах ([issue #15](https://github.com/malikov-pro/bslls-connector-for-edt/issues/15)).

Файл должен содержать:
* событие запуска анализа `computeTrigger` со значением `onSave`;
* путь к метаданным проекта в свойстве `configurationRoot`.

Шаблон: [example/.bsl-language-server.json](/example/.bsl-language-server.json).

⚠️ Не включайте в конфиге `traceLog`: BSL LS пишет лог **внутрь анализируемого проекта** (относительно рабочего каталога), и эти файлы засоряют проект в EDT.

### Отладка

В настройках коннектора есть группа **«Отладка»** с флажком **«Вести отладочный журнал»**. Для обычной работы включать не нужно.

* Ошибки и предупреждения плагина попадают в журнал **всегда**; флажок добавляет отладочные сообщения: команда запуска BSL LS, запуск LSP, LSP trace `verbose` вместо `off` (trace применяется при следующем запуске LS — кнопки «Применить»/«OK» перезапускают LS).
* Где смотреть отладочную информацию:
  * `Окно` → `Показать вид` → `Другое…` → `Общие` → `Журнал ошибок` — журнал Eclipse в интерфейсе;
  * файл `<воркспейс>/.metadata/.log` — тот же журнал на диске;
  * stderr процесса BSL LS: `~/.bsl-connector-for-edt/logs/ls-stderr-<режим>.log` — пишется всегда, независимо от флажка.

### Просмотр списка найденных проблем

Замечания BSL LS попадают в панель 1С:EDT **«Ошибки конфигурации»**. Если панель закрыта: `Окно` → `Показать панель` → `Ошибки конфигурации`. Те же ошибки видны в [MCP EDT](https://github.com/DitriXNew/EDT-MCP) через `get_project_errors`.
Каждая диагностика LS — отдельная проверка EDT: код совпадает с ключом BSL LS (`LineLength`, `MethodSize`, …).

Замечания BSL LS обновляются при правке модуля — после паузы EDT запускает «Расширенную проверку модулей», а также при открытии, сохранении и по **ПКМ → «Проверить»**. Проверки EDT и BSL LS идут **параллельно** и не склеиваются; текст замечаний LS начинается с `[BSL LS]`, чтобы его нельзя было спутать с типовым замечанием EDT. Подробнее о потоках проверок — в [CHECK-FLOWS.md](CHECK-FLOWS.md).

* **Подавить** у проверки EDT пишет `//@skip-check <код EDT>` — для типовых проверок так и осталось.
* Для диагностик BSL LS коннектор убирает эту вставку и добавляет вместо неё регионы `// BSLLS:<Код>-off` / `// BSLLS:<Код>-on` вокруг диапазона ошибки из ответа LS (аналога диагностики в типовой EDT нет — строка осталась бы мусором). Если LS недоступен, вставка остаётся. Регионы можно писать и вручную: `// BSLLS:DeprecatedCurrentDate-off` … `// BSLLS:DeprecatedCurrentDate-on` и глобальные `// BSLLS:off` … `// BSLLS:on`.
* **Открыть проверку** открывает карточку диагностики: текст статьи с [v8std.ru](https://v8std.ru/diagnostics/bslls/LineLength/) (раздел `bslls`). В разделе «См.» — URL карточки v8std и документации BSL LS (текст ссылки = сам URL), плюс ссылки из статьи (ИТС / std).

Каталог кодов диагностик: [v8std.ru/diagnostics](https://v8std.ru/diagnostics/) (раздел `bslls`).

### Установка из архива (без интернета)

1. Скачайте zip со страницы [Releases](https://github.com/malikov-pro/bslls-connector-for-edt/releases) — файл `…repository-<версия>.zip`.
2. `Справка` → `Установить новое ПО` → `Добавить` → `Архив` → укажите скачанный zip.
3. **Снимите** флажок `Обращаться во время инсталляции ко всем сайтам обновления…`. Иначе p2 лезет на `services.1c.dev` (ошибка аутентификации) и не находит локальные артефакты (`No repository found containing`).
4. Выберите `BSL LS connector for EDT` → `Далее` → `Готово` → перезапустите EDT.
5. Если EDT пишет **«Все элементы установлены»**, та же версия уже стоит: удалите фичу в `Справка` → `О программе` → `Сведения об установке` и поставьте заново.

## Разработчикам

Сборка, целевая платформа EDT, ветвление, релизы и каталог диагностик — в
[Руководстве разработчика](DEVELOPER.md).

## Статьи о плагине

[Проверки BSL Language Server прямо в 1С:EDT: оживляем коннектор](https://infostart.ru/1c/articles/2776674/) на ![Инфостарт](https://infostart.ru/bitrix/templates/sandbox_empty/assets/tpl/abo/img/logo.svg)

## Лицензия

[AGPL-3.0](LICENSE) — та же, что у [BSL LS](https://github.com/1c-syntax/bsl-language-server).
Проект включает код оригинального
[dt.bsl.lsconnector](https://github.com/otymko/dt.bsl.lsconnector)
(MIT © 2020 Oleg Tymko) — его уведомление сохранено в [LICENSE-MIT](LICENSE-MIT).

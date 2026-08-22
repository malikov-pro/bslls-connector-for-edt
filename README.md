# Коннектор BSLLS для 1С:EDT

Плагин включает проверки [BSL LS](https://github.com/1c-syntax/bsl-language-server) в среде разработки [1С:EDT](https://edt.1c.ru/).
Это добавляет `128+` [диагностик](https://1c-syntax.github.io/bsl-language-server/diagnostics/).

## Возможности

- [x] Проверки кода
- [ ] Быстрые исправления
- [ ] Произвольные ссылки

## Установка

1. Откройте `Справка` -> `Установить новое ПО`.
2. Введите ссылку:
```
https://otymko.github.io/bslls-connector-for-edt/update/bslls-connector-for-edt/latest/
```
3. Нажмите `Добавить`.
4. Установите флажок на `BSL LS connector for EDT`.
5. Убедитесь, что установлен фложок `Обращаться во время инсталяции ко всем сайтам обновления для поиска требуемого ПО`.
6. Нажмите `Далее` -> `Готово`.
7. Перезапустите 1С:EDT.

### Первый запуск

BSL Language Server (`*-exec.jar`) входит в плагин на этапе Maven-сборки. Отдельная загрузка с GitHub не нужна.

1. Откройте `Окно` → `Параметры` → `Коннектор BSLLS`.
2. **Команда Java** — `java` из PATH (для BSL LS 1.x нужна **Java 21+**; JVM самой EDT 2025.2 — 17, её указывать не надо).
3. **Путь к BSL LS** оставьте пустым, чтобы использовать встроенный jar.

Для настройки проверки используется файл [.bsl-language-server.json](https://1c-syntax.github.io/bsl-language-server/features/ConfigurationFile/).

Шаблон файла `.bsl-language-server.json` можно взять [example/.bsl-language-server.json](/example/.bsl-language-server.json).

Конфигурационный файл должен содержать:
* Событие запуска анализа `computeTrigger` на `onSave`.
* Путь к метаданным проекта в свойстве `configurationRoot`.

### Просмотр списка найденных проблем

Проверки, выполняемые 1С:EDT и текущим плагином используют разные панели отображения ошибок. Панель 1С:EDT разработана отдельно, называется `Проблемы конфигурации`. Плагин использует типовую панель Eclipse `Проблемы`.

### Установка из архива

Аналогична установке по адресу.
При выполнении шага 2 нажмите `Архив`.

## Разработчикам

### Требования

* JDK 17+
* Maven 3.9+
* Доступ к репозиторию EDT (credentials в `bom/settings.xml`)
* Плагин lombok (https://projectlombok.org/setup/eclipse) — для работы в IDE

### Целевая платформа в EDT

`Reload Target Platform` есть только в **EDT для разработки плагинов** (PDE). В обычном EDT с конфигурациями 1С этого пункта нет: туда ставится уже собранный p2 (`Справка` → `Установить новое ПО`).

В PDE-workspace:

1. Вид **Project Explorer** (не Package Explorer).
2. Проект `default` → файл `default.target` (EDT 2025.2 + Eclipse 2025-12). Для 2026.1 — `targets/edt-2026.1/edt-2026.1.target`.
3. Либо **Окно → Параметры → Plug-in Development → Target Platform** → **Add** → **Workspace**.

Target Editor этой PDE не открывает `<location type="Maven">` (lombok/utils). Для компиляции в IDE достаточно **Running Platform**, если в ней есть бандлы `com._1c.g5.v8.dt.*`.

### Локальная сборка

> `tycho-compiler-plugin` не умеет обрабатывать аннотации `lombok` вне Eclipse IDE,
> поэтому `lombok.jar` подключается как `-javaagent` через `MAVEN_OPTS`.

#### Linux / macOS

```bash
# 1. Скачайте lombok
export MAVEN_OPTS="-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0"
mvn dependency:copy@get-lombok -pl bundles/com.github.otymko.dt.bsl.lspconnector

# 2. Соберите проект
export MAVEN_OPTS="-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -javaagent:$(pwd)/bundles/com.github.otymko.dt.bsl.lspconnector/target/lombok.jar=ECJ"
mvn verify -Dtycho.localArtifacts=ignore
```

#### Windows

```bat
rem 1. Скачайте lombok
set MAVEN_OPTS=-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0
mvn dependency:copy@get-lombok -pl bundles/com.github.otymko.dt.bsl.lspconnector

rem 2. Соберите проект
set MAVEN_OPTS=-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -javaagent:%cd%\bundles\com.github.otymko.dt.bsl.lspconnector\target\lombok.jar=ECJ
mvn verify -Dtycho.localArtifacts=ignore
```

По умолчанию сборка идёт против EDT **2025.2**. Для EDT **2026.1**:

```bash
mvn verify -Dtycho.localArtifacts=ignore -Pedt-2026.1
```

Исходник один: версии пакетов 1С в `MANIFEST.MF` не зафиксированы. 2025.2 берёт LSP4J 0.23.1, 2026.1 — LSP4J 1.0.0.

Результат сборки — p2-репозиторий в `repositories/com.github.otymko.dt.bsl.lsconnector.repository/target/`.
Ставьте этот репозиторий в ту EDT, под которую собирали.

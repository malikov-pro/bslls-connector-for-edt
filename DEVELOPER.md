# Руководство разработчика — bslls-connector-for-edt

Плагин включает проверки [BSL LS](https://github.com/1c-syntax/bsl-language-server) в 1С:EDT.
Обзор возможностей, установка и первый запуск — в [README](README.md).

## Содержание

- [Требования](#требования)
- [Ветки и релизы](#ветки-и-релизы)
- [Сайт обновления (GitHub Pages)](#сайт-обновления-github-pages)
- [Целевая платформа в EDT](#целевая-платформа-в-edt)
- [Локальная сборка](#локальная-сборка)
- [Каталог диагностик](#каталог-диагностик)
- [Уровни проверки](#уровни-проверки)

## Требования

* JDK 17+
* Maven 3.9+ (Tycho 4.0.5 на 3.8.x падает «requires Maven version 3.9.0»)
* Доступ к репозиторию EDT (credentials в `connector/bom/settings.xml`)
* Сабмодуль [zeegin/v8std](https://github.com/zeegin/v8std): `git clone --recurse-submodules …` или `git submodule update --init third_party/v8std`

## Ветки и релизы

* `develop` — рабочая интеграционная ветка (default). Признаки `feature/*` / `fix/*` заводятся от неё.
* `main` — стабильная: в неё попадают готовые изменения из `develop` (merge по готовности к релизу).
* Теги `X.Y.Z` ставятся только на `main`.

Релиз — пуш тега из `main`, дальше всё автоматом (workflow `release.yml`):
сборка Tycho со снятым `-SNAPSHOT`, GitHub Release с p2-zip и автоматическое
обновление сайта обновления (GitHub Pages).

Порядок выпуска версии (пример `0.5.0`):

```bash
# 1. В develop поднять версию (обновит pom, MANIFEST и feature):
cd connector
mvn org.eclipse.tycho:tycho-versions-plugin:4.0.5:set-version -DnewVersion=0.5.0-SNAPSHOT -DgenerateBackupPoms=false
# 2. bom — родитель ВНЕ реактора, set-version его не трогает. Вручную поставить
#    0.5.0-SNAPSHOT в connector/bom/pom.xml (<version> bom'а) и в <parent><version>
#    файла connector/pom.xml.
# 3. Закоммитить в develop, слить в main и поставить тег:
git checkout main
git merge --no-ff develop
git tag 0.5.0
git push origin main develop --tags
```

Workflow сверяет тег с версией в pom: тег `0.5.0` требует `<version>0.5.0-SNAPSHOT</version>` в коммите.
У релизной сборки версия фиксированная (`set-version` снимает и `-SNAPSHOT`, и `.qualifier`),
в `develop` остаётся `X.Y.Z-SNAPSHOT` → `Bundle-Version: X.Y.Z.qualifier` — каждая сборка получает свой квалификатор с меткой времени.

CI (`ci.yml`) собирает каждый push/PR, после сборки гоняет анализ SonarCloud
(конфиг — `sonar-project.properties`, секрет `SONAR_TOKEN`; на форк-PR шаг пропускается).

### Сайт обновления (GitHub Pages)

Ссылка установки для пользователей (p2 лежит в корне Pages):

```
https://malikov-pro.github.io/bslls-connector-for-edt/
```

Публикует workflow `deploy-update-site.yml` (событие «релиз опубликован» или
вручную из вкладки Actions): собирает p2, кладёт его в корень Pages и дублирует
на старый путь `…/update/bslls-connector-for-edt/latest/` — у уже установленных
экземпляров EDT он прописан в «Доступных сайтах обновления», HTML-редирект p2
не понимает. EDT видит обновление по изменившемуся квалификатору.

Требуется:

1. `Settings → Pages → Source: GitHub Actions`.
2. Секреты `MAVEN_USERNAME` / `MAVEN_CENTRAL_TOKEN` (доступ к реджестри 1С).

## Целевая платформа в EDT

`Reload Target Platform` есть только в **EDT для разработки плагинов** (PDE). В обычном EDT с конфигурациями 1С этого пункта нет: туда ставится уже собранный p2 (`Справка` → `Установить новое ПО`).

В PDE-workspace:

1. Вид **Project Explorer** (не Package Explorer).
2. Проект `default` (в `connector/targets/default/`) → файл `default.target` (EDT 2025.2 + Eclipse 2025-12). Для 2026.1 — `connector/targets/edt-2026.1/edt-2026.1.target`.
3. Либо **Окно → Параметры → Plug-in Development → Target Platform** → **Add** → **Workspace**.

Target Editor этой PDE не открывает `<location type="Maven">` (utils). Для компиляции в IDE используйте локальный таргет `connector/targets/local-edt-2025.2.target` (p2-локации + стабильный каталог `connector/targets/local-p2/`, который обновляет `compile.sh`) и «Set as Active Target Platform».

## Локальная сборка

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

Результат сборки — p2-репозиторий в `connector/repositories/com.github.malikov-pro.dt.bsl.lsconnector.repository/target/`.
Ставьте этот репозиторий в ту EDT, под которую собирали.

## Каталог диагностик

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

Править сгенерированное (`plugin.xml` `<check>`-записи, tsv, карточки) руками нельзя — затрёт следующая регенерация.

## Уровни проверки

Зелёный нижний ярус не доказывает верхний: «собралось» ≠ «работает в EDT».

* **Ярус 0 — сборка:** `bash compile.sh` → BUILD SUCCESS, свежий квалификатор в имени p2-zip.
* **Ярус 1 — юнит-тесты:** планируется; чистая логика — регионы подавления, маппинг CamelCase↔dash-case.
* **Ярус 2 — живая установка:** `bash scripts/deploy-edt.sh` в EDT 2025.2, замечание `[BSL LS] …` в панели `Ошибки конфигурации`.
* **Ярус 3 — e2e в CI:** план; headless EDT через `p2 director`.

Перед словами «готово» и коммитом — чек-лист в `.claude/skills/bslls-ready-to-deploy/SKILL.md`.

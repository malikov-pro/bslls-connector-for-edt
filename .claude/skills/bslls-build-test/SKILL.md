---
name: bslls-build-test
description: Как собрать плагин bslls-connector-for-edt (Maven/Tycho) локально через compile.sh, получить p2-zip и установить его в EDT 2025.2. Использовать при сборке, проверке изменений перед коммитом или проблемах установки p2.
---

# Сборка и установка

## Раскладка

Реактор Maven/Tycho — в `connector/` (каталог изолирован для работы PDE):

| Каталог | Что |
|---|---|
| `connector/bom/` | родительский pom + `settings.xml`/`edt-credentials.env` (доступ к p2 EDT) |
| `connector/bundles/com.github.malikov-pro.dt.bsl.lspconnector` | сам плагин (один бандл) |
| `connector/features/com.github.malikov-pro.dt.bsl.lsconnector` | фича |
| `connector/repositories/com.github.malikov-pro.dt.bsl.lsconnector.repository` | p2-репозиторий (результат сборки) |
| `connector/targets/default/default.target` | целевая платформа EDT 2025.2 + Eclipse 2025-12 |
| `connector/targets/edt-2026.1/edt-2026.1.target` | платформа EDT 2026.1 (профиль `edt-2026.1`) |

Внимание к именам: бандл — **lsp**connector, фича/репозиторий — **ls**connector.

## Сборка

Единственный канонический вход — скрипт в корне:

```bash
bash compile.sh                     # mvn clean verify -T 1C + путь к zip
bash compile.sh --profile edt-2026.1
```

Скрипт сам: подхватывает `connector/bom/edt-credentials.env`, запускает сборку
(entity-лимиты из `connector/.mvn/jvm.config`) и обновляет стабильный каталог
`connector/targets/local-p2/` для IDE-таргета.

- Toolchain можно передать явно: `--java-home <JDK17+>` / `--maven-home <Maven>`.
  Точные пути машинно-специфичны — искать на месте, не хардкодить.
  Maven нужен **3.9+**: Tycho 4.0.5 на системном 3.8.x падает
  «requires Maven version 3.9.0».
- Первая сборка медленная: Tycho тянет p2 EDT (`edt.1c.ru`) и Eclipse SDK
  (сотни МБ) в `~/.m2/repository/p2`. Дальше — минуты.
- Нет сети/креденшеллов → сборка честно падает; не выдавать «зелёный» за факт.

## Результат и установка в тестовую EDT

Артефакт: `connector/repositories/com.github.malikov-pro.dt.bsl.lsconnector.repository/target/*.zip`
(имя содержит свежий квалификатор вида `0.4.0.v20260822191503`).

Канонический путь установки — скрипт через `p2 director`, без GUI:

```bash
bash scripts/deploy-edt.sh                 # авто-поиск 1C_EDT* в ~/.local/share/1C/1cedtstart/installations
bash scripts/deploy-edt.sh --edt "/путь/к/каталогу/1cedt"
```

Скрипт сам снимает прежнюю копию (фича закрепляет точный квалификат бандла —
без снятия будет конфликт зависимостей) и проверяет результат в `bundles.info`.

Важные факты:
- **EDT во время установки должна быть закрыта** (p2 блокирует профиль).
- Установка идёт в **инсталляцию**, не в воркспейс; артефакты инсталляций
  1cedtstart лежат в общем пуле `~/.p2/pool/plugins|features`.
- Ручной путь (GUI): zip → `Справка → Установить новое ПО → Добавить → Архив`,
  флажок «Обращаться во время инсталляции ко всем сайтам обновления…» **снять**
  (иначе p2 уйдёт на `services.1c.dev` и упадёт на аутентификации),
  затем перезапуск EDT.
- «Все элементы установлены» = квалификатор в zip совпадает с установленным:
  пересобрать (`bash compile.sh`) или удалить фичу в
  `Справка → О программе → Сведения об установке`.

## Перед коммитом

- [ ] `bash compile.sh` → BUILD SUCCESS
- [ ] имя zip со свежей датой-квалификатором
- [ ] README/DEVELOPER.md обновлены, если менялось поведение (установка/сборка/режимы)
- [ ] `git status` чистый (нет мусора вне ожидаемого diff'а)

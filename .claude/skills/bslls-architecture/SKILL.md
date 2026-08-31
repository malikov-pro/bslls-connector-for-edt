---
name: bslls-architecture
description: Карта бандла bslls-connector-for-edt — пакеты, два канала замечаний (ICheck и Xtext-валидатор), жизненный цикл BSL LS, кэш и режимы запуска. Использовать перед любой правкой кода, чтобы понять, где что лежит и кто на кого влияет.
---

# Архитектура бандла

Один бандл: `connector/bundles/com.github.malikov-pro.dt.bsl.lspconnector`,
корень пакетов `com/github/malikov-pro/dt/bsl/lsconnector`.
Плагин подключает внешний процесс **BSL Language Server** по LSP и публикует
его диагностики как проверки EDT.

## Пакеты

| Пакет | Содержит | Правило |
|---|---|---|
| *(корень)* | `BSLPlugin` — активатор, доступ к сервисам, `~/.bsl-connector-for-edt`; `BSLLsCheck` — запасная ICheck «Прочие диагностики BSL LS» для кодов вне каталога; `BSLValidator` — внешний Xtext-валидатор модулей | активатор — не свалка утилит |
| `check/` | Мост «диагностика LS → проверка EDT»: `BslLsDiagnosticCheck` (одна BasicCheck на код LS, инстанцируется из plugin.xml), `LsDiagnosticCatalog` (читает `ls-diagnostics.tsv`, `MESSAGE_PREFIX`), `LsCheckSupport`, `LsModuleAnalyzer`, `LsSkipCheck` (regex регионов подавления), `LsSuppressionComments` (разбор строки `//@skip-check`), `LsDiagnosticInfo` | всё генерируемое живёт рядом: `plugin.xml` + tsv |
| `listener/` | Реакция на редактор пользователя: `LsSkipCheckRewriter` (дополняет вставку EDT регионами `// BSLLS:Код-off/on` вокруг диапазона ошибки из LS), `PageEventListener`, `WindowEventListener`, `OpenEditorTrigger` | правят файл пользователя на диске — осторожно |
| `lsp/` | Транспорт LSP4J: `BSLConnector`, `BSLLanguageClient` | потоки процесса LS |
| `service/` | `LSService` — жизненный цикл процесса LS (`ensureStarted/start/stop/restart`, все методы `synchronized`); `LsStatusService`, `WindowsEventService`, `UpdateCheckResult` | рестарт дергается из настроек и статус-бара |
| `ui/` | `BSLPreferenceInitializer/Page` (страница настроек «Коннектор BSL LS»), `LsStatusContribution` (статус-бар) | смена настроек может дергать `LSService.restart()` |
| `util/` | `LaunchMode` (NATIVE/JAR; по умолчанию JAR), `LsCache` (слоты в `~/.bsl-connector-for-edt`, один слот на режим), `LsInstaller`, `GitHubRelease(s)`, `VersionCompare`, `LsVersionProbe`, `BSLCommon` | релизы BSL LS скачиваются только после явного выбора пользователем |

## Два канала замечаний (не склеивать!)

1. **ICheck-канал** — для каждого известного кода из каталога публикуется
   отдельная проверка EDT (`BslLsDiagnosticCheck`); замечания видны в панели
   `Ошибки конфигурации`, работают «Подавить»/«Открыть проверку».
2. **Xtext-канал** — `BSLValidator` (`IExternalBslValidator`) гоняет модуль через
   LS при сохранении/«Проверить».

Оба идут параллельно; текст любого замечания начинается с
`[BSL LS] ` (`LsDiagnosticCatalog.MESSAGE_PREFIX`) — это контракт отличимости
от типовых сообщений EDT.

## Поток данных

```
редактор/сохранение → BSLValidator ─┐
                                    ├→ LSService (процесс LS: native zip / jar)
plugin.xml <check> ×~187 ─→ BslLsDiagnosticCheck ─→ панель «Ошибки конфигурации»
        ↑ generate-ls-checks.py ← third_party/v8std
«Подавить» в EDT → //@skip-check (остаётся) → listener/LsSkipCheckRewriter → + регионы // BSLLS:Код-off/on вокруг ошибки
```

## Данные вне репозитория

- `~/.bsl-connector-for-edt` — рабочая папка плагина: скачанные дистрибутивы
  BSL LS (по одному слоту на native/jar; новая загрузка затирает слот).
- Настройки — стандартный Eclipse preference store (`BSLPreferenceInitializer`).

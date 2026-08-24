---
name: bslls-new-check
description: Как добавляется/обновляется каталог диагностик BSL LS в плагине — регенерация скриптом generate-ls-checks.py из сабмодуля v8std (plugin.xml, ls-diagnostics.tsv, карточки check.descriptions). Использовать при обновлении списка проверок, правке карточек или словах «добавить диагностику».
---

# Каталог диагностик: всё генерируется

Каталог проверок (~187 кодов) НЕ ведётся руками. Источник — статьи
[v8std](https://github.com/zeegin/v8std) (`bslls` + `v8-code-style`), скрипт
`scripts/generate-ls-checks.py` пишет:

1. `<check>`-записи в `connector/bundles/com.github.malikov-pro.dt.bsl.lspconnector/plugin.xml`
   (каждый код LS = отдельная проверка EDT);
2. ресурс `src/…/check/ls-diagnostics.tsv`, который читает `LsDiagnosticCatalog`;
3. карточки `check.descriptions/<Код>.md` (+ HTML-карточка `<Код>.html`) —
   каталог git-ignored, в бандл попадает при сборке (профиль `generate-checks`
   включается сам, см. `mvn -pl …lspconnector generate-resources`).

## Процедура обновления

```bash
git submodule update --init third_party/v8std     # или update --remote
python3 scripts/generate-ls-checks.py             # есть сеть
python3 scripts/generate-ls-checks.py --offline   # сабмодуль/.cache уже на месте
python3 scripts/generate-ls-checks.py --v8std-dir /path/to/v8std   # чужой клон
```

Опционально первым аргументом — старый индекс BSL LS (markdown-таблица ключей).

## Правила

- Ручные правки `plugin.xml`-каталога, tsv и карточек будут затёрты следующей
  регенерацией. Менять нужно **источник** (v8std) или **скрипт**.
- Код LS — CamelCase (`LineLength`), идентификатор проверки EDT — dash-case
  (`module-unused-local-variable`): маппинг делает скрипт, не изобретать свой.
- Новый код появляется в двух местах сразу: запись в plugin.xml И строка в tsv;
  рассинхрон = проверка без карточки или карточка без проверки.
- После регенерации — полный цикл: `bash compile.sh` → установка в EDT →
  открыть карточку («Открыть проверку» у замечания) и панель «Проблемы конфигурации».

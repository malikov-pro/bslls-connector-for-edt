#!/usr/bin/env node
/*
 * Роутер скиллов по путям (PostToolUse hook) — адаптация edt-skill-router.js
 * из EDT-MCP. Когда агент правит/пишет файл в чувствительной зоне проекта,
 * хук подсказывает подходящий скилл проекта, чтобы конвенции загрузились,
 * даже если описание скилла само не сработало.
 *
 * Механика: матчер хука фильтрует по ИМЕНИ ИНСТРУМЕНТА (Edit|Write); фильтр по
 * пути файла делается здесь (tool_input.file_path приходит в stdin). Хук только
 * выдаёт additionalContext и никогда не блокирует инструмент.
 *
 * Регистрация: .claude/settings.json → hooks.PostToolUse.
 */
'use strict';

function readStdin() {
  try {
    return require('fs').readFileSync(0, 'utf8');
  } catch (e) {
    return '';
  }
}

function main() {
  let data;
  try {
    data = JSON.parse(readStdin() || '{}');
  } catch (e) {
    process.exit(0); // никогда не блокировать при ошибке разбора
  }

  const input = data.tool_input || {};
  const raw = input.file_path || input.path || input.notebook_path || '';
  if (!raw) process.exit(0);

  const p = String(raw).replace(/\\/g, '/');
  const base = p.split('/').pop() || '';
  const tips = [];

  // Сгенерированное: каталог проверок, карточки, tsv
  const isGenerated =
    /check\.descriptions\//.test(p) ||
    /ls-diagnostics\.tsv$/.test(base) ||
    (/plugin\.xml$/.test(p) && /lspconnector/.test(p));
  if (isGenerated) {
    tips.push('файл генерируется скриптом scripts/generate-ls-checks.py из third_party/v8std — руками не править; см. /bslls-new-check');
  }

  // Пакет check/: контракты подавления и префикса
  if (/\/lsconnector\/check\//.test(p) || /Suppression|SkipCheck|DiagnosticCatalog/.test(base)) {
    tips.push('мост «диагностика LS → проверки EDT»: префикс [BSL LS] обязателен, regex регионов BSLLS: один в LsSkipCheck, //@skip-check чистим только от кодов LS; см. /bslls-architecture');
  }

  // listener/: правки файлов пользователя на диске
  if (/\/lsconnector\/listener\//.test(p)) {
    tips.push('listener правит строку //@skip-check в модуле пользователя: удалять ТОЛЬКО коды LS, типовые коды EDT не трогать; перед сдачей прогнать сценарий «Подавить» (см. /bslls-ready-to-deploy)');
  }

  // service/ + lsp/: жизненный цикл LS и транспорт
  if (/\/lsconnector\/(service|lsp)\//.test(p) || /LSService|BSLConnector|WebSocketLspTransport/.test(base)) {
    tips.push('жизненный цикл процесса LS / транспорт: все методы LSService synchronized, рестарт дергается из настроек и статус-бара, WebSocket идёт мимо кэша; см. /bslls-architecture');
  }

  // Две версии EDT
  if (/MANIFEST\.MF$/.test(base) || /\.target$/.test(base)) {
    tips.push('один исходник на EDT 2025.2 и 2026.1: версии пакетов в MANIFEST.MF не фиксировать, API ниже LSP4J 0.23.1 без явной оговорки не использовать');
  }

  // Сборка
  if (/^(compile\.sh|pom\.xml|jvm\.config)$/.test(base) || /\/bom\/(pom\.xml|settings\.xml)$/.test(p)) {
    tips.push('сборка — через bash compile.sh (entity-лимиты из connector/.mvn/jvm.config); см. /bslls-build-test');
  }

  if (tips.length === 0) process.exit(0);

  const msg =
    'bslls-connector-for-edt: напоминание для ' + base + ':\n- ' +
    tips.join('\n- ');

  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: 'PostToolUse',
        additionalContext: msg,
      },
    })
  );
  process.exit(0);
}

main();

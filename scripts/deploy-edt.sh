#!/usr/bin/env bash
# Установка собранного плагина в тестовую инсталляцию 1С:EDT через p2 director
# (без GUI «Установить новое ПО»). EDT во время установки должна быть ЗАКРЫТА.
#
# Использование:
#   bash scripts/deploy-edt.sh                          # авто-поиск EDT 2025.2
#   bash scripts/deploy-edt.sh --edt "/путь/к/1cedt"    # своя инсталляция (каталог с 1cedt)
#   bash scripts/deploy-edt.sh --workspace "/путь/к/ws" # свой EDT-воркспейс для чистки журнала
#
# Репозиторий берётся последний собранный:
#   connector/repositories/com.github.malikov-pro.dt.bsl.lsconnector.repository/target/repository

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_REPO="$ROOT/connector/repositories/com.github.malikov-pro.dt.bsl.lsconnector.repository/target/repository"
FEATURE_IU="com.github.malikov-pro.dt.bsl.lsconnector.feature.group"
# Тестовый EDT-воркспейс: в нём чистится журнал (.metadata/.log) при деплое.
TEST_WORKSPACE="$HOME/.local/share/1C/1cedtstart/projects/test_1c"

REPO=""
EDT=""

usage() { sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }
log() { echo "[deploy] $*"; }
die() { echo "[deploy] ОШИБКА: $*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --edt)  [[ $# -ge 2 ]] || die "--edt требует значение"; EDT="$2"; shift 2 ;;
        --repo) [[ $# -ge 2 ]] || die "--repo требует значение"; REPO="$2"; shift 2 ;;
        --workspace) [[ $# -ge 2 ]] || die "--workspace требует значение"; TEST_WORKSPACE="$2"; shift 2 ;;
        -h|--help) usage ;;
        *)      die "Неизвестный флаг: $1 (см. --help)" ;;
    esac
done

[[ -d "$REPO" ]] || REPO="$DEFAULT_REPO"
[[ -d "$REPO" ]] || die "p2-репозиторий не найден: $REPO (сначала bash compile.sh)"

if [[ -z "$EDT" ]]; then
    BASE="$HOME/.local/share/1C/1cedtstart/installations"
    EDT="$(ls -d "$BASE"/1C_EDT*/*/ 2>/dev/null | head -n 1 || true)"
    [[ -n "$EDT" ]] || die "инсталляция 1C_EDT не найдена в $BASE — передайте --edt"
fi

EDT="${EDT%/}"
[[ -x "$EDT/1cedt" ]] || die "$EDT/1cedt не найден или не исполняемый"

# EDT должна быть закрыта: p2 блокирует профиль
if pgrep -f "$EDT/1cedt( |$)" >/dev/null 2>&1 || pgrep -x 1cedt >/dev/null 2>&1; then
    die "инсталляция $EDT запущена — закройте EDT и повторите"
fi

REPO_URI="file://$(cd "$REPO" && pwd)"
log "EDT        : $EDT"
log "Репозиторий: $REPO_URI"

# Логи прошлых прогонов мешают разбору: чистим их здесь, чтобы после следующего
# запуска EDT в журналах остался только текущий прогон. EDT уже закрыта (см. выше).
LOG_DIR="$HOME/.bsl-connector-for-edt/logs"
WS_LOG_DIR="$TEST_WORKSPACE/.metadata"
cleaned=0
if compgen -G "$LOG_DIR/*.log" >/dev/null; then
    rm -f "$LOG_DIR"/*.log
    log "Очищены логи LS: ~/.bsl-connector-for-edt/logs/"
    cleaned=1
fi
if [[ -d "$WS_LOG_DIR" ]] && compgen -G "$WS_LOG_DIR/.log*" >/dev/null; then
    rm -f "$WS_LOG_DIR"/.log*
    log "Очищен журнал EDT-воркспейса: $TEST_WORKSPACE/.metadata/.log"
    cleaned=1
fi
[[ "$cleaned" -eq 1 ]] || log "Логов прошлых прогонов нет"

# Фича закрепляет точную версию бандла (qualifier меняется каждой сборкой),
# поэтому старую копию снимаем всегда; если её нет — просто игнорируем ошибку.
log "Снятие прежней копии (если была)…"
"$EDT/1cedt" -nosplash \
    -application org.eclipse.equinox.p2.director \
    -uninstallIU "$FEATURE_IU" \
    -profileProperties org.eclipse.update.reconcile=true 2>/dev/null || true

log "Установка IU: $FEATURE_IU"

"$EDT/1cedt" -nosplash \
    -application org.eclipse.equinox.p2.director \
    -repository "$REPO_URI" \
    -installIU "$FEATURE_IU" \
    -profileProperties org.eclipse.update.reconcile=true

# Инсталляции 1cedtstart хранят артефакты в общем пуле ~/.p2/pool,
# а список активных бандлов ведут в bundles.info — проверяем по нему.
EXPECTED_JAR="$(ls "$REPO"/plugins/com.github.malikov-pro.dt.bsl.lspconnector_*.jar | tail -n 1)"
EXPECTED_VER="$(basename "$EXPECTED_JAR" .jar)"
BUNDLES_INFO="$EDT/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
if ! grep -qF "$EXPECTED_VER" "$BUNDLES_INFO"; then
    die "в $BUNDLES_INFO нет $EXPECTED_VER"
fi

log "ГОТОВО, установлен: $(basename "$EXPECTED_JAR")"
log "Запустите эту EDT и проверьте:"
log "  Окно → Параметры → Коннектор BSL LS; сохранение BSL-модуля → [BSL LS] в «Проблемах конфигурации»."
log "Логи чистые, наполнятся этим прогоном: $TEST_WORKSPACE/.metadata/.log и ~/.bsl-connector-for-edt/logs/."

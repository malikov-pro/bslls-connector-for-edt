#!/usr/bin/env bash
# Канонический локальный сценарий сборки bslls-connector-for-edt.
# Воспроизводит то, что делает CI (.github/workflows/ci.yml):
#   1. скачивает lombok (если ещё нет),
#   2. запускает mvn clean verify с lombok-javaagent в MAVEN_OPTS,
#   3. показывает готовый p2-артефакт (zip) для установки в EDT.
#
# XML-entity лимиты задавать не нужно: их подхватывает .mvn/jvm.config.
#
# Использование:
#   bash compile.sh                     # сборка + упаковка p2
#   bash compile.sh --skip-lombok-copy  # lombok.jar уже на месте
#   bash compile.sh --profile edt-2026.1
#   bash compile.sh --java-home /usr/lib/jvm/axiomjdk-java25-pro-full-amd64 --maven-home /opt/maven

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONNECTOR="$ROOT/connector"
BUNDLE="bundles/com.github.malikov-pro.dt.bsl.lspconnector"
REPO_DIR="$CONNECTOR/repositories/com.github.malikov-pro.dt.bsl.lsconnector.repository/target"
LOMBOK="$CONNECTOR/$BUNDLE/target/lombok.jar"

PROFILE=""
SKIP_LOMBOK_COPY="false"

usage() {
    sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --profile)         [[ $# -ge 2 ]] || { echo "--profile требует значение" >&2; exit 1; }; PROFILE="$2"; shift 2 ;;
        --profile=*)       PROFILE="${1#*=}"; shift ;;
        --skip-lombok-copy) SKIP_LOMBOK_COPY="true"; shift ;;
        --java-home)       [[ $# -ge 2 ]] || { echo "--java-home требует значение" >&2; exit 1; }; JAVA_HOME_ARG="$2"; shift 2 ;;
        --java-home=*)     JAVA_HOME_ARG="${1#*=}"; shift ;;
        --maven-home)      [[ $# -ge 2 ]] || { echo "--maven-home требует значение" >&2; exit 1; }; MAVEN_HOME_ARG="$2"; shift 2 ;;
        --maven-home=*)    MAVEN_HOME_ARG="${1#*=}"; shift ;;
        -h|--help)         usage ;;
        *)                 echo "Неизвестный флаг: $1" >&2; usage >&2; exit 2 ;;
    esac
done

log()  { echo "[compile] $*"; }
die()  { echo "[compile] ОШИБКА: $*" >&2; exit 1; }

# --- toolchain -------------------------------------------------------------
if [[ -n "${JAVA_HOME_ARG:-}" ]]; then
    [[ -x "$JAVA_HOME_ARG/bin/java" ]] || die "bin/java не найден в --java-home: $JAVA_HOME_ARG"
    export JAVA_HOME="$JAVA_HOME_ARG"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if [[ -n "${MAVEN_HOME_ARG:-}" ]]; then
    [[ -x "$MAVEN_HOME_ARG/bin/mvn" ]] || die "bin/mvn не найден в --maven-home: $MAVEN_HOME_ARG"
    MVN="$MAVEN_HOME_ARG/bin/mvn"
else
    command -v mvn >/dev/null || die "mvn не найден в PATH, передайте --maven-home (нужен Maven 3.9+)"
    MVN="$(command -v mvn)"
fi

log "JAVA_HOME : ${JAVA_HOME:-<из PATH>}"
log "Maven     : $MVN"

# --- credentials для репозитория EDT (bom/settings.xml) ---------------------
CRED_ENV="$CONNECTOR/bom/edt-credentials.env"
if [[ -f "$CRED_ENV" ]]; then
    # shellcheck disable=SC1090
    set -a; source "$CRED_ENV"; set +a
    log "Учётные данные: connector/bom/edt-credentials.env"
else
    log "connector/bom/edt-credentials.env не найден — если p2 EDT попросит авторизацию, см. connector/bom/edt-credentials.env.example"
fi

# --- lombok -----------------------------------------------------------------
if [[ "$SKIP_LOMBOK_COPY" != "true" || ! -f "$LOMBOK" ]]; then
    log "Скачивание lombok (dependency:copy@get-lombok)…"
    (cd "$CONNECTOR" && "$MVN" -q dependency:copy@get-lombok -pl "$BUNDLE")
fi
[[ -f "$LOMBOK" ]] || die "lombok.jar не появился: $LOMBOK"

# tycho-compiler вне Eclipse IDE обрабатывает lombok только как javaagent (ECJ).
export MAVEN_OPTS="-javaagent:$LOMBOK=ECJ ${MAVEN_OPTS:-}"
log "MAVEN_OPTS: $MAVEN_OPTS"

# --- build -------------------------------------------------------------------
CMD=(clean verify --batch-mode -T 1C -Dtycho.localArtifacts=ignore)
[[ -n "$PROFILE" ]] && CMD+=(-P"$PROFILE")

log "Запуск: mvn ${CMD[*]} (в connector/)"
(cd "$CONNECTOR" && "$MVN" "${CMD[@]}")

# --- artifact ----------------------------------------------------------------
ZIP="$(ls -t "$REPO_DIR"/*.zip 2>/dev/null | head -n 1 || true)"
[[ -n "$ZIP" ]] || die "p2-zip не найден в $REPO_DIR"

log "ГОТОВО: p2-репозиторий:"
echo "  $ZIP"
echo "  профиль: ${PROFILE:-по умолчанию (EDT 2025.2)}"
echo "Установка: Справка → Установить новое ПО → Добавить → Архив → этот zip (флажок «Обращаться во время инсталляции ко всем сайтам…» СНЯТЬ)."

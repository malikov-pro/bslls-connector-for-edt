#!/usr/bin/env python3
"""Generate BSL LS check catalog, plugin.xml entries and HTML descriptions."""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "bundles" / "com.github.otymko.dt.bsl.lspconnector"
SRC_PKG = BUNDLE / "src" / "com" / "github" / "otymko" / "dt" / "bsl" / "lsconnector" / "check"
TSV = SRC_PKG / "ls-diagnostics.tsv"
PLUGIN_XML = BUNDLE / "plugin.xml"
DESC_EN = BUNDLE / "check.descriptions"
DESC_RU = DESC_EN / "ru"

V8STD = "https://v8std.ru/diagnostics/bslls"
BSL_DOCS = "https://1c-syntax.github.io/bsl-language-server/diagnostics"

TYPE_MAP = {
    "Ошибка": "error",
    "Дефект кода": "smell",
    "Уязвимость": "security",
    "Потенциальная уязвимость": "potential-security",
}
SEVERITY_MAP = {
    "Блокирующий": "blocker",
    "Критичный": "critical",
    "Важный": "major",
    "Незначительный": "minor",
    "Информационный": "trivial",
}

CATEGORY = {
    "error": ("com.github.otymko.dt.bsl.lsconnector.checks.error", "Ошибки BSL LS"),
    "smell": ("com.github.otymko.dt.bsl.lsconnector.checks.smell", "Дефекты кода BSL LS"),
    "security": ("com.github.otymko.dt.bsl.lsconnector.checks.security", "Уязвимости BSL LS"),
    "potential-security": (
        "com.github.otymko.dt.bsl.lsconnector.checks.security",
        "Уязвимости BSL LS",
    ),
}

ROW = re.compile(
    r"^\|\s*([A-Za-z][A-Za-z0-9]+)\s*\|\s*(.+?)\s*\|\s*(Да|Нет)\s*\|\s*"
    r"(Блокирующий|Критичный|Важный|Незначительный|Информационный)\s*\|\s*"
    r"(Ошибка|Дефект кода|Уязвимость|Потенциальная уязвимость)\s*\|"
)


def parse_index(text: str) -> list[tuple[str, str, str, str]]:
    rows: list[tuple[str, str, str, str]] = []
    seen: set[str] = set()
    for line in text.splitlines():
        match = ROW.match(line)
        if not match:
            continue
        code, title, _enabled, severity_ru, type_ru = match.groups()
        if code in seen:
            continue
        seen.add(code)
        rows.append(
            (
                code,
                title.strip(),
                TYPE_MAP[type_ru],
                SEVERITY_MAP[severity_ru],
            )
        )
    rows.sort(key=lambda item: item[0].lower())
    return rows


def write_tsv(rows: list[tuple[str, str, str, str]]) -> None:
    TSV.parent.mkdir(parents=True, exist_ok=True)
    lines = ["code\ttitle\ttype\tseverity"]
    for code, title, kind, severity in rows:
        lines.append(f"{code}\t{title}\t{kind}\t{severity}")
    TSV.write_text("\n".join(lines) + "\n", encoding="utf-8")


def html_page(code: str, title: str, lang: str) -> str:
    v8std = f"{V8STD}/{code}/"
    official = f"{BSL_DOCS}/{code}/"
    if lang == "ru":
        heading = f"{title} ({code})"
        lead = "Диагностика BSL Language Server."
        skip = f"Подавление в модуле: <code>//@skip-check {code}</code>"
        see = "Документация"
        official_label = "Описание на сайте BSL LS"
    else:
        heading = f"{title} ({code})"
        lead = "BSL Language Server diagnostic."
        skip = f"Suppress in module: <code>//@skip-check {code}</code>"
        see = "Documentation"
        official_label = "BSL LS documentation"
    return f"""<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">
<html lang="{lang}">
<head>
    <meta charset="utf-8" />
    <title>{heading}</title>
</head>
<body>
<h1>{heading}</h1>
<p>{lead}</p>
<p>{skip}</p>
<h2>{see}</h2>
<ul>
    <li><a href="{v8std}">{v8std}</a></li>
    <li><a href="{official}">{official_label}</a></li>
</ul>
</body>
</html>
"""


def write_html(rows: list[tuple[str, str, str, str]]) -> None:
    DESC_RU.mkdir(parents=True, exist_ok=True)
    DESC_EN.mkdir(parents=True, exist_ok=True)
    for path in DESC_EN.glob("*.html"):
        path.unlink()
    for path in DESC_RU.glob("*.html"):
        path.unlink()
    fallback_ru = """<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">
<html lang="ru">
<head>
    <meta charset="utf-8" />
    <title>Прочие диагностики BSL LS</title>
</head>
<body>
<h1>Прочие диагностики BSL LS</h1>
<p>Диагностика BSL Language Server, которой ещё нет в каталоге коннектора.</p>
<p>Подавление: <code>//@skip-check bsl-ls</code></p>
<p>Каталог диагностик: <a href="https://v8std.ru/diagnostics/">https://v8std.ru/diagnostics/</a></p>
</body>
</html>
"""
    fallback_en = """<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">
<html lang="en">
<head>
    <meta charset="utf-8" />
    <title>Other BSL LS diagnostics</title>
</head>
<body>
<h1>Other BSL LS diagnostics</h1>
<p>A BSL Language Server diagnostic that is not yet in the connector catalog.</p>
<p>Suppress: <code>//@skip-check bsl-ls</code></p>
<p>Diagnostics registry: <a href="https://v8std.ru/diagnostics/">https://v8std.ru/diagnostics/</a></p>
</body>
</html>
"""
    (DESC_RU / "bsl-ls.html").write_text(fallback_ru, encoding="utf-8")
    (DESC_EN / "bsl-ls.html").write_text(fallback_en, encoding="utf-8")
    for code, title, _kind, _severity in rows:
        (DESC_RU / f"{code}.html").write_text(html_page(code, title, "ru"), encoding="utf-8")
        (DESC_EN / f"{code}.html").write_text(html_page(code, title, "en"), encoding="utf-8")


def plugin_xml(rows: list[tuple[str, str, str, str]]) -> str:
    checks = []
    for code, _title, kind, _severity in rows:
        category, _ = CATEGORY[kind]
        checks.append(
            f"""      <check
            category="{category}"
            class="com.github.otymko.dt.bsl.lsconnector.check.BslLsDiagnosticCheck:{code}">
      </check>"""
        )
    check_block = "\n".join(checks)
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<!-- Каталог диагностик: scripts/generate-ls-checks.py -->
<plugin>
   <extension
         point="com.e1c.g5.v8.dt.check.checks">
      <category
            id="com.github.otymko.dt.bsl.lsconnector.checks"
            title="Проверка BSL LS"
            description="Диагностики BSL Language Server. Карточки ведут на https://v8std.ru/diagnostics/bslls/">
      </category>
      <category
            category="com.github.otymko.dt.bsl.lsconnector.checks"
            id="com.github.otymko.dt.bsl.lsconnector.checks.error"
            title="Ошибки BSL LS">
      </category>
      <category
            category="com.github.otymko.dt.bsl.lsconnector.checks"
            id="com.github.otymko.dt.bsl.lsconnector.checks.smell"
            title="Дефекты кода BSL LS">
      </category>
      <category
            category="com.github.otymko.dt.bsl.lsconnector.checks"
            id="com.github.otymko.dt.bsl.lsconnector.checks.security"
            title="Уязвимости BSL LS">
      </category>
      <check
            category="com.github.otymko.dt.bsl.lsconnector.checks"
            class="com.github.otymko.dt.bsl.lsconnector.BSLLsCheck">
      </check>
{check_block}
   </extension>
   <extension
         point="org.eclipse.ui.preferencePages">
      <page
            class="com.github.otymko.dt.bsl.lsconnector.ui.BSLPreferencePage"
            id="com.github.otymko.dt.bsl.lsconnector.plugin.page"
            name="Коннектор BSL LS">
      </page>
   </extension>
   <extension
         point="org.eclipse.core.runtime.preferences">
      <initializer
            class="com.github.otymko.dt.bsl.lsconnector.ui.BSLPreferenceInitializer">
      </initializer>
   </extension>
   <extension
         point="org.eclipse.ui.menus">
      <menuContribution
            locationURI="toolbar:org.eclipse.ui.trim.status">
         <toolbar
               id="com.github.otymko.dt.bsl.lsconnector.statusBar">
            <control
                  class="com.github.otymko.dt.bsl.lsconnector.ui.LsStatusContribution"
                  id="com.github.otymko.dt.bsl.lsconnector.status">
            </control>
         </toolbar>
      </menuContribution>
   </extension>

</plugin>
"""


def main() -> int:
    source = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if source is None:
        candidates = list(
            pathlib.Path(
                "/home/aleksandr/.cursor/projects/home-aleksandr-bslls-connector-for-edt/agent-tools"
            ).glob("*.txt")
        )
        source = None
        for path in sorted(candidates, key=lambda item: item.stat().st_mtime, reverse=True):
            text = path.read_text(encoding="utf-8", errors="replace")
            if "LineLength" in text and "AllFunctionPathMustHaveReturn" in text:
                source = path
                break
        if source is None:
            raise SystemExit("Передайте файл индекса диагностик: generate-ls-checks.py <index.txt>")
    rows = parse_index(source.read_text(encoding="utf-8"))
    if len(rows) < 100:
        raise SystemExit(f"Слишком мало диагностик в индексе: {len(rows)}")
    write_tsv(rows)
    write_html(rows)
    PLUGIN_XML.write_text(plugin_xml(rows), encoding="utf-8")
    print(f"Сгенерировано {len(rows)} диагностик из {source}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

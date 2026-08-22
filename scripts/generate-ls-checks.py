#!/usr/bin/env python3
"""Generate BSL LS check catalog, plugin.xml entries and HTML descriptions.

Descriptions come from the [zeegin/v8std](https://github.com/zeegin/v8std)
git submodule (`third_party/v8std`). Articles are normalized to the same HTML
shape as EDT v8-code-style cards: title, lead, examples, «См.» with
v8std / BSL LS URLs (visible text = URL) plus links from the article.
"""

from __future__ import annotations

import argparse
import html
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "bundles" / "com.github.otymko.dt.bsl.lspconnector"
SRC_PKG = BUNDLE / "src" / "com" / "github" / "otymko" / "dt" / "bsl" / "lsconnector" / "check"
TSV = SRC_PKG / "ls-diagnostics.tsv"
PLUGIN_XML = BUNDLE / "plugin.xml"
DESC_EN = BUNDLE / "check.descriptions"
DESC_RU = DESC_EN / "ru"
V8STD_SUBMODULE = ROOT / "third_party" / "v8std"
V8STD_CACHE = ROOT / ".cache" / "v8std"
V8STD_GIT = "https://github.com/zeegin/v8std.git"
V8STD_SITE = "https://v8std.ru"
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

BSL_INDEX_ROW = re.compile(
    r"^\|\s*([A-Za-z][A-Za-z0-9]+)\s*\|\s*(.+?)\s*\|\s*(Да|Нет)\s*\|\s*"
    r"(Блокирующий|Критичный|Важный|Незначительный|Информационный)\s*\|\s*"
    r"(Ошибка|Дефект кода|Уязвимость|Потенциальная уязвимость)\s*\|"
)
V8STD_INDEX_ROW = re.compile(
    r"^\|\s*\[([A-Za-z][A-Za-z0-9]+)\]\([^)]+\)\s*\|\s*"
    r"(Ошибка|Дефект кода|Уязвимость|Потенциальная уязвимость)\s*\|\s*"
    r"(Блокирующий|Критичный|Важный|Незначительный|Информационный)\s*\|"
)
H1_RE = re.compile(r"^#\s+(.+?)\s*$", re.M)
TITLE_WITH_CODE = re.compile(r"^(.*)\s+\(([A-Za-z][A-Za-z0-9]+)\)\s*$")
FRONT_MATTER = re.compile(r"^---\n.*?\n---\n", re.S)
HTML_COMMENT = re.compile(r"<!--.*?-->", re.S)
STD_LINK = re.compile(r"^(?:\.\./)+std/(\d+)\.md(?:#(.*))?$")
DIAG_LINK = re.compile(r"^([A-Za-z][A-Za-z0-9-]+)\.md(?:#(.*))?$")
HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
META_ITEM = re.compile(
    r"^[-*]\s+(Тип|Важность|Включена по умолчанию|Теги|Категория)\s*:",
    re.I,
)
SEE_HEADING = re.compile(r"^##\s+(См\.|See)\s*$", re.M)
EMPTY_STANDARDS = re.compile(r"нет подтвержд[её]нных связей", re.I)
HEADING_DROP = {"описание диагностики", "description"}
HEADING_SKIP = {"источник диагностики", "diagnostic source"}
HEADING_RENAME = {
    "источники": "См.",
    "see": "See",
    "см": "См.",
    "см.": "См.",
}
UL_ITEM = re.compile(r"^(\s*)[-*]\s+(.+)$")
OL_ITEM = re.compile(r"^(\s*)\d+\.\s+(.+)$")
FENCE = re.compile(r"^```(\w*)\s*$")
TABLE_ROW = re.compile(r"^\s*\|(.+)\|\s*$")
TABLE_SEP = re.compile(r"^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$")
INLINE_CODE = re.compile(r"`([^`]+)`")
INLINE_LINK = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")
INLINE_BOLD = re.compile(r"\*\*(.+?)\*\*")
INLINE_ITALIC = re.compile(r"(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)")


def parse_bsl_index(text: str) -> list[tuple[str, str, str, str]]:
    rows: list[tuple[str, str, str, str]] = []
    seen: set[str] = set()
    for line in text.splitlines():
        match = BSL_INDEX_ROW.match(line)
        if not match:
            continue
        code, title, _enabled, severity_ru, type_ru = match.groups()
        if code in seen:
            continue
        seen.add(code)
        rows.append((code, title.strip(), TYPE_MAP[type_ru], SEVERITY_MAP[severity_ru]))
    rows.sort(key=lambda item: item[0].lower())
    return rows


def parse_v8std_index(text: str) -> list[tuple[str, str, str, str]]:
    rows: list[tuple[str, str, str, str]] = []
    seen: set[str] = set()
    for line in text.splitlines():
        match = V8STD_INDEX_ROW.match(line)
        if not match:
            continue
        code, type_ru, severity_ru = match.groups()
        if code in seen:
            continue
        seen.add(code)
        rows.append((code, code, TYPE_MAP[type_ru], SEVERITY_MAP[severity_ru]))
    rows.sort(key=lambda item: item[0].lower())
    return rows


def title_from_markdown(text: str, code: str) -> str:
    match = H1_RE.search(text)
    if not match:
        return code
    heading = match.group(1).strip()
    with_code = TITLE_WITH_CODE.match(heading)
    if with_code:
        return with_code.group(1).strip()
    return heading


def write_tsv(rows: list[tuple[str, str, str, str]]) -> None:
    TSV.parent.mkdir(parents=True, exist_ok=True)
    lines = ["code\ttitle\ttype\tseverity"]
    for code, title, kind, severity in rows:
        lines.append(f"{code}\t{title}\t{kind}\t{severity}")
    TSV.write_text("\n".join(lines) + "\n", encoding="utf-8")


def rewrite_href(href: str) -> str:
    href = href.strip()
    if href.startswith("http://") or href.startswith("https://") or href.startswith("#"):
        return href
    std = STD_LINK.match(href)
    if std:
        fragment = f"#{std.group(2)}" if std.group(2) else ""
        return f"{V8STD_SITE}/std/{std.group(1)}/{fragment}"
    diag = DIAG_LINK.match(href)
    if diag:
        name = diag.group(1)
        fragment = f"#{diag.group(2)}" if diag.group(2) else ""
        family = "v8-code-style" if "-" in name else "bslls"
        return f"{V8STD_SITE}/diagnostics/{family}/{name}/{fragment}"
    return href


def heading_key(title: str) -> str:
    return title.strip().rstrip(".").lower()


def normalize_article(markdown: str) -> str:
    text = FRONT_MATTER.sub("", markdown, count=1)
    text = HTML_COMMENT.sub("", text)
    lines: list[str] = []
    for line in text.replace("\r\n", "\n").split("\n"):
        if re.match(r"^#{5,}\s+", line) or META_ITEM.match(line):
            continue
        lines.append(line)

    sections: list[tuple[int, str | None, list[str]]] = [(0, None, [])]
    for line in lines:
        match = HEADING.match(line)
        if match and len(match.group(1)) <= 4:
            sections.append((len(match.group(1)), match.group(2).strip(), []))
        else:
            sections[-1][2].append(line)

    out: list[str] = []
    for level, title, body in sections:
        if title is None:
            out.extend(body)
            continue
        if level == 1:
            continue
        key = heading_key(title)
        if key in HEADING_SKIP:
            continue
        if key in {"соответствие стандартам", "related standards"}:
            body_text = "\n".join(body)
            if EMPTY_STANDARDS.search(body_text) and "[#" not in body_text:
                continue
        if key in HEADING_DROP:
            out.extend(body)
            continue
        title = HEADING_RENAME.get(key, title)
        if key in {"неправильно", "правильно", "noncompliant code example", "compliant solution"}:
            if not "".join(body).strip():
                continue
        out.append(f"{'#' * level} {title}")
        out.extend(body)
    return "\n".join(out).strip() + "\n"


def ensure_see_section(markdown: str, links: list[tuple[str, str]], heading: str = "См.") -> str:
    items = "".join(f"- [{label}]({url})\n" for label, url in links)
    match = SEE_HEADING.search(markdown)
    if match:
        rest = markdown[match.end() :].lstrip("\n")
        return markdown[: match.end()] + "\n\n" + items + rest
    return (markdown.rstrip() + f"\n\n## {heading}\n\n" + items).lstrip() + "\n"


def see_links(code: str) -> list[tuple[str, str]]:
    v8std = f"{V8STD}/{code}/"
    bsl = f"{BSL_DOCS}/{code}/"
    return [(v8std, v8std), (bsl, bsl)]


def inline_html(text: str) -> str:
    slots: list[str] = []

    def keep(fragment: str) -> str:
        slots.append(fragment)
        return f"\x00{len(slots) - 1}\x00"

    def take_code(match: re.Match[str]) -> str:
        return keep(f"<code>{html.escape(match.group(1))}</code>")

    def take_link(match: re.Match[str]) -> str:
        label = inline_html(match.group(1))
        href = html.escape(rewrite_href(match.group(2)), quote=True)
        return keep(f'<a href="{href}">{label}</a>')

    text = INLINE_CODE.sub(take_code, text)
    text = INLINE_LINK.sub(take_link, text)
    text = html.escape(text)
    text = INLINE_BOLD.sub(r"<strong>\1</strong>", text)
    text = INLINE_ITALIC.sub(r"<em>\1</em>", text)
    return re.sub(r"\x00(\d+)\x00", lambda match: slots[int(match.group(1))], text)


def _flush_paragraph(buf: list[str], out: list[str]) -> None:
    if not buf:
        return
    out.append(f"<p>{inline_html(' '.join(buf))}</p>")
    buf.clear()


def _flush_list(kind: str | None, items: list[str], out: list[str]) -> None:
    if kind is None or not items:
        return
    tag = "ul" if kind == "ul" else "ol"
    out.append(f"<{tag}>")
    for item in items:
        out.append(f"<li>{inline_html(item)}</li>")
    out.append(f"</{tag}>")
    items.clear()


def _flush_table(rows: list[list[str]], out: list[str]) -> None:
    if not rows:
        return
    out.append("<table>")
    header, *body = rows
    out.append("<tr>")
    for cell in header:
        out.append(f"<th>{inline_html(cell)}</th>")
    out.append("</tr>")
    for row in body:
        out.append("<tr>")
        for cell in row:
            out.append(f"<td>{inline_html(cell)}</td>")
        out.append("</tr>")
    out.append("</table>")
    rows.clear()


def _split_table_row(line: str) -> list[str]:
    raw = line.strip()
    if raw.startswith("|"):
        raw = raw[1:]
    if raw.endswith("|"):
        raw = raw[:-1]
    return [cell.strip() for cell in raw.split("|")]


def md_to_html_body(markdown: str) -> str:
    text = FRONT_MATTER.sub("", markdown, count=1)
    text = HTML_COMMENT.sub("", text)
    lines = text.replace("\r\n", "\n").split("\n")
    out: list[str] = []
    paragraph: list[str] = []
    list_kind: str | None = None
    list_items: list[str] = []
    table_rows: list[list[str]] = []
    fence_lang: str | None = None
    fence_lines: list[str] = []

    def close_blocks() -> None:
        nonlocal list_kind
        _flush_paragraph(paragraph, out)
        _flush_list(list_kind, list_items, out)
        list_kind = None
        _flush_table(table_rows, out)

    for line in lines:
        if fence_lang is not None:
            if FENCE.match(line):
                lang = fence_lang or "bsl"
                code = html.escape("\n".join(fence_lines))
                out.append(f'<pre><code class="language-{html.escape(lang, quote=True)}">{code}</code></pre>')
                fence_lang = None
                fence_lines = []
            else:
                fence_lines.append(line)
            continue

        fence = FENCE.match(line)
        if fence:
            close_blocks()
            fence_lang = fence.group(1) or "bsl"
            continue

        if not line.strip():
            close_blocks()
            continue

        if re.match(r"^#{5,}\s+", line):
            continue

        heading = HEADING.match(line)
        if heading:
            close_blocks()
            level = min(len(heading.group(1)), 4)
            title = heading.group(2).strip()
            if level == 1:
                continue
            out.append(f"<h{level}>{inline_html(title)}</h{level}>")
            continue

        if TABLE_ROW.match(line):
            _flush_paragraph(paragraph, out)
            _flush_list(list_kind, list_items, out)
            list_kind = None
            if TABLE_SEP.match(line):
                continue
            table_rows.append(_split_table_row(line))
            continue
        if table_rows:
            _flush_table(table_rows, out)

        ul = UL_ITEM.match(line)
        if ul:
            _flush_paragraph(paragraph, out)
            _flush_table(table_rows, out)
            if list_kind not in (None, "ul"):
                _flush_list(list_kind, list_items, out)
            list_kind = "ul"
            list_items.append(ul.group(2))
            continue

        ol = OL_ITEM.match(line)
        if ol:
            _flush_paragraph(paragraph, out)
            _flush_table(table_rows, out)
            if list_kind not in (None, "ol"):
                _flush_list(list_kind, list_items, out)
            list_kind = "ol"
            list_items.append(ol.group(2))
            continue

        if list_kind is not None:
            _flush_list(list_kind, list_items, out)
            list_kind = None
        paragraph.append(line.strip())

    if fence_lang is not None:
        lang = fence_lang or "bsl"
        code = html.escape("\n".join(fence_lines))
        out.append(f'<pre><code class="language-{html.escape(lang, quote=True)}">{code}</code></pre>')
    close_blocks()
    return "\n".join(out)


def html_page(code: str, title: str, body: str) -> str:
    heading = f"{title} ({code})"
    skip = f"Подавление в модуле: <code>//@skip-check {code}</code>"
    return f"""<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">
<html>
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>{html.escape(heading)}</title>
</head>
<body>
<h1>{html.escape(heading)}</h1>
<p>{skip}</p>
{body}
</body>
</html>
"""


def fallback_html() -> str:
    return """<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">
<html>
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>Прочие диагностики BSL LS</title>
</head>
<body>
<h1>Прочие диагностики BSL LS</h1>
<p>Подавление в модуле: <code>//@skip-check bsl-ls</code></p>
<p>Диагностика BSL Language Server, которой ещё нет в каталоге коннектора.</p>
<h2>См.</h2>
<ul>
<li><a href="https://v8std.ru/diagnostics/">https://v8std.ru/diagnostics/</a></li>
<li><a href="https://1c-syntax.github.io/bsl-language-server/diagnostics/">https://1c-syntax.github.io/bsl-language-server/diagnostics/</a></li>
</ul>
</body>
</html>
"""


def article_html(code: str, title: str, markdown: str | None) -> str:
    prepared = normalize_article(markdown) if markdown else ""
    prepared = ensure_see_section(prepared, see_links(code))
    return html_page(code, title, md_to_html_body(prepared))


def write_html(rows: list[tuple[str, str, str, str]], articles: dict[str, str]) -> int:
    DESC_RU.mkdir(parents=True, exist_ok=True)
    DESC_EN.mkdir(parents=True, exist_ok=True)
    for path in DESC_EN.glob("*.html"):
        path.unlink()
    for path in DESC_RU.glob("*.html"):
        path.unlink()
    (DESC_RU / "bsl-ls.html").write_text(fallback_html(), encoding="utf-8")
    (DESC_EN / "bsl-ls.html").write_text(fallback_html(), encoding="utf-8")
    converted = 0
    for code, title, _kind, _severity in rows:
        markdown = articles.get(code)
        page = article_html(code, title, markdown)
        if markdown:
            converted += 1
        (DESC_RU / f"{code}.html").write_text(page, encoding="utf-8")
        (DESC_EN / f"{code}.html").write_text(page, encoding="utf-8")
    return converted


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
         point="com._1c.g5.v8.dt.bsl.externalBslValidator">
      <externalValidator
            class="com.github.otymko.dt.bsl.lsconnector.BSLValidator">
      </externalValidator>
   </extension>
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


def run_git(args: list[str], **kwargs) -> subprocess.CompletedProcess[str]:
    print("+ git " + " ".join(args), file=sys.stderr)
    return subprocess.run(["git", *args], check=True, text=True, **kwargs)


def has_articles(root: pathlib.Path) -> bool:
    return (root / "docs" / "diagnostics" / "bslls").is_dir()


def default_v8std_dir() -> pathlib.Path:
    if has_articles(V8STD_SUBMODULE):
        return V8STD_SUBMODULE
    return V8STD_CACHE


def is_git_checkout(dest: pathlib.Path) -> bool:
    git = dest / ".git"
    return git.is_dir() or git.is_file()


def ensure_v8std(dest: pathlib.Path, *, update: bool) -> pathlib.Path:
    dest = dest.resolve()
    if is_git_checkout(dest):
        # Сабмодуль не двигаем: SHA зафиксирован родительским репозиторием.
        if update and dest != V8STD_SUBMODULE.resolve() and (dest / ".git").is_dir():
            try:
                run_git(["-C", str(dest), "fetch", "--depth", "1", "origin", "main"])
                run_git(["-C", str(dest), "checkout", "-B", "main", "FETCH_HEAD"])
            except subprocess.CalledProcessError as exc:
                print(f"Не удалось обновить {dest}: {exc}", file=sys.stderr)
        return dest
    if dest.exists() and any(dest.iterdir()):
        raise SystemExit(f"{dest} существует и это не git-клон v8std")
    dest.parent.mkdir(parents=True, exist_ok=True)
    run_git(["clone", "--depth", "1", "--branch", "main", V8STD_GIT, str(dest)])
    return dest


def load_articles(v8std_root: pathlib.Path) -> dict[str, str]:
    folder = v8std_root / "docs" / "diagnostics" / "bslls"
    if not folder.is_dir():
        raise SystemExit(f"В клоне v8std нет {folder}")
    articles: dict[str, str] = {}
    for path in folder.glob("*.md"):
        if path.name.lower() == "index.md":
            continue
        articles[path.stem] = path.read_text(encoding="utf-8")
    return articles


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "index",
        nargs="?",
        type=pathlib.Path,
        help="Необязательный индекс BSL LS (markdown-таблица). Без него берётся docs/diagnostics/bslls/index.md из v8std.",
    )
    parser.add_argument(
        "--v8std-dir",
        type=pathlib.Path,
        default=None,
        help="Каталог zeegin/v8std (по умолчанию third_party/v8std, иначе .cache/v8std)",
    )
    parser.add_argument(
        "--offline",
        action="store_true",
        help="Не клонировать и не обновлять: нужен сабмодуль или уже существующий --v8std-dir",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    v8std_dir = args.v8std_dir or default_v8std_dir()
    if args.offline:
        if not has_articles(v8std_dir):
            raise SystemExit(
                f"--offline, но нет статей в {v8std_dir}. "
                "Сделайте git submodule update --init third_party/v8std"
            )
        v8std_root = v8std_dir
    else:
        if v8std_dir == V8STD_SUBMODULE and not has_articles(v8std_dir):
            raise SystemExit(
                "Сабмодуль third_party/v8std пуст. "
                "Сделайте git submodule update --init third_party/v8std"
            )
        v8std_root = ensure_v8std(v8std_dir, update=v8std_dir.exists() and v8std_dir != V8STD_SUBMODULE)
    articles = load_articles(v8std_root)
    if args.index is not None:
        rows = parse_bsl_index(args.index.read_text(encoding="utf-8"))
        source = str(args.index)
    else:
        index_path = v8std_root / "docs" / "diagnostics" / "bslls" / "index.md"
        rows = parse_v8std_index(index_path.read_text(encoding="utf-8"))
        source = str(index_path)
        rows = [
            (code, title_from_markdown(articles.get(code, ""), code), kind, severity)
            for code, _title, kind, severity in rows
        ]
    if len(rows) < 100:
        raise SystemExit(f"Слишком мало диагностик в индексе: {len(rows)}")
    write_tsv(rows)
    converted = write_html(rows, articles)
    PLUGIN_XML.write_text(plugin_xml(rows), encoding="utf-8")
    print(f"Сгенерировано {len(rows)} диагностик из {source}; статей v8std: {converted}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

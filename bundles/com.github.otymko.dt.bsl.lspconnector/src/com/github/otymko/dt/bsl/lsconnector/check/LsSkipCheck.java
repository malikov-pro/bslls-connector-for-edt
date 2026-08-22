package com.github.otymko.dt.bsl.lsconnector.check;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Подавление BSL LS — регионы
 * {@code // BSLLS:LineLength-off} … {@code // BSLLS:LineLength-on}
 * или {@code // BSLLS:off} … {@code // BSLLS:on}.
 * Вставленный EDT {@code //@skip-check LineLength} удаляется и диагностику
 * BSL LS не глушит.
 */
public final class LsSkipCheck {
    public static final String ALL_LS = "bsl-ls";

    private static final Pattern SKIP = Pattern.compile("(?i)//\\s*@skip-check\\s+([^\\r\\n]+)");
    private static final Pattern BSLLS_TOGGLE = Pattern.compile("(?i)//\\s*BSLLS:(?:(\\w+)-)?(off|on)\\b");

    private LsSkipCheck() {
    }

    public static boolean isSkipped(String content, int lineNumber1Based, String checkId) {
	if (content == null || checkId == null || checkId.isBlank()) {
	    return false;
	}
	var lines = content.split("\n", -1);
	if (lineNumber1Based < 1 || lineNumber1Based > lines.length) {
	    return false;
	}
	int index = lineNumber1Based - 1;
	return isInsideBsllsOff(lines, index, checkId);
    }

    public static boolean isSkipped(Set<String> ids, String checkId) {
	if (ids.contains(ALL_LS) || ids.contains(normalize(checkId))) {
	    return true;
	}
	return ids.contains(toKebab(checkId));
    }

    public static Set<String> skipCheckIds(String text) {
	return collectEdtIds(text);
    }

    public static boolean isLsCheckId(String id) {
	return canonicalLsCode(id) != null;
    }

    public static String canonicalLsCode(String id) {
	if (id == null || id.isBlank()) {
	    return null;
	}
	if (ALL_LS.equalsIgnoreCase(id)) {
	    return ALL_LS;
	}
	if (LsDiagnosticCatalog.isKnown(id)) {
	    return id;
	}
	for (String code : LsDiagnosticCatalog.codes()) {
	    if (code.equalsIgnoreCase(id) || toKebab(code).equalsIgnoreCase(id)) {
		return code;
	    }
	}
	return null;
    }

    private static boolean isInsideBsllsOff(String[] lines, int lastIndexInclusive, String checkId) {
	boolean allOff = false;
	var codesOff = new HashSet<String>();
	for (int i = 0; i <= lastIndexInclusive; i++) {
	    var matcher = BSLLS_TOGGLE.matcher(lines[i]);
	    while (matcher.find()) {
		var code = matcher.group(1);
		boolean off = "off".equalsIgnoreCase(matcher.group(2));
		if (code == null || code.isBlank()) {
		    allOff = off;
		    continue;
		}
		var id = normalize(code);
		if (off) {
		    codesOff.add(id);
		    codesOff.add(toKebab(id));
		} else {
		    codesOff.remove(id);
		    codesOff.remove(toKebab(id));
		}
	    }
	}
	return allOff || isSkipped(codesOff, checkId);
    }

    private static Set<String> collectEdtIds(String text) {
	if (text == null || text.isBlank()) {
	    return Collections.emptySet();
	}
	var ids = new HashSet<String>();
	var matcher = SKIP.matcher(text);
	while (matcher.find()) {
	    var payload = matcher.group(1);
	    int commentPart = payload.indexOf(" -");
	    if (commentPart >= 0) {
		payload = payload.substring(0, commentPart);
	    }
	    for (String token : payload.split("[,\\s]+")) {
		if (!token.isBlank()) {
		    ids.add(normalize(token));
		}
	    }
	}
	return ids;
    }

    private static String normalize(String value) {
	return value.trim();
    }

    private static String toKebab(String value) {
	var builder = new StringBuilder();
	for (int i = 0; i < value.length(); i++) {
	    char ch = value.charAt(i);
	    if (Character.isUpperCase(ch) && i > 0) {
		builder.append('-');
	    }
	    builder.append(Character.toLowerCase(ch));
	}
	return builder.toString().toLowerCase(Locale.ROOT);
    }
}

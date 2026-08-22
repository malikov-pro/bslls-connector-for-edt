package com.github.otymko.dt.bsl.lsconnector.check;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * EDT пишет {@code //@skip-check <id>}. Для BSL LS id — ключ диагностики
 * ({@code LineLength}) или {@code bsl-ls} на все диагностики сервера.
 */
public final class LsSkipCheck {
    public static final String ALL_LS = "bsl-ls";

    private static final Pattern SKIP = Pattern.compile("(?i)//\\s*@skip-check\\s+([^\\r\\n]+)");

    private LsSkipCheck() {
    }

    public static boolean isSkipped(String content, int lineNumber1Based, String checkId) {
	if (content == null || checkId == null || checkId.isBlank()) {
	    return false;
	}
	var lines = content.split("\n", -1);
	if (isSkipped(collectIds(firstNonEmptyLines(lines, 8)), checkId)) {
	    return true;
	}
	if (lineNumber1Based < 1 || lineNumber1Based > lines.length) {
	    return false;
	}
	int index = lineNumber1Based - 1;
	if (isSkipped(collectIds(lines[index]), checkId)) {
	    return true;
	}
	for (int i = index - 1; i >= 0; i--) {
	    var line = lines[i];
	    if (line.isBlank()) {
		continue;
	    }
	    if (!isCommentLine(line)) {
		break;
	    }
	    if (isSkipped(collectIds(line), checkId)) {
		return true;
	    }
	}
	return false;
    }

    public static boolean isSkipped(Set<String> ids, String checkId) {
	if (ids.contains(ALL_LS) || ids.contains(normalize(checkId))) {
	    return true;
	}
	return ids.contains(toKebab(checkId));
    }

    static Set<String> collectIds(String text) {
	if (text == null || text.isBlank()) {
	    return Collections.emptySet();
	}
	var ids = new HashSet<String>();
	var matcher = SKIP.matcher(text);
	while (matcher.find()) {
	    for (String token : matcher.group(1).split("[,\\s]+")) {
		if (!token.isBlank()) {
		    ids.add(normalize(token));
		}
	    }
	}
	return ids;
    }

    private static String firstNonEmptyLines(String[] lines, int limit) {
	var builder = new StringBuilder();
	int taken = 0;
	for (String line : lines) {
	    if (line.isBlank()) {
		if (taken > 0) {
		    break;
		}
		continue;
	    }
	    if (!isCommentLine(line)) {
		break;
	    }
	    if (builder.length() > 0) {
		builder.append('\n');
	    }
	    builder.append(line);
	    taken++;
	    if (taken >= limit) {
		break;
	    }
	}
	return builder.toString();
    }

    private static boolean isCommentLine(String line) {
	var trimmed = line.stripLeading();
	return trimmed.startsWith("//");
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

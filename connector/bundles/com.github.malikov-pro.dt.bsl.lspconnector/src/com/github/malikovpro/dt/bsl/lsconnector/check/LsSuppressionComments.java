package com.github.malikovpro.dt.bsl.lsconnector.check;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Разбор комментария EDT {@code //@skip-check}: распознавание вставки и
 * извлечение кодов BSL LS. Вставку EDT коннектор не меняет — в ответ на
 * «Подавить» диагностик LS добавляются регионы {@code // BSLLS:Код-off/on}
 * (см. {@code LsSkipCheckRewriter}).
 */
public final class LsSuppressionComments {
    private static final Pattern SKIP_TAIL = Pattern.compile("(?i)//\\s*@skip-check\\s+([^\\r\\n]+)");

    private LsSuppressionComments() {
    }

    public static boolean looksLikeSkipCheckInsert(String text) {
	return text != null && text.contains("@skip-check");
    }

    public static List<String> lsCodesFromSkipLine(String line) {
	if (line == null) {
	    return List.of();
	}
	var matcher = SKIP_TAIL.matcher(line);
	if (!matcher.find()) {
	    return List.of();
	}
	var payload = matcher.group(1);
	int commentPart = payload.indexOf(" -");
	if (commentPart >= 0) {
	    payload = payload.substring(0, commentPart);
	}
	var codes = new ArrayList<String>();
	for (String token : payload.split("[,\\s]+")) {
	    var code = LsSkipCheck.canonicalLsCode(token);
	    if (code != null && !codes.contains(code)) {
		codes.add(code);
	    }
	}
	return codes;
    }

    public static String leadingWhitespace(String line) {
	if (line == null || line.isEmpty()) {
	    return "";
	}
	int i = 0;
	while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
	    i++;
	}
	return line.substring(0, i);
    }
}

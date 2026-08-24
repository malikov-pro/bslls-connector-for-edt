package com.github.malikovpro.dt.bsl.lsconnector.check;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Удаление кодов BSL LS из комментария EDT {@code //@skip-check}.
 */
public final class LsSuppressionComments {
    private static final Pattern SKIP_TAIL = Pattern.compile("(?i)//\\s*@skip-check\\s+([^\\r\\n]+)");

    private LsSuppressionComments() {
    }

    public static boolean looksLikeSkipCheckInsert(String text) {
	return text != null && text.contains("@skip-check");
    }

    public static String removeLsCodesFromSkipLine(String line) {
	if (line == null) {
	    return null;
	}
	var matcher = SKIP_TAIL.matcher(line);
	if (!matcher.find()) {
	    return line;
	}
	var payload = matcher.group(1);
	int commentPart = payload.indexOf(" -");
	if (commentPart >= 0) {
	    payload = payload.substring(0, commentPart);
	}
	var edtCodes = new ArrayList<String>();
	boolean hasLsCode = false;
	for (String token : payload.split("[,\\s]+")) {
	    if (token.isBlank()) {
		continue;
	    }
	    var ls = LsSkipCheck.canonicalLsCode(token);
	    if (ls != null) {
		hasLsCode = true;
	    } else {
		edtCodes.add(token);
	    }
	}
	if (!hasLsCode) {
	    return line;
	}
	var before = line.substring(0, matcher.start()).stripTrailing();
	if (edtCodes.isEmpty()) {
	    return before;
	}
	var separator = before.isBlank() ? leadingWhitespace(line) : before + " ";
	return separator + "//@skip-check " + String.join(", ", edtCodes);
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

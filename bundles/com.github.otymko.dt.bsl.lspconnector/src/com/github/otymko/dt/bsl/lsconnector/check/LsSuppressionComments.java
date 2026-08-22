package com.github.otymko.dt.bsl.lsconnector.check;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Пара комментариев BSL LS вместо EDT {@code //@skip-check}.
 */
public final class LsSuppressionComments {
    private static final Pattern SKIP_TAIL = Pattern.compile("(?i)//\\s*@skip-check\\s+([^\\r\\n]+)");

    private LsSuppressionComments() {
    }

    public static String offComment(String canonicalCode) {
	if (LsSkipCheck.ALL_LS.equals(canonicalCode)) {
	    return "// BSLLS:off";
	}
	return "// BSLLS:" + canonicalCode + "-off";
    }

    public static String onComment(String canonicalCode) {
	if (LsSkipCheck.ALL_LS.equals(canonicalCode)) {
	    return "// BSLLS:on";
	}
	return "// BSLLS:" + canonicalCode + "-on";
    }

    public static boolean looksLikeSkipCheckInsert(String text) {
	return text != null && text.length() <= 400 && text.contains("@skip-check");
    }

    /**
     * Заменяет строку {@code //@skip-check …} на комментарии BSL LS, оставляя
     * коды EDT в {@code @skip-check}. Пустой список — нечего переписывать.
     */
    public static List<String> rewriteSkipLine(String line) {
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
	var lsCodes = new ArrayList<String>();
	var edtCodes = new ArrayList<String>();
	for (String token : payload.split("[,\\s]+")) {
	    if (token.isBlank()) {
		continue;
	    }
	    var ls = LsSkipCheck.canonicalLsCode(token);
	    if (ls != null) {
		if (!lsCodes.contains(ls)) {
		    lsCodes.add(ls);
		}
	    } else {
		edtCodes.add(token);
	    }
	}
	if (lsCodes.isEmpty()) {
	    return List.of();
	}
	var indent = leadingWhitespace(line);
	var lines = new ArrayList<String>();
	if (!edtCodes.isEmpty()) {
	    lines.add(indent + "//@skip-check " + String.join(", ", edtCodes));
	}
	for (String code : lsCodes) {
	    lines.add(indent + offComment(code));
	}
	return lines;
    }

    public static List<String> lsCodesFromSkipLine(String line) {
	var rewritten = rewriteSkipLine(line);
	var codes = new ArrayList<String>();
	for (String rewrittenLine : rewritten) {
	    var trimmed = rewrittenLine.strip();
	    if (trimmed.startsWith("// BSLLS:off")) {
		codes.add(LsSkipCheck.ALL_LS);
	    } else if (trimmed.startsWith("// BSLLS:") && trimmed.endsWith("-off")) {
		codes.add(trimmed.substring("// BSLLS:".length(), trimmed.length() - "-off".length()));
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

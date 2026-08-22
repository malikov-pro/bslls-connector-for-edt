package com.github.otymko.dt.bsl.lsconnector.check;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.e1c.g5.v8.dt.check.settings.IssueSeverity;
import com.e1c.g5.v8.dt.check.settings.IssueType;
import com.github.otymko.dt.bsl.lsconnector.BSLPlugin;

public final class LsDiagnosticCatalog {
    public static final String V8STD_INDEX = "https://v8std.ru/diagnostics/";
    public static final String V8STD_BSLLS = "https://v8std.ru/diagnostics/bslls/";

    private static final Map<String, LsDiagnosticInfo> BY_CODE = load();

    private LsDiagnosticCatalog() {
    }

    public static boolean isKnown(String code) {
	return code != null && BY_CODE.containsKey(code);
    }

    public static Set<String> codes() {
	return Collections.unmodifiableSet(BY_CODE.keySet());
    }

    public static LsDiagnosticInfo get(String code) {
	return BY_CODE.get(code);
    }

    public static String v8stdUrl(String code) {
	if (code == null || code.isBlank() || "BSL LS".equals(code)) {
	    return V8STD_INDEX;
	}
	return V8STD_BSLLS + code + "/";
    }

    public static String titleOrCode(String code) {
	var info = get(code);
	return info == null ? code : info.getTitle() + " (" + code + ")";
    }

    private static Map<String, LsDiagnosticInfo> load() {
	var result = new LinkedHashMap<String, LsDiagnosticInfo>();
	try (var in = LsDiagnosticCatalog.class.getResourceAsStream("ls-diagnostics.tsv")) {
	    if (in == null) {
		return result;
	    }
	    try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
		String line;
		while ((line = reader.readLine()) != null) {
		    if (line.isBlank() || line.startsWith("code\t")) {
			continue;
		    }
		    var parts = line.split("\t", 4);
		    if (parts.length < 4) {
			continue;
		    }
		    result.put(parts[0], new LsDiagnosticInfo(parts[0], parts[1], toType(parts[2]),
			    toSeverity(parts[3])));
		}
	    }
	} catch (Exception e) {
	    BSLPlugin.createErrorStatus("Не удалось прочитать каталог диагностик BSL LS", e);
	}
	return result;
    }

    private static IssueType toType(String value) {
	if ("error".equals(value)) {
	    return IssueType.ERROR;
	}
	if ("security".equals(value) || "potential-security".equals(value)) {
	    return IssueType.SECURITY;
	}
	return IssueType.CODE_STYLE;
    }

    private static IssueSeverity toSeverity(String value) {
	return switch (value) {
	case "blocker" -> IssueSeverity.BLOCKER;
	case "critical" -> IssueSeverity.CRITICAL;
	case "major" -> IssueSeverity.MAJOR;
	case "trivial" -> IssueSeverity.TRIVIAL;
	default -> IssueSeverity.MINOR;
	};
    }
}

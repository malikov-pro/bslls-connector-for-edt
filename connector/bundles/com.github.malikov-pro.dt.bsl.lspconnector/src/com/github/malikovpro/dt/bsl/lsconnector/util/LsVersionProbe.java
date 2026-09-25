package com.github.malikovpro.dt.bsl.lsconnector.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Pattern;

public final class LsVersionProbe {
    private static final Pattern JAVA_VERSION = Pattern.compile("version\\s+\"(\\d+)(?:\\.(\\d+))?");
    // Числовое ядро версии (major.minor[.patch]) — без пересекающихся классов,
    // поэтому без суперлинейного бэктрекинга (java:S8786). Суффикс релиза
    // ("-rc.3") добирается вручную в findVersionToken — без экзотических
    // квантификаторов (java:S5856).
    private static final Pattern VERSION_CORE = Pattern.compile("v?\\d+\\.\\d+(?:\\.\\d+)?");
    public static final int REQUIRED_JAVA_MAJOR = 21;

    private LsVersionProbe() {
    }

    public static String languageServerVersion(Path artifact, boolean jar, String javaCommand, String javaOpts)
	    throws IOException, InterruptedException {
	List<String> command = new ArrayList<>();
	if (jar) {
	    command.add(blankToJava(javaCommand));
	    addOpts(command, javaOpts);
	    command.add("-jar");
	}
	command.add(artifact.toString());
	command.add("version");
	return extractLanguageServerVersion(BSLCommon.runAndReadOutput(command));
    }

    public static String javaVersionOutput(String javaCommand) throws IOException, InterruptedException {
	return BSLCommon.runAndReadOutput(List.of(blankToJava(javaCommand), "-version"));
    }

    public static OptionalInt parseJavaMajor(String versionOutput) {
	if (versionOutput == null || versionOutput.isBlank()) {
	    return OptionalInt.empty();
	}
	var matcher = JAVA_VERSION.matcher(versionOutput);
	if (!matcher.find()) {
	    return OptionalInt.empty();
	}
	var major = Integer.parseInt(matcher.group(1));
	if (major == 1 && matcher.group(2) != null) {
	    return OptionalInt.of(Integer.parseInt(matcher.group(2)));
	}
	return OptionalInt.of(major);
    }

    public static String extractLanguageServerVersion(String output) {
	if (output == null || output.isBlank()) {
	    return "";
	}
	var fallback = "";
	for (var raw : output.split("\\R")) {
	    var line = raw.trim();
	    if (line.isEmpty() || isJvmNoise(line)) {
		continue;
	    }
	    var version = findVersionToken(line);
	    if (version != null) {
		return version;
	    }
	    if (fallback.isEmpty()) {
		fallback = line;
	    }
	}
	return fallback;
    }

    /** Первый токен версии в строке: числовое ядро + суффикс релиза ("-rc.3"). */
    private static String findVersionToken(String line) {
	var matcher = VERSION_CORE.matcher(line);
	if (!matcher.find()) {
	    return null;
	}
	int end = matcher.end();
	while (end < line.length()) {
	    var ch = line.charAt(end);
	    if (Character.isLetterOrDigit(ch) || ch == '.' || ch == '-') {
		end++;
	    } else {
		break;
	    }
	}
	return line.substring(matcher.start(), end);
    }

    public static String firstLine(String text) {
	if (text == null || text.isBlank()) {
	    return "";
	}
	for (var raw : text.split("\\R")) {
	    var line = raw.trim();
	    if (!line.isEmpty() && !isJvmNoise(line)) {
		return line;
	    }
	}
	var newline = text.indexOf('\n');
	return newline < 0 ? text.trim() : text.substring(0, newline).trim();
    }

    private static boolean isJvmNoise(String line) {
	var upper = line.toUpperCase();
	return upper.startsWith("WARNING:") || upper.startsWith("PICKED UP ") || line.contains("sun.misc.Unsafe")
		|| line.contains("terminally deprecated");
    }

    public static void addOpts(List<String> command, String javaOpts) {
	if (javaOpts == null || javaOpts.isBlank()) {
	    return;
	}
	for (var opt : javaOpts.trim().split("\\s+")) {
	    if (!opt.isEmpty()) {
		command.add(opt);
	    }
	}
    }

    private static String blankToJava(String javaCommand) {
	return javaCommand == null || javaCommand.isBlank() ? "java" : javaCommand;
    }
}

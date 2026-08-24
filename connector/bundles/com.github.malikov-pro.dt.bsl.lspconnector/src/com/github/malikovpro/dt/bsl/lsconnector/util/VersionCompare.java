package com.github.malikovpro.dt.bsl.lsconnector.util;

public final class VersionCompare {
    private VersionCompare() {
    }

    public static int compare(String left, String right) {
	var a = parse(left);
	var b = parse(right);
	var length = Math.max(a.numbers.length, b.numbers.length);
	for (var i = 0; i < length; i++) {
	    var av = i < a.numbers.length ? a.numbers[i] : 0;
	    var bv = i < b.numbers.length ? b.numbers[i] : 0;
	    if (av != bv) {
		return Integer.compare(av, bv);
	    }
	}
	var aPre = !a.pre.isEmpty();
	var bPre = !b.pre.isEmpty();
	if (aPre != bPre) {
	    return aPre ? -1 : 1;
	}
	return a.pre.compareTo(b.pre);
    }

    public static String normalize(String raw) {
	if (raw == null) {
	    return "";
	}
	var value = raw.trim();
	if (value.startsWith("v") || value.startsWith("V")) {
	    value = value.substring(1);
	}
	var qualifier = value.indexOf(".qualifier");
	if (qualifier > 0) {
	    value = value.substring(0, qualifier);
	}
	return value;
    }

    private static Parsed parse(String raw) {
	var value = normalize(raw);
	var dash = value.indexOf('-');
	var core = dash < 0 ? value : value.substring(0, dash);
	var pre = dash < 0 ? "" : value.substring(dash + 1);
	var parts = core.split("\\.");
	var numbers = new int[parts.length];
	for (var i = 0; i < parts.length; i++) {
	    var digits = parts[i].replaceAll("[^0-9].*$", "");
	    numbers[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
	}
	return new Parsed(numbers, pre);
    }

    private static final class Parsed {
	private final int[] numbers;
	private final String pre;

	private Parsed(int[] numbers, String pre) {
	    this.numbers = numbers;
	    this.pre = pre;
	}
    }
}

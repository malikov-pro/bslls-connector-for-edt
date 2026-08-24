package com.github.malikovpro.dt.bsl.lsconnector.util;

public enum LaunchMode {
    NATIVE("native"),
    JAR("jar"),
    WEBSOCKET("websocket");

    private final String id;

    LaunchMode(String id) {
	this.id = id;
    }

    public String getId() {
	return id;
    }

    public static LaunchMode from(String value) {
	if (value != null) {
	    for (var mode : values()) {
		if (mode.id.equalsIgnoreCase(value)) {
		    return mode;
		}
	    }
	}
	return JAR;
    }
}

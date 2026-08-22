package com.github.otymko.dt.bsl.lsconnector.util;

import org.eclipse.core.runtime.Platform;

public final class GitHubRelease {
    private final String tag;
    private final String jarUrl;
    private final String winUrl;
    private final String nixUrl;
    private final String macUrl;

    public GitHubRelease(String tag, String jarUrl, String winUrl, String nixUrl, String macUrl) {
	this.tag = tag;
	this.jarUrl = jarUrl;
	this.winUrl = winUrl;
	this.nixUrl = nixUrl;
	this.macUrl = macUrl;
    }

    public String getTag() {
	return tag;
    }

    public String getJarUrl() {
	return jarUrl;
    }

    public String assetUrl(LaunchMode mode) {
	if (mode == LaunchMode.JAR) {
	    return jarUrl;
	}
	if (mode != LaunchMode.NATIVE) {
	    return null;
	}
	var os = Platform.getOS();
	if (Platform.OS_WIN32.equals(os)) {
	    return winUrl;
	}
	if (Platform.OS_MACOSX.equals(os)) {
	    return macUrl;
	}
	return nixUrl;
    }

    public String assetFileName(LaunchMode mode) {
	if (mode == LaunchMode.JAR) {
	    return jarFileName();
	}
	var os = Platform.getOS();
	if (Platform.OS_WIN32.equals(os)) {
	    return "bsl-language-server_win.zip";
	}
	if (Platform.OS_MACOSX.equals(os)) {
	    return "bsl-language-server_mac.zip";
	}
	return "bsl-language-server_nix.zip";
    }

    public String jarFileName() {
	var version = tag.startsWith("v") ? tag.substring(1) : tag;
	return "bsl-language-server-" + version + "-exec.jar";
    }

    public boolean hasAsset(LaunchMode mode) {
	var url = assetUrl(mode);
	return url != null && !url.isBlank();
    }
}

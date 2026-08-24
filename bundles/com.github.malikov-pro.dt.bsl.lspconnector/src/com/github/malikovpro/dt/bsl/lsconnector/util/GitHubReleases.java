package com.github.malikovpro.dt.bsl.lsconnector.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class GitHubReleases {
    public static final String API_URL = "https://api.github.com/repos/1c-syntax/bsl-language-server/releases?per_page=5";
    public static final String USER_AGENT = "bslls-connector-for-edt";
    private static final Pattern STRING_VALUE = Pattern.compile("\"([^\"]+)\"");

    private GitHubReleases() {
    }

    public static List<GitHubRelease> fetchLatest() throws IOException {
	return fetchLatest(API_URL);
    }

    public static List<GitHubRelease> fetchLatest(String apiUrl) throws IOException {
	var client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(15))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	var request = HttpRequest.newBuilder(URI.create(apiUrl))
		.header("User-Agent", USER_AGENT)
		.header("Accept", "application/vnd.github+json")
		.timeout(Duration.ofSeconds(20))
		.GET()
		.build();
	try {
	    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
	    if (response.statusCode() != 200) {
		throw new IOException("GitHub API HTTP " + response.statusCode() + ": " + excerpt(response.body()));
	    }
	    return parse(response.body());
	} catch (InterruptedException e) {
	    Thread.currentThread().interrupt();
	    throw new IOException("Запрос списка релизов прерван", e);
	}
    }

    static List<GitHubRelease> parse(String json) {
	List<GitHubRelease> releases = new ArrayList<>();
	var idx = 0;
	while (releases.size() < 5) {
	    var tagPos = json.indexOf("\"tag_name\"", idx);
	    if (tagPos < 0) {
		break;
	    }
	    var nextTag = json.indexOf("\"tag_name\"", tagPos + 10);
	    var end = nextTag < 0 ? json.length() : nextTag;
	    var block = json.substring(tagPos, end);
	    var tag = extractStringAfter(block, "\"tag_name\"");
	    if (tag != null && !tag.isBlank()) {
		releases.add(new GitHubRelease(tag,
			findAssetUrl(block, "-exec.jar"),
			findAssetUrl(block, "bsl-language-server_win.zip"),
			findAssetUrl(block, "bsl-language-server_nix.zip"),
			findAssetUrl(block, "bsl-language-server_mac.zip")));
	    }
	    idx = end;
	}
	return releases;
    }

    private static String findAssetUrl(String block, String nameSuffixOrExact) {
	var searchFrom = 0;
	while (true) {
	    var nameKey = block.indexOf("\"name\"", searchFrom);
	    if (nameKey < 0) {
		return null;
	    }
	    var name = extractStringAfter(block.substring(nameKey), "\"name\"");
	    if (name != null && (name.equals(nameSuffixOrExact) || name.endsWith(nameSuffixOrExact))) {
		var url = extractStringAfter(block.substring(nameKey), "\"browser_download_url\"");
		if (url != null) {
		    return url;
		}
	    }
	    searchFrom = nameKey + 6;
	}
    }

    private static String extractStringAfter(String text, String key) {
	var pos = text.indexOf(key);
	if (pos < 0) {
	    return null;
	}
	var after = text.substring(pos + key.length());
	var colon = after.indexOf(':');
	if (colon < 0) {
	    return null;
	}
	var matcher = STRING_VALUE.matcher(after.substring(colon + 1));
	if (matcher.find()) {
	    return matcher.group(1);
	}
	return null;
    }

    private static String excerpt(String body) {
	if (body == null) {
	    return "";
	}
	var trimmed = body.replaceAll("\\s+", " ").trim();
	return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }
}

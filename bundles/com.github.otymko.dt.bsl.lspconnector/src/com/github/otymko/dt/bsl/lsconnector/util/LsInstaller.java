package com.github.otymko.dt.bsl.lsconnector.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;

public final class LsInstaller {
    private LsInstaller() {
    }

    public static Path install(Path appDir, LaunchMode mode, GitHubRelease release, IProgressMonitor monitor)
	    throws IOException {
	if (mode == LaunchMode.WEBSOCKET) {
	    throw new IllegalArgumentException("Режим WebSocket не загружает дистрибутив");
	}
	if (!release.hasAsset(mode)) {
	    throw new IOException("В релизе " + release.getTag() + " нет файла " + release.assetFileName(mode));
	}

	var progress = SubMonitor.convert(monitor, "Загрузка BSL Language Server " + release.getTag(), 100);
	var tmp = Files.createTempFile("bsl-ls-", ".download");
	try {
	    download(URI.create(release.assetUrl(mode)), tmp, progress.split(80));
	    progress.setTaskName("Установка " + release.getTag());
	    LsCache.clearSlot(appDir, mode);
	    if (mode == LaunchMode.NATIVE) {
		var dest = LsCache.nativeDir(appDir).resolve(sanitizeTag(release.getTag()));
		Files.createDirectories(dest);
		unzipSafely(tmp, dest, progress.split(20));
		var binary = LsCache.findNativeBinary(appDir)
			.orElseThrow(() -> new IOException("После распаковки не найден бинарник BSL Language Server"));
		binary.toFile().setExecutable(true);
		return binary;
	    }
	    var destDir = LsCache.jarDir(appDir);
	    Files.createDirectories(destDir);
	    var dest = destDir.resolve(release.jarFileName());
	    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
	    tmp = null;
	    progress.split(20).done();
	    return dest;
	} finally {
	    if (tmp != null) {
		Files.deleteIfExists(tmp);
	    }
	}
    }

    private static void download(URI uri, Path dest, IProgressMonitor monitor) throws IOException {
	var client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(20))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	var request = HttpRequest.newBuilder(uri)
		.header("User-Agent", GitHubReleases.USER_AGENT)
		.timeout(Duration.ofMinutes(15))
		.GET()
		.build();
	try {
	    var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
	    if (response.statusCode() != 200) {
		throw new IOException("Не удалось скачать " + uri + " (HTTP " + response.statusCode() + ")");
	    }
	    var contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
	    monitor.beginTask("Скачивание", contentLength > 0 ? (int) Math.min(contentLength, Integer.MAX_VALUE) : IProgressMonitor.UNKNOWN);
	    try (InputStream in = response.body(); var out = Files.newOutputStream(dest)) {
		var buffer = new byte[8192];
		var copied = 0L;
		var reported = 0;
		int read;
		while ((read = in.read(buffer)) >= 0) {
		    if (monitor.isCanceled()) {
			throw new OperationCanceledException();
		    }
		    out.write(buffer, 0, read);
		    copied += read;
		    if (contentLength > 0) {
			var worked = (int) Math.min(copied, Integer.MAX_VALUE);
			if (worked > reported) {
			    monitor.worked(worked - reported);
			    reported = worked;
			}
		    }
		}
	    }
	    monitor.done();
	} catch (InterruptedException e) {
	    Thread.currentThread().interrupt();
	    throw new IOException("Загрузка прервана", e);
	}
    }

    private static void unzipSafely(Path zip, Path dest, IProgressMonitor monitor) throws IOException {
	var destAbs = dest.toAbsolutePath().normalize();
	monitor.beginTask("Распаковка", IProgressMonitor.UNKNOWN);
	try (var zis = new ZipInputStream(Files.newInputStream(zip))) {
	    ZipEntry entry;
	    while ((entry = zis.getNextEntry()) != null) {
		if (monitor.isCanceled()) {
		    throw new OperationCanceledException();
		}
		var target = destAbs.resolve(entry.getName()).normalize();
		if (!target.startsWith(destAbs)) {
		    throw new IOException("Небезопасный путь в архиве: " + entry.getName());
		}
		if (entry.isDirectory()) {
		    Files.createDirectories(target);
		} else {
		    if (target.getParent() != null) {
			Files.createDirectories(target.getParent());
		    }
		    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
		}
	    }
	}
	monitor.done();
    }

    private static String sanitizeTag(String tag) {
	return tag.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

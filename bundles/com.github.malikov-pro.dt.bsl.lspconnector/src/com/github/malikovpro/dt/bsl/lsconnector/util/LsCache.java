package com.github.malikovpro.dt.bsl.lsconnector.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public final class LsCache {
    public static final String NATIVE_DIR = "native";
    public static final String JAR_DIR = "jar";
    private static final String NATIVE_BINARY = "bsl-language-server";

    private LsCache() {
    }

    public static Path nativeDir(Path appDir) {
	return appDir.resolve(NATIVE_DIR);
    }

    public static Path jarDir(Path appDir) {
	return appDir.resolve(JAR_DIR);
    }

    public static Optional<Path> findNativeBinary(Path appDir) {
	var named = findFirst(nativeDir(appDir), path -> {
	    var name = path.getFileName().toString();
	    return name.equalsIgnoreCase(NATIVE_BINARY + ".exe") || name.equals(NATIVE_BINARY);
	});
	if (named.isPresent()) {
	    return named;
	}
	return findFirst(nativeDir(appDir), path -> path.getFileName().toString().toLowerCase().endsWith(".exe"));
    }

    public static Optional<Path> findJar(Path appDir) {
	var execJar = findFirst(jarDir(appDir), path -> path.getFileName().toString().endsWith("-exec.jar"));
	if (execJar.isPresent()) {
	    return execJar;
	}
	return findFirst(jarDir(appDir), path -> path.getFileName().toString().endsWith(".jar"));
    }

    public static Optional<Path> findArtifact(Path appDir, LaunchMode mode) {
	if (mode == LaunchMode.NATIVE) {
	    return findNativeBinary(appDir);
	}
	if (mode == LaunchMode.JAR) {
	    return findJar(appDir);
	}
	return Optional.empty();
    }

    public static void clearSlot(Path appDir, LaunchMode mode) throws IOException {
	if (mode == LaunchMode.NATIVE) {
	    deleteRecursively(nativeDir(appDir));
	} else if (mode == LaunchMode.JAR) {
	    deleteRecursively(jarDir(appDir));
	}
    }

    public static void deleteRecursively(Path root) throws IOException {
	if (!Files.exists(root)) {
	    return;
	}
	try (Stream<Path> walk = Files.walk(root)) {
	    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
		try {
		    Files.deleteIfExists(path);
		} catch (IOException e) {
		    throw new IllegalStateException("Не удалось удалить " + path, e);
		}
	    });
	} catch (IllegalStateException e) {
	    var cause = e.getCause();
	    if (cause instanceof IOException) {
		throw (IOException) cause;
	    }
	    throw e;
	}
    }

    private static Optional<Path> findFirst(Path root, java.util.function.Predicate<Path> match) {
	if (!Files.isDirectory(root)) {
	    return Optional.empty();
	}
	try (Stream<Path> walk = Files.walk(root)) {
	    return walk.filter(Files::isRegularFile).filter(match).findFirst();
	} catch (IOException e) {
	    return Optional.empty();
	}
    }
}

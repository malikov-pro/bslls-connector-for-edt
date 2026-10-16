package com.github.malikovpro.dt.bsl.lsconnector.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.Range;

import com._1c.g5.v8.dt.bsl.ui.editor.BslXtextEditor;
import com.github._1c_syntax.utils.Absolute;

public final class BSLCommon {

    private BSLCommon() {
    }

    public static String runAndReadOutput(List<String> command) throws IOException, InterruptedException {
	return runAndReadOutput(command, 15);
    }

    public static String runAndReadOutput(List<String> command, int timeoutSeconds)
	    throws IOException, InterruptedException {
	var builder = new ProcessBuilder(command);
	builder.redirectErrorStream(true);
	var process = builder.start();
	var output = new StringBuilder();
	try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
	    String line;
	    while ((line = reader.readLine()) != null) {
		if (output.length() > 0) {
		    output.append('\n');
		}
		output.append(line);
	    }
	}
	if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
	    process.destroyForcibly();
	    throw new IOException("Команда не завершилась за " + timeoutSeconds + " с: " + command);
	}
	return output.toString().trim();
    }

    public static Optional<Path> getConfigurationFileFromWorkspace(Path pathToWorkspace) throws IOException {
	try (var walk = Files.walk(pathToWorkspace)) {
	    var listFiles = walk.filter(Files::isRegularFile)
		    .filter(path -> path.endsWith(".bsl-language-server.json")).collect(Collectors.toList());
	    if (!listFiles.isEmpty()) {
		return Optional.of(listFiles.get(0));
	    }
	}
	return Optional.empty();
    }

    public static int[] getOffsetByRange(Range range, Document document) throws BadLocationException {
	int offset, lenght = 0;
	offset = document.getLineOffset(range.getStart().getLine()) + range.getStart().getCharacter();
	lenght = document.getLineOffset(range.getEnd().getLine()) + range.getEnd().getCharacter() - offset;
	return new int[] { offset, lenght };
    }

    public static String getContentFromXtextEditor(BslXtextEditor editor) {
	var document = editor.getDocument();
	if (document == null) {
	    return "";
	}
	var content = document.get();
	if (content == null) {
	    content = "";
	}
	return content;
    }

    public static URI uri(URI uri) {
	return Absolute.uri(uri);
    }

    /**
     * Возвращает список доступных 1С-проектов воркспейса (содержащих каталог src).
     */
    public static List<IProject> find1CProjects() {
	var result = new ArrayList<IProject>();
	for (var project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
	    if (!project.isAccessible() || project.getLocation() == null) {
		continue;
	    }
	    var src = project.getLocation().append("src").toFile();
	    if (src.isDirectory()) {
		result.add(project);
	    }
	}
	return result;
    }

    /**
     * Проверяет, является ли проект основной конфигурацией 1С (содержит Configuration.mdo).
     */
    public static boolean isConfigurationProject(IProject project) {
	if (project == null || !project.isAccessible() || project.getLocation() == null) {
	    return false;
	}
	var mdo = project.getLocation().append("src").append("Configuration").append("Configuration.mdo").toFile();
	if (mdo.isFile()) {
	    return true;
	}
	var mdoAlt = project.getLocation().append("src").append("Configuration.mdo").toFile();
	return mdoAlt.isFile();
    }

    /**
     * Находит основной проект конфигурации:
     * 1. Если задано preferredProjectName (не пусто и не "(Авто)") — ищет проект с таким именем среди 1С-проектов.
     * 2. Иначе ищет проект, содержащий Configuration.mdo (основная конфигурация 1С).
     * 3. Иначе берет первый доступный 1С-проект с каталогом src.
     */
    public static Optional<IProject> findPrimaryProject(String preferredProjectName) {
	var projects = find1CProjects();
	if (preferredProjectName != null && !preferredProjectName.isEmpty() && !"(Авто)".equals(preferredProjectName)) {
	    for (var project : projects) {
		if (project.getName().equals(preferredProjectName)) {
		    return Optional.of(project);
		}
	    }
	}
	for (var project : projects) {
	    if (isConfigurationProject(project)) {
		return Optional.of(project);
	    }
	}
	if (!projects.isEmpty()) {
	    return Optional.of(projects.get(0));
	}
	return Optional.empty();
    }

    /**
     * Ищет файл конфигурации .bsl-language-server.json:
     * 1. В основном проекте (выбранном явно или определенном автоматически по Configuration.mdo) и его родителях (Git-корень).
     * 2. В остальных 1С-проектах воркспейса и их родителях.
     * 3. В корне воркспейса Eclipse (запасной вариант, только если в проектах файл не найден).
     */
    public static Optional<Path> getConfigurationFile(String preferredProjectName, Path pathToWorkspace) {
	var primary = findPrimaryProject(preferredProjectName);
	if (primary.isPresent()) {
	    var config = findConfigFileInProject(primary.get());
	    if (config.isPresent()) {
		return config;
	    }
	}

	for (var project : find1CProjects()) {
	    if (primary.isPresent() && project.equals(primary.get())) {
		continue;
	    }
	    var config = findConfigFileInProject(project);
	    if (config.isPresent()) {
		return config;
	    }
	}

	if (pathToWorkspace != null) {
	    var wsConfig = pathToWorkspace.resolve(".bsl-language-server.json");
	    if (Files.isRegularFile(wsConfig)) {
		return Optional.of(wsConfig);
	    }
	}

	return Optional.empty();
    }

    public static Optional<Path> getConfigurationFileFromProjects(String preferredProjectName) {
	return getConfigurationFile(preferredProjectName, null);
    }

    public static Optional<Path> getConfigurationFileFromProjects() {
	return getConfigurationFile(null, null);
    }

    private static Optional<Path> findConfigFileInProject(IProject project) {
	var location = project.getLocation();
	if (location == null) {
	    return Optional.empty();
	}
	var current = location.toFile().toPath();
	for (var i = 0; i < 4 && current != null; i++) {
	    var candidate = current.resolve(".bsl-language-server.json");
	    if (Files.isRegularFile(candidate)) {
		return Optional.of(candidate);
	    }
	    if (Files.isDirectory(current.resolve(".git"))) {
		break;
	    }
	    current = current.getParent();
	}
	return Optional.empty();
    }

}

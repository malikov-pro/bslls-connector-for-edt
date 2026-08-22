package com.github.otymko.dt.bsl.lsconnector.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.Range;
import org.osgi.framework.FrameworkUtil;

import com._1c.g5.v8.dt.bsl.ui.editor.BslXtextEditor;
import com.github._1c_syntax.utils.Absolute;
import com.github.otymko.dt.bsl.lsconnector.BSLPlugin;

public final class BSLCommon {
    public static final String BUNDLED_LS_PATH = "lib/bsl-language-server-exec.jar";

    private BSLCommon() {
    }

    public static Optional<Path> getBundledLanguageServerJar() {
	var bundle = FrameworkUtil.getBundle(BSLCommon.class);
	if (bundle == null) {
	    return Optional.empty();
	}
	var url = FileLocator.find(bundle, new org.eclipse.core.runtime.Path(BUNDLED_LS_PATH), null);
	if (url == null) {
	    return Optional.empty();
	}
	try {
	    var fileUrl = FileLocator.toFileURL(url);
	    var path = Path.of(fileUrl.toURI());
	    if (path.toFile().isFile()) {
		return Optional.of(path);
	    }
	} catch (IOException | URISyntaxException e) {
	    BSLPlugin.createErrorStatus("Не удалось извлечь встроенный BSL Language Server", e);
	}
	return Optional.empty();
    }

    public static Optional<Path> getConfigurationFileFromWorkspace(Path pathToWorkspace) throws IOException {
	var listFiles = Files.walk(pathToWorkspace).filter(Files::isRegularFile)
		.filter(path -> path.endsWith(".bsl-language-server.json")).collect(Collectors.toList());
	if (!listFiles.isEmpty()) {
	    return Optional.of(listFiles.get(0));
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

}

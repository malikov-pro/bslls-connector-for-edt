package com.github.malikovpro.dt.bsl.lsconnector.check;

import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com._1c.g5.v8.dt.bsl.model.Method;
import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.model.Statement;
import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;
import com.github.malikovpro.dt.bsl.lsconnector.util.BSLCommon;

/**
 * Один запрос к BSL LS на модуль: все ICheck диагностик делят этот кэш.
 */
public final class LsModuleAnalyzer {
    /// Сколько ждём публикации диагностик от LS после didOpen/didChange.
    private static final long DIAGNOSTICS_TIMEOUT_MILLIS = 10_000;
    private final Object lock = new Object();
    private String cacheKey;
    private List<Diagnostic> cache = List.of();

    public Analysis analyze(Module module, IProgressMonitor progressMonitor) {
	var node = NodeModelUtils.findActualNodeFor(module);
	if (node == null) {
	    return Analysis.empty(module);
	}
	var content = node.getText();
	if (content == null) {
	    content = "";
	}
	return new Analysis(module, content, new Document(content), diagnostics(module, content, progressMonitor));
    }

    private List<Diagnostic> diagnostics(Module module, String content, IProgressMonitor progressMonitor) {
	var plugin = BSLPlugin.getPlugin();
	if (plugin == null || !plugin.getLsService().ensureStarted()) {
	    return List.of();
	}
	var connector = plugin.getLsService().getConnector();
	if (connector == null) {
	    return List.of();
	}
	var moduleFile = ResourcesPlugin.getWorkspace().getRoot()
		.getFile(new Path(EcoreUtil.getURI(module).toPlatformString(true)));
	var uri = BSLCommon.uri(moduleFile.getLocationURI());
	var key = uri + "\n" + content.hashCode() + "\n" + content.length();

	synchronized (lock) {
	    if (key.equals(cacheKey)) {
		return cache;
	    }
	}

	var alreadyOpened = plugin.getWorkbenchParts().contains(uri.toString());
	if (alreadyOpened) {
	    connector.textDocumentDidChange(uri, content);
	} else {
	    plugin.getWorkbenchParts().add(uri.toString());
	    connector.textDocumentDidOpen(uri, content);
	}

	plugin.getStatusService().beginBusy();
	try {
	    // Ждём публикации диагностик ограниченно: LS может отвечать дольше секунды,
	    // а пустой преждевременный результат здесь кэшируется.
	    var deadline = System.currentTimeMillis() + DIAGNOSTICS_TIMEOUT_MILLIS;
	    List<Diagnostic> found = null;
	    while (found == null && System.currentTimeMillis() < deadline) {
		if (progressMonitor.isCanceled() || !plugin.getWorkbenchParts().contains(uri.toString())) {
		    return List.of();
		}
		found = connector.diagnostics(uri.toString());
		if (found == null) {
		    plugin.sleepCurrentThread(250);
		}
	    }
	    if (progressMonitor.isCanceled() || !plugin.getWorkbenchParts().contains(uri.toString())) {
		return List.of();
	    }
	    var diagnostics = found == null ? List.<Diagnostic>of() : found;
	    synchronized (lock) {
		cacheKey = key;
		cache = List.copyOf(diagnostics);
		return cache;
	    }
	} finally {
	    plugin.getStatusService().endBusy();
	    if (!alreadyOpened && plugin.getWorkbenchParts().contains(uri.toString())) {
		plugin.getWorkbenchParts().remove(uri.toString());
		connector.textDocumentDidClose(uri);
	    }
	}
    }

    public static final class Analysis {
	private final Module module;
	private final String content;
	private final Document document;
	private final List<Diagnostic> diagnostics;

	private Analysis(Module module, String content, Document document, List<Diagnostic> diagnostics) {
	    this.module = module;
	    this.content = content;
	    this.document = document;
	    this.diagnostics = diagnostics;
	}

	static Analysis empty(Module module) {
	    return new Analysis(module, "", new Document(""), List.of());
	}

	public Module getModule() {
	    return module;
	}

	public String getContent() {
	    return content;
	}

	public Document getDocument() {
	    return document;
	}

	public List<Diagnostic> getDiagnostics() {
	    return diagnostics;
	}

	public EObject findCauser(int offset) {
	    var semantic = semanticAt(offset);
	    var statement = enclosingStatement(semantic);
	    if (statement != null) {
		return statement;
	    }
	    var next = firstObjectAtOrAfter(offset);
	    statement = enclosingStatement(next);
	    if (statement != null) {
		return statement;
	    }
	    if (semantic != null && !(semantic instanceof Module)) {
		return semantic;
	    }
	    if (next != null) {
		return next;
	    }
	    var method = enclosingMethod(semantic);
	    return method != null ? method : module;
	}

	private EObject enclosingMethod(EObject object) {
	    var current = object;
	    while (current != null && !(current instanceof Module)) {
		if (current instanceof Method) {
		    return current;
		}
		current = current.eContainer();
	    }
	    return null;
	}

	private EObject semanticAt(int offset) {
	    var root = NodeModelUtils.findActualNodeFor(module);
	    if (root == null) {
		return module;
	    }
	    INode leaf = NodeModelUtils.findLeafNodeAtOffset(root, Math.max(offset, 0));
	    if (leaf == null) {
		return module;
	    }
	    var semantic = NodeModelUtils.findActualSemanticObjectFor(leaf);
	    return semantic == null ? module : semantic;
	}

	private EObject enclosingStatement(EObject object) {
	    var current = object;
	    while (current != null && !(current instanceof Module)) {
		if (current instanceof Statement) {
		    return current;
		}
		current = current.eContainer();
	    }
	    return null;
	}

	private EObject firstObjectAtOrAfter(int offset) {
	    EObject best = null;
	    int bestStart = Integer.MAX_VALUE;
	    var iterator = module.eAllContents();
	    while (iterator.hasNext()) {
		var object = iterator.next();
		var node = NodeModelUtils.findActualNodeFor(object);
		if (node == null) {
		    continue;
		}
		int start = node.getOffset();
		if (start >= offset && start < bestStart) {
		    best = object;
		    bestStart = start;
		}
	    }
	    return best;
	}

	public List<Diagnostic> forCode(String code) {
	    if (code == null) {
		return List.of();
	    }
	    return diagnostics.stream().filter(diagnostic -> code.equals(diagnosticCode(diagnostic))).toList();
	}

	public List<Diagnostic> unknownCodes() {
	    return diagnostics.stream()
		    .filter(diagnostic -> !LsDiagnosticCatalog.isKnown(diagnosticCode(diagnostic)))
		    .toList();
	}

	public static String diagnosticCode(Diagnostic diagnostic) {
	    if (diagnostic == null || diagnostic.getCode() == null) {
		return "BSL LS";
	    }
	    return diagnostic.getCode().map(c -> c, Objects::toString);
	}
    }
}

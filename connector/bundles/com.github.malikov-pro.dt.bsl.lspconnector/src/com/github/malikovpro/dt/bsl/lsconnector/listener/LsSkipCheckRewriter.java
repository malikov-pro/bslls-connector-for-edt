package com.github.malikovpro.dt.bsl.lsconnector.listener;

import java.util.ArrayList;
import java.util.WeakHashMap;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import com._1c.g5.v8.dt.bsl.ui.editor.BslXtextEditor;
import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;
import com.github.malikovpro.dt.bsl.lsconnector.check.LsSkipCheck;
import com.github.malikovpro.dt.bsl.lsconnector.check.LsSuppressionComments;

/**
 * EDT для ICheck всегда пишет {@code //@skip-check}. Для кодов BSL LS
 * отменяем вставку, поэтому действие «Подавить» не меняет модуль.
 * {@code //@skip-check} типовых проверок EDT не трогаем.
 */
public final class LsSkipCheckRewriter implements IDocumentListener {
    private static final WeakHashMap<IDocument, LsSkipCheckRewriter> ATTACHED = new WeakHashMap<>();

    private final IDocument document;
    private boolean rewriting;
    private boolean rewriteScheduled;

    private LsSkipCheckRewriter(IDocument document) {
	this.document = document;
    }

    public static void attach(BslXtextEditor editor) {
	if (editor == null) {
	    return;
	}
	var document = editorDocument(editor);
	if (document == null) {
	    var display = display();
	    if (display != null) {
		display.asyncExec(() -> attachWhenReady(editor));
	    }
	    return;
	}
	attachTo(document);
    }

    public static void detach(BslXtextEditor editor) {
	if (editor == null) {
	    return;
	}
	var document = editorDocument(editor);
	if (document == null) {
	    return;
	}
	LsSkipCheckRewriter listener;
	synchronized (ATTACHED) {
	    listener = ATTACHED.remove(document);
	}
	if (listener != null) {
	    document.removeDocumentListener(listener);
	}
    }

    public static void attachOpenEditors(IWorkbenchPage page) {
	if (page == null) {
	    return;
	}
	for (IEditorReference reference : page.getEditorReferences()) {
	    var part = reference.getPart(false);
	    if (part instanceof BslXtextEditor) {
		attach((BslXtextEditor) part);
	    }
	}
    }

    @Override
    public void documentAboutToBeChanged(DocumentEvent event) {
	// none
    }

    @Override
    public void documentChanged(DocumentEvent event) {
	if (rewriting || event == null || !LsSuppressionComments.looksLikeSkipCheckInsert(event.getText())) {
	    return;
	}
	var text = event.getText();
	if (LsSkipCheck.skipCheckIds(text).stream().noneMatch(LsSkipCheck::isLsCheckId)) {
	    return;
	}
	scheduleRewrite(event.getOffset(), text.length());
    }

    private void scheduleRewrite(int offset, int length) {
	var display = display();
	if (display == null) {
	    return;
	}
	synchronized (this) {
	    if (rewriteScheduled) {
		return;
	    }
	    rewriteScheduled = true;
	}
	display.asyncExec(() -> {
	    synchronized (this) {
		rewriteScheduled = false;
	    }
	    rewriteSkipChecks(offset, length);
	});
    }

    private void rewriteSkipChecks(int offset, int length) {
	if (rewriting) {
	    return;
	}
	try {
	    int last = document.getNumberOfLines() - 1;
	    int start = Math.max(0, Math.min(offset, document.getLength()));
	    int end = Math.max(start, Math.min(offset + Math.max(length, 0), document.getLength()));
	    int startLine = Math.max(0, document.getLineOfOffset(start) - 1);
	    int endLine = Math.min(last, document.getLineOfOffset(end));
	    var skipLines = new ArrayList<Integer>();
	    for (int line = startLine; line <= endLine; line++) {
		if (!LsSuppressionComments.lsCodesFromSkipLine(lineText(line)).isEmpty()) {
		    skipLines.add(line);
		}
	    }
	    rewriting = true;
	    try {
		for (int i = skipLines.size() - 1; i >= 0; i--) {
		    rewriteOne(skipLines.get(i));
		}
	    } finally {
		rewriting = false;
	    }
	} catch (BadLocationException e) {
	    BSLPlugin.logWarning("Не удалось отменить подавление BSL LS: " + e.getMessage(), e);
	}
    }

    private void rewriteOne(int skipLine) throws BadLocationException {
	int skipOffset = document.getLineOffset(skipLine);
	int skipLength = document.getLineLength(skipLine);
	var delimiter = document.getLineDelimiter(skipLine);
	int skipContent = delimiter == null ? skipLength : skipLength - delimiter.length();
	var skipText = document.get(skipOffset, skipContent);
	var replacement = LsSuppressionComments.removeLsCodesFromSkipLine(skipText);
	if (replacement == null || replacement.equals(skipText)) {
	    return;
	}
	int replacedLength = replacement.isBlank() ? skipLength : skipContent;
	document.replace(skipOffset, replacedLength, replacement);
    }

    private String lineText(int line) throws BadLocationException {
	int offset = document.getLineOffset(line);
	int length = document.getLineLength(line);
	var delimiter = document.getLineDelimiter(line);
	int content = delimiter == null ? length : length - delimiter.length();
	return content <= 0 ? "" : document.get(offset, content);
    }

    private static void attachWhenReady(BslXtextEditor editor) {
	if (editor == null || editor.getEditorInput() == null) {
	    return;
	}
	var document = editorDocument(editor);
	if (document != null) {
	    attachTo(document);
	}
    }

    private static void attachTo(IDocument document) {
	synchronized (ATTACHED) {
	    if (ATTACHED.containsKey(document)) {
		return;
	    }
	    var listener = new LsSkipCheckRewriter(document);
	    document.addDocumentListener(listener);
	    ATTACHED.put(document, listener);
	}
    }

    private static IDocument editorDocument(BslXtextEditor editor) {
	var document = editor.getDocument();
	if (document != null) {
	    return document;
	}
	var provider = editor.getDocumentProvider();
	var input = editor.getEditorInput();
	return provider == null || input == null ? null : provider.getDocument(input);
    }

    private static Display display() {
	if (!PlatformUI.isWorkbenchRunning()) {
	    return null;
	}
	return PlatformUI.getWorkbench().getDisplay();
    }
}

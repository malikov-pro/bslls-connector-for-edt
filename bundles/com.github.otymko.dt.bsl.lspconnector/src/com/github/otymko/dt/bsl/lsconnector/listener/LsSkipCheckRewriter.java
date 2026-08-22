package com.github.otymko.dt.bsl.lsconnector.listener;

import java.util.ArrayList;
import java.util.List;
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
import com.github.otymko.dt.bsl.lsconnector.BSLPlugin;
import com.github.otymko.dt.bsl.lsconnector.check.LsSkipCheck;
import com.github.otymko.dt.bsl.lsconnector.check.LsSuppressionComments;

/**
 * EDT для ICheck всегда пишет {@code //@skip-check}. Для кодов BSL LS
 * заменяем это на пару вокруг одной строки маркера:
 * {@code // BSLLS:Code-off} … строка … {@code // BSLLS:Code-on}.
 * {@code //@skip-check} типовых проверок EDT не трогаем.
 */
public final class LsSkipCheckRewriter implements IDocumentListener {
    private static final WeakHashMap<IDocument, LsSkipCheckRewriter> ATTACHED = new WeakHashMap<>();

    private final IDocument document;
    private boolean rewriting;

    private LsSkipCheckRewriter(IDocument document) {
	this.document = document;
    }

    public static void attach(BslXtextEditor editor) {
	if (editor == null) {
	    return;
	}
	var document = editor.getDocument();
	if (document == null) {
	    return;
	}
	synchronized (ATTACHED) {
	    if (ATTACHED.containsKey(document)) {
		return;
	    }
	    var listener = new LsSkipCheckRewriter(document);
	    document.addDocumentListener(listener);
	    ATTACHED.put(document, listener);
	}
    }

    public static void detach(BslXtextEditor editor) {
	if (editor == null) {
	    return;
	}
	var document = editor.getDocument();
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
	if (LsSkipCheck.skipCheckIds(event.getText()).stream().noneMatch(LsSkipCheck::isLsCheckId)) {
	    return;
	}
	int offset = event.getOffset();
	var display = display();
	if (display == null) {
	    return;
	}
	display.asyncExec(() -> rewriteInsertedSkip(offset));
    }

    private void rewriteInsertedSkip(int offset) {
	if (rewriting) {
	    return;
	}
	try {
	    int skipLine = document.getLineOfOffset(Math.min(offset, document.getLength()));
	    int skipOffset = document.getLineOffset(skipLine);
	    int skipLength = document.getLineLength(skipLine);
	    var delimiter = document.getLineDelimiter(skipLine);
	    int skipContent = delimiter == null ? skipLength : skipLength - delimiter.length();
	    var skipText = document.get(skipOffset, skipContent);
	    var lsCodes = LsSuppressionComments.lsCodesFromSkipLine(skipText);
	    if (lsCodes.isEmpty()) {
		return;
	    }
	    int markedLine = nextContentLine(skipLine + 1);
	    var indent = lineIndent(markedLine >= 0 ? markedLine : skipLine, skipText);
	    var offLines = reindent(LsSuppressionComments.rewriteSkipLine(skipText), indent);
	    if (offLines.isEmpty()) {
		return;
	    }
	    var lineDelim = delimiter == null ? System.lineSeparator() : delimiter;
	    var replacement = join(offLines, lineDelim);
	    int markedEnd = markedLine >= 0 ? lineEndOffset(markedLine) : skipOffset + skipLength;
	    var onBlock = onComments(lsCodes, indent, lineDelim);
	    if (markedEnd < document.getLength() && !endsWithLineBreak(markedEnd, lineDelim)) {
		onBlock = lineDelim + onBlock;
	    }
	    rewriting = true;
	    try {
		if (markedEnd >= skipOffset + skipLength) {
		    document.replace(markedEnd, 0, onBlock);
		} else {
		    document.replace(skipOffset + skipContent, 0, lineDelim + onBlock);
		}
		document.replace(skipOffset, skipContent, replacement);
	    } finally {
		rewriting = false;
	    }
	} catch (BadLocationException e) {
	    BSLPlugin.createWarningStatus("Не удалось записать подавление BSL LS: " + e.getMessage(), e);
	}
    }

    private int nextContentLine(int fromLine) throws BadLocationException {
	int last = document.getNumberOfLines() - 1;
	for (int line = fromLine; line <= last; line++) {
	    if (!lineText(line).isBlank()) {
		return line;
	    }
	}
	return -1;
    }

    private int lineEndOffset(int line) throws BadLocationException {
	return document.getLineOffset(line) + document.getLineLength(line);
    }

    private String lineText(int line) throws BadLocationException {
	int offset = document.getLineOffset(line);
	int length = document.getLineLength(line);
	var delimiter = document.getLineDelimiter(line);
	int content = delimiter == null ? length : length - delimiter.length();
	return content <= 0 ? "" : document.get(offset, content);
    }

    private String lineIndent(int line, String fallbackLine) throws BadLocationException {
	if (line >= 0 && line < document.getNumberOfLines()) {
	    var text = lineText(line);
	    if (!text.isBlank()) {
		return LsSuppressionComments.leadingWhitespace(text);
	    }
	}
	return LsSuppressionComments.leadingWhitespace(fallbackLine);
    }

    private static List<String> reindent(List<String> lines, String indent) {
	var result = new ArrayList<String>();
	for (String line : lines) {
	    result.add(indent + line.stripLeading());
	}
	return result;
    }

    private static String join(List<String> lines, String delimiter) {
	var builder = new StringBuilder();
	for (int i = 0; i < lines.size(); i++) {
	    if (i > 0) {
		builder.append(delimiter);
	    }
	    builder.append(lines.get(i));
	}
	return builder.toString();
    }

    private static String onComments(List<String> codes, String indent, String delimiter) {
	var builder = new StringBuilder();
	for (int i = codes.size() - 1; i >= 0; i--) {
	    builder.append(indent).append(LsSuppressionComments.onComment(codes.get(i))).append(delimiter);
	}
	return builder.toString();
    }

    private boolean endsWithLineBreak(int offset, String delimiter) throws BadLocationException {
	if (offset <= 0 || delimiter == null || delimiter.isEmpty() || offset < delimiter.length()) {
	    return false;
	}
	return delimiter.equals(document.get(offset - delimiter.length(), delimiter.length()));
    }

    private static Display display() {
	if (!PlatformUI.isWorkbenchRunning()) {
	    return null;
	}
	return PlatformUI.getWorkbench().getDisplay();
    }
}

package com.github.malikovpro.dt.bsl.lsconnector.listener;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import com._1c.g5.v8.dt.bsl.ui.editor.BslXtextEditor;
import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;
import com.github.malikovpro.dt.bsl.lsconnector.check.LsModuleAnalyzer;
import com.github.malikovpro.dt.bsl.lsconnector.check.LsSkipCheck;
import com.github.malikovpro.dt.bsl.lsconnector.check.LsSuppressionComments;
import com.github.malikovpro.dt.bsl.lsconnector.util.BSLCommon;

/**
 * EDT для ICheck всегда пишет {@code //@skip-check}. Для диагностик BSL LS
 * коннектор дополняет вставку регионами {@code // BSLLS:Код-off} …
 * {@code // BSLLS:Код-on} вокруг диапазона ошибки из ответа LS. Сама вставка
 * EDT не меняется: она глушит типовой дубликат проверки, регион — диагностику
 * LS. Диапазон берём у LS; если LS недоступен — обёртка одной строкой.
 */
public final class LsSkipCheckRewriter implements IDocumentListener {
    private static final WeakHashMap<IDocument, LsSkipCheckRewriter> ATTACHED = new WeakHashMap<>();
    /** Сколько ждём диагностик от LS, прежде обернуть одной строкой. */
    private static final long DIAGNOSTICS_TIMEOUT_MILLIS = 5_000;
    private static final String REGION_PREFIX = "// BSLLS:";
    private static final String REGION_OFF_SUFFIX = "-off";
    private static final String REGION_ON_SUFFIX = "-on";
    private static final String FALLBACK_NEW_LINE = "\n";
    /** Дальний диапазон диагностики того же кода не захватываем. */
    private static final int MAX_DIAGNOSTIC_DISTANCE_LINES = 10;
    /** Предохранитель сканирования оператора до конца вызова. */
    private static final int MAX_STATEMENT_SCAN_LINES = 200;

    private final IDocument document;
    private final URI moduleUri;
    private boolean inserting;

    private LsSkipCheckRewriter(IDocument document, URI moduleUri) {
	this.document = document;
	this.moduleUri = moduleUri;
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
	attachTo(document, editorUri(editor));
    }

    /**
     * Подключает рерайтер к BSL-редактору части: редактор модуля сам может быть
     * BSL-редактором или hosting'ом вложенного (общие модули открываются
     * многостраничным DtGranularEditor с BSL-редактором внутри).
     */
    public static void attachToEditor(org.eclipse.ui.IWorkbenchPart part) {
	if (part instanceof org.eclipse.ui.IEditorPart editorPart) {
	    attachToEditorPart(editorPart);
	}
    }

    private static void attachToEditorPart(org.eclipse.ui.IEditorPart part) {
	if (part == null) {
	    return;
	}
	if (part instanceof BslXtextEditor editor) {
	    attach(editor);
	    return;
	}
	if (part instanceof org.eclipse.ui.part.MultiPageEditorPart multi) {
	    for (var nested : nestedEditors(multi)) {
		attachToEditorPart(nested);
	    }
	    // Многостраничные редакторы EDT (общие модули — DtGranularEditor):
	    // страница текста модуля — IFormPage со встроенным редактором,
	    // в nestedEditors её нет; редактор достаётся через getAdapter.
	    if (part instanceof org.eclipse.ui.forms.editor.FormEditor formEditor) {
		for (var page : formPages(formEditor)) {
		    if (page instanceof org.eclipse.ui.forms.editor.IFormPage formPage) {
			attachToEditorPart(formPage
				.getAdapter(org.eclipse.xtext.ui.editor.XtextEditor.class));
		    }
		}
	    }
	}
    }

    private static List<Object> formPages(org.eclipse.ui.forms.editor.FormEditor editor) {
	try {
	    var field = org.eclipse.ui.forms.editor.FormEditor.class.getDeclaredField("pages");
	    field.setAccessible(true);
	    if (field.get(editor) instanceof java.util.Vector<?> vector) {
		return new ArrayList<Object>(vector);
	    }
	} catch (Exception e) {
	    BSLPlugin.debug("Не удалось получить страницы редактора «" + editor.getTitle() + "»: " + e.getMessage());
	}
	return List.of();
    }

    private static List<org.eclipse.ui.IEditorPart> nestedEditors(org.eclipse.ui.part.MultiPageEditorPart multi) {
	try {
	    var field = org.eclipse.ui.part.MultiPageEditorPart.class.getDeclaredField("nestedEditors");
	    field.setAccessible(true);
	    if (field.get(multi) instanceof ArrayList<?> list) {
		var result = new ArrayList<org.eclipse.ui.IEditorPart>();
		for (var item : list) {
		    if (item instanceof org.eclipse.ui.IEditorPart editorPart) {
			result.add(editorPart);
		    }
		}
		return result;
	    }
	} catch (Exception e) {
	    BSLPlugin.debug("Не удалось получить вложенные редакторы «" + multi.getTitle() + "»: " + e.getMessage());
	}
	return List.of();
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
	    attachToEditor(reference.getPart(true));
	}
    }

    @Override
    public void documentAboutToBeChanged(DocumentEvent event) {
	// none
    }

    @Override
    public void documentChanged(DocumentEvent event) {
	if (inserting || event == null || !LsSuppressionComments.looksLikeSkipCheckInsert(event.getText())) {
	    return;
	}
	var codes = LsSuppressionComments.lsCodesFromSkipLine(event.getText()).stream()
		.filter(code -> !LsSkipCheck.ALL_LS.equals(code))
		.toList();
	if (codes.isEmpty()) {
	    return;
	}
	BSLPlugin.debug("Подавление LS: перехвачена вставка EDT «" + event.getText().strip() + "»");
	scheduleRegionInsert(event.getOffset(), event.getText(), codes);
    }

    private void scheduleRegionInsert(int offset, String eventText, List<String> codes) {
	var display = display();
	if (display == null) {
	    return;
	}
	display.asyncExec(() -> {
	    var content = documentContent();
	    if (content == null) {
		return;
	    }
	    var job = new Job("Подавление BSL LS: запрос диапазона ошибки") {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
		    var diagnostics = queryDiagnostics(content);
		    display.asyncExec(() -> insertRegions(offset, eventText, codes, diagnostics));
		    return Status.OK_STATUS;
		}
	    };
	    job.setSystem(true);
	    job.schedule();
	});
    }

    /** Снимок текста; вызывать в UI-потоке. */
    private String documentContent() {
	try {
	    return document.get();
	} catch (Exception e) {
	    BSLPlugin.debug("Подавление LS: не удалось снять текст документа: " + e.getMessage());
	    return null;
	}
    }

    private List<Diagnostic> queryDiagnostics(String content) {
	var plugin = BSLPlugin.getPlugin();
	if (plugin == null || moduleUri == null || !plugin.getLsService().ensureStarted()) {
	    return List.of();
	}
	var connector = plugin.getLsService().getConnector();
	if (connector == null) {
	    return List.of();
	}
	var alreadyOpened = plugin.getWorkbenchParts().contains(moduleUri.toString());
	BSLPlugin.debug("Подавление LS: запрос диагностик по " + moduleUri);
	plugin.getStatusService().beginBusy();
	try {
	    if (alreadyOpened) {
		connector.textDocumentDidChange(moduleUri, content);
	    } else {
		plugin.getWorkbenchParts().add(moduleUri.toString());
		connector.textDocumentDidOpen(moduleUri, content);
	    }
	    var deadline = System.currentTimeMillis() + DIAGNOSTICS_TIMEOUT_MILLIS;
	    List<Diagnostic> previous = null;
	    while (System.currentTimeMillis() < deadline) {
		var found = connector.diagnostics(moduleUri.toString());
		if (!found.isEmpty()) {
		    if (found.equals(previous)) {
			// Два одинаковых ответа подряд — LS досчитал текущий текст,
			// а не вернул диапазоны для предыдущей версии.
			BSLPlugin.debug("Подавление LS: LS вернул диагностик: " + found.size());
			return found;
		    }
		    previous = found;
		}
		plugin.sleepCurrentThread(250);
	    }
	} finally {
	    plugin.getStatusService().endBusy();
	    if (!alreadyOpened && plugin.getWorkbenchParts().contains(moduleUri.toString())) {
		plugin.getWorkbenchParts().remove(moduleUri.toString());
		connector.textDocumentDidClose(moduleUri);
	    }
	}
	BSLPlugin.debug("Подавление LS: LS не ответил за " + (DIAGNOSTICS_TIMEOUT_MILLIS / 1000) + " с — оберну одну строку");
	return List.of();
    }

    private void insertRegions(int offset, String eventText, List<String> codes, List<Diagnostic> diagnostics) {
	if (inserting) {
	    return;
	}
	try {
	    // Порядок детерминирован: сначала убираем вставку EDT, затем считаем
	    // геометрию по уже чистому документу. Диагностики LS получены для
	    // текста со вставкой EDT — диапазоны ниже удалённой строки сдвигаем.
	    int skipLine0 = document.getLineOfOffset(clamp(offset));
	    int removedLine0 = removeSkipLine(offset, eventText);
	    int anchorLine0 = anchorFor(codes, skipLine0, removedLine0);

	    var regions = new ArrayList<Region>();
	    for (String code : codes) {
		var range = shiftedRange(diagnosticRange(code, anchorLine0, diagnostics), removedLine0);
		int startLine0 = expandToStatementStart(range[0]);
		int endLine0 = expandToStatementEnd(startLine0, range[1]);
		regions.add(new Region(code, startLine0, endLine0));
	    }
	    regions.sort(Comparator.comparingInt((Region region) -> region.startLine0).reversed());

	    inserting = true;
	    try {
		for (Region region : regions) {
		    insertRegion(region);
		    BSLPlugin.debug("Подавление LS: регион " + region.code + " вставлен вокруг строк "
			    + (region.startLine0 + 1) + "–" + (region.endLine0 + 1) + " («"
			    + lineText(region.startLine0).strip() + "»)");
		}
	    } finally {
		inserting = false;
	    }
	} catch (BadLocationException e) {
	    BSLPlugin.logWarning("Не удалось вставить регион подавления BSL LS: " + e.getMessage(), e);
	} finally {
	    inserting = false;
	}
    }

    /**
     * Убирает вставленную EDT строку {@code //@skip-check <код LS>}: после
     * региона она ничего не подавляет. Ищем её рядом с исходной позицией.
     *
     * @return индекс удалённой строки (0-индексация) или -1, если не нашли.
     */
    private int removeSkipLine(int offset, String eventText) throws BadLocationException {
	var expected = eventText.strip();
	int hintLine0 = document.getLineOfOffset(clamp(offset));
	int first = Math.max(0, hintLine0 - 2);
	int last = Math.min(document.getNumberOfLines() - 1, hintLine0 + 4);
	for (int line = first; line <= last; line++) {
	    if (!lineText(line).strip().equalsIgnoreCase(expected)) {
		continue;
	    }
	    int lineOffset = document.getLineOffset(line);
	    int lineLength = document.getLineLength(line);
	    document.replace(lineOffset, lineLength, "");
	    BSLPlugin.debug("Подавление LS: вставка EDT удалена (строка " + (line + 1) + ")");
	    return line;
	}
	BSLPlugin.debug("Подавление LS: вставка EDT не найдена рядом со строкой " + (hintLine0 + 1));
	return -1;
    }

    /** Диапазон диагностики в координатах документа после удаления вставки EDT. */
    private static int[] shiftedRange(int[] range, int removedLine0) {
	if (removedLine0 < 0) {
	    return range;
	}
	int start = range[0] > removedLine0 ? range[0] - 1 : range[0];
	int end = range[1] > removedLine0 ? range[1] - 1 : range[1];
	return new int[] { start, end };
    }

    /**
     * Якорь помеченной строки в координатах после удаления вставки EDT.
     * Правило EDT: подавление ставится под помеченным комментарием и над
     * помеченным кодом. Строка над вставкой — комментарий (и не ещё одна
     * вставка) → помечен комментарий (якорь выше); иначе — код (якорь ниже).
     */
    private int anchorFor(List<String> codes, int skipLine0, int removedLine0) {
	try {
	    int above = skipLine0 - 1;
	    if (above >= 0) {
		var text = lineText(above).strip();
		if (text.startsWith("//") && !LsSuppressionComments.looksLikeSkipCheckInsert(text)) {
		    return above;
		}
	    }
	} catch (BadLocationException e) {
	    // нет строки выше — якорь под вставкой
	}
	if (removedLine0 >= 0) {
	    return Math.min(skipLine0, document.getNumberOfLines() - 1);
	}
	return Math.min(skipLine0 + 1, document.getNumberOfLines() - 1);
    }

    private void insertRegion(Region region) throws BadLocationException {
	var indent = LsSuppressionComments.leadingWhitespace(lineText(region.endLine0));
	var delimiter = document.getLineDelimiter(region.endLine0);
	if (delimiter == null) {
	    delimiter = FALLBACK_NEW_LINE;
	}
	// Снизу вверх: сначала замыкающий on, потом открывающий off.
	int onAt = endOfLineContent(region.endLine0);
	document.replace(onAt, 0, delimiter + indent + REGION_PREFIX + region.code + REGION_ON_SUFFIX);
	int offAt = document.getLineOffset(region.startLine0);
	document.replace(offAt, 0, indent + REGION_PREFIX + region.code + REGION_OFF_SUFFIX + delimiter);
    }

    /**
     * Диагностика кода, ближайшая к вставке skip-check по строкам (EDT ставит
     * вставку рядом с помеченным местом, но не всегда вплотную: пустые строки,
     * комментарии). Дальние диагностики не захватываем — без кандидата в
     * пределах окна обернём первую строку после вставки.
     */
    private int[] diagnosticRange(String code, int problemLine0, List<Diagnostic> diagnostics) {
	Diagnostic best = null;
	int bestDistance = MAX_DIAGNOSTIC_DISTANCE_LINES;
	for (Diagnostic diagnostic : diagnostics) {
	    if (!code.equalsIgnoreCase(LsModuleAnalyzer.Analysis.diagnosticCode(diagnostic))) {
		continue;
	    }
	    int start = diagnostic.getRange().getStart().getLine();
	    int end = diagnostic.getRange().getEnd().getLine();
	    int distance = skipLine0Distance(problemLine0, start, end);
	    if (distance < bestDistance) {
		bestDistance = distance;
		best = diagnostic;
	    }
	}
	if (best == null) {
	    return new int[] { problemLine0, problemLine0 };
	}
	return new int[] { best.getRange().getStart().getLine(), best.getRange().getEnd().getLine() };
    }

    private static int skipLine0Distance(int line, int start, int end) {
	if (line < start) {
	    return start - line;
	}
	return line > end ? line - end : 0;
    }

    /**
     * Расширяет начало диапазона вверх до первой строки оператора: строки без
     * «;» (продолжение вызова) относятся к тому же оператору. Комментарии,
     * пустые строки и завершённые операторы выше не захватываем.
     */
    private int expandToStatementStart(int startLine0) throws BadLocationException {
	var first = lineText(startLine0).strip();
	if (first.isEmpty() || first.startsWith("//")) {
	    return startLine0;
	}
	int start = startLine0;
	for (int line = startLine0 - 1; line >= 0 && line >= startLine0 - MAX_STATEMENT_SCAN_LINES; line--) {
	    var prev = codeTail(lineText(line));
	    if (prev.isEmpty() || prev.startsWith("//") || prev.endsWith(";") || endsWithBlockKeyword(prev)) {
		break;
	    }
	    start = line;
	}
	return start;
    }

    /**
     * Расширяет конец диапазона до конца оператора: если первая строка
     * открывает незакрытые скобки (вызов на несколько строк), идём вниз до
     * строки, где баланс закрыт и стоит «;». Комментарии и пустые строки
     * не расширяются.
     */
    private int expandToStatementEnd(int startLine0, int endLine0) throws BadLocationException {
	var first = lineText(startLine0).strip();
	if (first.isEmpty() || first.startsWith("//")) {
	    return endLine0;
	}
	int last = document.getNumberOfLines() - 1;
	int balance = 0;
	for (int line = startLine0; line <= last && line <= startLine0 + MAX_STATEMENT_SCAN_LINES; line++) {
	    balance += parenBalance(lineText(line));
	    if (balance <= 0 && codeTail(lineText(line)).endsWith(";")) {
		return line > endLine0 ? line : endLine0;
	    }
	}
	return endLine0;
    }

    /** Строка без строковых литералов и комментариев — для проверок «хвоста». */
    private static String codeTail(String line) {
	var code = new StringBuilder();
	boolean inString = false;
	for (int i = 0; i < line.length(); i++) {
	    char ch = line.charAt(i);
	    if (inString) {
		if (ch == '"') {
		    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
			i++;
		    } else {
			inString = false;
		    }
		}
		continue;
	    }
	    if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
		i++;
		continue;
	    }
	    if (ch == '"') {
		inString = true;
		continue;
	    }
	    if (ch == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
		break;
	    }
	    code.append(ch);
	}
	return code.toString().strip();
    }

    private static boolean endsWithBlockKeyword(String code) {
	var upper = code.toUpperCase(Locale.ROOT);
	return upper.endsWith("ТОГДА") || upper.endsWith("ЦИКЛ") || upper.endsWith("ПОПЫТКА")
		|| upper.endsWith("ИСКЛЮЧЕНИЕ") || upper.endsWith("ИНАЧЕ");
    }

    /** Баланс круглых скобок в строке вне строковых литералов и комментариев. */
    private static int parenBalance(String line) {
	int balance = 0;
	boolean inString = false;
	boolean inComment = false;
	for (int i = 0; i < line.length(); i++) {
	    char ch = line.charAt(i);
	    if (inComment) {
		continue;
	    }
	    if (inString) {
		if (ch == '"') {
		    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
			i++;
		    } else {
			inString = false;
		    }
		}
		continue;
	    }
	    if (ch == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
		inComment = true;
		continue;
	    }
	    if (ch == '"') {
		inString = true;
		continue;
	    }
	    if (ch == '(') {
		balance++;
	    } else if (ch == ')') {
		balance--;
	    }
	}
	return balance;
    }

    private int endOfLineContent(int line0) throws BadLocationException {
	int offset = document.getLineOffset(line0);
	int length = document.getLineLength(line0);
	var delimiter = document.getLineDelimiter(line0);
	int content = delimiter == null ? length : length - delimiter.length();
	return offset + content;
    }

    private String lineText(int line0) throws BadLocationException {
	int offset = document.getLineOffset(line0);
	int length = document.getLineLength(line0);
	var delimiter = document.getLineDelimiter(line0);
	int content = delimiter == null ? length : length - delimiter.length();
	return content <= 0 ? "" : document.get(offset, content);
    }

    private int clamp(int offset) {
	return Math.max(0, Math.min(offset, document.getLength()));
    }

    private static void attachWhenReady(BslXtextEditor editor) {
	if (editor == null || editor.getEditorInput() == null) {
	    return;
	}
	var document = editorDocument(editor);
	if (document != null) {
	    attachTo(document, editorUri(editor));
	}
    }

    private static void attachTo(IDocument document, URI moduleUri) {
	synchronized (ATTACHED) {
	    if (ATTACHED.containsKey(document)) {
		return;
	    }
	    var listener = new LsSkipCheckRewriter(document, moduleUri);
	    document.addDocumentListener(listener);
	    ATTACHED.put(document, listener);
	}
	BSLPlugin.debug("Рерайтер подавления подписан на документ " + moduleUri);
    }

    private static URI editorUri(BslXtextEditor editor) {
	try {
	    var resource = editor.getResource();
	    return resource == null ? null : BSLCommon.uri(resource.getLocationURI());
	} catch (Exception e) {
	    return null;
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

    /** Регион вставки в координатах текста на момент «Подавить»; строки включительно. */
    private record Region(String code, int startLine0, int endLine0) {
    }
}

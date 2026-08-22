package com.github.otymko.dt.bsl.lsconnector.check;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4j.Diagnostic;

import com._1c.g5.v8.dt.bsl.model.Module;
import com.e1c.g5.v8.dt.check.BslDirectLocationIssue;
import com.e1c.g5.v8.dt.check.DirectLocation;
import com.e1c.g5.v8.dt.check.components.BasicCheck.ResultAcceptor;
import com.github.otymko.dt.bsl.lsconnector.BSLPlugin;
import com.github.otymko.dt.bsl.lsconnector.check.LsModuleAnalyzer.Analysis;
import com.github.otymko.dt.bsl.lsconnector.util.BSLCommon;

public final class LsCheckSupport {
    private static final LsModuleAnalyzer ANALYZER = new LsModuleAnalyzer();

    private LsCheckSupport() {
    }

    public static void report(Object object, ResultAcceptor resultAcceptor, IProgressMonitor progressMonitor,
	    String checkId, boolean unknownOnly) {
	if (progressMonitor.isCanceled() || !(object instanceof Module)) {
	    return;
	}
	var analysis = ANALYZER.analyze((Module) object, progressMonitor);
	var diagnostics = unknownOnly ? analysis.unknownCodes() : analysis.forCode(checkId);
	for (Diagnostic diagnostic : diagnostics) {
	    if (progressMonitor.isCanceled()) {
		return;
	    }
	    acceptIssue(analysis, resultAcceptor, diagnostic, checkId);
	}
    }

    private static void acceptIssue(Analysis analysis, ResultAcceptor resultAcceptor, Diagnostic diagnostic,
	    String checkId) {
	int[] offsetParams;
	try {
	    offsetParams = BSLCommon.getOffsetByRange(diagnostic.getRange(), analysis.getDocument());
	} catch (BadLocationException e) {
	    BSLPlugin.createErrorStatus(e.getMessage(), e);
	    return;
	}

	var code = Analysis.diagnosticCode(diagnostic);
	var lineNumber = diagnostic.getRange().getStart().getLine() + 1;
	if (LsSkipCheck.isSkipped(analysis.getContent(), lineNumber, checkId)
		|| LsSkipCheck.isSkipped(analysis.getContent(), lineNumber, code)
		|| LsSkipCheck.isSkipped(analysis.getContent(), lineNumber, LsSkipCheck.ALL_LS)) {
	    return;
	}

	var message = "[" + code + "] " + diagnostic.getMessage();
	var causer = analysis.findCauser(offsetParams[0]);
	var location = new DirectLocation(offsetParams[0], offsetParams[1], lineNumber, causer);
	resultAcceptor.addIssue(new BslDirectLocationIssue(message, location, code,
		LsDiagnosticCatalog.v8stdUrl(code)));
    }
}

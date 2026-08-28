package com.github.malikovpro.dt.bsl.lsconnector.check;

import java.util.Collections;
import java.util.HashSet;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.xtext.util.CancelIndicator;
import org.osgi.framework.ServiceReference;

import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.validation.CustomValidationMessageAcceptor;
import com.e1c.g5.v8.dt.check.BslDirectLocationIssue;
import com.e1c.g5.v8.dt.check.DirectLocation;
import com.e1c.g5.v8.dt.check.ICheckScheduler;
import com.e1c.g5.v8.dt.check.components.BasicCheck.ResultAcceptor;
import com.github.malikovpro.dt.bsl.lsconnector.BSLLsCheck;
import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;
import com.github.malikovpro.dt.bsl.lsconnector.check.LsModuleAnalyzer.Analysis;
import com.github.malikovpro.dt.bsl.lsconnector.util.BSLCommon;

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
	    try {
		acceptIssue(analysis, resultAcceptor, diagnostic, checkId);
	    } catch (RuntimeException e) {
		BSLPlugin.logWarning(
			"Не удалось добавить замечание BSL LS " + checkId + ": " + e.getMessage(), e);
	    }
	}
    }

    public static void validateFromXtext(Module module, CustomValidationMessageAcceptor messageAcceptor,
	    CancelIndicator monitor) {
	var progress = new CancelAdapter(monitor);
	ANALYZER.analyze(module, progress);
	if (progress.isCanceled()) {
	    return;
	}
	scheduleConfigurationChecks(module, progress);
    }

    private static void acceptIssue(Analysis analysis, ResultAcceptor resultAcceptor, Diagnostic diagnostic,
	    String checkId) {
	int[] offsetParams;
	try {
	    offsetParams = BSLCommon.getOffsetByRange(diagnostic.getRange(), analysis.getDocument());
	} catch (BadLocationException e) {
	    BSLPlugin.logError(e.getMessage(), e);
	    return;
	}

	var code = Analysis.diagnosticCode(diagnostic);
	var lineNumber = diagnostic.getRange().getStart().getLine() + 1;
	var causer = analysis.findCauser(offsetParams[0]);
	if (isIssueSkipped(analysis, lineNumber, checkId, code)) {
	    return;
	}

	var message = LsDiagnosticCatalog.formatIssueMessage(code, diagnostic.getMessage());
	var length = Math.max(1, offsetParams[1]);
	var target = causer != null ? causer : analysis.getModule();
	if (target == null) {
	    return;
	}
	var location = new DirectLocation(offsetParams[0], length, lineNumber, target);
	resultAcceptor.addIssue(new BslDirectLocationIssue(message, location));
    }

    private static boolean isIssueSkipped(Analysis analysis, int diagnosticLine, String checkId, String code) {
	var content = analysis.getContent();
	if (LsSkipCheck.isSkipped(content, diagnosticLine, code)) {
	    return true;
	}
	return checkId != null && LsSkipCheck.isSkipped(content, diagnosticLine, checkId);
    }

    private static void scheduleConfigurationChecks(Module module, IProgressMonitor progress) {
	var file = moduleFile(module);
	if (file == null || !file.exists()) {
	    return;
	}
	var scheduler = checkScheduler();
	if (scheduler == null) {
	    return;
	}
	var project = file.getProject();
	var objectId = EcoreUtil.getURI(module);
	if (objectId == null || !objectId.isPlatformResource()) {
	    objectId = URI.createPlatformResourceURI(file.getFullPath().toString(), true);
	}
	var checkIds = new HashSet<String>(LsDiagnosticCatalog.codes());
	checkIds.add(BSLLsCheck.CHECK_ID);
	try {
	    scheduler.permitDeactivatedCheckRequest(objectId, project);
	    scheduler.scheduleValidation(project, checkIds, Collections.singleton(objectId), progress);
	} catch (Exception e) {
	    BSLPlugin.logWarning("Не удалось поставить проверки BSL LS в очередь: " + e.getMessage(), e);
	}
    }

    private static IFile moduleFile(Module module) {
	var uri = EcoreUtil.getURI(module);
	if (uri == null || !uri.isPlatformResource()) {
	    return null;
	}
	return ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(uri.toPlatformString(true)));
    }

    private static ICheckScheduler checkScheduler() {
	var context = BSLPlugin.getContext();
	if (context == null) {
	    return null;
	}
	ServiceReference<ICheckScheduler> typed = context.getServiceReference(ICheckScheduler.class);
	if (typed != null) {
	    return context.getService(typed);
	}
	@SuppressWarnings("unchecked")
	ServiceReference<ICheckScheduler> named = (ServiceReference<ICheckScheduler>) context
		.getServiceReference(ICheckScheduler.SERVICE_NAME);
	return named == null ? null : context.getService(named);
    }

    private static final class CancelAdapter extends NullProgressMonitor {
	private final CancelIndicator indicator;

	private CancelAdapter(CancelIndicator indicator) {
	    this.indicator = indicator;
	}

	@Override
	public boolean isCanceled() {
	    return indicator != null && indicator.isCanceled();
	}
    }
}

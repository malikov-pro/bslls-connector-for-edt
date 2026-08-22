package com.github.otymko.dt.bsl.lsconnector.check;

import static com._1c.g5.v8.dt.bsl.model.BslPackage.Literals.MODULE;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IProgressMonitor;

import com.e1c.g5.v8.dt.check.CheckComplexity;
import com.e1c.g5.v8.dt.check.ICheckParameters;
import com.e1c.g5.v8.dt.check.components.BasicCheck;
import com.e1c.g5.v8.dt.check.settings.IssueSeverity;
import com.e1c.g5.v8.dt.check.settings.IssueType;

/**
 * Одна диагностика BSL LS как отдельная проверка EDT. Код проверки совпадает
 * с ключом LS ({@code LineLength}), поэтому «Подавить» пишет
 * {@code //@skip-check LineLength}, а «Открыть проверку» открывает карточку
 * со ссылкой на {@code https://v8std.ru/diagnostics/bslls/LineLength/}.
 */
public class BslLsDiagnosticCheck extends BasicCheck<Void> implements IExecutableExtension {
    private String diagnosticCode = "Unknown";

    @Override
    public void setInitializationData(IConfigurationElement config, String propertyName, Object data)
	    throws CoreException {
	if (data instanceof String && !((String) data).isBlank()) {
	    diagnosticCode = (String) data;
	}
    }

    @Override
    public String getCheckId() {
	return diagnosticCode;
    }

    @Override
    protected void configureCheck(CheckConfigurer configurationBuilder) {
	var info = LsDiagnosticCatalog.get(diagnosticCode);
	var title = info == null ? diagnosticCode : info.getTitle() + " (" + diagnosticCode + ")";
	var description = info == null
		? "Диагностика BSL Language Server. " + LsDiagnosticCatalog.v8stdUrl(diagnosticCode)
		: info.getDescription();
	var severity = info == null ? IssueSeverity.MINOR : info.getSeverity();
	var issueType = info == null ? IssueType.CODE_STYLE : info.getIssueType();
	configurationBuilder.title(title)
		.description(description)
		.complexity(CheckComplexity.COMPLEX)
		.severity(severity)
		.issueType(issueType)
		.module()
		.checkedObjectType(MODULE);
    }

    @Override
    protected void check(Object object, ResultAcceptor resultAcceptor, ICheckParameters parameters,
	    IProgressMonitor progressMonitor) {
	LsCheckSupport.report(object, resultAcceptor, progressMonitor, diagnosticCode, false);
    }
}

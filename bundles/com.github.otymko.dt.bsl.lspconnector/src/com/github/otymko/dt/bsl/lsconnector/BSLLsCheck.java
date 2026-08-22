package com.github.otymko.dt.bsl.lsconnector;

import static com._1c.g5.v8.dt.bsl.model.BslPackage.Literals.MODULE;

import org.eclipse.core.runtime.IProgressMonitor;

import com.e1c.g5.v8.dt.check.CheckComplexity;
import com.e1c.g5.v8.dt.check.ICheckParameters;
import com.e1c.g5.v8.dt.check.components.BasicCheck;
import com.e1c.g5.v8.dt.check.settings.IssueSeverity;
import com.e1c.g5.v8.dt.check.settings.IssueType;
import com.github.otymko.dt.bsl.lsconnector.check.LsCheckSupport;
import com.github.otymko.dt.bsl.lsconnector.check.LsSkipCheck;

/**
 * Запасная проверка для диагностик BSL LS, которых ещё нет в каталоге.
 * Известные коды ({@code LineLength} и др.) публикуют отдельные ICheck.
 */
public class BSLLsCheck extends BasicCheck<Void> {
    public static final String CHECK_ID = LsSkipCheck.ALL_LS;

    @Override
    public String getCheckId() {
	return CHECK_ID;
    }

    @Override
    protected void configureCheck(CheckConfigurer configurationBuilder) {
	configurationBuilder.title("Прочие диагностики BSL LS")
		.description("Прочие диагностики BSL Language Server")
		.complexity(CheckComplexity.COMPLEX)
		.severity(IssueSeverity.MINOR)
		.issueType(IssueType.CODE_STYLE)
		.module()
		.checkedObjectType(MODULE);
    }

    @Override
    protected void check(Object object, ResultAcceptor resultAcceptor, ICheckParameters parameters,
	    IProgressMonitor progressMonitor) {
	LsCheckSupport.report(object, resultAcceptor, progressMonitor, CHECK_ID, true);
    }
}

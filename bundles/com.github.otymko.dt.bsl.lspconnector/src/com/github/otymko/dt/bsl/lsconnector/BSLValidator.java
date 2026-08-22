package com.github.otymko.dt.bsl.lsconnector;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.CheckType;

import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.resource.BslResource;
import com._1c.g5.v8.dt.bsl.validation.CustomValidationMessageAcceptor;
import com.github.otymko.dt.bsl.lsconnector.check.LsCheckSupport;

/**
 * Xtext-канал будит BSL LS и ставит ICheck в очередь. Замечания в редактор
 * и в «Проблемы конфигурации» пишет только ICheck — иначе hover дублирует
 * «Подавить предупреждение» и «Подавить сообщение».
 */
@SuppressWarnings("deprecation")
public class BSLValidator implements com._1c.g5.v8.dt.bsl.validation.IExternalBslValidator {

    @Override
    public boolean needValidation(EObject object) {
	return object instanceof Module;
    }

    @Override
    @Check(CheckType.NORMAL)
    public void validate(EObject object, CustomValidationMessageAcceptor messageAcceptor, CancelIndicator monitor) {
	validateModule(object, messageAcceptor, monitor, false);
    }

    @Check(CheckType.EXPENSIVE)
    public void validateExpensive(EObject object, CustomValidationMessageAcceptor messageAcceptor,
	    CancelIndicator monitor) {
	validateModule(object, messageAcceptor, monitor, true);
    }

    private static void validateModule(EObject object, CustomValidationMessageAcceptor messageAcceptor,
	    CancelIndicator monitor, boolean expensive) {
	if (monitor.isCanceled() || !(object instanceof Module)) {
	    return;
	}
	var resource = ((Module) object).eResource();
	if (!(resource instanceof BslResource)) {
	    return;
	}
	if (expensive && !((BslResource) resource).isDeepAnalysing()) {
	    return;
	}
	LsCheckSupport.validateFromXtext((Module) object, messageAcceptor, monitor);
    }
}

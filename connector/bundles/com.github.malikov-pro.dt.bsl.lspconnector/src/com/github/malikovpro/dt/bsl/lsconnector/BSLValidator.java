package com.github.malikovpro.dt.bsl.lsconnector;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.CheckType;

import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.validation.CustomValidationMessageAcceptor;
import com.github.malikovpro.dt.bsl.lsconnector.check.LsCheckSupport;

/**
 * Xtext-канал будит BSL LS и ставит ICheck в очередь. Замечания в редактор
 * и в «Проблемы конфигурации» пишет только ICheck — иначе hover дублирует
 * «Подавить предупреждение» и «Подавить сообщение».
 *
 * Только @Check(NORMAL): прогоны Xtext накопительные и в EXPENSIVE-проходе
 * («Расширенная проверка модулей») NORMAL-метод выполняется и так — второй
 * @Check(EXPENSIVE) будил бы LS повторно (issue #16).
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
	if (monitor.isCanceled() || !(object instanceof Module)) {
	    return;
	}
	LsCheckSupport.validateFromXtext((Module) object, messageAcceptor, monitor);
    }
}

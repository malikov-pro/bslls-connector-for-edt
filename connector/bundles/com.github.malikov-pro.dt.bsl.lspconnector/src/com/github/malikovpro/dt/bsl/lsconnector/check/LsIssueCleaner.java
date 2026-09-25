package com.github.malikovpro.dt.bsl.lsconnector.check;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.osgi.framework.ServiceReference;

import com.e1c.g5.v8.dt.check.ICheckScheduler;
import com.github.malikovpro.dt.bsl.lsconnector.BSLLsCheck;
import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;

/**
 * Снятие уже опубликованных замечаний BSL LS из панели «Ошибки конфигурации».
 * Нужно при выключении плагина: процесс остановлен, новых замечаний не будет,
 * а старые без явной очистки висят до перевалидации каждого модуля.
 */
public final class LsIssueCleaner {
    /** Сколько ждать регистрации сервиса проверок после старта EDT. */
    private static final long SERVICE_WAIT_MILLIS = 120_000;
    private static final long SERVICE_RETRY_MILLIS = 2_000;

    private LsIssueCleaner() {
    }

    /**
     * Снимает результаты всех проверок коннектора во всех открытых проектах.
     * Фоновое задание: сервис проверок после старта EDT может регистрироваться
     * с задержкой, поэтому задание его дожидается.
     */
    public static void clearAsync() {
	var job = new Job("Снятие замечаний BSL LS") {
	    @Override
	    protected IStatus run(IProgressMonitor monitor) {
		var scheduler = awaitScheduler(monitor);
		if (scheduler == null) {
		    return Status.OK_STATUS;
		}
		var ids = checkIds();
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
		    if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		    }
		    if (project.isAccessible()) {
			scheduler.scheduleClearance(project, ids, monitor);
		    }
		}
		return Status.OK_STATUS;
	    }
	};
	job.setSystem(true);
	job.schedule();
    }

    /** Все идентификаторы проверок коннектора: каталог диагностик + запасная проверка. */
    static Set<String> checkIds() {
	var ids = new HashSet<>(LsDiagnosticCatalog.codes());
	ids.add(BSLLsCheck.CHECK_ID);
	return ids;
    }

    static ICheckScheduler awaitScheduler(IProgressMonitor monitor) {
	var waited = 0L;
	while (!monitor.isCanceled() && waited < SERVICE_WAIT_MILLIS) {
	    var scheduler = scheduler();
	    if (scheduler != null) {
		return scheduler;
	    }
	    try {
		Thread.sleep(SERVICE_RETRY_MILLIS);
	    } catch (InterruptedException e) {
		Thread.currentThread().interrupt();
		return null;
	    }
	    waited += SERVICE_RETRY_MILLIS;
	}
	return scheduler();
    }

    private static ICheckScheduler scheduler() {
	var context = BSLPlugin.getContext();
	if (context == null) {
	    return null;
	}
	ServiceReference<ICheckScheduler> reference = context.getServiceReference(ICheckScheduler.class);
	return reference == null ? null : context.getService(reference);
    }
}
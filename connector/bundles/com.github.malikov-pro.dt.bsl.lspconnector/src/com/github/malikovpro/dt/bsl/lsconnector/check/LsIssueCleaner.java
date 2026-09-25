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

import com._1c.g5.v8.dt.validation.marker.v2.IMarkerManagerV2;
import com.e1c.g5.v8.dt.check.settings.CheckUid;
import com.e1c.g5.v8.dt.check.settings.ICheckRepository;
import com.github.malikovpro.dt.bsl.lsconnector.BSLLsCheck;
import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;

/**
 * Снятие уже опубликованных замечаний BSL LS из панели «Ошибки конфигурации».
 * Нужно при выключении плагина: процесс остановлен, новых замечаний не будет,
 * а старые без явной очистки висят до перевалидации каждого модуля.
 *
 * <p>Маркеры валидации хранят проверки под <b>короткими UID</b> (вида SU23),
 * поэтому сырой id проверки («LineLength») сначала конвертируется через
 * {@link ICheckRepository} в CheckUid и короткий UID — иначе
 * {@code removeMarkersByCheckId} не находит ни одного маркера.
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
		var repository = await(monitor, ICheckRepository.class);
		var markerManager = await(monitor, IMarkerManagerV2.class);
		if (repository == null || markerManager == null) {
		    BSLPlugin.logWarning("Снятие замечаний BSL LS: сервисы EDT недоступны"
			    + " (ICheckRepository=" + (repository != null)
			    + ", IMarkerManagerV2=" + (markerManager != null) + ")");
		    return Status.OK_STATUS;
		}
		var rawIds = checkIds();
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
		    if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		    }
		    if (!project.isAccessible()) {
			continue;
		    }
		    var removed = clearProject(repository, markerManager, project, rawIds);
		    BSLPlugin.logInfo("Снятие замечаний BSL LS: " + project.getName()
			    + " — проверено id: " + rawIds.size() + ", найдено коротких UID: " + removed);
		}
		return Status.OK_STATUS;
	    }
	};
	job.setSystem(true);
	job.schedule();
    }

    /** Число коротких UID, по которым реально снимались маркеры (для журнала). */
    private static int clearProject(ICheckRepository repository, IMarkerManagerV2 markerManager,
	    IProject project, Set<String> rawIds) {
	var shortUids = 0;
	for (var rawId : rawIds) {
	    try {
		for (CheckUid uid : repository.getCheckUidForCheckId(rawId, project)) {
		    var shortUid = repository.getShortUid(uid, project);
		    if (shortUid != null && !shortUid.isBlank()) {
			shortUids++;
			markerManager.removeMarkersByCheckId(project, shortUid);
		    }
		}
	    } catch (Exception e) {
		BSLPlugin.logWarning("Снятие замечаний BSL LS: " + project.getName()
			+ ", проверка " + rawId + ": " + e.getMessage());
	    }
	}
	return shortUids;
    }

    /** Все идентификаторы проверок коннектора: каталог диагностик + запасная проверка. */
    static Set<String> checkIds() {
	var ids = new HashSet<>(LsDiagnosticCatalog.codes());
	ids.add(BSLLsCheck.CHECK_ID);
	return ids;
    }

    static <T> T await(IProgressMonitor monitor, Class<T> type) {
	var waited = 0L;
	while (!monitor.isCanceled() && waited < SERVICE_WAIT_MILLIS) {
	    var service = service(type);
	    if (service != null) {
		return service;
	    }
	    try {
		Thread.sleep(SERVICE_RETRY_MILLIS);
	    } catch (InterruptedException e) {
		Thread.currentThread().interrupt();
		return null;
	    }
	    waited += SERVICE_RETRY_MILLIS;
	}
	return service(type);
    }

    private static <T> T service(Class<T> type) {
	var context = BSLPlugin.getContext();
	if (context == null) {
	    return null;
	}
	ServiceReference<T> reference = context.getServiceReference(type);
	return reference == null ? null : context.getService(reference);
    }
}

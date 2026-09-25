package com.github.malikovpro.dt.bsl.lsconnector.check;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com._1c.g5.v8.bm.core.IBmTransaction;
import com._1c.g5.v8.bm.integration.AbstractBmTask;
import com._1c.g5.v8.bm.integration.IBmModel;
import com._1c.g5.v8.dt.core.platform.IBmModelManager;
import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;

/**
 * Перевалидация открытых проектов силами проверок коннектора. Нужна после
 * «выключенного» периода: опубликованные замечания сняты, а сама по себе
 * EDT перевалидацию не перезапускает — панели остаются пустыми до правки
 * модулей. Запускается из {@code LSService} после первого успешного старта LS
 * и ждёт готовности LS ({@code awaitInitialized}) — пустые ответы LS на
 * неинициализировавшийся процесс кэшировались бы как «замечаний нет».
 */
public final class LsRevalidation {

    private LsRevalidation() {
    }

    /** Планирует перевалидацию всех открытых проектов — фоновым заданием. */
    public static void scheduleAsync() {
	var job = new Job("Перевалидация проектов (BSL LS)") {
	    @Override
	    protected IStatus run(IProgressMonitor monitor) {
		var plugin = BSLPlugin.getPlugin();
		if (plugin == null || !plugin.getLsService().awaitInitialized()) {
		    return Status.OK_STATUS; // LS не готов — замечания появятся на следующей волне проверок
		}
		var scheduler = LsIssueCleaner.awaitScheduler(monitor);
		if (scheduler == null) {
		    return Status.OK_STATUS;
		}
		var modelManager = service(IBmModelManager.class);
		if (modelManager == null) {
		    return Status.OK_STATUS;
		}
		var ids = LsIssueCleaner.checkIds();
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
		    if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		    }
		    if (!project.isAccessible()) {
			continue;
		    }
		    var objectIds = collectTopObjectIds(modelManager.getModel(project));
		    if (!objectIds.isEmpty()) {
			scheduler.scheduleValidation(project, ids, objectIds, monitor);
		    }
		}
		return Status.OK_STATUS;
	    }
	};
	job.setUser(true);
	job.schedule();
    }

    /** Собирает идентификаторы всех top-объектов проекта в read-транзакции BM. */
    private static List<Object> collectTopObjectIds(IBmModel model) {
	if (model == null) {
	    return List.of();
	}
	var ids = new ArrayList<Object>();
	model.executeReadonlyTask(new AbstractBmTask<Void>("Сбор объектов для перевалидации BSL LS") {
	    @Override
	    public Void execute(IBmTransaction transaction, IProgressMonitor monitor) {
		var iterator = transaction.getTopObjectIterator();
		while (iterator.hasNext()) {
		    var object = iterator.next();
		    if (object != null && object.bmGetId() > 0) {
			ids.add(object.bmGetId());
		    }
		}
		return null;
	    }
	});
	return ids;
    }

    private static <T> T service(Class<T> type) {
	var context = BSLPlugin.getContext();
	if (context == null) {
	    return null;
	}
	var reference = context.getServiceReference(type);
	return reference == null ? null : context.getService(reference);
    }
}

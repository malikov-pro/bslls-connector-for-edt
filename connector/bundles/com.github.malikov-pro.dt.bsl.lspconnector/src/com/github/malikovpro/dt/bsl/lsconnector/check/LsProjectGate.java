package com.github.malikovpro.dt.bsl.lsconnector.check;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.osgi.framework.ServiceReference;

import com.e1c.g5.v8.dt.check.settings.CheckSettingsChange;
import com.e1c.g5.v8.dt.check.settings.CheckUid;
import com.e1c.g5.v8.dt.check.settings.ICheckRepository;
import com.e1c.g5.v8.dt.check.settings.ICheckSettings;
import com.e1c.g5.v8.dt.check.settings.ICheckSettingsChangeListener;
import com.github.malikovpro.dt.bsl.lsconnector.BSLLsCheck;
import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;

/**
 * Гейт «нужен ли BSL LS этому проекту»: проект считает нужным, если в его
 * профиле валидации включена хотя бы одна проверка коннектора
 * (Свойства проекта → Валидация → категория «Проверка BSL LS»).
 *
 * <p>Пока гейт закрыт, LS для модулей проекта не поднимается и не будится —
 * иначе LS молотит проекты с полностью выключенными проверками (issue #26).
 * Результат кэшируется; кэш сбрасывается слушателем смены настроек проверок.
 */
public final class LsProjectGate {
    private static final ConcurrentMap<String, Boolean> ENABLED_BY_PROJECT = new ConcurrentHashMap<>();
    private static final AtomicBoolean LISTENER_REGISTERED = new AtomicBoolean();

    private LsProjectGate() {
    }

    /**
     * @return true, если проекту нужен BSL LS. Недоступный/неизвестный проект
     *         и недоступный сервис настроек дают true — не блокируем работу
     *         (безопасный дефолт = прежнее поведение).
     */
    public static boolean isEnabledFor(IProject project) {
	if (project == null || !project.isAccessible()) {
	    return false;
	}
	ensureListener();
	return ENABLED_BY_PROJECT.computeIfAbsent(project.getName(), name -> computeEnabled(project));
    }

    /** Хоть в одном проекте воркспейса включены проверки BSL LS. */
    public static boolean anyEnabled() {
	for (var project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
	    if (project.isAccessible() && isEnabledFor(project)) {
		return true;
	    }
	}
	return false;
    }

    public static void invalidate(IProject project) {
	ENABLED_BY_PROJECT.remove(project.getName());
    }

    public static void invalidateAll() {
	ENABLED_BY_PROJECT.clear();
    }

    private static boolean computeEnabled(IProject project) {
	var repository = service(ICheckRepository.class);
	if (repository == null) {
	    return true;
	}
	for (var rawId : LsIssueCleaner.checkIds()) {
	    try {
		for (CheckUid uid : repository.getCheckUidForCheckId(rawId, project)) {
		    ICheckSettings settings = repository.getSettings(uid, project);
		    if (settings != null && settings.isEnabled()) {
			return true;
		    }
		}
	    } catch (Exception e) {
		// Настройки не читаются — считаем проверки включёнными (прежнее поведение).
		BSLPlugin.logWarning("Гейт BSL LS: не удалось прочитать настройки проверок проекта "
			+ project.getName() + ": " + e.getMessage());
		return true;
	    }
	}
	return false;
    }

    private static void ensureListener() {
	if (!LISTENER_REGISTERED.compareAndSet(false, true)) {
	    return;
	}
	var repository = service(ICheckRepository.class);
	if (repository == null) {
	    LISTENER_REGISTERED.set(false); // сервис ещё не поднялся — попробуем при следующем вызове
	    return;
	}
	repository.addChangeListener(new ICheckSettingsChangeListener() {
	    @Override
	    public void onChange(IProject project, Collection<CheckSettingsChange> changes) {
		if (project != null) {
		    invalidate(project);
		} else {
		    invalidateAll();
		}
	    }

	    @Override
	    public void onPreferenceChange(IProject project) {
		if (project != null) {
		    invalidate(project);
		} else {
		    invalidateAll();
		}
	    }
	});
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

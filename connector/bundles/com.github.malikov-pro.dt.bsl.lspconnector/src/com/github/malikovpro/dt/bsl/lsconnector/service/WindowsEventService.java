package com.github.malikovpro.dt.bsl.lsconnector.service;

import org.eclipse.ui.PlatformUI;

import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;
import com.github.malikovpro.dt.bsl.lsconnector.listener.WindowEventListener;

public class WindowsEventService {
    private static final WindowEventListener WINDOWS_EVENT_LISTENER = new WindowEventListener();
    private boolean started;

    public synchronized void start() {
	if (!PlatformUI.isWorkbenchRunning()) {
	    BSLPlugin.debug("Слушатели частей не зарегистрированы: воркспейс ещё не запущен");
	    return;
	}
	if (started) {
	    return;
	}
	PlatformUI.getWorkbench().addWindowListener(WINDOWS_EVENT_LISTENER);
	started = true;
	var windows = PlatformUI.getWorkbench().getWorkbenchWindows();
	for (var window : windows) {
	    WindowEventListener.addListenerToAllPages(window);
	}
	BSLPlugin.debug("Слушатели частей зарегистрированы: окон " + windows.length);
    }

    public synchronized void stop() {
	if (!started) {
	    return;
	}
	started = false;
	if (PlatformUI.isWorkbenchRunning()) {
	    for (var window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
		WindowEventListener.removeListenerFromAllPages(window);
	    }
	    PlatformUI.getWorkbench().removeWindowListener(WINDOWS_EVENT_LISTENER);
	}
    }
}

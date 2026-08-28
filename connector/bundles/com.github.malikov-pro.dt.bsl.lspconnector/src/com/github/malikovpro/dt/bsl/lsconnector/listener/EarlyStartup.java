package com.github.malikovpro.dt.bsl.lsconnector.listener;

import org.eclipse.ui.IStartup;

import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;

/**
 * Бандл активируется раньше окна воркспейса, поэтому в {@code start()} бандла
 * {@code getWorkbenchWindows()} пуст, а {@code windowOpened} для уже
 * открывающегося главного окна не придёт — слушатели частей не на чем
 * регистрировать. Точка {@code org.eclipse.ui.startup} гарантирует вызов
 * после инициализации воркспейса.
 */
public final class EarlyStartup implements IStartup {
    @Override
    public void earlyStartup() {
	var plugin = BSLPlugin.getPlugin();
	if (plugin != null) {
	    plugin.getWindowsEventService().start();
	}
    }
}

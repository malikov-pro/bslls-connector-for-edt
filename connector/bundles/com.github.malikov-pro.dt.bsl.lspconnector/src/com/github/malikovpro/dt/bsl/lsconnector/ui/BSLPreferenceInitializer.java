package com.github.malikovpro.dt.bsl.lsconnector.ui;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;

import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;
import com.github.malikovpro.dt.bsl.lsconnector.util.LaunchMode;

public class BSLPreferenceInitializer extends AbstractPreferenceInitializer {

    public BSLPreferenceInitializer() {
	// none
    }

    @Override
    public void initializeDefaultPreferences() {
	var node = DefaultScope.INSTANCE.getNode(BSLPlugin.PLUGIN_ID);
	node.put(BSLPreferencePage.LAUNCH_MODE, LaunchMode.JAR.getId());
	node.put(BSLPreferencePage.PATH_TO_JAVA, "java");
	node.put(BSLPreferencePage.JAVA_OPTS, "");
	node.put(BSLPreferencePage.WEBSOCKET_URL, BSLPreferencePage.DEFAULT_WEBSOCKET_URL);
	node.putBoolean(BSLPreferencePage.DEBUG, false);
    }

}

/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.event.ReloadListener;

import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Luigi R. Viggiano
 */
public class PropertiesManagerForTest extends PropertiesManager {
    public PropertiesManagerForTest(Class<? extends Config> clazz, Properties properties,
                             ScheduledExecutorService scheduler, VariablesExpander expander, LoadersManager loaders,
                             Map<?, ?>... imports) {
        super(clazz, properties, scheduler, expander, loaders, imports);
    }

    @Override
    public Properties load() {
        return super.load();
    }

    public List<ReloadListener> getReloadListeners() {
        return reloadListeners;
    }

    public List<PropertyChangeListener> getPropertyChangeListeners() {
        return propertyChangeListeners;
    }
}

/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Luigi R. Viggiano
 */
public class PropertiesManagerForTest extends PropertiesManager {
    public PropertiesManagerForTest(Class<? extends Config> clazz, Properties properties, 
                             ScheduledExecutorService scheduler, VariablesExpander expander, Map<?, ?>... imports) {
        super(clazz, properties, scheduler, expander, imports);
    }

    @Override
    public Properties doLoad(ConfigURLStreamHandler handler) throws IOException {
        return super.doLoad(handler);
    }

    @Override
    public Properties load() {
        return super.load();
    }
}

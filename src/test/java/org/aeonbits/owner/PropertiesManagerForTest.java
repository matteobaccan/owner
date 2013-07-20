/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Sources;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Luigi R. Viggiano
 */
public class PropertiesManagerForTest extends PropertiesManager {
    private ArrayList<URL> urls;

    public PropertiesManagerForTest(Class<? extends Config> clazz, Properties properties, 
                             ScheduledExecutorService scheduler, VariablesExpander expander, Map<?, ?>... imports) {
        super(clazz, properties, scheduler, expander, imports);
    }

    @Override
    public ArrayList<URL> toURLs(Sources sources) {
        return urls = super.toURLs(sources);
    }

    @Override
    public Properties load() {
        return super.load();
    }

    @Override
    public Properties doLoad() throws IOException {
        return super.doLoad();
    }

    public ArrayList<URL> getUrls() {
        return urls;
    }
}

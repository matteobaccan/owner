/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Util.SystemProvider;

import java.util.Map;
import java.util.Properties;

/**
 * @author Luigi R. Viggiano
 */
public class SystemProviderForTest implements SystemProvider {

    private final Properties system;
    private final Map<String, String> env;

    public SystemProviderForTest(Properties system, Map<String, String> env) {
        this.system = system;
        this.env = env;
    }

    public String getProperty(String key) {
        return system.getProperty(key);
    }

    public Map<String, String> getenv() {
        return env;
    }

    public Properties getProperties() {
        return system;
    }
}

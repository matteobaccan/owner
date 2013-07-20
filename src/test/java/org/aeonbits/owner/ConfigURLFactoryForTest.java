/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Luigi R. Viggiano
 */
public class ConfigURLFactoryForTest extends ConfigURLFactory {
    public ConfigURLFactoryForTest(ClassLoader classLoader, VariablesExpander expander) {
        super(classLoader, expander);
    }

    @Override
    public URL newURL(String spec) throws MalformedURLException {
        return super.newURL(spec);
    }
}

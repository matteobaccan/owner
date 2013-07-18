/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * @author Luigi R. Viggiano
 */
public class ConfigURLStreamHandlerForTest extends ConfigURLStreamHandler {
    public ConfigURLStreamHandlerForTest(ClassLoader classLoader, VariablesExpander expander) {
        super(classLoader, expander);
    }

    @Override
    public URLConnection openConnection(URL url) throws IOException {
        return super.openConnection(url);
    }
}

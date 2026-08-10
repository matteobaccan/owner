/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

/**
 * A loader that exists to be found: public, with a public no-argument constructor, which is what
 * {@link java.util.ServiceLoader} requires of anything it instantiates.
 * <p>
 * It is deliberately <b>not</b> declared in a service file under <code>src/test/resources</code>. That
 * would make it discovered in every test of this module and quietly change the sources every configuration
 * looks for. The tests that want it found put a service file in a jar of their own instead.
 * </p>
 *
 * @author Matteo Baccan
 */
public class DiscoverableLoader implements Loader {

    private static final long serialVersionUID = 8163723952851733193L;

    public static final String SUFFIX = ".discoverable";

    @Override
    public boolean accept(URI uri) {
        return SourceOptions.path(uri).toLowerCase().endsWith(SUFFIX);
    }

    @Override
    public void load(Properties result, URI uri) throws IOException {
        result.setProperty("loaded.by", getClass().getName());
    }

    @Override
    public String defaultSpecFor(String uriPrefix) {
        return uriPrefix + SUFFIX;
    }
}

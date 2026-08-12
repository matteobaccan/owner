/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

import org.aeonbits.owner.loaders.Loader;

import java.net.URI;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

/**
 * Runs inside the class loader built by {@link HoconDiscoveryWithoutTypesafeTest}, where every
 * <code>com.typesafe.config</code> class is unavailable, and reports what happened as a string.
 *
 * @author Matteo Baccan
 * @see WithoutCuratorProbe
 */
public class WithoutTypesafeProbe implements Callable<String> {

    @Override
    public String call() {
        StringBuilder report = new StringBuilder();

        report.append("typesafe absent: ").append(typesafeIsReallyAbsent()).append('\n');

        Loader discovered = findHoconLoader();
        report.append("discovered: ").append(discovered != null).append('\n');
        if (discovered == null)
            return report.toString();

        report.append("accepts conf: ")
                .append(discovered.accept(URI.create("file:/etc/app.conf"))).append('\n');
        report.append("accepts properties: ")
                .append(discovered.accept(URI.create("file:/etc/app.properties"))).append('\n');
        // the default spec is offered without the dependency, so a configuration with no @Sources still
        // probes for a .conf and only pays for it if one is there
        report.append("default spec: ").append(discovered.defaultSpecFor("classpath:MyConfig")).append('\n');

        try {
            discovered.load(new Properties(), URI.create("file:/etc/app.conf"));
            report.append("reading: no failure at all");
        } catch (Throwable failed) {
            report.append("reading: ").append(failed.getClass().getName())
                    .append(" / ").append(failed.getMessage());
        }
        return report.toString();
    }

    private static boolean typesafeIsReallyAbsent() {
        try {
            Class.forName("com.typesafe.config.ConfigFactory", false,
                    WithoutTypesafeProbe.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException | NoClassDefFoundError absent) {
            return true;
        }
    }

    private static Loader findHoconLoader() {
        for (Loader loader : ServiceLoader.load(Loader.class, WithoutTypesafeProbe.class.getClassLoader()))
            if (loader.getClass().getName().equals(HoconLoader.class.getName()))
                return loader;
        return null;
    }
}

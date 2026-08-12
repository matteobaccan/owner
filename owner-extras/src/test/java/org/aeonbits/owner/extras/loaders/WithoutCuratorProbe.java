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
 * Runs inside the class loader built by {@link ZooKeeperDiscoveryWithoutCuratorTest}, where every
 * <code>org.apache.curator</code> class is unavailable, and reports what happened as a string.
 *
 * <p>
 * It answers through {@link Callable}, a JDK type and therefore the same class in both loaders, so the
 * caller needs no reflection beyond constructing this. Nothing here asserts: the assertions belong to the
 * test, which can read a plain string and show it when it fails.
 * </p>
 *
 * @author Matteo Baccan
 */
public class WithoutCuratorProbe implements Callable<String> {

    @Override
    public String call() {
        StringBuilder report = new StringBuilder();

        report.append("curator absent: ").append(curatorIsReallyAbsent()).append('\n');

        Loader discovered = findZooKeeperLoader();
        report.append("discovered: ").append(discovered != null).append('\n');
        if (discovered == null)
            return report.toString();

        // the loader was instantiated with Curator absent, which is the whole point; now the two things it
        // has to do without it
        report.append("accepts zookeeper: ")
                .append(discovered.accept(URI.create("zookeeper://localhost:2181/config/app"))).append('\n');
        report.append("accepts file: ")
                .append(discovered.accept(URI.create("file:/etc/app.properties"))).append('\n');

        try {
            discovered.load(new Properties(), URI.create("zookeeper://localhost:2181/config/app"));
            report.append("reading: no failure at all");
        } catch (Throwable failed) {
            report.append("reading: ").append(failed.getClass().getName())
                    .append(" / ").append(failed.getMessage());
        }
        return report.toString();
    }

    /** Confirms the class loader really is withholding Curator, so the rest of the report means something. */
    private static boolean curatorIsReallyAbsent() {
        try {
            Class.forName("org.apache.curator.framework.CuratorFramework", false,
                    WithoutCuratorProbe.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException | NoClassDefFoundError absent) {
            return true;
        }
    }

    private static Loader findZooKeeperLoader() {
        for (Loader loader : ServiceLoader.load(Loader.class, WithoutCuratorProbe.class.getClassLoader()))
            if (loader.getClass().getName().equals(ZooKeeperLoader.class.getName()))
                return loader;
        return null;
    }
}

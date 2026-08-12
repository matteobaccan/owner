/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

import org.junit.Test;

import java.util.concurrent.Callable;

import static org.junit.Assert.assertTrue;

/**
 * What happens on a class path that has <code>owner-extras</code> and not Typesafe Config - which is the
 * ordinary case, that dependency being optional, and the one the suite can never be run in.
 *
 * <p>
 * {@link HoconLoader} is discovered by {@link java.util.ServiceLoader} like every other loader, so it is
 * <b>instantiated in every application that has this artifact</b>, whether or not it will ever read a
 * <code>.conf</code>. This is the proof that costs nothing: the loader is created, answers about sources,
 * offers its default spec, and only reading a document fails - naming the artifact to add.
 * </p>
 *
 * @author Matteo Baccan
 * @see ZooKeeperDiscoveryWithoutCuratorTest
 */
public class HoconDiscoveryWithoutTypesafeTest {

    @Test
    @SuppressWarnings("unchecked")
    public void theLoaderIsDiscoveredAndSaysWhatIsMissingOnlyWhenItIsAsked() throws Exception {
        String report;
        try (WithholdingClassLoader loader = new WithholdingClassLoader("com.typesafe")) {
            Class<?> probe = loader.loadClass(WithoutTypesafeProbe.class.getName());
            report = ((Callable<String>) probe.getDeclaredConstructor().newInstance()).call();
        }

        assertTrue("the class loader is not withholding Typesafe Config, so this proves nothing:\n" + report,
                report.contains("typesafe absent: true"));

        // a failure here would break every configuration in the application, not only a .conf one
        assertTrue("HoconLoader was not discovered without Typesafe Config:\n" + report,
                report.contains("discovered: true"));

        assertTrue(report, report.contains("accepts conf: true"));
        assertTrue(report, report.contains("accepts properties: false"));
        assertTrue(report, report.contains("default spec: classpath:MyConfig.conf"));

        assertTrue("reading a .conf did not report the missing dependency:\n" + report,
                report.contains("reading: java.lang.UnsupportedOperationException"));
        assertTrue("the failure does not say what to add:\n" + report,
                report.contains("com.typesafe:config"));
    }
}

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
 * What happens on a class path that has <code>owner-extras</code> and not
 * <a href="https://curator.apache.org">Apache Curator</a> - which is the ordinary case, Curator being an
 * optional dependency, and the one the suite can never be run in because the tests need a real ZooKeeper.
 *
 * <p>
 * Since 2.0.0 {@link ZooKeeperLoader} is discovered by {@link java.util.ServiceLoader} like every other
 * loader, which means it is <b>instantiated in every application that has this artifact</b>, whether or not
 * it will ever read a <code>zookeeper:</code> source. That only works because nothing in it names Curator -
 * see {@link ZooKeeperLoaderIsolationTest} - and this test is the other half of that claim: the isolation
 * test proves the class does not mention Curator, this one proves the consequence.
 * </p>
 *
 * <p>
 * The absence is simulated by {@link WithholdingClassLoader}, shared with the HOCON loader, which faces the
 * same problem with the same shape.
 * </p>
 *
 * @author Matteo Baccan
 */
public class ZooKeeperDiscoveryWithoutCuratorTest {

    @Test
    @SuppressWarnings("unchecked")
    public void theLoaderIsDiscoveredAndSaysWhatIsMissingOnlyWhenItIsAsked() throws Exception {
        String report;
        try (WithholdingClassLoader loader = new WithholdingClassLoader("org.apache.curator")) {
            Class<?> probe = loader.loadClass(WithoutCuratorProbe.class.getName());
            report = ((Callable<String>) probe.getDeclaredConstructor().newInstance()).call();
        }

        assertTrue("the class loader is not withholding Curator, so this test proves nothing:\n" + report,
                report.contains("curator absent: true"));

        // discovery has to survive Curator being absent: ServiceLoader instantiates what it finds, and a
        // failure here would break every configuration in the application, not only a zookeeper: one
        assertTrue("ZooKeeperLoader was not discovered without Curator:\n" + report,
                report.contains("discovered: true"));

        // and so does answering about a source, which every loader is asked about every source
        assertTrue(report, report.contains("accepts zookeeper: true"));
        assertTrue(report, report.contains("accepts file: false"));

        // only reading one fails, and it fails by saying what to add rather than naming a missing class
        assertTrue("reading a zookeeper: source did not report the missing dependency:\n" + report,
                report.contains("reading: java.lang.UnsupportedOperationException"));
        assertTrue("the failure does not say what to add:\n" + report,
                report.contains("curator-framework"));
    }
}

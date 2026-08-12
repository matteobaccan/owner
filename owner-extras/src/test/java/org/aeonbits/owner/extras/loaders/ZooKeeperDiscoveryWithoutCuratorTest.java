/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * The absence is simulated with a class loader that loads <code>org.aeonbits.owner</code> itself rather than
 * delegating - so that the classes under test are the ones it defines - and refuses
 * <code>org.apache.curator</code> outright. Everything else, the JDK included, goes to the parent, so
 * {@link Callable} is the same class on both sides and the probe can answer through it.
 * </p>
 *
 * @author Matteo Baccan
 */
public class ZooKeeperDiscoveryWithoutCuratorTest {

    @Test
    @SuppressWarnings("unchecked")
    public void theLoaderIsDiscoveredAndSaysWhatIsMissingOnlyWhenItIsAsked() throws Exception {
        String report;
        try (CuratorlessClassLoader loader = new CuratorlessClassLoader()) {
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

    /**
     * Child-first for <code>org.aeonbits.owner</code>, closed for <code>org.apache.curator</code>, parent for
     * everything else.
     */
    private static final class CuratorlessClassLoader extends ClassLoader implements AutoCloseable {

        private CuratorlessClassLoader() {
            super(ZooKeeperDiscoveryWithoutCuratorTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.apache.curator"))
                throw new ClassNotFoundException(name + " (withheld by " + getClass().getSimpleName() + ")");
            if (!name.startsWith("org.aeonbits.owner"))
                return super.loadClass(name, resolve);

            synchronized (getClassLoadingLock(name)) {
                Class<?> already = findLoadedClass(name);
                if (already != null)
                    return already;
                byte[] bytes = bytecodeOf(name);
                if (bytes == null)
                    return super.loadClass(name, resolve);
                Class<?> defined = defineClass(name, bytes, 0, bytes.length);
                if (resolve)
                    resolveClass(defined);
                return defined;
            }
        }

        private byte[] bytecodeOf(String name) {
            String resource = name.replace('.', '/') + ".class";
            try (InputStream in = getParent().getResourceAsStream(resource)) {
                if (in == null)
                    return null;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int read; (read = in.read(buffer)) != -1; )
                    bytes.write(buffer, 0, read);
                return bytes.toByteArray();
            } catch (IOException cannotRead) {
                return null;
            }
        }

        @Override
        public void close() {
            // nothing to release: the bytes are read and closed one class at a time
        }
    }
}

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
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ZooKeeperLoader} must not name Apache Curator anywhere, so that it can be discovered and
 * instantiated on a class path where Curator - an optional dependency - is absent. Everything that names it
 * lives in {@link ZooKeeperReader}, which is reached only when a <code>zookeeper:</code> source is read.
 *
 * <p>
 * The check reads the compiled class rather than running it: a class file names every type it refers to, in
 * a method body, a signature or a field, in its constant pool as plain UTF-8.
 * </p>
 *
 * <p>
 * <b>This is deliberately stricter than the JVM.</b> Measured, not assumed: a <code>CuratorFramework</code>
 * local left null does not stop the class being loaded, since the constant pool entry is never resolved.
 * What breaks discovery is a mention that gets <em>executed</em> - a static initialiser, a constructor, a
 * call - and {@link ZooKeeperDiscoveryWithoutCuratorTest} is what catches that. Refusing every mention here
 * is the cheaper guard and the one that holds over time, because a mention added today is what becomes an
 * execution next year. The suite runs with Curator present, so without these two nothing in the project
 * notices either problem.
 * </p>
 *
 * @author Matteo Baccan
 */
public class ZooKeeperLoaderIsolationTest {

    private static final String CURATOR = "org/apache/curator";

    @Test
    public void theLoaderDoesNotNameCurator() throws IOException {
        assertFalse("ZooKeeperLoader names " + CURATOR + " in its constant pool, so ServiceLoader could not "
                        + "instantiate it without Curator on the class path. Whatever was added belongs in "
                        + "ZooKeeperReader - see the javadoc there.",
                constantPoolOf(ZooKeeperLoader.class).contains(CURATOR));
    }

    /**
     * The other half of the check: if the reader stopped naming Curator, the test above would keep passing
     * for the wrong reason - the two classes having been merged the other way round, or the search string
     * having gone stale against a repackaged Curator.
     */
    @Test
    public void theReaderDoesNameCurator() throws IOException {
        assertTrue(constantPoolOf(ZooKeeperReader.class).contains(CURATOR));
    }

    /** The compiled class as text, which is enough to search for a type name. */
    private static String constantPoolOf(Class<?> type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull("cannot read " + resource, in);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) != -1; )
                bytes.write(buffer, 0, read);
            return new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
        }
    }
}

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
 * {@link HoconLoader} must not name Typesafe Config anywhere, so that it can be discovered and instantiated
 * on a class path where that optional dependency is absent. Everything naming it lives in
 * {@link HoconReader}, which is reached only when a <code>.conf</code> source is read.
 *
 * <p>
 * The same guard as {@code ZooKeeperLoaderIsolationTest} and for the same reason, which is why they are two
 * tests rather than one parameterized over both: each names the class it protects, so a failure says which
 * loader broke and where the code belongs. It reads the compiled class rather than running it - a class file
 * names every type it refers to in its constant pool as plain UTF-8 - and is deliberately stricter than the
 * JVM, which only fails on a mention that gets executed. A mention added today is what becomes an execution
 * next year. {@link HoconDiscoveryWithoutTypesafeTest} catches the executed kind.
 * </p>
 *
 * @author Matteo Baccan
 */
public class HoconLoaderIsolationTest {

    private static final String TYPESAFE = "com/typesafe/config";

    @Test
    public void theLoaderDoesNotNameTypesafeConfig() throws IOException {
        assertFalse("HoconLoader names " + TYPESAFE + " in its constant pool, so ServiceLoader could not "
                        + "instantiate it without Typesafe Config on the class path. Whatever was added "
                        + "belongs in HoconReader - see the javadoc there.",
                constantPoolOf(HoconLoader.class).contains(TYPESAFE));
    }

    /** The converse, so the guard cannot pass because the two classes were merged the other way round. */
    @Test
    public void theReaderDoesNameTypesafeConfig() throws IOException {
        assertTrue(constantPoolOf(HoconReader.class).contains(TYPESAFE));
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

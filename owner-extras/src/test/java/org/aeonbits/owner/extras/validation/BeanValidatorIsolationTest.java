/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.validation;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link BeanValidator} must not name either validation API anywhere, so that it can be discovered and
 * instantiated on a class path where both optional dependencies are absent - which is nearly every class
 * path that has this artifact. Everything naming them lives in {@link JakartaBeanValidation} and
 * {@link JavaxBeanValidation}, which are reached only when there is a constrained property to check.
 *
 * <p>
 * The same guard as {@code HoconLoaderIsolationTest} and {@code ZooKeeperLoaderIsolationTest}, and for the
 * same reason. It reads the compiled class rather than running it - a class file names every type it refers
 * to in its constant pool as plain UTF-8 - and is deliberately stricter than the JVM, which only fails on a
 * mention that gets executed. A mention added today is what becomes an execution next year.
 * {@link BeanValidatorDiscoveryTest} catches the executed kind.
 * </p>
 *
 * @author Matteo Baccan
 */
public class BeanValidatorIsolationTest {

    private static final String JAKARTA = "jakarta/validation";
    private static final String JAVAX = "javax/validation";

    @Test
    public void theValidatorNamesNeitherApi() throws IOException {
        String constantPool = constantPoolOf(BeanValidator.class);

        assertFalse("BeanValidator names " + JAKARTA + " in its constant pool, so ServiceLoader could not "
                        + "instantiate it without jakarta.validation on the class path. Whatever was added "
                        + "belongs in JakartaBeanValidation - see the javadoc there.",
                constantPool.contains(JAKARTA));
        assertFalse("BeanValidator names " + JAVAX + " in its constant pool; it belongs in "
                        + "JavaxBeanValidation.", constantPool.contains(JAVAX));
    }

    /** The converse, so the guard cannot pass because the classes were merged the other way round. */
    @Test
    public void eachHalfNamesItsOwnApiAndNotTheOther() throws IOException {
        String jakarta = constantPoolOf(JakartaBeanValidation.class);
        String javax = constantPoolOf(JavaxBeanValidation.class);

        assertTrue(jakarta.contains(JAKARTA));
        assertFalse("the jakarta half must not drag in the javax API", jakarta.contains(JAVAX));
        assertTrue(javax.contains(JAVAX));
        assertFalse("the javax half must not drag in the jakarta API", javax.contains(JAKARTA));
    }

    /** Shared between the two halves precisely because it can be: it works on the path as text. */
    @Test
    public void thePathHelperNamesNoApiEither() throws IOException {
        String constantPool = constantPoolOf(ViolationPath.class);

        assertFalse(constantPool.contains(JAKARTA));
        assertFalse(constantPool.contains(JAVAX));
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

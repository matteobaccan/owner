/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A class loader that pretends an optional dependency is not there.
 *
 * <p>
 * Everything in this artifact that adapts a third-party library adapts one that most users of
 * <code>owner-extras</code> will not have - the two loaders in this package, and the Bean Validation support
 * in <code>org.aeonbits.owner.extras.validation</code> - and every one of them is discovered by
 * {@link java.util.ServiceLoader} regardless. So each needs to be tried on a class path without that
 * library, which is a class path the suite itself can never run on, needing all of them to test anything at
 * all. It is public for that reason: the validation tests are in another package and want the same
 * arrangement rather than a second copy of it.
 * </p>
 *
 * <p>
 * It is <b>child-first for <code>org.aeonbits.owner</code></b>, so the classes under test are the ones it
 * defines and their references resolve here rather than in the parent; <b>closed for the withheld
 * prefix</b>; and parent for everything else, so the JDK is one set of classes across both sides and a probe
 * can answer through {@link java.util.concurrent.Callable}.
 * </p>
 *
 * @author Matteo Baccan
 */
public final class WithholdingClassLoader extends ClassLoader implements AutoCloseable {

    private final String[] withheld;

    /**
     * @param withheld the package prefixes to refuse, as in <code>org.apache.curator</code>. More than one
     *                 because the same feature can rest on more than one optional dependency: Bean
     *                 Validation comes under two names, and a class path holding neither is the case worth
     *                 testing.
     */
    public WithholdingClassLoader(String... withheld) {
        super(WithholdingClassLoader.class.getClassLoader());
        this.withheld = withheld.clone();
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (isWithheld(name))
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

    private boolean isWithheld(String name) {
        for (String prefix : withheld)
            if (name.startsWith(prefix))
                return true;
        return false;
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

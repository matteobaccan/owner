/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;

/**
 * A class loader that reloads the org.aeonbits.owner classes from their class files, so that
 * their static state can be re-initialized in a controlled environment without affecting the
 * copy of the classes used by the rest of the test suite.
 * <p>
 * The environment can be altered in two ways:
 * <ul>
 *     <li>classes listed as <em>hidden</em> are reported as non-existent, simulating a runtime
 *     where they are not on the classpath (e.g. {@code java.util.Base64} on Java 6/7);</li>
 *     <li>classes registered via {@link #addClass(String, byte[])} are defined from the given
 *     bytecode, simulating a runtime where they are available (e.g. {@code Java8SupportImpl},
 *     which lives in the owner-java8 module).</li>
 * </ul>
 *
 * @author Matteo Baccan
 */
public class IsolatedClassLoader extends ClassLoader {

    private final Set<String> hiddenClasses;
    private final Map<String, byte[]> extraClasses = new HashMap<String, byte[]>();

    public IsolatedClassLoader(ClassLoader parent, String... hiddenClasses) {
        super(parent);
        this.hiddenClasses = new HashSet<String>(asList(hiddenClasses));
    }

    /**
     * Registers a class, unavailable on the parent classpath, to be defined by this loader
     * from the given bytecode.
     */
    public void addClass(String name, byte[] bytecode) {
        extraClasses.put(name, bytecode);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (hiddenClasses.contains(name))
            throw new ClassNotFoundException(name + " is hidden by " + getClass().getSimpleName());
        if (name.startsWith("org.aeonbits.owner.")) {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null)
                    loaded = defineIsolatedClass(name);
                if (resolve)
                    resolveClass(loaded);
                return loaded;
            }
        }
        return super.loadClass(name, resolve);
    }

    private Class<?> defineIsolatedClass(String name) throws ClassNotFoundException {
        byte[] bytes = extraClasses.containsKey(name) ? extraClasses.get(name) : readClassResource(name);
        // a non-null ProtectionDomain gives the class a code location, so JaCoCo instruments it
        // and the coverage recorded in this classloader is merged into the main report
        return defineClass(name, bytes, 0, bytes.length, IsolatedClassLoader.class.getProtectionDomain());
    }

    private byte[] readClassResource(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        InputStream in = getParent().getResourceAsStream(path);
        if (in == null)
            throw new ClassNotFoundException(name);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1)
                out.write(buffer, 0, count);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }
}

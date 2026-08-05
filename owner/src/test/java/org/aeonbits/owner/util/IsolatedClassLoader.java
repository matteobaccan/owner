/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;

/**
 * A class loader that reloads the org.aeonbits.owner classes from their class files, so that
 * their static state can be re-initialized in a controlled environment without affecting the
 * copy of the classes used by the rest of the test suite. Classes listed as <em>hidden</em> are
 * reported as non-existent, simulating a runtime where they are not on the classpath (e.g.
 * {@code java.beans.PropertyEditorManager} on a JRE without the java.desktop module).
 *
 * @author Matteo Baccan
 */
public class IsolatedClassLoader extends ClassLoader {

    private final Set<String> hiddenClasses;

    public IsolatedClassLoader(ClassLoader parent, String... hiddenClasses) {
        super(parent);
        this.hiddenClasses = new HashSet<>(asList(hiddenClasses));
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
        byte[] bytes = readClassResource(name);
        // a non-null ProtectionDomain gives the class a code location, so JaCoCo instruments it
        // and the coverage recorded in this classloader is merged into the main report
        return defineClass(name, bytes, 0, bytes.length, IsolatedClassLoader.class.getProtectionDomain());
    }

    private byte[] readClassResource(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        try (InputStream in = getParent().getResourceAsStream(path)) {
            if (in == null)
                throw new ClassNotFoundException(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1)
                out.write(buffer, 0, count);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}

/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.extras.loaders.ZooKeeperLoader;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that this artifact and the core one do not share a package.
 * <p>
 * The module system rejects a package that lives in two modules, so a single class put back under a package
 * the core already owns is enough to keep the two jars from ever sitting together on the module path — and
 * nothing else in the build would notice, since OSGi and the class path both tolerate it. That is how
 * <code>ZooKeeperLoader</code> came to live in <code>org.aeonbits.owner.loaders</code> for eleven years, and
 * this test is here so it cannot happen again quietly.
 * </p>
 *
 * @author Matteo Baccan
 */
public class SplitPackageTest {

    @Test
    public void theExtrasArtifactSharesNoPackageWithTheCore() throws IOException, URISyntaxException {
        Set<String> core = packagesOf(Config.class);
        Set<String> extras = packagesOf(ZooKeeperLoader.class);

        assertFalse("no package found in the core artifact", core.isEmpty());
        assertFalse("no package found in the extras artifact", extras.isEmpty());

        Set<String> shared = new TreeSet<>(extras);
        shared.retainAll(core);

        assertEquals("these packages live in both artifacts, which keeps them off the module path: " + shared,
                new TreeSet<String>(), shared);
    }

    /**
     * The specific case this test was written for, stated on its own so that a failure says what happened
     * rather than only that something overlaps.
     * <p>
     * Asserting that the core does own <code>org.aeonbits.owner.loaders</code> is what keeps the check above
     * from passing for the wrong reason: were the enumeration to come back with nothing useful, the two sets
     * would not overlap either.
     * </p>
     */
    @Test
    public void theZooKeeperLoaderLeftThePackageOwnedByTheCore() throws IOException, URISyntaxException {
        assertEquals("org.aeonbits.owner.extras.loaders", ZooKeeperLoader.class.getPackage().getName());

        assertTrue("the core artifact should own org.aeonbits.owner.loaders",
                packagesOf(Config.class).contains("org.aeonbits.owner.loaders"));
        assertFalse("the extras artifact should not put anything in it",
                packagesOf(ZooKeeperLoader.class).contains("org.aeonbits.owner.loaders"));
    }

    /**
     * Returns the packages of the artifact the given class comes from, be it a directory of classes as during
     * the build or a jar as when the dependency is resolved from the repository.
     */
    private static Set<String> packagesOf(Class<?> clazz) throws IOException, URISyntaxException {
        File location = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        Set<String> packages = new LinkedHashSet<>();
        if (location.isDirectory())
            collectFromDirectory(location, location, packages);
        else
            collectFromJar(location, packages);
        return packages;
    }

    private static void collectFromDirectory(File root, File current, Set<String> packages) {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectFromDirectory(root, child, packages);
            } else if (child.getName().endsWith(".class")) {
                String relative = root.toURI().relativize(child.toURI()).getPath();
                packages.add(packageOfEntry(relative));
            }
        }
    }

    private static void collectFromJar(File jarFile, Set<String> packages) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class"))
                    packages.add(packageOfEntry(entry.getName()));
            }
        }
    }

    private static String packageOfEntry(String entryName) {
        int lastSlash = entryName.lastIndexOf('/');
        return lastSlash < 0 ? "" : entryName.substring(0, lastSlash).replace('/', '.');
    }
}

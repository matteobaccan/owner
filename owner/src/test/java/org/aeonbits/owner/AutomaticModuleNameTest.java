/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Verifies that the artifact declares the name it takes when used as an automatic module on the module path.
 * <p>
 * The name is written by the bundle plugin into the manifest of <code>target/classes</code> during
 * <code>process-classes</code>, so it is already in place when the tests run and the jar later inherits it.
 * Should the manifest not have been generated at all — which happens when the test is run from an IDE that
 * compiled the sources on its own — the test is skipped rather than failed: there is no artifact to make a
 * statement about. When the manifest is there, the entry has to be there too.
 * </p>
 *
 * @author Matteo Baccan
 */
public class AutomaticModuleNameTest {

    private static final String AUTOMATIC_MODULE_NAME = "Automatic-Module-Name";
    private static final String EXPECTED_MODULE_NAME = "org.aeonbits.owner";

    @Test
    public void theArtifactDeclaresItsAutomaticModuleName() throws Exception {
        Manifest manifest = manifestOf(Config.class);
        assumeTrue("the manifest has not been generated yet: run 'mvn process-classes'", manifest != null);

        String moduleName = manifest.getMainAttributes().getValue(AUTOMATIC_MODULE_NAME);
        assertNotNull(AUTOMATIC_MODULE_NAME + " is missing from the manifest", moduleName);
        assertEquals(EXPECTED_MODULE_NAME, moduleName);
    }

    /**
     * A module name is a sequence of Java identifiers separated by dots. Getting this wrong makes the jar
     * unusable on the module path, and the error surfaces in the build of whoever depends on us rather than
     * in ours, so it is worth stating here.
     */
    @Test
    public void theAutomaticModuleNameIsAValidModuleName() {
        for (String segment : EXPECTED_MODULE_NAME.split("\\.", -1)) {
            assertTrue("empty segment in '" + EXPECTED_MODULE_NAME + "'", !segment.isEmpty());
            assertTrue("'" + segment + "' does not start a Java identifier",
                    Character.isJavaIdentifierStart(segment.charAt(0)));
            for (char c : segment.toCharArray())
                assertTrue("'" + c + "' is not valid in a Java identifier", Character.isJavaIdentifierPart(c));
        }
    }

    /**
     * Reads the manifest of the artifact the given class comes from, be it a directory of classes as during
     * the build, or a jar as when the test is run against the packaged artifact.
     *
     * @return the manifest, or <code>null</code> when there is none to read.
     */
    private static Manifest manifestOf(Class<?> clazz) throws IOException, URISyntaxException {
        File location = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (location.isDirectory()) {
            File manifestFile = new File(location, "META-INF/MANIFEST.MF");
            if (!manifestFile.isFile()) return null;
            try (InputStream in = new FileInputStream(manifestFile)) {
                return new Manifest(in);
            }
        }
        try (JarFile jar = new JarFile(location)) {
            return jar.getManifest();
        }
    }
}

/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertTrue;

/**
 * Every published package carries a <code>package-info.java</code>, and it says something.
 * <p>
 * The overview page of the API documentation is a list of packages and one sentence each, and it is the
 * first page anybody arriving at the Javadoc reads. A package without a <code>package-info.java</code> is
 * a blank line on it - nine of the fifteen were blank on 2026-08-17 - and nothing anywhere complains:
 * javadoc has no warning for it, because a package that describes itself nowhere is not an error, only a
 * page that says nothing.
 * </p>
 * <p>
 * The three published modules are read, main sources only: a test package explains itself by its tests.
 * </p>
 *
 * @author Matteo Baccan
 */
public class PackagesAreDescribedTest {

    /**
     * Relative to this module's directory, which is where surefire runs. The list is explicit rather than
     * discovered, so that a module added to the build and forgotten here is a line to write rather than a
     * silence.
     */
    private static final String[] PUBLISHED_SOURCES = {
            "src/main/java", "../owner-extras/src/main/java", "../owner-formats/src/main/java"};

    @Test
    public void everyPackageWithClassesInItDescribesItself() throws IOException {
        List<String> undescribed = new ArrayList<>();

        for (String tree : PUBLISHED_SOURCES) {
            Path root = Paths.get(tree);
            assertTrue(root.toAbsolutePath() + " is not there: this test is looking in the wrong place",
                    Files.isDirectory(root));

            try (Stream<Path> walk = Files.walk(root)) {
                for (Path directory : (Iterable<Path>) walk.filter(Files::isDirectory)::iterator)
                    if (holdsClasses(directory) && !describesItself(directory))
                        undescribed.add(directory.toString());
            }
        }

        assertTrue("these packages have no package-info.java, so the Javadoc overview page has a blank "
                + "line where their description should be:\n" + String.join("\n", undescribed),
                undescribed.isEmpty());
    }

    private static boolean holdsClasses(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith(".java")
                    && !p.getFileName().toString().equals("package-info.java"));
        }
    }

    /**
     * A <code>package-info.java</code> counts when it carries a javadoc comment with something in it: the
     * file exists to hold a description, and an empty one is the blank line this test is about.
     */
    private static boolean describesItself(Path directory) throws IOException {
        Path packageInfo = directory.resolve("package-info.java");
        if (!Files.isRegularFile(packageInfo))
            return false;

        String text = new String(Files.readAllBytes(packageInfo), UTF_8);
        int javadoc = text.indexOf("/**");
        if (javadoc < 0)
            return false;

        int end = text.indexOf("*/", javadoc);
        String described = text.substring(javadoc + 3, end < 0 ? text.length() : end)
                .replace("*", "").trim();
        return !described.isEmpty();
    }
}

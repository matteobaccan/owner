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
 * A javadoc comment documents whatever is declared under it, so <b>two of them in a row means the first
 * one documents nothing</b>: it stays in the file, reads as though it were published, and is not.
 * <p>
 * This is not a hypothetical. It had happened four times before this test was written, always the same
 * way - a new documented member inserted immediately below an existing comment rather than below the
 * member that comment belonged to. {@code @Separator} lost its documentation the day
 * {@code @Description} arrived; {@link Accessible#storeToXML(java.io.OutputStream, String)} lost its the
 * day {@code save(File)} did; the note explaining why OAEP is given its parameters explicitly ended up
 * over an unrelated method in {@code RsaHandler}; and a test carried two descriptions of itself.
 * </p>
 * <p>
 * <b>Neither the compiler nor javadoc says anything about it.</b> Javadoc warns about a member with no
 * comment, and in every one of those four cases the member below had one of its own - it was the
 * <i>orphan above</i> that was lost, and nothing was missing to complain about. CodeQL caught two of
 * them and only by accident, through the {@code @param} names of the stranded block not matching the
 * method it had drifted onto. Reading the source catches all of them, which is the argument for doing it
 * here rather than remembering.
 * </p>
 * <p>
 * A block comment that is not javadoc - the licence header every file opens with - is not a candidate
 * and is not flagged: only a comment opened with {@code /**} promises to document something.
 * </p>
 *
 * @author Matteo Baccan
 */
public class JavadocIsAttachedTest {

    @Test
    public void noJavadocCommentIsLeftDocumentingAnother() throws IOException {
        List<String> orphans = new ArrayList<>();
        for (Path source : sources())
            orphans.addAll(orphansIn(source));

        assertTrue("a javadoc comment is followed by another, so the first documents nothing:\n"
                + String.join("\n", orphans), orphans.isEmpty());
    }

    /**
     * Every java file of the three modules, main and test. The paths are relative to this module's
     * directory, which is where surefire runs, and the assertion below says so out loud rather than
     * letting a wrong one turn this test into one that passes because it looked at nothing.
     */
    private static List<Path> sources() throws IOException {
        List<Path> found = new ArrayList<>();
        for (String tree : new String[] {
                "src/main/java", "src/test/java",
                "../owner-extras/src/main/java", "../owner-extras/src/test/java",
                "../owner-formats/src/main/java", "../owner-formats/src/test/java"}) {
            Path root = Paths.get(tree);
            assertTrue(root.toAbsolutePath() + " is not there: this test is looking in the wrong place",
                    Files.isDirectory(root));
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(found::add);
            }
        }
        return found;
    }

    private static List<String> orphansIn(Path source) throws IOException {
        String text = new String(Files.readAllBytes(source), UTF_8);
        List<String> orphans = new ArrayList<>();

        // the search resumes past the closing */ of each comment, never inside one: a piece of sample
        // code quoting a comment opener would otherwise be read as a comment of its own
        for (int open = text.indexOf("/**"); open >= 0; open = text.indexOf("/**", open + 3)) {
            int close = text.indexOf("*/", open);
            if (close < 0)
                break;

            int next = close + 2;
            while (next < text.length() && Character.isWhitespace(text.charAt(next)))
                next++;

            if (text.startsWith("/**", next))
                orphans.add(source + ":" + lineOf(text, open) + " is followed by another comment, at line "
                        + lineOf(text, next));

            open = close;
        }
        return orphans;
    }

    private static int lineOf(String text, int at) {
        int line = 1;
        for (int i = 0; i < at; i++)
            if (text.charAt(i) == '\n')
                line++;
        return line;
    }
}

/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The command line half of <a href="https://github.com/matteobaccan/owner/issues/3">#3</a>: writing the
 * properties file of an interface without running the application, and without the interface having to
 * extend {@link Accessible} to be allowed to.
 *
 * @author Matteo Baccan
 */
public class TemplateToolTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;

    @Before
    public void collectTheOutput() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
    }

    private int run(String... args) {
        return TemplateTool.run(args, new PrintStream(out, true), new PrintStream(err, true));
    }

    private String written() throws UnsupportedEncodingException {
        return out.toString("ISO-8859-1");
    }

    private String complained() throws UnsupportedEncodingException {
        return err.toString("ISO-8859-1");
    }

    /**
     * <b>Not</b> {@link Accessible}, which is the point: this is the shape of an interface that has never
     * had a file, and until now nothing could write one for it.
     */
    @Config.Description("Everything this application needs in order to start.")
    public interface NeverHadAFile extends Config {

        @Config.Description("Where the service listens.")
        @Config.DefaultValue("8080")
        int port();

        @Config.DefaultValue("30")
        int seconds();
    }

    @Test
    public void aTemplateIsWrittenToStandardOutputWithTheDescriptionsFromTheCode() throws Exception {
        assertEquals(0, run(NeverHadAFile.class.getName()));

        assertEquals(""
                + "# Everything this application needs in order to start.\n"
                + "\n"
                + "# Where the service listens.\n"
                + "port = 8080\n"
                + "\n"
                + "seconds = 30\n", written());
        assertEquals("", complained());
    }

    @Test
    public void withIntoTheFileGoesWhereTheConventionLooksForIt() throws Exception {
        assertEquals(0, run("--into", folder.getRoot().getAbsolutePath(), NeverHadAFile.class.getName()));

        File expected = new File(folder.getRoot(),
                NeverHadAFile.class.getName().replace('.', '/') + ".properties");
        assertTrue(expected + " is where a configuration with no @Sources would be looked for",
                expected.isFile());
        assertTrue(contentsOf(expected), contentsOf(expected).contains("port = 8080"));
        assertEquals("nothing goes to stdout when a file was asked for", "", written());
    }

    /** The whole reason it is the writer of {@code save(File)} and not a new one. */
    @Test
    public void asecondRunKeepsWhatYouEditedInBetween() throws Exception {
        File root = folder.getRoot();
        run("--into", root.getAbsolutePath(), NeverHadAFile.class.getName());

        File file = new File(root, NeverHadAFile.class.getName().replace('.', '/') + ".properties");
        Files.write(file.toPath(), ("# a note of my own\n"
                + "\n"
                + "seconds = 90\n"
                + "port = 9090\n"
                + "somebody.elses.key = kept\n").getBytes(ISO_8859_1));

        assertEquals(0, run("--into", root.getAbsolutePath(), NeverHadAFile.class.getName()));

        String contents = contentsOf(file);
        assertTrue(contents, contents.contains("# a note of my own"));
        assertTrue("the order of the file is the file's", contents.indexOf("seconds") < contents.indexOf("port ="));
        assertTrue("a key of somebody else's is not ours to drop", contents.contains("somebody.elses.key = kept"));
        assertTrue("and the description comes back from the code", contents.contains("# Where the service listens."));
    }

    @Config.Sources("classpath:org/aeonbits/owner/first.properties")
    public interface WithASourceOfItsOwn extends Config {

        @Config.DefaultValue("from the code")
        String foo();
    }

    /**
     * <b>No source is read.</b> first.properties says <code>foo=first</code> and is right there on the
     * classpath: a tool that loaded it would be writing the machine it ran on into somebody's template.
     */
    @Test
    public void theTemplateIsWhatTheCodeSaysAndNotWhatTheSourcesHold() throws Exception {
        assertEquals(0, run(WithASourceOfItsOwn.class.getName()));

        assertTrue(written(), written().contains("foo = from the code"));
        assertFalse(written(), written().contains("first"));
    }

    public interface NotAConfigAtAll {
    }

    @Test
    public void anInterfaceThatIsNotAConfigurationIsRefusedByName() throws Exception {
        assertEquals(1, run(NotAConfigAtAll.class.getName()));

        assertTrue(complained(), complained().contains("does not extend"));
        assertEquals("", written());
    }

    @Test
    public void aClassThatIsNotThereSaysWhereToPutIt() throws Exception {
        assertEquals(1, run("com.acme.NotHere"));

        assertTrue(complained(), complained().contains("is not on the classpath"));
    }

    @Test
    public void twoConfigurationsAreTwoFilesAndStandardOutputIsOne() throws Exception {
        assertEquals(2, run(NeverHadAFile.class.getName(), WithASourceOfItsOwn.class.getName()));

        assertTrue(complained(), complained().contains("Two configurations are two files"));
        assertEquals("", written());
    }

    @Test
    public void withNoArgumentsItSaysHowItIsUsed() throws Exception {
        assertEquals(2, run());

        assertTrue(complained(), complained().contains("--into"));
        assertTrue(complained(), complained().contains("No source is read"));
    }

    /** <code>--into</code> with nothing behind it: the one misuse that reads like a typo rather than a mistake. */
    @Test
    public void intoWithoutADirectorySaysWhatIsMissing() throws Exception {
        assertEquals(2, run(NeverHadAFile.class.getName(), "--into"));

        assertTrue(complained(), complained().contains("--into needs the directory to write into."));
        assertEquals("", written());
    }

    /**
     * A class that implements {@link Config} is not what carries the keys: the annotations are read off an
     * interface, and a class reaching here means somebody named the implementation instead of the mapping.
     */
    public static class AConfigurationClass implements Config {
    }

    @Test
    public void aClassIsRefusedAndTheMessageSaysWhatToNameInstead() throws Exception {
        assertEquals(1, run(AConfigurationClass.class.getName()));

        assertTrue(complained(), complained().contains("is a class"));
        assertTrue(complained(), complained().contains("mapping interface"));
        assertEquals("", written());
    }

    /**
     * The directory cannot be made because a <b>file</b> of that name is in the way — the ordinary shape of
     * a mistyped <code>--into</code>. What matters is that the tool says which template it failed to write
     * and answers 1, rather than dying with a stack trace at the caller.
     */
    @Test
    public void aDirectoryThatCannotBeMadeIsReportedAgainstTheTemplateItBelongsTo() throws Exception {
        File inTheWay = folder.newFile("not-a-directory");

        assertEquals(1, run(NeverHadAFile.class.getName(), "--into", inTheWay.getAbsolutePath()));

        assertTrue(complained(), complained().contains("Could not write the template for "
                + NeverHadAFile.class.getName()));
        assertTrue(complained(), complained().contains("cannot create"));
        assertEquals("", written());
    }

    private static String contentsOf(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), ISO_8859_1);
    }
}

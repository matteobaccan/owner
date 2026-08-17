/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link Accessible#save(File)} — the write that keeps the file, which is the other half of
 * <a href="https://github.com/matteobaccan/owner/issues/16">#16</a> and the answer to
 * <a href="https://github.com/matteobaccan/owner/issues/3">#3</a>.
 * <p>
 * Its companion is {@link WritingTheFileBackTest}, which asserts what
 * {@link Accessible#store(OutputStream, String)} destroys. Both are kept because they are two different
 * promises, and the difference between them is the feature.
 * </p>
 * <p>
 * <b>The division under test</b>, decided in <code>WRITING.md</code>: the code owns the descriptions,
 * the file owns the arrangement. Every test below is one consequence of that sentence.
 * </p>
 *
 * @author Matteo Baccan
 */
public class SavingTheFileTest {

    private static final String LOCATION = "test.saved.file";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File file;

    @Config.Sources("file:${test.saved.file}")
    public interface AppConfig extends Mutable, Accessible {

        @Config.Description("The database we talk to. A host name or an address; the port is separate.")
        @Config.DefaultValue("localhost")
        String host();

        @Config.Description("How long to wait for a connection before giving up, in milliseconds.")
        @Config.DefaultValue("8080")
        int port();

        // deliberately undescribed: whatever the file says above it is the file's business
        @Config.DefaultValue("2")
        int retries();
    }

    @Before
    public void locateTheFile() throws IOException {
        file = folder.newFile("app.properties");
        ConfigFactory.setProperty(LOCATION, file.getAbsolutePath().replace('\\', '/'));
    }

    @After
    public void forgetTheLocation() {
        ConfigFactory.clearProperty(LOCATION);
    }

    private void given(String contents) throws IOException {
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.ISO_8859_1));
    }

    private String contents() throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
    }

    /** The whole promise in one assertion: the file comes back as it was, with one value changed. */
    @Test
    public void theFileKeepsItsOrderItsBlankLinesAndOneChangedValue() throws IOException {
        given("port = 8080\n"
                + "\n"
                + "retries = 5\n"
                + "host = localhost\n");

        AppConfig config = ConfigFactory.create(AppConfig.class);
        config.setProperty("port", "9090");
        config.save(file);

        assertEquals(""
                + "# How long to wait for a connection before giving up, in milliseconds.\n"
                + "port = 9090\n"
                + "\n"
                + "retries = 5\n"
                + "# The database we talk to. A host name or an address; the port is separate.\n"
                + "host = localhost\n", contents());
    }

    /** A key this interface has never heard of is somebody else's, and is not ours to drop. */
    @Test
    public void aKeyTheInterfaceDoesNotDeclareIsLeftExactlyAsItWas() throws IOException {
        given("host = localhost\n"
                + "spring.datasource.url = jdbc:h2:mem:test\n"
                + "logging.level.root = INFO\n");

        ConfigFactory.create(AppConfig.class).save(file);

        assertTrue(contents(), contents().contains("spring.datasource.url = jdbc:h2:mem:test"));
        assertTrue(contents(), contents().contains("logging.level.root = INFO"));
    }

    /** The comment rule, in the one case where somebody could be surprised. */
    @Test
    public void ourCommentIsReplacedAndEverybodyElsesIsKept() throws IOException {
        given("# Written by a person, above a key we describe.\n"
                + "host = localhost\n"
                + "\n"
                + "# Written by a person, above a key we do not.\n"
                + "retries = 5\n");

        ConfigFactory.create(AppConfig.class).save(file);

        assertFalse("the note above a described key is replaced by the code's",
                contents().contains("above a key we describe"));
        assertTrue("and the code's is what stands there now",
                contents().contains("# The database we talk to."));
        assertTrue("the note above an undescribed key is untouched",
                contents().contains("# Written by a person, above a key we do not."));
    }

    /**
     * <b>The banner convention</b>, which is the documented answer to "how do I keep a comment of my
     * own": put it above a blank line. The replaced block is the contiguous one touching the key, so a
     * blank line ends it — and that is the whole mechanism, which is to say there is none.
     * <p>
     * This exists because a marker on generated lines was considered and rejected: it would be a second
     * syntax to explain and a branch in the writer, for something one rule already does. If the rule ever
     * stops holding, this test is what fails.
     * </p>
     */
    @Test
    public void aNoteAboveABlankLineIsKeptForeverAndThatIsTheWholeConvention() throws IOException {
        given("# --------------------------------------\n"
                + "# checkout service - staging\n"
                + "# the box moved to Frankfurt in March\n"
                + "# --------------------------------------\n"
                + "\n"
                + "host = localhost\n");

        AppConfig config = ConfigFactory.create(AppConfig.class);
        config.save(file);
        config.save(file);

        assertTrue("the banner survives, twice over", contents().startsWith(
                "# --------------------------------------\n"
                + "# checkout service - staging\n"
                + "# the box moved to Frankfurt in March\n"
                + "# --------------------------------------\n"
                + "\n"
                + "# The database we talk to. A host name or an address; the port is separate.\n"
                + "host = localhost\n"));
    }

    /**
     * And the other half of the same rule, stated so nobody discovers it: a note touching a described
     * key <b>is</b> lost. It is the one case the convention exists to steer people away from.
     */
    @Test
    public void aNoteTouchingADescribedKeyIsLostAndThatIsTheDocumentedTrade() throws IOException {
        given("# bumped from 2000 after the incident on the 3rd\n"
                + "port = 9000\n");

        ConfigFactory.create(AppConfig.class).save(file);

        assertFalse(contents(), contents().contains("the incident on the 3rd"));
        assertTrue(contents(), contents().contains("# How long to wait for a connection"));
        assertTrue("the value it annotated is untouched", contents().contains("port = 9000"));
    }

    /** A key the file does not have is appended rather than inserted, so the diff is an addition. */
    @Test
    public void newKeysGoAtTheEndAlphabeticallyAmongThemselves() throws IOException {
        given("host = written-by-hand\n");

        ConfigFactory.create(AppConfig.class).save(file);

        String written = contents();
        assertTrue(written, written.startsWith("# The database we talk to."));
        assertTrue("host keeps its place and its value", written.contains("host = written-by-hand"));
        assertTrue("port before retries", written.indexOf("port = ") < written.indexOf("retries = "));
        assertTrue("and the values are the defaults", written.contains("port = 8080"));
        assertTrue(written.contains("retries = 2"));
    }

    /** No file yet: the same code path generates a template, which is #3 with no second feature. */
    @Test
    public void withNoFileToKeepItGeneratesOneAlphabetically() throws IOException {
        File fresh = new File(folder.getRoot(), "generated.properties");
        assertFalse(fresh.exists());

        ConfigFactory.create(AppConfig.class).save(fresh);

        assertEquals(""
                + "# The database we talk to. A host name or an address; the port is separate.\n"
                + "host = localhost\n"
                + "\n"
                + "# How long to wait for a connection before giving up, in milliseconds.\n"
                + "port = 8080\n"
                + "\n"
                + "retries = 2\n",
                new String(Files.readAllBytes(fresh.toPath()), StandardCharsets.ISO_8859_1));
    }

    /** A property removed through {@link Mutable} takes its own comment with it, and only its own. */
    @Test
    public void aRemovedKeyTakesTheCommentWeWroteForIt() throws IOException {
        given("# The database we talk to. A host name or an address; the port is separate.\n"
                + "host = localhost\n"
                + "retries = 5\n");

        AppConfig config = ConfigFactory.create(AppConfig.class);
        config.removeProperty("host");
        config.save(file);

        assertTrue("what was left keeps its place", contents().startsWith("retries = 5\n"));
        assertFalse("the key is gone", contents().contains("host"));
        assertFalse("and so is the comment we had written for it",
                contents().contains("The database we talk to"));
    }

    /**
     * The correctness fix hiding inside a formatting feature: <code>store()</code> writes the whole
     * merged configuration, environment and all. This writes what the interface is about.
     */
    @Config.LoadPolicy(Config.LoadType.MERGE)
    @Config.Sources({"file:${test.saved.file}", "system:properties"})
    public interface MergedWithTheSystem extends Mutable, Accessible {
        @Config.DefaultValue("localhost")
        String host();
    }

    @Test
    public void theSystemPropertiesDoNotEndUpInYourFile() throws IOException {
        given("host = localhost\n");

        MergedWithTheSystem config = ConfigFactory.create(MergedWithTheSystem.class);
        assertEquals("the system properties really are loaded", System.getProperty("java.version"),
                config.getProperty("java.version"));

        config.save(file);

        assertEquals("but only the declared key is written", "host = localhost\n", contents());
    }

    /** Saving twice changes nothing the second time, which is what makes it safe in a build. */
    @Test
    public void savingTwiceIsIdempotent() throws IOException {
        given("# ---- section ----\n\nhost = localhost\nunknown.key = kept\n");

        AppConfig config = ConfigFactory.create(AppConfig.class);
        config.save(file);
        String once = contents();
        config.save(file);

        assertEquals(once, contents());
    }

    @Config.Description("Everything this application needs in order to start.")
    @Config.Sources("file:${test.saved.file}")
    public interface Described extends Accessible {
        @Config.DefaultValue("localhost")
        String host();
    }

    /** A description on the interface is the header of a file being generated. */
    @Test
    public void theInterfacesOwnDescriptionHeadsAGeneratedFile() throws IOException {
        File fresh = new File(folder.getRoot(), "headed.properties");

        ConfigFactory.create(Described.class).save(fresh);

        assertEquals("# Everything this application needs in order to start.\n"
                + "\n"
                + "host = localhost\n",
                new String(Files.readAllBytes(fresh.toPath()), StandardCharsets.ISO_8859_1));
    }

    /**
     * The shape <a href="https://github.com/matteobaccan/owner/issues/3">#3</a> is actually about: an
     * interface that has never had a file, carrying nothing but its defaults. No {@code @Sources} at all.
     */
    @Config.Description("Generated from the interface. Edit the values, not the descriptions.")
    public interface NeverHadAFile extends Accessible {

        @Config.Description("Where the service listens.")
        @Config.DefaultValue("8080")
        int port();

        @Config.DefaultValue("30")
        int seconds();
    }

    /** A template out of an interface and its defaults, which is the whole of what #3 asked to generate. */
    @Test
    public void anInterfaceThatNeverHadAFileGeneratesOneFromItsDefaults() throws IOException {
        File fresh = new File(folder.getRoot(), "template.properties");

        ConfigFactory.create(NeverHadAFile.class).save(fresh);

        assertEquals(""
                + "# Generated from the interface. Edit the values, not the descriptions.\n"
                + "\n"
                + "# Where the service listens.\n"
                + "port = 8080\n"
                + "\n"
                + "seconds = 30\n",
                new String(Files.readAllBytes(fresh.toPath()), StandardCharsets.ISO_8859_1));
    }

    @Config.Sources("file:${test.saved.file}")
    public interface Awkward extends Mutable, Accessible {
        @Config.DefaultValue("nothing")
        String value();
    }

    /**
     * The least interesting part and the easiest to get subtly wrong: what we write, we must read back
     * as the same thing. Every character {@link java.util.Properties#store} escapes, round-tripped.
     */
    @Test
    public void everyAwkwardCharacterSurvivesTheRoundTrip() throws IOException {
        String[] awkward = {
                "a = b", "a : b", "a # b", "a ! b", "back\\slash", " leading space", "trailing space ",
                "tab\there", "caffè", "中文", "", "==="};

        for (String value : awkward) {
            File each = new File(folder.getRoot(), "awkward.properties");
            Files.deleteIfExists(each.toPath());

            Awkward config = ConfigFactory.create(Awkward.class);
            config.setProperty("value", value);
            config.save(each);

            java.util.Properties read = new java.util.Properties();
            try (java.io.InputStream in = Files.newInputStream(each.toPath())) {
                read.load(in);
            }
            assertEquals("round trip of [" + value + "]", value, read.getProperty("value"));
        }
    }

    /**
     * A line of somebody else's carrying a broken <code>&#92;u</code> escape is still a line of theirs, and
     * saving must not die on it. Reading the key is how the writer decides whether a line is ours, and the
     * four characters after a <code>&#92;u</code> in a key were being converted without being looked at:
     * not hexadecimal threw {@link NumberFormatException}, and fewer than four before the end of the line
     * threw {@link StringIndexOutOfBoundsException} - both out of {@link Accessible#save(File)}.
     * <p>
     * <b>The file is one this configuration never read</b>, and it has to be: such a file cannot be loaded
     * at all, {@link java.util.Properties#load(java.io.InputStream)} refusing it outright with <i>Malformed
     * &#92;uxxxx encoding</i>. Saving into a file is not reading it, though - the target of
     * <code>save</code> is any file you name, written by anything - so the writer meets what the loader
     * would have turned away.
     * </p>
     * <p>
     * What it does with it is what it does with everything else it cannot claim: the escape is kept as the
     * two characters it is written with, the key matches none of ours, and the line goes back out
     * untouched. Refusing the file is the other defensible answer and is the wrong one here - the promise
     * of this writer is that what it does not understand it does not damage.
     * </p>
     */
    @Test
    public void aBrokenUnicodeEscapeInSomebodyElsesLineIsCarriedThroughUntouched() throws IOException {
        File alien = new File(folder.getRoot(), "written-by-something-else.properties");
        Files.write(alien.toPath(), ("host = localhost\n"
                + "not\\uZZZZ.hex = kept\n"
                + "truncated\\u12\n").getBytes(StandardCharsets.ISO_8859_1));

        AppConfig config = ConfigFactory.create(AppConfig.class);
        config.setProperty("host", "db.internal");
        config.save(alien);

        String written = new String(Files.readAllBytes(alien.toPath()), StandardCharsets.ISO_8859_1);
        assertTrue(written, written.contains("not\\uZZZZ.hex = kept\n"));
        assertTrue(written, written.contains("truncated\\u12\n"));
        assertTrue(written, written.contains("host = db.internal\n"));
    }

    /**
     * The same thing said where it happens, one line at a time - and the two ways a broken escape breaks
     * are different: four characters that are not hexadecimal, and fewer than four characters left.
     */
    @Test
    public void aKeyIsReadThroughItsEscapesAndAroundTheBrokenOnes() {
        assertEquals("caffè", PropertiesFileWriter.keyOf("caff\\u00e8 = x"));
        assertEquals("a b", PropertiesFileWriter.keyOf("a\\ b = x"));

        assertEquals("not\\uZZZZ.hex", PropertiesFileWriter.keyOf("not\\uZZZZ.hex = kept"));
        assertEquals("truncated\\u12", PropertiesFileWriter.keyOf("truncated\\u12"));
        assertEquals("ends.with\\u", PropertiesFileWriter.keyOf("ends.with\\u"));
    }
}

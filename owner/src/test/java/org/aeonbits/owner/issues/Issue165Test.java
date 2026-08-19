/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.DeclaredOnly;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Factory;
import org.aeonbits.owner.Traceable;
import org.aeonbits.owner.util.LogCapture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static java.util.Arrays.asList;
import static org.aeonbits.owner.Config.LoadType.MERGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * See: https://github.com/matteobaccan/owner/issues/165
 * <p>
 * nagkumar asked in March 2016 that a <code>.properties</code> file be able to say for itself which other
 * file it builds on, <i>"this way even the properties file dependencies are decided at properties file
 * level and not in code"</i>. {@link Sources} puts that decision in the code, so a deployment wanting one
 * more file has to be recompiled — which is exactly what the issue asks to avoid.
 * </p>
 * <p>
 * The shape is a <b>key inside the file</b>, <code>owner.include</code>, and not the naming convention his
 * second proposal suggested: a convention that guesses is a convention that surprises. Spring Boot
 * (<code>spring.config.import</code>) and Commons Configuration (<code>include</code>) agree on the shape;
 * the decisions where we differ from them are on each test below.
 * </p>
 * <p>
 * The specification these tests were written from is <code>INCLUDES.md</code> in the repository root.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue165Test {

    private static final File DIR = new File("target/issue165");

    private Factory factory;

    @Before
    public void before() {
        DIR.mkdirs();
        factory = ConfigFactory.newInstance();
    }

    @After
    public void after() {
        for (File file : filesIn(new File(DIR, "deep")))
            file.delete();
        new File(DIR, "deep").delete();
        for (File file : filesIn(DIR))
            file.delete();
    }

    private static File[] filesIn(File dir) {
        File[] found = dir.listFiles();
        return found == null ? new File[0] : found;
    }

    /** Writes a source verbatim, so that a test can say what the file looks like rather than what a map holds. */
    private static void write(String name, String... lines) throws IOException {
        File file = new File(DIR, name);
        try (PrintWriter out = new PrintWriter(file, Charset.defaultCharset().name())) {
            for (String line : lines)
                out.println(line);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // 1-4: the algorithm
    // ------------------------------------------------------------------------------------------------

    @Sources("file:target/issue165/child.properties")
    interface Child extends Config, Accessible, Traceable {
        String fromChild();

        String fromParent();

        String shared();
    }

    /**
     * Decision 1: <b>the file that includes wins over the file it includes.</b>
     * <p>
     * Against Spring, and deliberately. There <code>import</code> means "the more specific file for this
     * environment", so what is imported takes precedence. Here the word is <i>inheritance</i>: the included
     * file is a template and the file that includes it specialises it. It is also what
     * {@link Config.LoadType#MERGE} already says — the source specified first prevails — and an included
     * file is inserted below the one that declares it.
     * </p>
     */
    @Test
    public void theFileThatIncludesWinsOverTheOneItIncludes() throws IOException {
        write("parent.properties",
                "fromParent = from the parent",
                "shared = the parent's");
        write("child.properties",
                "owner.include = file:target/issue165/parent.properties",
                "fromChild = from the child",
                "shared = the child's");

        Child cfg = factory.create(Child.class);
        assertEquals("from the child", cfg.fromChild());
        assertEquals("from the parent", cfg.fromParent());
        assertEquals("the child's", cfg.shared());
    }

    @Sources("file:target/issue165/b.properties")
    interface Chain extends Config, Accessible {
        String fromB();

        String fromA();

        String fromZ();

        String shared();
    }

    /**
     * A chain of three, in depth, in the right order: <code>b</code> includes <code>a</code>,
     * <code>a</code> includes <code>z</code>, and the answer is <code>b, a, z</code>.
     * <p>
     * This is the <b>inheritance of an include</b>: what a file includes is loaded with the same rule as
     * what the interface declares, so the chain has no depth limit and no special case at the second level.
     * Precedence runs the whole way down — <code>b</code> beats <code>a</code> beats <code>z</code> — which
     * is the property a test at two levels cannot tell apart from "the root always wins".
     * </p>
     */
    @Test
    public void aChainOfThreeInDepth() throws IOException {
        write("z.properties",
                "fromZ = from z",
                "shared = z's",
                "twoDeep = z's");
        write("a.properties",
                "owner.include = file:target/issue165/z.properties",
                "fromA = from a",
                "shared = a's",
                "twoDeep = a's");
        write("b.properties",
                "owner.include = file:target/issue165/a.properties",
                "fromB = from b",
                "shared = b's");

        Chain cfg = factory.create(Chain.class);
        assertEquals("from b", cfg.fromB());
        assertEquals("from a", cfg.fromA());
        assertEquals("from z", cfg.fromZ());
        // the whole chain, not just the root: b over a, and a over z where b says nothing
        assertEquals("b's", cfg.shared());
        assertEquals("a's", cfg.getProperty("twoDeep"));
    }

    @Sources("file:target/issue165/listing.properties")
    interface Listing extends Config, Accessible {
        String shared();

        String deep();
    }

    /**
     * <b>The order inside the directive decides.</b>
     * <p>
     * It is the other half of the answer to "does the position matter": <i>where</i> the directive stands
     * in the file cannot matter, and the order of the sources <i>inside</i> it is the only ordering a file
     * can express — so it is the one that carries all the meaning. First named, first scheduled, and first
     * scheduled wins, which is the rule {@link Sources} already has.
     * </p>
     */
    @Test
    public void theOrderInsideTheDirectiveDecidesWhichSourceWins() throws IOException {
        write("earlier.properties", "shared = the earlier one's", "deep = the earlier one's");
        write("later.properties", "shared = the later one's", "deep = the later one's");
        write("listing.properties",
                "owner.include = file:target/issue165/earlier.properties, file:target/issue165/later.properties");

        Listing cfg = factory.create(Listing.class);
        assertEquals("the earlier one's", cfg.shared());

        // and it is the order and not the names: the same two files the other way round answer the other way
        write("listing.properties",
                "owner.include = file:target/issue165/later.properties, file:target/issue165/earlier.properties");
        Listing swapped = ConfigFactory.newInstance().create(Listing.class);
        assertEquals("the later one's", swapped.shared());
    }

    /**
     * A source named in the directive brings <b>everything it includes</b> before the next source named
     * beside it: the walk is depth first, not breadth first.
     * <p>
     * So a file's parent outranks its sibling. That is the reading of <i>inheritance</i> — what a file
     * builds on is part of that file — and it is the assertion that tells the two walks apart, since at one
     * level they are the same list.
     * </p>
     */
    @Test
    public void aSourceBringsWhatItIncludesBeforeTheNextSourceBesideIt() throws IOException {
        write("earlierParent.properties", "deep = the earlier one's parent's");
        write("earlier.properties",
                "owner.include = file:target/issue165/earlierParent.properties",
                "shared = the earlier one's");
        write("later.properties", "shared = the later one's", "deep = the later one's");
        write("listing.properties",
                "owner.include = file:target/issue165/earlier.properties, file:target/issue165/later.properties");

        Listing cfg = factory.create(Listing.class);
        assertEquals("the earlier one's", cfg.shared());
        // depth first: the parent of the first sits above the second, not below it
        assertEquals("the earlier one's parent's", cfg.deep());
    }

    @Sources("file:target/issue165/twice.properties")
    interface WrittenTwice extends Config, Accessible {
        @DefaultValue("nothing")
        String which();
    }

    /**
     * The directive written twice in a properties file: <b>the last line wins and the first is lost, in
     * silence</b>.
     * <p>
     * Nothing here decides that and nothing here can see it — {@link java.util.Properties#load} keeps the
     * last of two lines with the same key, and what arrives is a map with one entry. It is pinned because
     * it is the answer a person needs and because the same two lines mean something different in the other
     * formats: an INI file makes a list of them, and JSON, YAML and TOML refuse the document outright.
     * </p>
     */
    @Test
    public void aDirectiveWrittenTwiceInAPropertiesFileKeepsTheLastLine() throws IOException {
        write("one.properties", "which = one");
        write("two.properties", "which = two");
        write("twice.properties",
                "owner.include = file:target/issue165/one.properties",
                "owner.include = file:target/issue165/two.properties");

        WrittenTwice cfg = factory.create(WrittenTwice.class);
        assertEquals("two", cfg.which());
    }

    @Sources("file:target/issue165/twice.ini")
    interface WrittenTwiceInIni extends Config, Accessible {
        @DefaultValue("nothing")
        String which();
    }

    /**
     * The directive written twice in an <b>INI</b> file becomes a list — <code>owner.include[0]</code>,
     * <code>owner.include[1]</code> — so there is no key called the token and <b>nothing at all is
     * included</b>.
     * <p>
     * That is the one of the three that would be invisible, so it is the one that is reported: the message
     * names the spelling the directive does have and hands back the sources that were written, so the fix
     * can be copied out of it. It is <b>not</b> read as a list of sources — the directive would then have
     * two spellings, one existing only in one format, and the same two lines would mean two sources here
     * and one in a properties file.
     * </p>
     */
    @Test
    public void aDirectiveWrittenTwiceInAnIniFileIsReportedAndIncludesNothing() throws IOException {
        write("one.properties", "which = one");
        write("two.properties", "which = two");
        write("twice.ini",
                "owner.include = file:target/issue165/one.properties",
                "owner.include = file:target/issue165/two.properties");

        try (LogCapture log = LogCapture.ofLibrary(Level.WARNING)) {
            WrittenTwiceInIni cfg = factory.create(WrittenTwiceInIni.class);
            assertEquals("nothing", cfg.which());

            String said = log.messagesAt(Level.WARNING);
            assertTrue(said, said.contains("owner.include is written 2 times"));
            assertTrue(said, said.contains("one.properties, file:target/issue165/two.properties"));
            // and the indexed keys are gone with it: they were the directive, misspelt, not somebody's data
            assertFalse(cfg.propertyNames().contains("owner.include[0]"));
        }
    }

    @Sources("file:target/issue165/root.properties")
    interface Diamond extends Config, Accessible {
        String shared();
    }

    /**
     * Decision 2, first half: <b>a file reached twice is loaded once, and its position is the first one.</b>
     * <p>
     * <code>root</code> includes <code>left</code> and <code>right</code>, both of which include
     * <code>common</code>. Re-reading a file yields the same values, so "the second overwrites the first"
     * would change nothing about the content; what it would change is the <i>position</i> of that file in
     * the precedence order — a file nobody edited moving because a third file named it again, which is the
     * kind of effect nobody can debug. So <code>common</code> sits where <code>left</code> put it, above
     * <code>right</code>, and <code>right</code> does not get to overrule it.
     * </p>
     */
    @Test
    public void aFileReachedTwiceIsLoadedOnceAndKeepsItsFirstPosition() throws IOException {
        write("common.properties", "shared = common's");
        write("left.properties", "owner.include = file:target/issue165/common.properties");
        write("right.properties",
                "owner.include = file:target/issue165/common.properties",
                "shared = right's");
        write("root.properties",
                "owner.include = file:target/issue165/left.properties, file:target/issue165/right.properties");

        Diamond cfg = factory.create(Diamond.class);
        // common was scheduled by left, so it sits above right and right never gets to overrule it
        assertEquals("common's", cfg.shared());
    }

    @Sources("file:target/issue165/cycleA.properties")
    interface Cycle extends Config, Accessible {
        String fromA();

        String fromB();
    }

    /**
     * Decision 2, second half: <b>a cycle terminates by itself, with no exception.</b>
     * <p>
     * It falls out of the rule above rather than being a rule of its own: the second time a file is named
     * it is already scheduled, so it is skipped, so there is nothing left to follow. There is nothing to
     * report and nothing to explain.
     * </p>
     * <p>
     * <b>Note that this is a different answer from the one a variable gets.</b> A circular variable
     * reference throws, and that was decided for 2.0.0: a file named twice is not a mistake, while a value
     * that resolves to itself is.
     * </p>
     */
    @Test
    public void aCycleTerminatesWithoutAnException() throws IOException {
        write("cycleA.properties",
                "owner.include = file:target/issue165/cycleB.properties",
                "fromA = a");
        write("cycleB.properties",
                "owner.include = file:target/issue165/cycleA.properties",
                "fromB = b");

        Cycle cfg = factory.create(Cycle.class);
        assertEquals("a", cfg.fromA());
        assertEquals("b", cfg.fromB());
    }

    /**
     * With includes the list of sources is no longer written anywhere a person can look, so the library
     * says what it turned out to be — at <code>CONFIG</code>, the level that carries what the library
     * <i>decided</i>, and <b>only when a file actually named one</b>.
     * <p>
     * Without that line there is nothing left to read: {@link Sources} names the declared sources and the
     * files name the rest, and no single place holds both. A configuration whose files include nothing is
     * unaffected — its sources are on its interface, where they always were, and the line that names them
     * is still the whole truth.
     * </p>
     */
    @Test
    public void whatWasActuallyReadIsReportedWhenTheFilesAddedToIt() throws IOException {
        write("z.properties", "fromZ = from z", "shared = z's", "twoDeep = z's");
        write("a.properties",
                "owner.include = file:target/issue165/z.properties",
                "fromA = from a", "shared = a's", "twoDeep = a's");
        write("b.properties",
                "owner.include = file:target/issue165/a.properties",
                "fromB = from b", "shared = b's");

        try (LogCapture log = LogCapture.ofLibrary(Level.CONFIG)) {
            factory.create(Chain.class);

            String said = log.messagesAt(Level.CONFIG);
            assertTrue(said, said.contains("the first prevailing"));
            // in the order they were read, which is the order they prevail in
            int b = said.indexOf("b.properties");
            int a = said.indexOf("a.properties");
            int z = said.indexOf("z.properties");
            assertTrue(said, b >= 0 && b < a && a < z);
        }
    }

    /** And a configuration whose files name nothing says nothing extra: its sources are on its interface. */
    @Test
    public void nothingIsReportedWhenNoFileNamedASource() throws IOException {
        write("child.properties", "fromChild = from the child", "fromParent = x", "shared = y");

        try (LogCapture log = LogCapture.ofLibrary(Level.CONFIG)) {
            factory.create(Child.class);
            assertFalse(log.messagesAt(Level.CONFIG).contains("the first prevailing"));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // 5: what the value came from
    // ------------------------------------------------------------------------------------------------

    /**
     * An included file is <b>a source in its own right</b>: {@code originOf} names the included file and
     * not the one that included it.
     * <p>
     * That is why the includes join the list of sources rather than have their values poured into the map
     * of the file that named them — the merge is exactly what makes a value indistinguishable from the one
     * it overwrote, and a diagnostic that pointed at the child for a value the parent holds would be worse
     * than no diagnostic.
     * </p>
     */
    @Test
    public void originOfNamesTheIncludedFileAndNotTheOneThatIncludedIt() throws IOException {
        write("parent.properties",
                "fromParent = from the parent",
                "shared = the parent's");
        write("child.properties",
                "owner.include = file:target/issue165/parent.properties",
                "fromChild = from the child",
                "shared = the child's");

        Child cfg = factory.create(Child.class);
        assertTrue(cfg.originOf("fromParent").source().endsWith("parent.properties"));
        assertTrue(cfg.originOf("fromChild").source().endsWith("child.properties"));
        // the key both files hold is attributed to the one that won it
        assertTrue(cfg.originOf("shared").source().endsWith("child.properties"));
    }

    // ------------------------------------------------------------------------------------------------
    // 7: an included file that is not there
    // ------------------------------------------------------------------------------------------------

    @Sources("file:target/issue165/naming-nothing.properties")
    interface NamingNothing extends Config, Accessible {
        @DefaultValue("its default")
        String value();
    }

    /**
     * Decision 4: <b>an included file that is missing is passed over with a warning</b>, and refused under
     * <code>owner.strict</code>.
     * <p>
     * Not the silence a declared source gets. {@link Config.LoadType#FIRST} is a chain of fallbacks in
     * which every miss but the last is how the feature works, and a configuration with no {@link Sources}
     * probes four names for every interface — warning there would be unbearable noise. <b>Nobody builds a
     * fallback chain out of a file naming another file</b>: nothing else was going to answer in its place.
     * </p>
     */
    @Test
    public void anIncludedFileThatIsNotThereIsPassedOverWithAWarning() throws IOException {
        write("naming-nothing.properties",
                "owner.include = file:target/issue165/nobody-wrote-this.properties");

        try (LogCapture log = LogCapture.ofLibrary(Level.WARNING)) {
            NamingNothing cfg = factory.create(NamingNothing.class);
            assertEquals("its default", cfg.value());

            String said = log.messagesAt(Level.WARNING);
            assertTrue(said, said.contains("nobody-wrote-this.properties"));
            assertTrue(said, said.contains("owner.include"));
        }
    }

    @Sources("file:target/issue165/naming-two.properties")
    interface NamingTwo extends Config, Accessible, org.aeonbits.owner.Reloadable {
        @DefaultValue("its default")
        String value();
    }

    /**
     * <b>Two missing includes are two warnings</b>, not one taking turns with the other.
     * <p>
     * A classpath spec that matches no resource never becomes a URI at all, so there is nothing to key the
     * "said once" record by — and keying them all by one placeholder made the second report overwrite the
     * first's record, so at the next load the first looked new again and they alternated for ever. The key
     * carries the spec for that reason, and this is the test that says so.
     * </p>
     */
    @Test
    public void twoIncludesThatResolveToNothingAreBothReported() throws IOException {
        write("naming-two.properties",
                "owner.include = classpath:no/such/first.properties, classpath:no/such/second.properties");

        try (LogCapture log = LogCapture.ofLibrary(Level.WARNING)) {
            NamingTwo cfg = factory.create(NamingTwo.class);
            assertEquals("its default", cfg.value());

            String said = log.messagesAt(Level.WARNING);
            assertTrue(said, said.contains("no/such/first.properties"));
            assertTrue(said, said.contains("no/such/second.properties"));
        }
    }

    /**
     * And each of them is said <b>once</b>, however many times the configuration is loaded. A hot reload
     * runs the whole load again at its interval, for as long as the process lives, so a source that stays
     * missing would otherwise fill the log at that rate.
     */
    @Test
    public void anIncludeThatStaysMissingIsReportedOnce() throws IOException {
        write("naming-two.properties",
                "owner.include = classpath:no/such/first.properties, classpath:no/such/second.properties");

        try (LogCapture log = LogCapture.ofLibrary(Level.WARNING)) {
            NamingTwo cfg = factory.create(NamingTwo.class);
            int afterTheFirstLoad = log.linesAt(Level.WARNING).size();
            assertEquals(2, afterTheFirstLoad);

            cfg.reload();
            cfg.reload();
            assertEquals(afterTheFirstLoad, log.linesAt(Level.WARNING).size());
        }
    }

    /** The same source, under <code>owner.strict</code>: a refusal at the moment the object is created. */
    @Test
    public void anIncludedFileThatIsNotThereIsRefusedUnderStrict() throws IOException {
        write("naming-nothing.properties",
                "owner.include = file:target/issue165/nobody-wrote-this.properties");

        factory.setProperty("owner.strict", "true");
        try {
            factory.create(NamingNothing.class);
            fail("a source named inside another source and not there has to be refused under owner.strict");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("nobody-wrote-this.properties"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("owner.strict"));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // 8-9: the directive is a directive
    // ------------------------------------------------------------------------------------------------

    /**
     * The directive is <b>not a property</b>: it is removed once processed and appears in no view.
     * <p>
     * Checked in every view there is, because they are three different code paths and the point of the
     * decision is that a person reading any of them sees the configuration and not the plumbing.
     * </p>
     */
    @Test
    public void theDirectiveAppearsInNoView() throws IOException {
        write("parent.properties", "fromParent = from the parent", "shared = the parent's");
        write("child.properties",
                "owner.include = file:target/issue165/parent.properties",
                "fromChild = from the child",
                "shared = the child's");

        Child cfg = factory.create(Child.class);
        assertFalse(cfg.propertyNames().contains("owner.include"));
        assertNull(cfg.getProperty("owner.include"));
        assertFalse(cfg.toString().contains("owner.include"));
        assertNull(cfg.originOf("owner.include"));
        // and what it named is there, so it was read and not merely dropped
        assertEquals("from the parent", cfg.fromParent());
    }

    @Sources("file:target/issue165/child.properties")
    interface WithOwnToken extends Config, Accessible {
        String fromParent();

        @DefaultValue("")
        String ownerInclude();
    }

    /**
     * Decision 3: <b>the token is read from <code>owner.include.key</code></b> on the factory.
     * <p>
     * The second use is what makes the first defensible: whoever already has a legitimate property called
     * <code>owner.include</code> must be able to say <i>not here</i>, or this release would change what
     * files that already exist mean. It is a factory property and never a directive inside the file — a
     * directive that redefines itself is a maze.
     * </p>
     */
    @Test
    public void theTokenCanBeChanged() throws IOException {
        write("parent.properties", "fromParent = from the parent");
        write("child.properties", "needs = file:target/issue165/parent.properties");

        factory.setProperty("owner.include.key", "needs");
        WithOwnToken cfg = factory.create(WithOwnToken.class);
        assertEquals("from the parent", cfg.fromParent());
        assertFalse(cfg.propertyNames().contains("needs"));
    }

    /**
     * The empty value switches the feature off entirely, and then <code>owner.include</code> is an ordinary
     * property again — which is the whole reason the switch exists.
     */
    @Test
    public void theEmptyTokenSwitchesTheFeatureOff() throws IOException {
        write("parent.properties", "fromParent = from the parent");
        write("child.properties",
                "owner.include = file:target/issue165/parent.properties",
                "ownerInclude = read as a value");

        factory.setProperty("owner.include.key", "");
        WithOwnToken cfg = factory.create(WithOwnToken.class);
        // the parent was never read
        assertNull(cfg.getProperty("fromParent"));
        // and the key is a property like any other
        assertEquals("file:target/issue165/parent.properties", cfg.getProperty("owner.include"));
        assertTrue(cfg.propertyNames().contains("owner.include"));
    }

    @Sources("file:target/issue165/saying-nothing.properties")
    interface SayingNothing extends Config, Accessible {
        @DefaultValue("its default")
        String value();
    }

    /**
     * A directive with nothing after it names no source and is still not a property.
     * <p>
     * It is what a generated file looks like when the list it was generated from was empty, so answering it
     * with a warning about an unreadable source would be a warning about nothing.
     * </p>
     */
    @Test
    public void aDirectiveNamingNothingIsStillNotAProperty() throws IOException {
        write("saying-nothing.properties", "owner.include =");

        try (LogCapture log = LogCapture.ofLibrary(Level.WARNING)) {
            SayingNothing cfg = factory.create(SayingNothing.class);
            assertEquals("its default", cfg.value());
            assertFalse(cfg.propertyNames().contains("owner.include"));
            assertEquals("", log.messagesAt(Level.WARNING));
        }
    }

    /**
     * A source may say for itself that it has to be there, and an include says it the same way a
     * {@link Sources} entry does — in the fragment. Then it is refused without {@code owner.strict} having
     * to be on, because the person who wrote the spec is the one who asked.
     */
    @Test
    public void anIncludeThatSaysItIsRequiredIsRefusedWhenItIsNotThere() throws IOException {
        write("naming-nothing.properties",
                "owner.include = file:target/issue165/nobody-wrote-this.properties#required=true");

        try {
            factory.create(NamingNothing.class);
            fail("an include that says it is required has to be refused when it cannot be read");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("required"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("nobody-wrote-this.properties"));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // the keys the interface declares, and the ones it does not
    // ------------------------------------------------------------------------------------------------

    @Sources("file:target/issue165/child.properties")
    interface DeclaringOne extends Config, Accessible {
        String fromParent();
    }

    @DeclaredOnly
    @Sources("file:target/issue165/child.properties")
    interface DeclaringOneOnly extends Config, Accessible {
        String fromParent();
    }

    /**
     * A key an included file brings and the interface <b>does not</b> declare is an ordinary property of
     * this configuration: visible, readable, and attributed to the file it came from.
     * <p>
     * There is no second class of property here. An include is a source, so what it holds arrives the way
     * everything else a source holds arrives.
     * </p>
     */
    @Test
    public void anIncludedFileBringsItsUndeclaredKeysToo() throws IOException {
        write("parent.properties",
                "fromParent = from the parent",
                "nobodyDeclaredThis = and yet here it is");
        write("child.properties",
                "owner.include = file:target/issue165/parent.properties",
                "norThis = from the child");

        DeclaringOne cfg = factory.create(DeclaringOne.class);
        assertEquals("from the parent", cfg.fromParent());
        assertEquals("and yet here it is", cfg.getProperty("nobodyDeclaredThis"));
        assertTrue(cfg.propertyNames().contains("nobodyDeclaredThis"));
        assertTrue(cfg.propertyNames().contains("norThis"));
    }

    /**
     * Under {@link DeclaredOnly} the view narrows to what the interface declares — <b>and the value of a
     * declared key still comes from the included file</b>.
     * <p>
     * The two features do not meet: {@link DeclaredOnly} restricts the <i>views</i>, includes decide what
     * was <i>read</i>. The directive is invisible either way, having never been a property at all, so
     * turning the restriction on changes nothing about it.
     * </p>
     */
    @Test
    public void declaredOnlyNarrowsTheViewAndNotWhatWasRead() throws IOException {
        write("parent.properties",
                "fromParent = from the parent",
                "nobodyDeclaredThis = and yet here it is");
        write("child.properties",
                "owner.include = file:target/issue165/parent.properties");

        DeclaringOneOnly cfg = factory.create(DeclaringOneOnly.class);
        // the value of the declared key comes from the included file
        assertEquals("from the parent", cfg.fromParent());
        assertEquals(asList("fromParent"), sorted(cfg.propertyNames()));
        // getProperty is deliberately not restricted: see #150 and #230
        assertEquals("and yet here it is", cfg.getProperty("nobodyDeclaredThis"));
        // the directive is not a property, so there is nothing for the restriction to hide
        assertNull(cfg.getProperty("owner.include"));
    }

    private static List<String> sorted(java.util.Set<String> names) {
        List<String> list = new java.util.ArrayList<>(names);
        java.util.Collections.sort(list);
        return list;
    }

    // ------------------------------------------------------------------------------------------------
    // where the directive is recognised
    // ------------------------------------------------------------------------------------------------

    @Sources("file:target/issue165/nested-directive.properties")
    interface NotADirective extends Config, Accessible {
        @DefaultValue("its default")
        String fromParent();
    }

    /**
     * The directive is recognised <b>at the root of the file and nowhere else</b>.
     * <p>
     * Every loader in this project hands back a flat {@link java.util.Properties}, a nested format having
     * been flattened with dots on the way, so <code>owner.include</code> written inside a section arrives
     * as <code>section.owner.include</code>. That has to stay somebody's property: a rule that matched a
     * suffix would turn any key ending in the token into a directive, and a nested format is where that
     * would happen by accident.
     * </p>
     */
    @Test
    public void theDirectiveIsNotRecognisedBelowTheRoot() throws IOException {
        write("parent.properties", "fromParent = from the parent");
        write("nested-directive.properties",
                "database.owner.include = file:target/issue165/parent.properties");

        NotADirective cfg = factory.create(NotADirective.class);
        assertEquals("its default", cfg.fromParent());
        assertEquals("file:target/issue165/parent.properties",
                cfg.getProperty("database.owner.include"));
    }

    /**
     * <b>Where in the file the directive is written does not matter</b>, and the answer is not a preference:
     * by the time it is read the file is a map, {@link java.util.Properties} being a
     * {@link java.util.Hashtable}, and the line order the author saw is gone. Asking for it at the top
     * would be a rule nothing could enforce without parsing every format a second time to find out which
     * line a key was on.
     */
    @Test
    public void whereInTheFileTheDirectiveIsWrittenDoesNotMatter() throws IOException {
        write("parent.properties", "fromParent = from the parent", "shared = the parent's");

        write("child.properties",
                "owner.include = file:target/issue165/parent.properties",
                "fromChild = from the child",
                "shared = the child's");
        Child atTheTop = factory.create(Child.class);

        write("child.properties",
                "fromChild = from the child",
                "shared = the child's",
                "owner.include = file:target/issue165/parent.properties");
        Child atTheBottom = ConfigFactory.newInstance().create(Child.class);

        assertEquals(atTheTop.fromParent(), atTheBottom.fromParent());
        assertEquals(atTheTop.shared(), atTheBottom.shared());
        assertEquals("the child's", atTheBottom.shared());
    }

    // ------------------------------------------------------------------------------------------------
    // the load policies
    // ------------------------------------------------------------------------------------------------

    @LoadPolicy(MERGE)
    @Sources({"file:target/issue165/first.properties", "file:target/issue165/second.properties"})
    interface Merged extends Config, Accessible {
        String value();

        String fromFirstsParent();

        String fromSecondsParent();
    }

    /** With {@link Config.LoadType#MERGE} every declared source is read, and so is everything each includes. */
    @Test
    public void mergeReadsTheIncludesOfEveryDeclaredSource() throws IOException {
        write("firstParent.properties", "fromFirstsParent = a", "value = the first parent's");
        write("secondParent.properties", "fromSecondsParent = b", "value = the second parent's");
        write("first.properties", "owner.include = file:target/issue165/firstParent.properties");
        write("second.properties", "owner.include = file:target/issue165/secondParent.properties");

        Merged cfg = factory.create(Merged.class);
        assertEquals("a", cfg.fromFirstsParent());
        assertEquals("b", cfg.fromSecondsParent());
        // first, then what first includes, then second, then what second includes
        assertEquals("the first parent's", cfg.value());
    }

    @Sources({"file:target/issue165/absent.properties", "file:target/issue165/answering.properties",
            "file:target/issue165/never-read.properties"})
    interface FirstOne extends Config, Accessible {
        String value();

        String fromTheWinnersParent();

        @DefaultValue("not read")
        String fromTheOneAfter();
    }

    /**
     * With {@link Config.LoadType#FIRST} the source that answers wins and the ones after it are never read
     * — <b>but its own includes are loaded</b>, merged below it: what a file includes is part of what that
     * file means.
     * <p>
     * The source before it is missing, which is the ordinary shape of a fallback chain and stays silent.
     * </p>
     */
    @Test
    public void firstLoadsTheIncludesOfTheSourceThatAnswered() throws IOException {
        write("winnersParent.properties",
                "fromTheWinnersParent = from the winner's parent",
                "value = the parent's");
        write("answering.properties",
                "owner.include = file:target/issue165/winnersParent.properties",
                "value = the winner's");
        write("never-read.properties", "fromTheOneAfter = read after all");

        FirstOne cfg = factory.create(FirstOne.class);
        assertEquals("the winner's", cfg.value());
        assertEquals("from the winner's parent", cfg.fromTheWinnersParent());
        assertEquals("not read", cfg.fromTheOneAfter());
    }

    // ------------------------------------------------------------------------------------------------
    // the spec is a spec
    // ------------------------------------------------------------------------------------------------

    @Sources("file:target/issue165/expanding.properties")
    interface Expanding extends Config, Accessible {
        String fromParent();
    }

    /**
     * A spec inside the directive is <b>the same grammar a {@link Sources} entry has</b>, expanded by the
     * same {@link org.aeonbits.owner.Config.Sources} machinery — so it may name a variable the factory set.
     * <p>
     * The variables come from the factory and not from the file: the properties of the file are the thing
     * being assembled, and a source list that depended on what the sources say would have to be worked out
     * before it could be worked out.
     * </p>
     */
    @Test
    public void aSpecIsExpandedTheWayASourcesEntryIs() throws IOException {
        write("parent.properties", "fromParent = from the parent");
        write("expanding.properties", "owner.include = file:${issue165.dir}/parent.properties");

        factory.setProperty("issue165.dir", "target/issue165");
        Expanding cfg = factory.create(Expanding.class);
        assertEquals("from the parent", cfg.fromParent());
    }

    @Sources("file:target/issue165/onTheClasspath.properties")
    interface FromTheClasspath extends Config, Accessible {
        String fromTheClasspath();
    }

    /** A spec may name a classpath resource, which is the form most configurations write. */
    @Test
    public void anIncludeMayBeAClasspathResource() throws IOException {
        write("onTheClasspath.properties",
                "owner.include = classpath:org/aeonbits/owner/issues/issue165-included.properties");

        FromTheClasspath cfg = factory.create(FromTheClasspath.class);
        assertEquals("from the classpath", cfg.fromTheClasspath());
    }

    // ------------------------------------------------------------------------------------------------
    // a spec with no scheme: beside the source that named it
    // ------------------------------------------------------------------------------------------------

    @Sources("file:target/issue165/deep/child.properties")
    interface Deep extends Config, Accessible {
        String fromNextDoor();

        @DefaultValue("nothing")
        String fromUpstairs();
    }

    private static void writeDeep(String name, String... lines) throws IOException {
        File dir = new File(DIR, "deep");
        dir.mkdirs();
        try (PrintWriter out = new PrintWriter(new File(dir, name), Charset.defaultCharset().name())) {
            for (String line : lines)
                out.println(line);
        }
    }

    /**
     * A spec naming <b>no scheme</b> is looked for beside the source that named it.
     * <p>
     * Spring Boot's rule, in their words: a location carrying a URL-style prefix is <i>fixed</i>, anything
     * else is <i>relative</i> and resolves against the file that declared it. C has had the same two-tier
     * rule since 1972 — <code>#include "next door"</code> against <code>#include &lt;on the search
     * path&gt;</code>. It cost nothing to adopt: a spec with no scheme was an error before this, so no file
     * that worked means anything different now.
     * </p>
     * <p>
     * What it fixes is the case this feature would otherwise have got wrong most often. A person writing
     * <code>/etc/myapp/production.properties</code> who names <code>local.properties</code> means the file
     * next to it — and used to get the file next to wherever the JVM happened to start.
     * </p>
     */
    @Test
    public void aSpecWithNoSchemeIsLookedForBesideTheSourceThatNamedIt() throws IOException {
        writeDeep("nextDoor.properties", "fromNextDoor = from next door");
        writeDeep("child.properties", "owner.include = nextDoor.properties");

        Deep cfg = factory.create(Deep.class);
        assertEquals("from next door", cfg.fromNextDoor());
    }

    /**
     * It walks up as well as sideways, and it <b>chains</b>: each source resolves against itself and not
     * against the first one, which is what makes a tree of files movable as a tree.
     */
    @Test
    public void aRelativeSpecWalksUpAndChains() throws IOException {
        write("upstairs.properties", "fromUpstairs = from upstairs");
        writeDeep("nextDoor.properties",
                "owner.include = ../upstairs.properties",
                "fromNextDoor = from next door");
        writeDeep("child.properties", "owner.include = nextDoor.properties");

        Deep cfg = factory.create(Deep.class);
        assertEquals("from next door", cfg.fromNextDoor());
        // resolved against nextDoor, which is one level down from where the chain started
        assertEquals("from upstairs", cfg.fromUpstairs());
    }

    @Sources("file:target/issue165/deep/fixed.properties")
    interface StillFixed extends Config, Accessible {
        String fromNextDoor();
    }

    /**
     * A spec that <b>does</b> name a scheme is fixed and behaves exactly as it did: resolved the way a
     * {@link Sources} entry is, against the working directory for a <code>file:</code> path.
     * <p>
     * That is the half of the rule that carries the compatibility. Every configuration written before this
     * release names a scheme, this library's grammar having always required one.
     * </p>
     */
    @Test
    public void aSpecThatNamesASchemeIsStillFixed() throws IOException {
        writeDeep("nextDoor.properties", "fromNextDoor = the one beside it");
        write("nextDoor.properties", "fromNextDoor = the one from the working directory");
        writeDeep("fixed.properties", "owner.include = file:target/issue165/nextDoor.properties");

        StillFixed cfg = factory.create(StillFixed.class);
        assertEquals("the one from the working directory", cfg.fromNextDoor());
    }

    @Sources("classpath:org/aeonbits/owner/issues/issue165-relative.properties")
    interface FromTheClasspathRelatively extends Config, Accessible {
        String fromTheClasspath();
    }

    /**
     * A classpath resource names its neighbours the same way, the resolution happening against <b>where the
     * resource turned out to be</b> — a directory under <code>target/classes</code> here, and an entry in a
     * jar once the application is packaged. The file says the same thing in both.
     */
    @Test
    public void aClasspathResourceNamesItsNeighboursTheSameWay() {
        FromTheClasspathRelatively cfg = factory.create(FromTheClasspathRelatively.class);
        assertEquals("from the classpath", cfg.fromTheClasspath());
    }

    @Sources("jar:file:target/issue165/inside.jar!/config/base.properties")
    interface InsideAJar extends Config, Accessible {
        String fromTheEntryBesideIt();

        String fromTheJarRoot();
    }

    /**
     * <b>Inside a jar it works too, and that is the case worth measuring.</b>
     * <p>
     * A packaged application is where a configuration most often lives, and a <code>jar:</code> URI is
     * <b>opaque</b>: {@link java.net.URI#resolve} hands the relative reference straight back unchanged,
     * which would leave the source being looked for under a name with no scheme at all — a wrong answer
     * arrived at in silence. That is why the resolution goes through {@link java.net.URL}, whose
     * <code>jar:</code> handler understands the part after the <code>!</code>. Spring reaches for the same
     * constructor for the same reason, and this is the test that says we did.
     * </p>
     * <p>
     * So a file inside a jar names its neighbours exactly as a file on disk does — and a leading
     * <code>/</code> means <b>the root of the jar</b>, which is what a URL has always meant by it.
     * </p>
     */
    @Test
    public void aSourceInsideAJarNamesItsNeighboursTheSameWay() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("config/base.properties",
                "owner.include = beside.properties, /root.properties\n");
        entries.put("config/beside.properties", "fromTheEntryBesideIt = from the entry beside it\n");
        entries.put("root.properties", "fromTheJarRoot = from the root of the jar\n");
        writeJar(new File(DIR, "inside.jar"), entries);

        InsideAJar cfg = factory.create(InsideAJar.class);
        assertEquals("from the entry beside it", cfg.fromTheEntryBesideIt());
        assertEquals("from the root of the jar", cfg.fromTheJarRoot());
    }

    /** Writes a jar of the given entries, there being no helper for more than one. */
    private static void writeJar(File target, Map<String, String> entries) throws IOException {
        try (java.util.jar.JarOutputStream jar =
                     new java.util.jar.JarOutputStream(new java.io.FileOutputStream(target))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                jar.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                jar.write(entry.getValue().getBytes(Charset.defaultCharset()));
                jar.closeEntry();
            }
        }
    }

    @LoadPolicy(MERGE)
    @Sources("system:properties")
    interface FromTheSystemProperties extends Config, Accessible {
        @DefaultValue("nothing")
        String value();
    }

    /**
     * A relative spec named by a source that <b>nothing can be found beside</b>.
     * <p>
     * <code>system:properties</code> is a source like any other and may carry the directive — somebody
     * setting <code>-Downer.include=…</code> is a legitimate way to add a file from the command line — but
     * it is not a place, so there is no "next door" to look in. The message says that, and says the two
     * spellings that would work, rather than letting the JDK answer <i>unknown protocol: system</i>.
     * </p>
     */
    @Test
    public void aRelativeSpecNamedBySomethingThatIsNotAPlaceIsRefused() {
        System.setProperty("owner.include", "sibling.properties");
        try {
            factory.create(FromTheSystemProperties.class);
            fail("a relative spec has to be answered for when there is nothing to be relative to");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("names no scheme"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("file:sibling.properties"));
        } finally {
            System.clearProperty("owner.include");
        }
    }

    @Sources("file:target/issue165/deep/drive.properties")
    interface WithADriveLetter extends Config, Accessible {
        @DefaultValue("nothing")
        String value();
    }

    /**
     * A Windows path with a drive letter is refused, by name.
     * <p>
     * <code>C:/app/config.properties</code> is not a scheme and a drive, it is the scheme <code>c</code> —
     * which is why the pattern that recognises a scheme wants <b>two characters at least</b>. Left to the
     * JDK it comes out as <code>unknown protocol: c</code>, which tells nobody anything; here it says what
     * to write instead. Half of this project's CI runs on Windows and this is a thing people write.
     * </p>
     */
    @Test
    public void aWindowsDriveLetterIsRefusedByName() throws IOException {
        writeDeep("drive.properties", "owner.include = C:/app/config.properties");

        try {
            factory.create(WithADriveLetter.class);
            fail("a drive letter is not a URL scheme and has to be answered for");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("drive letter"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("file:/C:/app/config.properties"));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // 10: what the writer does with the line
    // ------------------------------------------------------------------------------------------------

    /**
     * <code>save(File)</code> keeps the directive's line.
     * <p>
     * Not by knowing about it: the writer keeps every line of the file it does not own, and the directive
     * is one — it lives in the file and not among the properties, so nothing about it had to be arranged.
     * That is worth a test rather than an assumption, since "the writer keeps what it does not know" and
     * "the directive is not a property" are two decisions taken years apart.
     * </p>
     */
    @Test
    public void savingTheFileKeepsTheDirectiveLine() throws IOException {
        write("parent.properties", "fromParent = from the parent", "shared = the parent's");
        write("child.properties",
                "owner.include = file:target/issue165/parent.properties",
                "fromChild = from the child",
                "shared = the child's");

        Child cfg = factory.create(Child.class);
        File file = new File(DIR, "child.properties");
        cfg.save(file);

        String written = read(file);
        assertTrue(written, written.contains("owner.include = file:target/issue165/parent.properties"));

        // and it still works when read back
        Child again = ConfigFactory.newInstance().create(Child.class);
        assertEquals("from the parent", again.fromParent());
    }

    private static String read(File file) throws IOException {
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        return new String(bytes, Charset.defaultCharset());
    }

    // ------------------------------------------------------------------------------------------------
    // the formats the core can read
    // ------------------------------------------------------------------------------------------------

    @Sources("file:target/issue165/from.ini")
    interface FromIni extends Config, Accessible {
        String fromParent();

        @DefaultValue("its default")
        String sectioned();
    }

    /**
     * The directive works in an INI file, and only at the root of it.
     * <p>
     * A key written before any section is a root key; the same key written inside <code>[database]</code>
     * arrives as <code>database.owner.include</code> and is a property. That is the whole rule for every
     * nested format, measured here on the one the core can read without another artifact.
     * </p>
     */
    @Test
    public void theDirectiveWorksInAnIniFileAtItsRoot() throws IOException {
        write("parent.properties", "fromParent = from the parent");
        write("from.ini",
                "owner.include = file:target/issue165/parent.properties",
                "",
                "[database]",
                "owner.include = file:target/issue165/never.properties");

        FromIni cfg = factory.create(FromIni.class);
        assertEquals("from the parent", cfg.fromParent());
        assertEquals("file:target/issue165/never.properties",
                cfg.getProperty("database.owner.include"));
    }

    @Sources("file:target/issue165/from.env")
    interface FromDotEnv extends Config, Accessible {
        String fromParent();
    }

    /** The directive works in a <code>.env</code> file. */
    @Test
    public void theDirectiveWorksInADotEnvFile() throws IOException {
        write("parent.properties", "fromParent = from the parent");
        write("from.env", "owner.include=file:target/issue165/parent.properties");

        FromDotEnv cfg = factory.create(FromDotEnv.class);
        assertEquals("from the parent", cfg.fromParent());
        assertFalse(cfg.propertyNames().contains("owner.include"));
    }

    @Sources("file:target/issue165/from.xml")
    interface FromXml extends Config, Accessible {
        String fromParent();
    }

    /** The directive works in an XML properties file. */
    @Test
    public void theDirectiveWorksInAnXmlFile() throws IOException {
        write("parent.properties", "fromParent = from the parent");
        write("from.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">",
                "<properties>",
                "  <entry key=\"owner.include\">file:target/issue165/parent.properties</entry>",
                "</properties>");

        FromXml cfg = factory.create(FromXml.class);
        assertEquals("from the parent", cfg.fromParent());
        assertFalse(cfg.propertyNames().contains("owner.include"));
    }

    @Sources("file:target/issue165/crossing.properties")
    interface AcrossFormats extends Config, Accessible {
        String fromIni();

        String fromEnv();

        String fromProperties();
    }

    /**
     * A file of one format may include a file of another, and so may the file it includes.
     * <p>
     * Nothing arranges that: an include is resolved into a source, and which loader reads a source has been
     * decided by its extension since long before this feature. It is worth a test because it is the thing
     * somebody will assume does not work.
     * </p>
     */
    @Test
    public void aFileOfOneFormatMayIncludeAFileOfAnother() throws IOException {
        write("third.env", "fromEnv=from the env file");
        write("second.ini",
                "owner.include = file:target/issue165/third.env",
                "fromIni = from the ini file");
        write("crossing.properties",
                "owner.include = file:target/issue165/second.ini",
                "fromProperties = from the properties file");

        AcrossFormats cfg = factory.create(AcrossFormats.class);
        assertEquals("from the properties file", cfg.fromProperties());
        assertEquals("from the ini file", cfg.fromIni());
        assertEquals("from the env file", cfg.fromEnv());
    }

    // ------------------------------------------------------------------------------------------------
    // what a reload sees
    // ------------------------------------------------------------------------------------------------

    @Sources("file:target/issue165/reloading.properties")
    interface Reloading extends Config, org.aeonbits.owner.Reloadable, Accessible {
        String fromParent();

        @DefaultValue("nothing yet")
        String fromTheNewParent();
    }

    /**
     * A reload works the whole list out again: a file that stops naming an include loses its values, and a
     * file that starts naming one gains them.
     * <p>
     * This is the half of the feature that could not be built on the old shape at all — the list of sources
     * was computed once in the constructor. It is checked with an explicit {@code reload()} rather than
     * with {@link Config.HotReload}, so that what is being measured is the list being worked out again and
     * not a timestamp on a filesystem.
     * </p>
     */
    @Test
    public void aReloadWorksTheListOutAgain() throws IOException {
        write("parent.properties", "fromParent = from the parent");
        write("newParent.properties", "fromTheNewParent = from the new parent");
        write("reloading.properties", "owner.include = file:target/issue165/parent.properties");

        Reloading cfg = factory.create(Reloading.class);
        assertEquals("from the parent", cfg.fromParent());
        assertEquals("nothing yet", cfg.fromTheNewParent());

        write("reloading.properties", "owner.include = file:target/issue165/newParent.properties");
        cfg.reload();

        assertEquals("from the new parent", cfg.fromTheNewParent());
        // and what the file it no longer names held is gone
        assertNull(cfg.getProperty("fromParent"));
    }
}

/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.crossing;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Factory;
import org.aeonbits.owner.Mutable;
import org.aeonbits.owner.handlers.AesGcmHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Two features at a time, where each of them works and the question is whether they meet.
 * <p>
 * Every other test in this suite exercises one thing. A configuration uses several at once, and the
 * failures that survive into a release are the ones nobody thought to cross: the masking that is right
 * until the file is written back, the directive that is read until a prefix is set, the list that is
 * merged until it comes from two files. Each test below names the two features and asserts what has to
 * hold between them.
 * </p>
 *
 * @author Matteo Baccan
 */
public class FeaturesThatMeetTest {

    private static final File DIR = new File("target/crossing");

    private Factory factory;

    @Before
    public void before() {
        DIR.mkdirs();
        factory = ConfigFactory.newInstance();
    }

    @After
    public void after() {
        File[] found = DIR.listFiles();
        if (found != null)
            for (File file : found)
                file.delete();
    }

    private static File write(String name, String... lines) throws IOException {
        File file = new File(DIR, name);
        try (PrintWriter out = new PrintWriter(file, Charset.defaultCharset().name())) {
            for (String line : lines)
                out.println(line);
        }
        return file;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), Charset.defaultCharset());
    }

    // ------------------------------------------------------------------ @Sensitive x save(File)

    @Sources("file:target/crossing/secret.properties")
    interface WithASecret extends Config, Accessible, Mutable {
        @Config.Sensitive
        @Config.Key("db.password")
        String password();

        @Config.Key("db.host")
        String host();
    }

    /**
     * <b>The mask must never reach the file.</b> It is for a listing, a log and a {@code toString()};
     * writing it into the configuration would replace the secret with eight asterisks and lose it, which
     * is the one failure in this area nobody could undo.
     */
    @Test
    public void aSensitiveValueIsMaskedInTheListingAndWrittenWholeToTheFile() throws IOException {
        File file = write("secret.properties", "db.password = s3cr3t", "db.host = localhost");

        WithASecret cfg = factory.create(WithASecret.class);
        assertTrue(cfg.toString(), cfg.toString().contains("********"));
        assertFalse(cfg.toString(), cfg.toString().contains("s3cr3t"));

        cfg.setProperty("db.host", "db.example.com");
        cfg.save(file);

        String written = read(file);
        assertTrue(written, written.contains("s3cr3t"));
        assertFalse(written, written.contains("********"));
        assertTrue(written, written.contains("db.example.com"));
    }

    // ------------------------------------------------------ @Sensitive x relaxed binding

    @Sources("file:target/crossing/relaxed-secret.properties")
    interface WithARelaxedSecret extends Config, Accessible {
        @Config.Sensitive
        String dbPassword();
    }

    /**
     * A secret written in a spelling other than the method's is <b>still masked</b>. The mask is computed
     * from the method, the file writes what it likes, and a listing that showed the value because the
     * author used kebab-case would be a leak of exactly the kind this annotation exists to stop.
     */
    @Test
    public void aSecretWrittenInAnotherSpellingIsStillMasked() throws IOException {
        write("relaxed-secret.properties", "db-password = s3cr3t");

        WithARelaxedSecret cfg = factory.create(WithARelaxedSecret.class);
        assertEquals("s3cr3t", cfg.dbPassword());
        assertFalse(cfg.toString(), cfg.toString().contains("s3cr3t"));
        assertTrue(cfg.toString(), cfg.toString().contains("********"));
    }

    // ------------------------------------------------------------- owner.include x key prefix

    @Sources("file:target/crossing/prefixed.properties")
    interface Prefixed extends Config, Accessible {
        @Config.DefaultValue("nothing")
        String fromParent();
    }

    /**
     * A global key prefix moves the keys of every configuration the factory creates. It must <b>not</b>
     * move the directive: <code>owner.include</code> is written in the file by whoever wrote the file, and
     * a prefix set in Java cannot be expected to appear in it.
     */
    @Test
    public void theIncludeDirectiveIsNotMovedByTheKeyPrefix() throws IOException {
        write("parent.properties", "app.fromParent = from the parent");
        write("prefixed.properties", "owner.include = file:target/crossing/parent.properties");

        factory.setProperty("owner.key.prefix", "app.");
        assertEquals("from the parent", factory.create(Prefixed.class).fromParent());
    }

    // ------------------------------------------------------------- owner.include x indexed keys

    @Sources("file:target/crossing/list-child.properties")
    interface Listed extends Config, Accessible {
        List<String> servers();
    }

    /**
     * A list merges across the include boundary <b>element by element</b>, because the merge is by key and
     * an element has a key of its own. So the including file overrides one element and keeps the rest,
     * which is the same thing it does to an ordinary value.
     */
    @Test
    public void aListMergesAcrossTheIncludeBoundaryElementByElement() throws IOException {
        write("list-parent.properties", "servers[0] = alpha", "servers[1] = beta");
        write("list-child.properties",
                "owner.include = file:target/crossing/list-parent.properties",
                "servers[0] = overridden");

        assertEquals(java.util.Arrays.asList("overridden", "beta"), factory.create(Listed.class).servers());
    }

    /**
     * And a gap left between the two files is refused, exactly as one inside a single file is. The rule is
     * about the list the reader gets, not about where the elements were written.
     */
    @Test
    public void aGapAcrossTheIncludeBoundaryIsRefused() throws IOException {
        write("list-parent.properties", "servers[0] = alpha");
        write("list-child.properties",
                "owner.include = file:target/crossing/list-parent.properties",
                "servers[2] = gamma");

        try {
            factory.create(Listed.class).servers();
            fail("a gap is a gap wherever the elements came from");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("servers"));
        }
    }

    // --------------------------------------------------- owner.include x variable expansion

    @Sources("file:target/crossing/var-child.properties")
    interface Composed extends Config, Accessible {
        String url();
    }

    /**
     * A variable resolves across the include boundary, <b>in both directions</b>: the included file may
     * name a key the including one defines. The properties are merged before anything is expanded, so
     * there is no order to get wrong — and that is worth pinning, because it is the reason a template
     * file is usable at all.
     */
    @Test
    public void aVariableResolvesAcrossTheIncludeBoundary() throws IOException {
        write("var-parent.properties", "host = db.example.com", "url = jdbc://${host}:${port}/${name}");
        write("var-child.properties",
                "owner.include = file:target/crossing/var-parent.properties",
                "port = 5432",
                "name = books");

        assertEquals("jdbc://db.example.com:5432/books", factory.create(Composed.class).url());
    }

    // ------------------------------------------------------- relaxed binding x save(File)

    @Sources("file:target/crossing/kebab.properties")
    interface Kebab extends Config, Accessible, Mutable {
        String maxThreads();
    }

    /**
     * A value changed and written back into a file that spells the key <b>its own way</b>.
     * <p>
     * Relaxed binding reads four spellings; the file uses one of them, and that is the one the writer has
     * to find. Writing under the declared spelling instead leaves the old line where it was and adds a
     * second one — the file then holds the same property twice, with two values, and the library warns
     * about a file it wrote itself.
     * </p>
     */
    @Test
    public void savingUpdatesTheSpellingTheFileUses() throws IOException {
        File file = write("kebab.properties", "max-threads = 10");

        Kebab cfg = factory.create(Kebab.class);
        assertEquals("10", cfg.maxThreads());

        cfg.setProperty("maxThreads", "42");
        cfg.save(file);

        String written = read(file);
        assertTrue(written, written.contains("max-threads = 42"));
        assertFalse("the file must not end up holding both spellings: " + written,
                written.contains("maxThreads"));
    }

    /**
     * The same write, made under the spelling the <b>file</b> uses, which is the natural thing to do when
     * you are looking at the file. It must reach the file: a set that is accepted, read back correctly in
     * memory and then silently not saved is a lost update with nothing to notice.
     */
    @Test
    public void aValueSetUnderTheFilesOwnSpellingIsSaved() throws IOException {
        File file = write("kebab.properties", "max-threads = 10");

        Kebab cfg = factory.create(Kebab.class);
        cfg.setProperty("max-threads", "42");
        cfg.save(file);

        assertTrue(read(file), read(file).contains("max-threads = 42"));
    }

    /**
     * And what comes of it: the file a configuration saves must be a file it can read again. Under
     * <code>owner.strict</code> two spellings of one key are a refusal, so a writer that adds a second
     * spelling produces a file that the next start will not load.
     */
    @Test
    public void theFileASaveProducesCanBeReadBackUnderStrict() throws IOException {
        File file = write("kebab.properties", "max-threads = 10");

        Kebab cfg = factory.create(Kebab.class);
        cfg.setProperty("maxThreads", "42");
        cfg.save(file);

        Factory strict = ConfigFactory.newInstance();
        strict.setProperty("owner.strict", "true");
        assertEquals("42", strict.create(Kebab.class).maxThreads());
    }

    // ------------------------------------------------- an encrypted marker x save(File)

    @Sources("file:target/crossing/marker.properties")
    interface WithAMarker extends Config, Accessible, Mutable {
        @Config.Key("db.password")
        String password();

        @Config.Key("db.host")
        String host();
    }

    /**
     * <b>The plain secret must never reach the file.</b> A marker is resolved on the way to the method and
     * the properties keep the marker itself, so a save writes the marker back — writing what the method
     * answered would put in clear into the file the very thing it was encrypted to keep out of it.
     */
    @Test
    public void savingAConfigurationWithAMarkerWritesTheMarkerAndNotTheSecret() throws IOException {
        AesGcmHandler handler = new AesGcmHandler("hunter2".toCharArray());
        String marker = "${$aes-gcm::" + handler.encrypt("s3cr3t") + "}";
        File file = write("marker.properties", "db.password = " + marker, "db.host = localhost");

        factory.registerValueHandler(handler);
        WithAMarker cfg = factory.create(WithAMarker.class);
        assertEquals("s3cr3t", cfg.password());

        cfg.setProperty("db.host", "db.example.com");
        cfg.save(file);

        String written = read(file);
        assertFalse("the secret must not be in the file: " + written, written.contains("s3cr3t"));
        // the marker itself, whose colons the writer escapes as java.util.Properties.store does - which is
        // why this asserts the round trip rather than the text: what matters is that it reads back
        assertTrue(written, written.contains("$aes-gcm"));

        Factory again = ConfigFactory.newInstance();
        again.registerValueHandler(handler);
        assertEquals("s3cr3t", again.create(WithAMarker.class).password());
    }

    // ------------------------------------------------------ @Sensitive x store, and the include

    /**
     * <b>Masking covers the listing and never what is written out.</b> It is deliberate and it surprises
     * people, so it is pinned in both directions: {@code toString()} masks, while {@code store} and
     * {@code storeToXML} — two different code paths — write the value whole, because what goes out has to
     * be able to come back.
     */
    @Test
    public void maskingCoversTheListingAndNeitherOfTheTwoWaysOfWritingOut() throws IOException {
        write("secret.properties", "db.password = s3cr3t", "db.host = localhost");
        WithASecret cfg = factory.create(WithASecret.class);

        java.io.ByteArrayOutputStream text = new java.io.ByteArrayOutputStream();
        cfg.store(text, "a comment");
        java.io.ByteArrayOutputStream xml = new java.io.ByteArrayOutputStream();
        cfg.storeToXML(xml, "a comment");

        assertTrue(cfg.toString(), cfg.toString().contains("********"));
        assertTrue("store must write the value", new String(text.toByteArray()).contains("s3cr3t"));
        assertTrue("storeToXML must too", new String(xml.toByteArray()).contains("s3cr3t"));
    }

    @Sources("file:target/crossing/inc-secret.properties")
    interface AnIncludedSecret extends Config, Accessible {
        @Config.Sensitive
        @Config.Key("db.password")
        String password();
    }

    /** And a secret that arrives from an included file is masked like any other. */
    @Test
    public void aSecretThatCameFromAnIncludedFileIsMasked() throws IOException {
        write("inc-parent.properties", "db.password = s3cr3t");
        write("inc-secret.properties", "owner.include = file:target/crossing/inc-parent.properties");

        AnIncludedSecret cfg = factory.create(AnIncludedSecret.class);
        assertEquals("s3cr3t", cfg.password());
        assertFalse(cfg.toString(), cfg.toString().contains("s3cr3t"));
    }

    // ---------------------------------------------------------- @HotReload x save(File)

    @Sources("file:target/crossing/hot.properties")
    @Config.HotReload(value = 1, type = Config.HotReloadType.SYNC)
    interface Hot extends Config, Accessible, Mutable {
        String value();
    }

    /**
     * Saving the very file being watched. The write is the configuration's own, so the reload it provokes
     * has to find what was just written and not undo it — a configuration that reverted its own save
     * would be unusable, and the failure would look like the disk.
     */
    @Test
    public void savingTheFileItIsWatchingDoesNotUndoTheWrite() throws IOException {
        File file = write("hot.properties", "value = one");

        Hot cfg = factory.create(Hot.class);
        assertEquals("one", cfg.value());

        cfg.setProperty("value", "two");
        cfg.save(file);

        assertTrue(read(file), read(file).contains("value = two"));
        assertEquals("two", cfg.value());
    }

    // ------------------------------------------------------------- imports x owner.include

    @Sources("file:target/crossing/imp-child.properties")
    interface Imported extends Config, Accessible {
        String which();
    }

    /**
     * An import is handed over in Java when the configuration is created; an include is named by a file.
     * The import wins, which is the order everything else already has: what the code says beats what a
     * file says, and a file cannot overrule the caller.
     */
    @Test
    public void anImportBeatsWhatAnIncludedFileSays() throws IOException {
        write("imp-parent.properties", "which = from the included file");
        write("imp-child.properties", "owner.include = file:target/crossing/imp-parent.properties");

        java.util.Map<String, String> imported = new java.util.HashMap<>();
        imported.put("which", "from the import");
        assertEquals("from the import", factory.create(Imported.class, imported).which());
    }

    // ------------------------------------------------------- @DeclaredOnly x save(File)

    @Config.DeclaredOnly
    @Sources("file:target/crossing/declared.properties")
    interface DeclaredOnlySaved extends Config, Accessible, Mutable {
        String mine();
    }

    /**
     * {@link Config.DeclaredOnly} narrows the views; it must not narrow what a save <b>keeps</b>. A key
     * this interface does not declare is somebody else's line either way, and dropping it because a view
     * stopped showing it would delete another reader's configuration.
     */
    @Test
    public void declaredOnlyDoesNotMakeSaveDropSomebodyElsesKeys() throws IOException {
        File file = write("declared.properties", "mine = a", "someone.elses = b");

        DeclaredOnlySaved cfg = factory.create(DeclaredOnlySaved.class);
        cfg.setProperty("mine", "changed");
        cfg.save(file);

        String written = read(file);
        assertTrue(written, written.contains("mine = changed"));
        assertTrue("another reader's key has to survive: " + written, written.contains("someone.elses = b"));
    }

    // --------------------------------------------------- owner.include x an XML of your own

    @Sources("file:target/crossing/own.xml")
    interface OwnXml extends Config, Accessible {
        @Config.DefaultValue("nothing")
        String fromParent();
    }

    /**
     * The one format that <b>cannot</b> carry the directive — and it is not a defect, it is two rules
     * meeting.
     * <p>
     * An XML of your own builds every key from the path of its elements, the root included, so
     * <code>&lt;config&gt;&lt;owner.include&gt;</code> arrives as <code>config.owner.include</code>. The
     * directive is recognised only at the root, exactly so that a key one level down stays somebody's
     * property. That format has no root level to write at, so it cannot express the directive at all. The
     * Java XML properties format has one, and works.
     * </p>
     */
    @Test
    public void anXmlOfYourOwnCannotCarryTheDirective() throws IOException {
        write("xml-parent.properties", "fromParent = from the parent");
        write("own.xml", "<?xml version=\"1.0\"?><config>"
                + "<owner.include>file:target/crossing/xml-parent.properties</owner.include></config>");

        OwnXml cfg = factory.create(OwnXml.class);
        assertEquals("nothing", cfg.fromParent());
        // it is a property, under the root element, and readable as one
        assertEquals("file:target/crossing/xml-parent.properties", cfg.getProperty("config.owner.include"));
    }
}

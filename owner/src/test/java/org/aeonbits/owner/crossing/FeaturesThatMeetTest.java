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
}

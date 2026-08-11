/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config.Sources;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.aeonbits.owner.Origin.Kind.DEFAULT_VALUE;
import static org.aeonbits.owner.Origin.Kind.IMPORT;
import static org.aeonbits.owner.Origin.Kind.RUNTIME;
import static org.aeonbits.owner.Origin.Kind.SOURCE;
import static org.aeonbits.owner.TestConstants.RESOURCES_DIR;
import static org.aeonbits.owner.util.UtilTest.fileFromURI;
import static org.aeonbits.owner.util.UtilTest.save;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Where a property came from: see {@link Traceable} for why the merged properties cannot say on their own,
 * and issue #277 for what it was asked for.
 *
 * @author Matteo Baccan
 */
public class TraceableTest {

    private static final String ONE = "file:" + RESOURCES_DIR + "/TraceableOne.properties";
    private static final String TWO = "file:" + RESOURCES_DIR + "/TraceableTwo.properties";

    @Sources({ONE, TWO})
    @LoadPolicy(LoadType.MERGE)
    public interface MergedConfig extends Config, Accessible, Mutable, Reloadable, Traceable {
        String fromOne();

        String fromTwo();

        String inBoth();

        @DefaultValue("42")
        int defaulted();
    }

    @Sources({ONE, TWO})
    public interface FirstConfig extends Config, Traceable {
        String fromOne();

        String fromTwo();
    }

    private static void writeTheSources() throws Exception {
        save(fileFromURI(ONE), new Properties() {{
            setProperty("fromOne", "one");
            setProperty("inBoth", "from one");
        }});
        save(fileFromURI(TWO), new Properties() {{
            setProperty("fromTwo", "two");
            setProperty("inBoth", "from two");
        }});
    }

    // -------------------------------------------------------------------------------------------------
    // which source answered
    // -------------------------------------------------------------------------------------------------

    @Test
    public void eachPropertyNamesTheSourceItCameFrom() throws Exception {
        writeTheSources();
        MergedConfig cfg = ConfigFactory.create(MergedConfig.class);

        assertEquals(ONE, cfg.originOf("fromOne").source());
        assertEquals(TWO, cfg.originOf("fromTwo").source());
        assertEquals(SOURCE, cfg.originOf("fromOne").kind());
    }

    /**
     * The one that could not be answered before: a key written in two of the merged sources reports the one
     * whose value survived, which under {@link LoadType#MERGE} is the first declared.
     */
    @Test
    public void aKeyInTwoSourcesNamesTheOneThatWon() throws Exception {
        writeTheSources();
        MergedConfig cfg = ConfigFactory.create(MergedConfig.class);

        assertEquals("from one", cfg.inBoth());
        assertEquals(ONE, cfg.originOf("inBoth").source());
    }

    /** Under {@link LoadType#FIRST} the sources after the one that answered are not read at all. */
    @Test
    public void whatNoSourceProducedHasNoOrigin() throws Exception {
        writeTheSources();
        FirstConfig cfg = ConfigFactory.create(FirstConfig.class);

        assertEquals(ONE, cfg.originOf("fromOne").source());
        assertNull("the second source never answered", cfg.originOf("fromTwo"));
    }

    @Test
    public void aKeyThatIsNotThereHasNoOrigin() throws Exception {
        writeTheSources();
        assertNull(ConfigFactory.create(MergedConfig.class).originOf("nowhere"));
    }

    // -------------------------------------------------------------------------------------------------
    // the three places that are not a source
    // -------------------------------------------------------------------------------------------------

    @Test
    public void aDefaultValueSaysSoAndNamesNoSource() throws Exception {
        writeTheSources();
        Origin origin = ConfigFactory.create(MergedConfig.class).originOf("defaulted");

        assertEquals(DEFAULT_VALUE, origin.kind());
        assertNull(origin.source());
        assertFalse("the interface declared it; nobody wrote it", origin.isWritten());
        assertEquals("@DefaultValue", origin.toString());
    }

    @Test
    public void anImportSaysWhichOneItWas() throws Exception {
        writeTheSources();
        Map<String, String> first = new HashMap<>();
        first.put("fromFirstImport", "x");
        Map<String, String> second = new HashMap<>();
        second.put("fromSecondImport", "y");

        MergedConfig cfg = ConfigFactory.create(MergedConfig.class, first, second);

        assertEquals(IMPORT, cfg.originOf("fromFirstImport").kind());
        assertEquals("import[0]", cfg.originOf("fromFirstImport").source());
        assertEquals("import[1]", cfg.originOf("fromSecondImport").source());
    }

    @Test
    public void aValueWrittenAtRunTimeSaysSo() throws Exception {
        writeTheSources();
        MergedConfig cfg = ConfigFactory.create(MergedConfig.class);

        cfg.setProperty("fromOne", "changed");
        assertEquals(RUNTIME, cfg.originOf("fromOne").kind());
        assertEquals("set at run time", cfg.originOf("fromOne").toString());

        cfg.removeProperty("fromOne");
        assertNull(cfg.originOf("fromOne"));
    }

    @Test
    public void aReloadWorksTheOriginsOutAgain() throws Exception {
        writeTheSources();
        MergedConfig cfg = ConfigFactory.create(MergedConfig.class);
        cfg.setProperty("fromOne", "changed");
        assertEquals(RUNTIME, cfg.originOf("fromOne").kind());

        cfg.reload();

        assertEquals("the file is the source again", ONE, cfg.originOf("fromOne").source());
    }

    // -------------------------------------------------------------------------------------------------
    // all of them at once
    // -------------------------------------------------------------------------------------------------

    @Test
    public void everyPropertyIsInTheMapAndNothingElseIs() throws Exception {
        writeTheSources();
        MergedConfig cfg = ConfigFactory.create(MergedConfig.class);

        Map<String, Origin> origins = cfg.origins();
        assertEquals(cfg.propertyNames(), origins.keySet());
        assertEquals(ONE, origins.get("inBoth").source());
        assertEquals(DEFAULT_VALUE, origins.get("defaulted").kind());
    }

    /**
     * The recipe issue #277 asked for, and the reason this exists: with {@code MERGE} over the environment
     * and a file, storing the configuration writes the whole environment back into the file. Filtering by
     * origin is what makes it possible to save only what came from the file.
     */
    @Test
    public void aConfigurationCanBeSavedBackWithoutWhatItDidNotOwn() throws Exception {
        writeTheSources();
        Map<String, String> environment = new HashMap<>();
        environment.put("PATH", "/usr/bin");
        MergedConfig cfg = ConfigFactory.create(MergedConfig.class, environment);

        Properties mine = new Properties();
        for (Map.Entry<String, Origin> entry : cfg.origins().entrySet())
            if (ONE.equals(entry.getValue().source()))
                mine.setProperty(entry.getKey(), cfg.getProperty(entry.getKey()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mine.store(out, null);
        String saved = out.toString("ISO-8859-1");

        assertTrue(saved.contains("fromOne=one"));
        assertTrue(saved.contains("inBoth=from one"));
        assertFalse("what the environment carried is not ours to save", saved.contains("PATH"));
        assertFalse("nor is the other file's", saved.contains("fromTwo"));
        assertFalse("nor is a default nobody wrote", saved.contains("defaulted"));
    }

    // -------------------------------------------------------------------------------------------------
    // what an origin may not say
    // -------------------------------------------------------------------------------------------------

    /**
     * A source may carry credentials, and an origin handed to the caller would be one more place they
     * appear. It is masked exactly as the log lines and the exception messages are.
     */
    @Test
    public void aSourceNeverCarriesItsCredentials() throws Exception {
        Origin origin = Origin.of(new URI("https://user:secret@config.example.com/app.properties"));

        assertFalse(origin.source(), origin.source().contains("secret"));
        assertEquals("https://***@config.example.com/app.properties", origin.source());
    }

    /**
     * An origin is a value: two properties that came from the same place are the same origin, and that is
     * what lets a caller group or filter by it without comparing strings by hand.
     */
    @Test
    public void twoPropertiesFromTheSamePlaceHaveTheSameOrigin() throws Exception {
        writeTheSources();
        MergedConfig cfg = ConfigFactory.create(MergedConfig.class);

        Origin one = cfg.originOf("fromOne");
        Origin alsoOne = cfg.originOf("inBoth");
        Origin two = cfg.originOf("fromTwo");

        assertEquals(one, alsoOne);
        assertEquals(one.hashCode(), alsoOne.hashCode());
        assertEquals(one, one);
        assertNotEquals(one, two);
        assertNotEquals("a default came from nowhere", one, cfg.originOf("defaulted"));
        assertNotEquals("and the source it names is not itself", one, ONE);
    }

    @Test
    public void anOriginReadsAsTheSourceItNames() throws Exception {
        writeTheSources();
        assertEquals(ONE, ConfigFactory.create(MergedConfig.class).originOf("fromOne").toString());
    }

    @Test
    public void theOriginsSurviveSerialization() throws Exception {
        writeTheSources();
        MergedConfig cfg = ConfigFactory.create(MergedConfig.class);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(cfg);
        }
        MergedConfig read;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            read = (MergedConfig) in.readObject();
        }

        assertEquals(ONE, read.originOf("inBoth").source());
        assertEquals(DEFAULT_VALUE, read.originOf("defaulted").kind());
    }
}

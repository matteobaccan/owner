/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Factory;
import org.aeonbits.owner.loaders.Loader;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.aeonbits.owner.Config.LoadType.MERGE;
import static org.aeonbits.owner.Config.Sources.CONVENTIONAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * See: https://github.com/matteobaccan/owner/issues/349
 * <p>
 * thungsten needs <b>a list of places to look for properties files, whose number and paths may change</b>,
 * and asked whether the <code>@SourcesLocatorClass</code> lviggiano sketched in 2016 on
 * <a href="https://github.com/matteobaccan/owner/issues/173">#173</a> was ever implemented. It was not, and
 * these tests are the answer to why it is not needed: <b>a {@link Loader} already decides the sources of a
 * configuration</b>, in Java, at the moment the configuration is created.
 * </p>
 * <p>
 * {@link Loader#defaultSpecsFor(String)} is asked what a given configuration's own files are called, and
 * whatever it answers is looked for. The loader below returns one spec per directory it is told to scan,
 * reads none of them, and is never asked to: the files are ordinary properties files and
 * <code>PropertiesLoader</code> reads them. A locator and a reader are the same interface here, and a
 * locator that reads nothing is a legitimate use of it.
 * </p>
 * <p>
 * What it receives is the classpath prefix of the configuration -
 * <code>classpath:org/aeonbits/owner/issues/Issue349Test$Demo</code> - so what it can derive the paths from
 * is the name of the interface being created. That is the limit worth knowing when deciding whether this
 * covers a case or not.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue349Test {

    /**
     * The locator. Where it looks is a system property here; in an application it would be an environment
     * variable, a mounted file, a service registry - anything Java can read, because this is Java.
     */
    public static class ScanTheseDirectories implements Loader {

        static final String PLACES = "issue349.places";

        @Override
        public String[] defaultSpecsFor(String urlPrefix) {
            String places = System.getProperty(PLACES, "");
            // the prefix is the fully qualified name with dots as slashes, so the simple name is what
            // follows the last of them - and the last '$' as well, a nested interface keeping its own
            String name = urlPrefix.substring(
                    Math.max(urlPrefix.lastIndexOf('/'), urlPrefix.lastIndexOf('$')) + 1);
            List<String> specs = new ArrayList<>();
            for (String place : places.split(",")) {
                if (!place.trim().isEmpty())
                    specs.add("classpath:org/aeonbits/owner/issues/issue349/" + place.trim()
                            + "/" + name + ".properties");
            }
            return specs.toArray(new String[0]);
        }

        @Override
        public boolean accept(URI uri) {
            return false;   // it locates; the reading is somebody else's business
        }

        @Override
        public void load(Properties result, URI uri) throws IOException {
            throw new UnsupportedOperationException("never asked, since accept() turns everything down");
        }
    }

    @LoadPolicy(MERGE)
    public interface Demo extends Config {

        String where();

        @Key("only.in.first")
        String onlyInFirst();

        @Key("only.in.second")
        String onlyInSecond();
    }

    @LoadPolicy(MERGE)
    @Sources({"classpath:org/aeonbits/owner/first.properties", CONVENTIONAL})
    public interface Declaring extends Config {

        String foo();

        String where();
    }

    private static Factory scanning(String places) {
        System.setProperty(ScanTheseDirectories.PLACES, places);
        Factory factory = ConfigFactory.newInstance();
        factory.registerLoader(new ScanTheseDirectories());
        return factory;
    }

    /**
     * A configuration that declares nothing gets whatever the locator listed, in the order it listed it -
     * which under MERGE is the order of precedence.
     */
    @Test
    public void theLoaderDecidesWhichFilesAConfigurationHas() {
        try {
            Demo config = scanning("first,second").create(Demo.class);

            assertEquals("first", config.where());
            assertEquals("yes", config.onlyInFirst());
            assertEquals("the second place is read too, and loses only where both say something",
                    "yes", config.onlyInSecond());
        } finally {
            System.clearProperty(ScanTheseDirectories.PLACES);
        }
    }

    /** The list is not fixed at compile time: the same interface, another set of places, another answer. */
    @Test
    public void theListIsWhateverJavaDecidesAtThatMoment() {
        try {
            assertEquals("second", scanning("second").create(Demo.class).where());
            assertEquals("first", scanning("first,second").create(Demo.class).where());
            assertNull("and nowhere to look is a configuration with no sources, not an error",
                    scanning("").create(Demo.class).where());
        } finally {
            System.clearProperty(ScanTheseDirectories.PLACES);
        }
    }

    /**
     * And it reaches a configuration that <b>does</b> declare its sources, through
     * {@link Sources#CONVENTIONAL}: what the locator lists is part of what the convention stands for, so
     * the two ways of naming sources compose instead of excluding each other.
     */
    @Test
    public void aConfigurationThatDeclaresItsSourcesReachesTheLocatedOnesThroughTheToken() {
        try {
            Declaring config = scanning("second").create(Declaring.class);

            assertEquals("first", config.foo());       // from the declared source
            assertEquals("second", config.where());    // from the located one
        } finally {
            System.clearProperty(ScanTheseDirectories.PLACES);
        }
    }

    /** A place that holds no file for this configuration is passed over, as any absent source is. */
    @Test
    public void aPlaceWithNothingInItIsPassedOver() {
        try {
            assertEquals("first", scanning("nowhere,first").create(Demo.class).where());
        } finally {
            System.clearProperty(ScanTheseDirectories.PLACES);
        }
    }
}

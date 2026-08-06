/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Factory;
import org.aeonbits.owner.loaders.Loader;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * See: https://github.com/lviggiano/owner/issues/124
 * <p>
 * Reading properties from a database needs no support in the core: a {@link Loader} registered on the factory
 * claims a URI scheme of its own and fills a {@link Properties} however it likes. Combined with
 * {@link LoadType#MERGE} it also answers the follow-up question in the thread — read from the database, and
 * fall back to a file for whatever the database does not define.
 * <p>
 * The loader here keeps its rows in memory rather than talking to a database, since what is being verified is
 * the mechanism and not JDBC.
 */
public class Issue124Test {

    /** Stands in for "select key, value from config where ..." */
    public static class TableLoader implements Loader {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean accept(URI uri) {
            return "db".equals(uri.getScheme());
        }

        @Override
        public void load(Properties result, URI uri) throws IOException {
            // one row per property, as a query would return them
            result.setProperty("server.host", "dbhost");
            result.setProperty("server.port", "9090");
        }

        @Override
        public String defaultSpecFor(String uriPrefix) {
            return uriPrefix + ".db";
        }
    }

    @Sources({"db://config/server", "classpath:org/aeonbits/owner/first.properties"})
    @LoadPolicy(LoadType.MERGE)
    public interface ServerConfig extends Config {
        @Key("server.host")
        String host();

        @Key("server.port")
        int port();

        /** Defined only in first.properties: the database does not know about it. */
        String foo();

        String neverDefined();
    }

    @Test
    public void aCustomLoaderFeedsTheConfiguration() {
        Factory factory = ConfigFactory.newInstance();
        factory.registerLoader(new TableLoader());

        ServerConfig cfg = factory.create(ServerConfig.class);

        assertEquals("dbhost", cfg.host());
        assertEquals(9090, cfg.port());
    }

    /** The follow-up question in the thread: what the database does not define falls back to the file. */
    @Test
    public void whatTheLoaderDoesNotDefineFallsBackToTheNextSource() {
        Factory factory = ConfigFactory.newInstance();
        factory.registerLoader(new TableLoader());

        ServerConfig cfg = factory.create(ServerConfig.class);

        assertEquals("first", cfg.foo());
        assertNull(cfg.neverDefined());
    }
}

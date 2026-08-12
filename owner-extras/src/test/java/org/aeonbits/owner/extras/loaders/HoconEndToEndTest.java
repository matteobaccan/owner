/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.aeonbits.owner.Config.Sources;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A HOCON document read the way anything else is read: nested interfaces, a list of sections, a map of
 * sections named by the file, an indexed list of values, and a type conversion - with nothing registered
 * and nothing written about HOCON in the interface.
 *
 * <p>
 * This is the claim the whole loader rests on. Nothing in the mapping is specific to HOCON: the document's
 * shape becomes the keys the rest of the library already reads, so the reader written for the flattening
 * convention and a format it was not written for meet without either being changed.
 * </p>
 *
 * @author Matteo Baccan
 */
public class HoconEndToEndTest {

    @Sources("classpath:org/aeonbits/owner/extras/loaders/cluster.conf")
    public interface ClusterConfig extends Config {
        String datacentre();

        ServerConfig server();

        List<ServerConfig> servers();

        Map<String, PoolConfig> pools();

        List<Integer> ports();

        String proxy();

        @DefaultValue("a default nobody overrode")
        String absent();
    }

    public interface ServerConfig extends Config {
        String host();

        int port();

        String region();

        Duration timeout();
    }

    public interface PoolConfig extends Config {
        int size();
    }

    private final ClusterConfig cfg = ConfigFactory.create(ClusterConfig.class);

    @Test
    public void theLoaderIsFoundWithoutAnythingBeingRegistered() {
        assertEquals("eu-west", cfg.datacentre());
    }

    @Test
    public void anObjectIsANestedInterface() {
        assertEquals("localhost", cfg.server().host());
        assertEquals(8080, cfg.server().port());
    }

    @Test
    public void thePartOfTheObjectWrittenTwiceMerged() {
        // 30s is HOCON's own way of writing it and java.time.Duration is converted automatically
        assertEquals(Duration.ofSeconds(30), cfg.server().timeout());
    }

    @Test
    public void aListOfObjectsIsAListOfSections() {
        assertEquals(2, cfg.servers().size());
        assertEquals("alpha", cfg.servers().get(0).host());
        assertEquals("beta", cfg.servers().get(1).host());
    }

    @Test
    public void aSubstitutionInsideAListOfObjectsWasResolved() {
        assertEquals("eu-west", cfg.servers().get(0).region());
        assertEquals("eu-west", cfg.servers().get(1).region());
    }

    @Test
    public void anObjectWhoseKeysTheFileDecidesIsAMapOfSections() {
        assertEquals(2, cfg.pools().size());
        assertTrue(cfg.pools().keySet().containsAll(Arrays.asList("small", "large")));
        assertEquals(4, cfg.pools().get("small").size());
        assertEquals(64, cfg.pools().get("large").size());
    }

    @Test
    public void aListOfValuesIsATypedList() {
        assertEquals(Arrays.asList(80, 443), cfg.ports());
    }

    @Test
    public void aNullIsAKeyThatIsNotThere() {
        assertNull(cfg.proxy());
    }

    @Test
    public void aDefaultStillApplies() {
        assertEquals("a default nobody overrode", cfg.absent());
    }
}

/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.toml;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.aeonbits.owner.Config.Sources;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A TOML document read the way anything else is read: nested interfaces, an array of tables as a list of
 * sections, a map of sections named by the file, an indexed list, and the four date-time types — with
 * nothing registered and nothing about TOML written in the interface.
 *
 * <p>
 * The date-times are the point worth watching. They convert with no TOML-specific code at all, the
 * conversion chain having learnt to build a type with its static <code>parse</code> factory, and
 * <code>maintenance</code> is written with a space where ISO wants a <code>T</code> — which the parser
 * canonicalises, since that is one value with two spellings.
 * </p>
 *
 * @author Matteo Baccan
 */
public class TomlEndToEndTest {

    @Sources("classpath:org/aeonbits/owner/formats/toml/cluster.toml")
    public interface ClusterConfig extends Config {
        String datacentre();

        List<Integer> ports();

        ServerConfig server();

        List<ServerConfig> servers();

        Map<String, PoolConfig> pools();

        LocalDate released();

        LocalTime opensAt();

        LocalDateTime maintenance();

        OffsetDateTime createdAt();

        @DefaultValue("a default nobody overrode")
        String absent();
    }

    public interface ServerConfig extends Config {
        String host();

        int port();

        Duration timeout();

        int weight();
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
    public void aTableIsANestedInterface() {
        assertEquals("localhost", cfg.server().host());
        assertEquals(8080, cfg.server().port());
        assertEquals(Duration.ofSeconds(30), cfg.server().timeout());
    }

    @Test
    public void anArrayOfTablesIsAListOfSections() {
        assertEquals(2, cfg.servers().size());
        assertEquals("alpha", cfg.servers().get(0).host());
        assertEquals("beta", cfg.servers().get(1).host());
    }

    @Test
    public void theFourSpellingsOfAnIntegerAllConvert() {
        assertEquals(16, cfg.servers().get(0).weight());     // 0x10
        assertEquals(8, cfg.servers().get(1).weight());      // 0b1000
        assertEquals(64, cfg.pools().get("large").size());   // 6_4
    }

    @Test
    public void tablesNamedByTheFileAreAMapOfSections() {
        assertEquals(2, cfg.pools().size());
        assertTrue(cfg.pools().keySet().containsAll(Arrays.asList("small", "large")));
        assertEquals(4, cfg.pools().get("small").size());
    }

    @Test
    public void anArrayOfValuesIsATypedList() {
        assertEquals(Arrays.asList(80, 443), cfg.ports());
    }

    @Test
    public void theFourDateAndTimeTypesConvertWithNoCodeOfTheirOwn() {
        assertEquals(LocalDate.of(2026, 8, 12), cfg.released());
        assertEquals(LocalTime.of(7, 32), cfg.opensAt());
        assertEquals(OffsetDateTime.parse("1979-05-27T07:32:00Z"), cfg.createdAt());
    }

    @Test
    public void aDateTimeWrittenWithASpaceStillConverts() {
        assertEquals(LocalDateTime.of(2026, 8, 12, 3, 0), cfg.maintenance());
    }

    @Test
    public void aDefaultStillApplies() {
        assertEquals("a default nobody overrode", cfg.absent());
    }
}

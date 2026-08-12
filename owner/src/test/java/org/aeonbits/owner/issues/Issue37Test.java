/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.aeonbits.owner.util.Collections.entry;
import static org.aeonbits.owner.util.Collections.map;
import static org.junit.Assert.assertEquals;

/**
 * See: https://github.com/lviggiano/owner/issues/37
 * <p>
 * A collection of <em>complex</em> types: the reporter asked for <code>ServerConfig[] servers()</code>,
 * "could also be <code>List&lt;ServerConfig&gt;</code> or <code>Map&lt;String, ServerConfig&gt;</code>",
 * over the keys <code>servers.1.host</code> and <code>servers.2.host</code>, and expected two servers or a
 * map keyed <code>"1"</code> and <code>"2"</code>.
 * <p>
 * All three shapes read in 2.0.0, through the nested configuration interfaces. The keys written in the
 * issue are the map: a name between the dots is a key, so <code>servers.1.host</code> gives exactly the
 * <code>"1"</code> and <code>"2"</code> the issue asked for. An ordered sequence is written
 * <code>servers[0].host</code> instead, which is the same indexed form every format here flattens to, and
 * fills a <code>List</code> or an array indifferently.
 * <p>
 * The other reading of the title — a collection whose elements are themselves collections, such as
 * <code>List&lt;List&lt;String&gt;&gt;</code> — is not this, and is refused rather than read: see
 * {@link org.aeonbits.owner.NestedCollectionsTest}.
 */
public class Issue37Test {

    public interface ServerConfig extends Config {
        String host();

        int port();
    }

    public interface ByName extends Config {
        Map<String, ServerConfig> servers();
    }

    public interface Ordered extends Config {
        List<ServerConfig> servers();
    }

    public interface OrderedAsArray extends Config {
        ServerConfig[] servers();
    }

    private static final Map<String, String> NAMED = map(
            entry("servers.1.host", "www.google.com"),
            entry("servers.1.port", "80"),
            entry("servers.2.host", "www.github.com"),
            entry("servers.2.port", "80"));

    private static final Map<String, String> INDEXED = map(
            entry("servers[0].host", "www.google.com"),
            entry("servers[0].port", "80"),
            entry("servers[1].host", "www.github.com"),
            entry("servers[1].port", "80"));

    @Test
    public void theKeysWrittenInTheIssueReadAsAMapOfSections() {
        Map<String, ServerConfig> servers = ConfigFactory.create(ByName.class, NAMED).servers();

        assertEquals(2, servers.size());
        assertEquals("www.google.com", servers.get("1").host());
        assertEquals(80, servers.get("1").port());
        assertEquals("www.github.com", servers.get("2").host());
    }

    @Test
    public void anIndexedKeyReadsAsAListOfSections() {
        List<ServerConfig> servers = ConfigFactory.create(Ordered.class, INDEXED).servers();

        assertEquals(2, servers.size());
        assertEquals("www.google.com", servers.get(0).host());
        assertEquals("www.github.com", servers.get(1).host());
        assertEquals(80, servers.get(1).port());
    }

    @Test
    public void anArrayOfSectionsReadsTheSameWayAListDoes() {
        ServerConfig[] servers = ConfigFactory.create(OrderedAsArray.class, INDEXED).servers();

        assertEquals(2, servers.length);
        assertEquals("www.google.com", servers[0].host());
        assertEquals("www.github.com", servers[1].host());
    }
}

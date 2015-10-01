/*
 * Copyright 2015 ThoughtWorks, Inc.
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class JSONLoaderTest {

    @Test
    public void testJSONReading() throws IOException {
        JSONLoader loader = new JSONLoader();
        Properties props = new Properties();
        loader.load(props, getClass().getClassLoader().getResourceAsStream("org/aeonbits/owner/server.test.json"));

        assertEquals(7, props.size());
        assertEquals("80", props.getProperty("server.http.port"));
        assertEquals("localhost", props.getProperty("server.http.hostname"));
        assertEquals("22", props.getProperty("server.ssh.port"));
        assertEquals("127.0.0.1", props.getProperty("server.ssh.address"));
        assertEquals("60", props.getProperty("server.ssh.alive.interval"));
        assertEquals("admin", props.getProperty("server.ssh.user"));
        assertEquals("bob, alice", props.getProperty("server.admins"));
    }
}

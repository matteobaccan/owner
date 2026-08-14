/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A source read over the network, which has worked since 1.0.5 and was never written down.
 * <p>
 * Every loader opens its source with <code>uri.toURL().openStream()</code>, so a
 * {@link Config.Sources} spec may name <b>any protocol the JVM knows</b> — <code>http</code>,
 * <code>https</code>, <code>jar</code>, <code>ftp</code> — and the format is still chosen from the
 * extension after the stream is open. That is most of what people mean by "configuration in the cloud":
 * a pre-signed S3 URL, an Azure Blob SAS, a signed GCS URL and a config server are all HTTPS.
 * </p>
 * <p>
 * <b>The server here is the JDK's own</b>, from <code>com.sun.net.httpserver</code>, and the files it
 * serves are this repository's — <code>first.properties</code> and <code>second.properties</code>, the
 * same two the merge tests use. So the test proves the capability against real files without reaching the
 * network: a test that fetched from the internet would fail on a train, and this build runs offline.
 * </p>
 * <p>
 * The port is chosen by the operating system and handed to the annotation through
 * {@link Factory#setProperty}, because a <code>@Sources</code> spec is expanded before it is read — which
 * is the same machinery a deployment uses to point one artifact at several environments.
 * </p>
 *
 * @author Matteo Baccan
 */
public class NetworkSourceTest {

    /** The factory property the <code>@Sources</code> specs below expand. */
    private static final String PORT = "test.http.port";

    private HttpServer server;

    @Before
    public void serveThisRepositoryOverHttp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serveTestResource);
        server.start();
        ConfigFactory.setProperty(PORT, String.valueOf(server.getAddress().getPort()));
    }

    @After
    public void stopServing() {
        server.stop(0);
        ConfigFactory.clearProperty(PORT);
    }

    /**
     * Serves whatever the path names out of this module's test resources; 404 for anything else, and 500
     * for the one path that asks for it — the two are not the same case, as the tests below show.
     */
    private void serveTestResource(HttpExchange exchange) throws IOException {
        if (exchange.getRequestURI().getPath().contains("refuse")) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }
        String resource = "org/aeonbits/owner" + exchange.getRequestURI().getPath();
        try (InputStream found = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (found == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = read(found);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private static byte[] read(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int n = input.read(buffer); n >= 0; n = input.read(buffer))
            bytes.write(buffer, 0, n);
        return bytes.toByteArray();
    }

    @Config.Sources("http://127.0.0.1:${test.http.port}/first.properties")
    public interface OverHttp extends Config {
        String foo();

        String baz();
    }

    @Test
    public void aSourceOverHttpIsReadLikeAnyOther() {
        OverHttp config = ConfigFactory.create(OverHttp.class);
        assertEquals("first", config.foo());
        assertEquals("first", config.baz());
    }

    /** Nothing about the loading strategy changes: a remote source merges with a local one. */
    @Config.LoadPolicy(Config.LoadType.MERGE)
    @Config.Sources({
            "http://127.0.0.1:${test.http.port}/first.properties",
            "classpath:org/aeonbits/owner/second.properties"})
    public interface RemoteFirstThenLocal extends Config {
        String foo();

        String bar();

        String baz();
    }

    @Test
    public void aRemoteSourceMergesWithALocalOne() {
        RemoteFirstThenLocal config = ConfigFactory.create(RemoteFirstThenLocal.class);
        assertEquals("the remote source wins where both have the key", "first", config.foo());
        assertEquals("and the local one fills what it does not say", "second", config.bar());
        assertEquals("first", config.baz());
    }

    /**
     * A source that answers 404 is a source that could not be read, and takes the ordinary path: passed
     * over with a warning, so the configuration goes on with what it did read. This is the case that makes
     * a remote source usable at all — a network is not a disk.
     */
    @Config.LoadPolicy(Config.LoadType.MERGE)
    @Config.Sources({
            "http://127.0.0.1:${test.http.port}/nothing-is-bound-here.properties",
            "classpath:org/aeonbits/owner/second.properties"})
    public interface RemoteSourceMissing extends Config {
        String foo();
    }

    @Test
    public void aRemoteSourceThatAnswersNotFoundIsPassedOver() {
        assertEquals("second", ConfigFactory.create(RemoteSourceMissing.class).foo());
    }

    /**
     * And it stays passed over <b>even under {@code owner.strict}</b>, which is the rule this test exists
     * to pin down and which I got wrong first: a 404 is not "the server refused", it is "there is nothing
     * there" — the network's version of a file that does not exist, and the case
     * {@link Config.LoadType#FIRST} expects by design. Refusing it would break the commonest shape a
     * configuration has.
     */
    @Test
    public void aRemoteSourceThatAnswersNotFoundStaysPassedOverUnderStrict() {
        assertEquals("second", strictFactory().create(RemoteSourceMissing.class).foo());
    }

    /** A server that is there and refuses is the other case, and the one strict exists for. */
    @Config.LoadPolicy(Config.LoadType.MERGE)
    @Config.Sources({
            "http://127.0.0.1:${test.http.port}/refuse.properties",
            "classpath:org/aeonbits/owner/second.properties"})
    public interface RemoteSourceRefusing extends Config {
        String foo();
    }

    @Test
    public void aRemoteSourceThatRefusesIsPassedOverWithAWarning() {
        assertEquals("second", ConfigFactory.create(RemoteSourceRefusing.class).foo());
    }

    @Test
    public void andUnderStrictARefusingRemoteSourceIsAnError() {
        try {
            strictFactory().create(RemoteSourceRefusing.class);
            fail("a server answering 500 is there and refusing, which owner.strict refuses back");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("could not be read"));
        }
    }

    private Factory strictFactory() {
        Factory factory = ConfigFactory.newInstance();
        factory.setProperty(PORT, ConfigFactory.getProperty(PORT));
        factory.setProperty(PropertiesManager.STRICT, "true");
        return factory;
    }
}

/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A source whose protocol the JVM did not know until the application taught it — read by OWNER with no
 * change of its own.
 *
 * <p>
 * This is the reference for <a href="https://github.com/matteobaccan/owner/issues/130">#130</a> and for
 * every request shaped like it: <code>s3:</code>, <code>gs:</code>, <code>consul:</code>. Every loader
 * here opens its source with <code>uri.toURL().openStream()</code>, so <b>the question is never whether
 * OWNER speaks a protocol — it is whether the JVM does</b>, and the JVM has an extension point for
 * exactly that. A handler registered there needs no loader, nothing registered with any factory of ours,
 * and no line of this library changed. Nothing below mentions OWNER except the configuration interfaces.
 * </p>
 *
 * <h2>Why this route and not the newer one</h2>
 * <p>
 * {@link URL#setURLStreamHandlerFactory} exists in <b>Java 8</b>, which is this library's baseline, so
 * what is demonstrated here is available to every user it has. Java 9 added
 * <code>java.net.spi.URLStreamHandlerProvider</code>, a service that is tidier — it needs no call and has
 * no once-per-JVM limit — and a reader on 9 or later should prefer it. It is not what this test uses,
 * because the test sources compile at the same release as the shipped ones and a demonstration that only
 * works above the baseline would be the wrong reference to hand somebody.
 * </p>
 *
 * <h2>The one constraint, and why it is an application's decision</h2>
 * <p>
 * The factory may be set <b>once</b> for the life of a JVM. That is precisely why it is not something a
 * library does on your behalf, and why OWNER does not: it is a decision that belongs to the application,
 * beside the credentials such a handler would need. This test is the only caller in this module — if
 * another one appears, both will fail loudly rather than quietly, which is the right outcome.
 * </p>
 *
 * @author Matteo Baccan
 */
public class UnknownProtocolSourceTest {

    /** A scheme nothing else uses, so teaching it to this JVM can break nothing. */
    private static final String SCHEME = "owner-demo";

    /** What the handler answers with, standing in for an object store's client. */
    private static final String CONTENT = "foo=from-the-handler\nbaz=from-the-handler\n";

    @BeforeClass
    public static void teachTheJvmAProtocolItDoesNotKnow() {
        URL.setURLStreamHandlerFactory(protocol -> {
            // null for everything else, which is the contract: the JVM then falls back on its own
            // handlers, so http, https, file and jar keep working exactly as before
            if (!SCHEME.equals(protocol))
                return null;
            return new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() {
                            // nothing to connect to: a real handler would call an SDK here
                        }

                        @Override
                        public InputStream getInputStream() throws IOException {
                            if (url.getPath().contains("absent"))
                                throw new IOException("no such object: " + url);
                            return new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8));
                        }
                    };
                }
            };
        });
    }

    @Test
    public void theJvmResolvesTheSchemeNow() throws Exception {
        assertEquals(SCHEME, URI.create("owner-demo://bucket/app.properties").toURL().getProtocol());
    }

    /** And the built-in protocols are untouched, which a factory that answered for everything would break. */
    @Test
    public void theProtocolsTheJvmAlreadyKnewStillWork() throws Exception {
        assertEquals("https", URI.create("https://example.com/app.properties").toURL().getProtocol());
        assertEquals("file", URI.create("file:/etc/app.properties").toURL().getProtocol());
    }

    @Config.Sources("owner-demo://bucket/app.properties")
    public interface OverAnInventedProtocol extends Config {
        String foo();

        String baz();
    }

    /** The point of the whole file: a protocol OWNER has never heard of, read as an ordinary source. */
    @Test
    public void aSourceOverAProtocolOwnerDoesNotKnowIsReadAnyway() {
        OverAnInventedProtocol config = ConfigFactory.create(OverAnInventedProtocol.class);
        assertEquals("from-the-handler", config.foo());
        assertEquals("from-the-handler", config.baz());
    }

    /** It is an ordinary source in every other respect: it takes part in a merge like any other. */
    @Config.LoadPolicy(Config.LoadType.MERGE)
    @Config.Sources({
            "owner-demo://bucket/app.properties",
            "classpath:org/aeonbits/owner/second.properties"})
    public interface MergedWithALocalFile extends Config {
        String foo();

        String bar();
    }

    @Test
    public void itMergesWithTheOtherSourcesLikeAnyOther() {
        MergedWithALocalFile config = ConfigFactory.create(MergedWithALocalFile.class);
        assertEquals("the invented protocol wins where both have the key", "from-the-handler", config.foo());
        assertEquals("and the local file fills what it does not say", "second", config.bar());
    }

    /**
     * A handler that cannot produce the object is a source that could not be read, and takes the path
     * every other unreadable source takes — so the diagnostics and <code>owner.strict</code> apply to a
     * protocol nobody here has ever heard of.
     */
    @Config.LoadPolicy(Config.LoadType.MERGE)
    @Config.Sources({
            "owner-demo://bucket/absent.properties",
            "classpath:org/aeonbits/owner/second.properties"})
    public interface TheObjectIsNotThere extends Config {
        String foo();
    }

    @Test
    public void whenTheHandlerCannotProduceItTheSourceIsPassedOver() {
        assertEquals("second", ConfigFactory.create(TheObjectIsNotThere.class).foo());
    }

    @Test
    public void andUnderStrictItIsRefused() {
        Factory factory = ConfigFactory.newInstance();
        factory.setProperty(PropertiesManager.STRICT, "true");
        try {
            factory.create(TheObjectIsNotThere.class);
            fail("a source that was named and could not be read is refused under owner.strict");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("could not be read"));
        }
    }

    /** A scheme nobody taught the JVM is still unknown, so a typo in a scheme cannot look like a source. */
    @Test
    public void aSchemeNobodyTaughtTheJvmIsStillUnknown() {
        try {
            URI.create("owner-nobody-registered-this://bucket/x.properties").toURL();
            fail("that protocol should not resolve");
        } catch (MalformedURLException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("protocol"));
        }
    }
}

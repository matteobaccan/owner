/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/matteobaccan/owner/issues/319
 * <p>
 * alexander-poulikakos asked why <code>hello()</code> expands the variables and
 * <code>getProperty("b")</code> does not, having to declare a method for every property in order to get the
 * value the library already knows how to build — and then answered the question in the same thread with a
 * workaround that reaches this class's private substitutor field by reflection. When somebody breaks
 * encapsulation to get a thing, the thing is worth having.
 * <p>
 * Since 2.0.0 the methods that answer with a <b>value</b> expand it and the methods that write the
 * properties <b>out</b> do not, which is the distinction these tests pin down.
 */
public class Issue319Test {

    /** The properties file written in the thread. */
    private static Properties values() {
        Properties p = new Properties();
        p.setProperty("s", "say");
        p.setProperty("hello", "${s} HELLO");
        p.setProperty("b", "${hello} AGAIN!");
        return p;
    }

    public interface MyConfig extends Config, Accessible {
        String hello();
    }

    @Test
    public void theOutputTheReporterAskedFor() {
        MyConfig conf = ConfigFactory.create(MyConfig.class, values());

        assertEquals("say HELLO", conf.hello());
        assertEquals("say HELLO AGAIN!", conf.getProperty("b"));
    }

    @Test
    public void theRawValueIsStillReachable() {
        MyConfig conf = ConfigFactory.create(MyConfig.class, values());

        assertEquals("${hello} AGAIN!", conf.getRawProperty("b"));
        assertNull(conf.getRawProperty("nowhere"));
        assertEquals("as given", conf.getRawProperty("nowhere", "as given"));
        assertEquals("${hello}", conf.getRawProperty("nowhere", "${hello}"));
    }

    @Test
    public void theDefaultOfTheCallerIsExpandedToo() {
        MyConfig conf = ConfigFactory.create(MyConfig.class, values());

        assertEquals("say HELLO AGAIN!", conf.getProperty("b", "unused"));
        assertEquals("say HELLO and more", conf.getProperty("nowhere", "${hello} and more"));
    }

    @Test
    public void fillHandsOutTheValuesToUse() {
        MyConfig conf = ConfigFactory.create(MyConfig.class, values());

        Map<String, String> map = new HashMap<>();
        conf.fill(map);

        assertEquals("say HELLO AGAIN!", map.get("b"));
        assertEquals("say HELLO", map.get("hello"));
        assertEquals("say", map.get("s"));
    }

    /**
     * The other half of the rule, and the one that makes the first half safe: what is written out is
     * written as it was, so that saving it back does not lose the reference that produced the value.
     */
    @Test
    public void whatIsWrittenOutKeepsItsVariables() throws Exception {
        MyConfig conf = ConfigFactory.create(MyConfig.class, values());

        ByteArrayOutputStream stored = new ByteArrayOutputStream();
        conf.store(stored, null);
        assertTrue(stored.toString("UTF-8").contains("${hello}"));

        ByteArrayOutputStream listed = new ByteArrayOutputStream();
        conf.list(new PrintStream(listed, true, "UTF-8"));
        assertTrue(listed.toString("UTF-8").contains("${hello}"));

        StringWriter written = new StringWriter();
        conf.list(new PrintWriter(written, true));
        assertTrue(written.toString().contains("${hello}"));
    }

    @Config.DisableFeature(Config.DisableableFeature.VARIABLE_EXPANSION)
    public interface ExpansionDisabled extends Config, Accessible {
        String hello();
    }

    /**
     * <code>@DisableFeature</code> written on the interface has to reach these methods as well, and it
     * cannot be read off them: they are declared on {@link Accessible} and never on the interface the user
     * wrote.
     */
    @Test
    public void disableFeatureOnTheInterfaceReachesGetPropertyAndFill() {
        ExpansionDisabled conf = ConfigFactory.create(ExpansionDisabled.class, values());

        assertEquals("${s} HELLO", conf.hello());
        assertEquals("${hello} AGAIN!", conf.getProperty("b"));

        Map<String, String> map = new HashMap<>();
        conf.fill(map);
        assertEquals("${hello} AGAIN!", map.get("b"));
    }

    public interface OverloadingTheAccessibleNames extends Config, Accessible {
        @Config.Key("a")
        @Config.DefaultValue("A")
        String getProperty();

        @Config.Key("b")
        @Config.DefaultValue("B")
        String getProperty(int index);

        @Config.Key("c")
        @Config.DefaultValue("C")
        String fill();

        @Config.Key("d")
        @Config.DefaultValue("D")
        String fill(String name);
    }

    /**
     * An overload of <code>getProperty</code> or <code>fill</code> that is not the one {@link Accessible}
     * declares is an ordinary mapping method, and is not intercepted. Without the check on the parameter
     * types the first argument would be cast to a {@link String} and a call like <code>getProperty(3)</code>
     * would end in a {@link ClassCastException} rather than reading the key it maps.
     */
    @Test
    public void anOverloadOfTheseNamesIsAnOrdinaryMappingMethod() {
        OverloadingTheAccessibleNames conf = ConfigFactory.create(OverloadingTheAccessibleNames.class);

        assertEquals("A", conf.getProperty());
        assertEquals("B", conf.getProperty(3));
        assertEquals("C", conf.fill());
        assertEquals("D", conf.fill("whatever"));

        // and Accessible's own are still Accessible's own, reading the keys those methods declared
        assertEquals("A", conf.getProperty("a"));
        assertEquals("D", conf.getRawProperty("d"));
    }

    public interface WithSecret extends Config, Accessible {
        @Config.Sensitive
        String password();

        @Config.Key("jdbc.url")
        String jdbcUrl();
    }

    /**
     * A masked value referred to by a value that is not masked. The mask is per key, so an expanded listing
     * would print the secret in clear inside the line that refers to it: this is why the listing stays the
     * raw one, and the test says so as the security property it is rather than as a formatting detail.
     */
    @Test
    public void aListingNeverResolvesAMaskedValueIntoAnotherLine() throws Exception {
        Properties p = new Properties();
        p.setProperty("password", "s3cret");
        p.setProperty("jdbc.url", "jdbc:h2:mem:test?password=${password}");

        WithSecret conf = ConfigFactory.create(WithSecret.class, p);

        ByteArrayOutputStream listed = new ByteArrayOutputStream();
        conf.list(new PrintStream(listed, true, "UTF-8"));
        String output = listed.toString("UTF-8");

        assertTrue(output.contains("password=" + Config.Sensitive.MASK));
        assertTrue(output.contains("${password}"));
        assertEquals("the secret is nowhere in the listing", -1, output.indexOf("s3cret"));

        // and it is still reachable by whoever asks for it on purpose
        assertEquals("jdbc:h2:mem:test?password=s3cret", conf.getProperty("jdbc.url"));
    }
}

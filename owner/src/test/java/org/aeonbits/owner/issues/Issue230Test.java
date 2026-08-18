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
import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Mutable;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/matteobaccan/owner/issues/230
 * <p>
 * ysmartin reported in 2018 that a configuration whose key holds a variable —
 * <code>@Key("${myproject.prefix}.debug")</code> — is listed twice by the {@link Accessible} methods: once
 * under <code>myproject.debug</code>, which is the key the method reads, and once under
 * <code>${myproject.prefix}.debug</code>, which is the key as it is written.
 * </p>
 * <p>
 * <b>lviggiano answered in 2020 that this is consistent</b>, the expansion being resolved when the method
 * is called and the listing being a dump of the internal structure. The first half is right and is why
 * the entry exists at all: a {@link DefaultValue} is registered under the key as written, and that is also
 * where the lookup looks when the expanded key finds nothing. What the answer did not have in front of it
 * is that the entry is not the property under an unexpanded name — <b>it is a place no method can ever
 * read</b>, sitting beside the key that is read, so a listing showing both says the configuration has two
 * properties where it has one.
 * </p>
 * <p>
 * It is shown under the key that is read now. The same rule fixed {@link org.aeonbits.owner.Accessible}'s
 * neighbour, <code>save(File)</code>, where naming the wrong key cost the value rather than the tidiness.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue230Test {

    @Sources("classpath:org/aeonbits/owner/issues/issue230.properties")
    public interface MyProjectConfig extends Config, Accessible {

        @Key("myproject.prefix")
        @DefaultValue("myproject")
        String configPrefix();

        @Key("${myproject.prefix}.debug")
        @DefaultValue("false")
        boolean debug();
    }

    /** The same interface over a file that has the prefix and not the property it names. */
    @Sources("classpath:org/aeonbits/owner/issues/issue230-nothing-but-the-prefix.properties")
    public interface NothingButThePrefix extends Config, Accessible {

        @Key("myproject.prefix")
        @DefaultValue("myproject")
        String configPrefix();

        @Key("${myproject.prefix}.debug")
        @DefaultValue("false")
        boolean debug();
    }

    /** The same shape again, writable, so that the prefix can be moved while the configuration runs. */
    public interface Movable extends Mutable, Accessible {

        @Key("myproject.prefix")
        @DefaultValue("myproject")
        String configPrefix();

        @Key("${myproject.prefix}.debug")
        @DefaultValue("false")
        boolean debug();
    }

    private String listOf(Accessible config) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        config.list(new PrintStream(out, true));
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** The report itself, as propertyNames() showed it. */
    @Test
    public void theKeyThatIsReadIsListedAndTheOneThatIsWrittenIsNot() {
        Set<String> names = ConfigFactory.create(MyProjectConfig.class).propertyNames();

        assertTrue(names.toString(), names.contains("myproject.debug"));
        assertFalse(names.toString(), names.contains("${myproject.prefix}.debug"));
    }

    /** And the value listed is the one the method answers with, not the default hiding behind it. */
    @Test
    public void andTheValueIsTheOneTheMethodAnswersWith() {
        MyProjectConfig config = ConfigFactory.create(MyProjectConfig.class);

        assertTrue(config.debug());
        assertEquals("true", config.getProperty("myproject.debug"));
        assertTrue(listOf(config), listOf(config).contains("myproject.debug=true"));
        assertFalse(listOf(config), listOf(config).contains("${myproject.prefix}"));
    }

    /**
     * When the file has no such property the default is all there is, and it is shown under the key it
     * would be read by — not under the key it happens to be stored below.
     */
    @Test
    public void aDefaultIsShownUnderTheKeyItWouldBeReadBy() {
        NothingButThePrefix config = ConfigFactory.create(NothingButThePrefix.class);

        assertFalse(config.debug());
        Set<String> names = config.propertyNames();
        assertTrue(names.toString(), names.contains("myproject.debug"));
        assertFalse(names.toString(), names.contains("${myproject.prefix}.debug"));
        assertTrue(listOf(config), listOf(config).contains("myproject.debug=false"));
    }

    /** Every way of looking at it says the same thing, which is the point of having one view underneath. */
    @Test
    public void storeAndFillAndToStringAgree() throws IOException {
        MyProjectConfig config = ConfigFactory.create(MyProjectConfig.class);

        StringWriter stored = new StringWriter();
        config.store(new PrintWriter(stored), null);
        assertTrue(stored.toString(), stored.toString().contains("myproject.debug=true"));
        assertFalse(stored.toString(), stored.toString().contains("${myproject.prefix}"));

        Map<String, String> filled = new HashMap<>();
        config.fill(filled);
        assertEquals("true", filled.get("myproject.debug"));
        assertFalse(filled.toString(), filled.containsKey("${myproject.prefix}.debug"));

        assertTrue(config.toString(), config.toString().contains("myproject.debug"));
        assertFalse(config.toString(), config.toString().contains("${myproject.prefix}"));
    }

    /**
     * <b>What is listed is what answers.</b> Naming the read key in the listing while the value stays
     * under the key as written would move the confusion rather than remove it: a loop over
     * <code>propertyNames()</code> calling <code>getProperty</code> — which is how <code>fill</code> and
     * the JMX attribute list are built, and how anybody writes a dump — would find nothing where the
     * listing had just said there was something. Measured before it was fixed:
     * <code>propertyNames()</code> said <code>myproject.debug</code> and
     * <code>getProperty("myproject.debug")</code> said <code>null</code>, while the method answered
     * <code>false</code>.
     */
    @Test
    public void everyKeyThatIsListedAnswersWhenItIsAskedFor() {
        NothingButThePrefix config = ConfigFactory.create(NothingButThePrefix.class);

        assertEquals("false", config.getProperty("myproject.debug"));
        assertEquals("false", config.getProperty("myproject.debug", "not this"));

        for (String name : config.propertyNames())
            assertTrue("listed and unanswered: " + name, config.getProperty(name) != null);
    }

    /** And the key as it is written keeps answering, for whoever already reads the properties by it. */
    @Test
    public void theKeyAsWrittenStillAnswersToo() {
        assertEquals("false",
                ConfigFactory.create(NothingButThePrefix.class).getProperty("${myproject.prefix}.debug"));
    }

    /**
     * <b>The key a method reads can move while the configuration is running</b>, and this is why the
     * default is stored under the key as it is <b>written</b> rather than under the one it expands to.
     * <p>
     * It settles a note that sat in {@code PropertiesInvocationHandler} from 2014 saying the fallback to
     * the unexpanded key "should go away" — the alternative offered in
     * <a href="https://github.com/matteobaccan/owner/pull/84">#84</a> being to register the defaults under
     * the expanded keys. Move the prefix and that default is left behind under the name the prefix used to
     * have: the method would answer <code>null</code> having been given one. Under the key as written it
     * belongs to the method instead, and follows it wherever the variable points.
     * </p>
     * <p>
     * <a href="https://github.com/matteobaccan/owner/issues/86">#86</a>, opened against that same line in
     * 2014, is this issue eight years early: the debugging methods showed the unexpanded key. That half is
     * fixed in the view, which is what the rest of this test is about.
     * </p>
     */
    @Test
    public void theViewFollowsTheKeyWhenTheVariableMoves() {
        Movable config = ConfigFactory.create(Movable.class);

        assertFalse(config.debug());
        assertTrue(config.propertyNames().toString(), config.propertyNames().contains("myproject.debug"));

        config.setProperty("myproject.prefix", "elsewhere");

        assertFalse("the default is still found, because it never depended on the prefix", config.debug());
        assertTrue("and the view says which key is read now",
                config.propertyNames().contains("elsewhere.debug"));
        assertFalse("under the old name it is nobody's property any more",
                config.propertyNames().contains("myproject.debug"));

        config.setProperty("elsewhere.debug", "true");
        assertTrue("and a value written where the method now looks is the one it answers with",
                config.debug());
    }

    /**
     * And nothing else moves: a key belonging to somebody else is still listed, because this is the
     * expansion being reported honestly and not a restriction — that one is asked for, and is #150.
     */
    @Test
    public void aKeyOfSomebodyElsesIsStillThere() {
        Set<String> names = ConfigFactory.create(MyProjectConfig.class).propertyNames();

        assertTrue(names.toString(), names.contains("somebody.else.key"));
    }
}

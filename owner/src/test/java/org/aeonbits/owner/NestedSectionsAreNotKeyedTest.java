/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A nested configuration interface may not extend {@link Accessible}, {@link Mutable} or {@link Traceable}.
 * <p>
 * Those are addressed by key and a section has no key space of its own: it shares one
 * <code>PropertiesManager</code> with the whole configuration, so <code>section.getProperty("host")</code>
 * would answer with the root's <code>host</code> rather than with <code>server.host</code> — a different
 * property, silently — and <code>section.clear()</code> would empty everything. It is refused when the
 * configuration is created, and everything it could have offered is available from the configuration object
 * the factory returned, which the second half of this class demonstrates rather than asserts in prose.
 * </p>
 *
 * @see NestedProperties#rejectKeyedSections(Class)
 */
public class NestedSectionsAreNotKeyedTest {

    private static Properties values() {
        Properties p = new Properties();
        p.setProperty("host", "root.example.org");
        p.setProperty("server.host", "nested.example.org");
        p.setProperty("server.password", "hunter2");
        p.setProperty("elsewhere.key", "unrelated");
        return p;
    }

    // ------------------------------------------------------------------ the refusal

    public interface AccessibleSection extends Config, Accessible {
        String host();
    }

    public interface MutableSection extends Config, Mutable {
        String host();
    }

    public interface TraceableSection extends Config, Traceable {
        String host();
    }

    public interface PlainSection extends Config {
        String host();
    }

    public interface WithAccessibleSection extends Config {
        AccessibleSection server();
    }

    public interface WithMutableSection extends Config {
        MutableSection server();
    }

    public interface WithTraceableSection extends Config {
        TraceableSection server();
    }

    @Test
    public void aSectionMayNotBeAccessible() {
        String message = refusalOf(WithAccessibleSection.class);
        assertTrue(message, message.contains("server"));
        assertTrue(message, message.contains("AccessibleSection"));
        assertTrue(message, message.contains("Accessible"));
        assertTrue("it says where to read the properties instead",
                message.contains("configuration object the factory created"));
    }

    @Test
    public void aSectionMayNotBeMutable() {
        assertTrue(refusalOf(WithMutableSection.class).contains("Mutable"));
    }

    @Test
    public void aSectionMayNotBeTraceable() {
        assertTrue(refusalOf(WithTraceableSection.class).contains("Traceable"));
    }

    // the same refusal in the other three shapes a section can be read in

    public interface WithListOfSections extends Config {
        List<AccessibleSection> servers();
    }

    public interface WithMapOfSections extends Config {
        Map<String, AccessibleSection> servers();
    }

    public interface WithParametrizedSection extends Config {
        @Key("servers.%s")
        AccessibleSection server(String name);
    }

    @Test
    public void theRefusalCoversAListOfSections() {
        assertTrue(refusalOf(WithListOfSections.class).contains("AccessibleSection"));
    }

    @Test
    public void theRefusalCoversAMapOfSections() {
        assertTrue(refusalOf(WithMapOfSections.class).contains("AccessibleSection"));
    }

    @Test
    public void theRefusalCoversAnAccessorTakingArguments() {
        assertTrue(refusalOf(WithParametrizedSection.class).contains("AccessibleSection"));
    }

    public interface Middle extends Config {
        AccessibleSection leaf();
    }

    public interface DeepBehindAList extends Config {
        List<Middle> items();
    }

    /**
     * The walk descends through every shape and not only through the plain sections, so a section reachable
     * solely as the element of a list is refused as well. Before the walk existed this case slipped through,
     * because the check of the mandatory properties — the other thing that runs when a configuration is
     * created — descends into the plain sections only.
     */
    @Test
    public void theRefusalReachesASectionOnlyAListAwayFromTheRoot() {
        assertTrue(refusalOf(DeepBehindAList.class).contains("AccessibleSection"));
    }

    public interface ReloadableSection extends Config, Reloadable {
        String host();
    }

    public interface WithReloadableSection extends Config {
        ReloadableSection server();
    }

    /**
     * {@link Reloadable} is the exception, and on purpose: it acts on the configuration as a whole, there is
     * exactly one of those, and so it means the same thing called from any point of the tree.
     */
    @Test
    public void aSectionMayBeReloadable() {
        WithReloadableSection cfg = ConfigFactory.create(WithReloadableSection.class, values());

        assertEquals("nested.example.org", cfg.server().host());
        cfg.server().reload();
        assertNotNull("reload() from a section is allowed and reloads the configuration", cfg.server());
    }

    /** The root itself is an Accessible in every one of these, which is the ordinary and supported case. */
    public interface Root extends Config, Accessible, Mutable, Traceable {
        PlainSection server();
    }

    @Test
    public void theRootMayBeAccessibleMutableAndTraceable() {
        Root cfg = ConfigFactory.create(Root.class, values());

        assertEquals("nested.example.org", cfg.server().host());
    }

    // ------------------------------------------------------- everything, from the root

    /**
     * Reading a section's properties by name: the keys are the ones written in the file, so the section is
     * addressed by its full path and nothing is out of reach.
     */
    @Test
    public void aSectionIsReadByNameFromTheRoot() {
        Root cfg = ConfigFactory.create(Root.class, values());

        assertEquals("nested.example.org", cfg.getProperty("server.host"));
        assertEquals("nested.example.org", cfg.getRawProperty("server.host"));
        assertTrue(cfg.propertyNames().contains("server.host"));

        Map<String, String> filled = new HashMap<>();
        cfg.fill(filled);
        assertEquals("nested.example.org", filled.get("server.host"));

        assertEquals("the root key is a different property, and says so",
                "root.example.org", cfg.getProperty("host"));
    }

    /**
     * Writing one, which is the half that would have been silently wrong: from the root the write lands on
     * the section, and the section's own method sees it. Through a <code>Mutable</code> section it would
     * have landed on the root's <code>host</code> instead, leaving <code>server.host()</code> unchanged.
     */
    @Test
    public void aSectionIsWrittenByNameFromTheRoot() {
        Root cfg = ConfigFactory.create(Root.class, values());

        cfg.setProperty("server.host", "written-from-the-root");

        assertEquals("written-from-the-root", cfg.server().host());
        assertEquals("and the root's own property is untouched",
                "root.example.org", cfg.getProperty("host"));
    }

    @Test
    public void theOriginOfASectionPropertyIsReadByNameFromTheRoot() {
        Root cfg = ConfigFactory.create(Root.class, values());

        assertNotNull(cfg.originOf("server.host"));
        assertEquals(Origin.Kind.IMPORT, cfg.originOf("server.host").kind());
    }

    public interface SecretiveSection extends Config {
        String host();

        @Sensitive
        String password();
    }

    public interface SecretiveRoot extends Config, Accessible {
        SecretiveSection server();
    }

    /**
     * And the masking of a section's properties is the root's business too, which is what makes the refusal
     * cost nothing: it was already computed over the whole tree from the root.
     */
    @Test
    public void aSectionSecretIsMaskedInTheRootListing() throws Exception {
        SecretiveRoot cfg = ConfigFactory.create(SecretiveRoot.class, values());

        ByteArrayOutputStream listed = new ByteArrayOutputStream();
        cfg.list(new PrintStream(listed, true, "UTF-8"));
        String output = listed.toString("UTF-8");

        assertTrue(output.contains("server.password=" + Config.Sensitive.MASK));
        assertFalse("the section's secret was printed", output.contains("hunter2"));
        assertEquals("masking is not encryption", "hunter2", cfg.server().password());
    }

    // ------------------------------------------------------------------------ helper

    private static String refusalOf(Class<? extends Config> configClass) {
        try {
            ConfigFactory.create(configClass, values());
            fail("expected " + configClass.getSimpleName() + " to be refused");
            return null;
        } catch (UnsupportedOperationException refused) {
            return refused.getMessage();
        }
    }
}

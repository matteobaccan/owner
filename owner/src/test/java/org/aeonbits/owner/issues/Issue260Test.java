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
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/matteobaccan/owner/issues/260
 * <p>
 * nando2301 declared an interface with no methods at all, read it from a JSF page, and was told to
 * <i>"create public method"</i>. What he was really asking for is in his last line: his file holds around
 * <b>three hundred</b> application messages, and declaring one method per message is not work anybody
 * wants to do — so he asked for a tool that would write the interface for him.
 * </p>
 * <p>
 * <b>He does not need one</b>, and this is what he needs instead: a configuration that declares nothing
 * still holds everything the file has, through {@link Accessible}. One <code>fill</code> puts the lot into
 * a <code>Map</code>, and a page indexes that map by key — which is also the only way to reach a key with
 * dots in it from an expression language, where <code>props.menu.home</code> would be read as three
 * property accesses and <code>props['menu.home']</code> is one lookup.
 * </p>
 * <p>
 * The other half of the answer is not in this test because it is not in this library: <b>messages are not
 * configuration</b>. A JSF page has <code>&lt;f:loadBundle&gt;</code> and a
 * {@link java.util.ResourceBundle} for them, which knows about locales, and that is the tool for three
 * hundred pieces of text. This is the tool for the handful of them that are settings.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue260Test {

    private static final String FILE = "classpath:org/aeonbits/owner/issues/issue260.properties";

    /** What nando2301 wrote, and it is enough: no methods, no annotations on methods, nothing. */
    @Sources(FILE)
    public interface AppConfig extends Config, Accessible {
    }

    /** And the same file with the two or three keys that really are settings declared, which is the point. */
    @Sources(FILE)
    public interface Settings extends Config, Accessible {
        @Key("AppName")
        String appName();
    }

    /** A configuration that declares nothing still reads everything. */
    @Test
    public void anInterfaceWithNoMethodsStillHoldsTheWholeFile() {
        AppConfig config = ConfigFactory.create(AppConfig.class);

        assertEquals("Meetings", config.getProperty("AppName"));
        assertEquals("Registration", config.getProperty("MenuRegistration"));

        Set<String> names = config.propertyNames();
        assertTrue(names.toString(), names.contains("menu.home"));
        assertEquals(names.toString(), 5, names.size());
    }

    /**
     * One call, and the page has the whole file. This is the shape a managed bean exposes: a getter
     * returning the map, filled once.
     */
    @Test
    public void andOneCallPutsItAllIntoAMap() {
        Map<String, String> messages = new HashMap<>();
        ConfigFactory.create(AppConfig.class).fill(messages);

        assertEquals(5, messages.size());
        assertEquals("Meetings", messages.get("AppName"));
        assertEquals("Log out", messages.get("menu.logout"));
    }

    /**
     * A key with dots in it is reached by indexing the map — <code>#{bean.props['menu.home']}</code> — and
     * not by walking it, which is what an expression language would do with the dots.
     */
    @Test
    public void aKeyWithDotsIsReachedByIndexingTheMap() {
        Map<String, String> messages = new HashMap<>();
        ConfigFactory.create(AppConfig.class).fill(messages);

        assertEquals("Home", messages.get("menu.home"));
    }

    /**
     * And what the map holds is the value <b>ready to show</b>: <code>fill</code> expands the variables, so
     * a message built out of another property arrives assembled rather than as its template. The rule and
     * the table are under <i>Which methods process the value</i>.
     */
    @Test
    public void theMessagesArriveExpanded() {
        Map<String, String> messages = new HashMap<>();
        ConfigFactory.create(AppConfig.class).fill(messages);

        assertEquals("Goodbye, and thanks for using Meetings", messages.get("farewell"));
        assertEquals("the raw form is still there for whoever wants it",
                "Goodbye, and thanks for using ${AppName}",
                ConfigFactory.create(AppConfig.class).getRawProperty("farewell"));
    }

    /**
     * Declaring the two or three that are settings does not cost the rest: a method is a typed, checked way
     * in for the values the code depends on, and everything else stays readable by name.
     */
    @Test
    public void whatIsWorthDeclaringCanStillBeDeclared() {
        Settings settings = ConfigFactory.create(Settings.class);

        assertEquals("Meetings", settings.appName());
        assertEquals("Home", settings.getProperty("menu.home"));
    }
}

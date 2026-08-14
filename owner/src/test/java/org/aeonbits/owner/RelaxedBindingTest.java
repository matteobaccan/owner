/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.DisableFeature;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Mandatory;
import org.aeonbits.owner.Config.Prefix;
import org.aeonbits.owner.util.LogCapture;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.aeonbits.owner.Config.DisableableFeature.RELAXED_BINDING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The four ways one property may be spelt, which is what
 * <a href="https://github.com/matteobaccan/owner/issues/116">issue #116</a> asked for in 2015.
 * <p>
 * Every test builds its properties from a map handed to the factory rather than from a file, so that the
 * key under test is visible in the test itself; a map is an import, and an import counts as written
 * exactly as a source does, which is what the precedence rules below turn on.
 * </p>
 *
 * @author Matteo Baccan
 */
public class RelaxedBindingTest {

    private static <T extends Config> T create(Class<T> clazz, String... keysAndValues) {
        return ConfigFactory.newInstance().create(clazz, map(keysAndValues));
    }

    private static Map<String, String> map(String... keysAndValues) {
        Map<String, String> properties = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2)
            properties.put(keysAndValues[i], keysAndValues[i + 1]);
        return properties;
    }

    // ------------------------------------------------------------------ the four forms

    public interface Person extends Config {
        String firstName();
    }

    @Test
    public void theKeyItselfIsFound() {
        assertEquals("Luigi", create(Person.class, "firstName", "Luigi").firstName());
    }

    @Test
    public void kebabCaseIsFound() {
        assertEquals("Luigi", create(Person.class, "first-name", "Luigi").firstName());
    }

    @Test
    public void snakeCaseIsFound() {
        assertEquals("Luigi", create(Person.class, "first_name", "Luigi").firstName());
    }

    @Test
    public void theEnvironmentVariableFormIsFound() {
        assertEquals("Luigi", create(Person.class, "FIRST_NAME", "Luigi").firstName());
    }

    /**
     * The set is closed: a spelling that is none of the four is not a spelling of this key. Boot 1 would
     * have found the first of these by ignoring the separators and the case; Boot 2 stopped doing that,
     * and so do we.
     */
    @Test
    public void nothingElseIsFound() {
        assertNull(create(Person.class, "firstname", "Luigi").firstName());
        assertNull(create(Person.class, "FirstName", "Luigi").firstName());
        assertNull(create(Person.class, "first.name", "Luigi").firstName());
        assertNull(create(Person.class, "FIRST-NAME", "Luigi").firstName());
    }

    /** The forms are derived from the key, so a {@code @Key} written in one convention finds the others. */
    public interface WrittenInKebab extends Config {
        @Key("first-name")
        String name();
    }

    @Test
    public void theFormsAreDerivedFromTheKeyAndNotFromTheMethodName() {
        assertEquals("Luigi", create(WrittenInKebab.class, "firstName", "Luigi").name());
        assertEquals("Luigi", create(WrittenInKebab.class, "FIRST_NAME", "Luigi").name());
        assertNull("the method name is not a key here", create(WrittenInKebab.class, "name", "Luigi").name());
    }

    public interface Acronyms extends Config {
        String httpURLConnection();
    }

    /**
     * An acronym does not swallow the word after it: without that boundary the kebab form would be
     * <code>http-urlconnection</code>, which nobody would ever write.
     */
    @Test
    public void anAcronymIsItsOwnWord() {
        assertEquals("on", create(Acronyms.class, "http-url-connection", "on").httpURLConnection());
        assertEquals("on", create(Acronyms.class, "HTTP_URL_CONNECTION", "on").httpURLConnection());
    }

    @Test
    public void theFormsOfAKey() {
        assertEquals(asList("first-name", "first_name", "FIRST_NAME"),
                RelaxedKeys.alternativesTo("firstName"));
        assertEquals("the key itself is never among its own alternatives",
                asList("first-name", "FIRST_NAME", "firstName"),
                RelaxedKeys.alternativesTo("first_name"));
        assertEquals("a single lower case word has only the environment form",
                asList("PORT"), RelaxedKeys.alternativesTo("port"));
        assertEquals("the nesting separator is structure and survives every form",
                asList("server.max-threads", "server.max_threads", "SERVER.MAX_THREADS"),
                RelaxedKeys.alternativesTo("server.maxThreads"));
        assertEquals("the path of a section is a prefix and keeps its trailing separator",
                asList("my-db.", "my_db.", "MY_DB."), RelaxedKeys.alternativesTo("myDb."));
    }

    // ------------------------------------------------------------------ precedence

    public interface Precedence extends Config {
        String firstName();
    }

    @Test
    public void theKeyItselfWinsOverEverySpellingOfIt() {
        assertEquals("canonical", create(Precedence.class,
                "firstName", "canonical", "first-name", "kebab", "FIRST_NAME", "env").firstName());
    }

    @Test
    public void amongTheSpellingsTheOrderIsTheDocumentedOne() {
        assertEquals("kebab", create(Precedence.class,
                "first-name", "kebab", "first_name", "snake", "FIRST_NAME", "env").firstName());
        assertEquals("snake", create(Precedence.class,
                "first_name", "snake", "FIRST_NAME", "env").firstName());
    }

    public interface Defaulted extends Config {
        @DefaultValue("42")
        int maxThreads();
    }

    /**
     * The rule that is not the obvious one. A <code>@DefaultValue</code> is a property under the key of
     * its method by the time anything is read, so "the key first, the spellings afterwards" would let the
     * default shadow the value somebody actually wrote.
     */
    @Test
    public void aValueThatWasWrittenBeatsOneThatWasOnlyDefaulted() {
        assertEquals(7, create(Defaulted.class, "max-threads", "7").maxThreads());
        assertEquals(7, create(Defaulted.class, "MAX_THREADS", "7").maxThreads());
        assertEquals("with nothing written, the default is still the answer", 42,
                create(Defaulted.class).maxThreads());
    }

    // ------------------------------------------------------------------ two spellings at once

    @Test
    public void twoSpellingsOfOneKeyAreReported() {
        try (LogCapture capture = LogCapture.ofLibrary(Level.WARNING)) {
            create(Precedence.class, "firstName", "canonical", "first-name", "kebab");

            String said = capture.messagesAt(Level.WARNING);
            assertTrue(said, said.contains("firstName"));
            assertTrue(said, said.contains("first-name"));
            assertTrue("it says which one is read", said.contains("reads 'firstName'"));
            assertFalse("no value ever reaches a log line", said.contains("kebab"));
        }
    }

    @Test
    public void oneSpellingIsNotWorthALine() {
        try (LogCapture capture = LogCapture.ofLibrary(Level.WARNING)) {
            create(Precedence.class, "first-name", "kebab");
            assertEquals("", capture.messagesAt(Level.WARNING));
        }
    }

    /**
     * A default under the key of the method pairs with anything the file writes, which is a default being
     * overridden and not two spellings of one thing. It would otherwise be reported for every defaulted
     * property in every configuration written in kebab-case.
     */
    @Test
    public void aDefaultIsNotOneOfTheTwoSpellings() {
        try (LogCapture capture = LogCapture.ofLibrary(Level.WARNING)) {
            assertEquals(7, create(Defaulted.class, "max-threads", "7").maxThreads());
            assertEquals("", capture.messagesAt(Level.WARNING));
        }
    }

    @Test
    public void twoSpellingsOfOneKeyAreRefusedUnderStrict() {
        Factory strict = ConfigFactory.newInstance();
        strict.setProperty("owner.strict", "true");
        try {
            strict.create(Precedence.class, map("firstName", "a", "first-name", "b"));
            fail("expected the two spellings to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("owner.strict"));
            assertTrue(refused.getMessage(), refused.getMessage().contains("first-name"));
        }
    }

    // ------------------------------------------------------------------ switching it off

    public interface Exactly extends Config {
        @DisableFeature(RELAXED_BINDING)
        String firstName();

        String lastName();
    }

    @DisableFeature(RELAXED_BINDING)
    public interface ExactlyThroughout extends Config {
        String firstName();
    }

    @Test
    public void theFeatureIsOffForAMethodThatSaysSo() {
        Exactly config = create(Exactly.class, "first-name", "Luigi", "last-name", "Viggiano");
        assertNull(config.firstName());
        assertEquals("the method next to it is unaffected", "Viggiano", config.lastName());
    }

    @Test
    public void theFeatureIsOffForAnInterfaceThatSaysSo() {
        assertNull(create(ExactlyThroughout.class, "first-name", "Luigi").firstName());
        assertEquals("Luigi", create(ExactlyThroughout.class, "firstName", "Luigi").firstName());
    }

    @Test
    public void aMethodThatSwitchedItOffIsNotReportedAsAmbiguousEither() {
        try (LogCapture capture = LogCapture.ofLibrary(Level.WARNING)) {
            create(Exactly.class, "firstName", "a", "first-name", "b");
            assertEquals("", capture.messagesAt(Level.WARNING));
        }
    }

    // ------------------------------------------------------------------ what it must not change

    public interface Readable extends Config, Accessible, Traceable {
        String firstName();
    }

    @Test
    public void thePropertiesKeepTheNamesTheyWereWrittenWith() throws IOException {
        Readable config = create(Readable.class, "first-name", "Luigi");

        assertEquals("Luigi", config.firstName());
        assertTrue(config.propertyNames().contains("first-name"));
        assertFalse("nothing is added under the key of the method",
                config.propertyNames().contains("firstName"));

        ByteArrayOutputStream stored = new ByteArrayOutputStream();
        config.store(stored, null);
        String text = new String(stored.toByteArray(), UTF_8);
        assertTrue(text, text.contains("first-name=Luigi"));
        assertFalse(text, text.contains("firstName"));
    }

    /**
     * Addressed by key, and answering about the key it was handed: relaxing these would mean
     * <code>getProperty(k)</code> reading something that is not <code>k</code>, and
     * <code>originOf(k)</code> naming a source for a key that is not in the configuration.
     */
    @Test
    public void theMethodsThatTakeAKeyAnswerAboutThatKey() {
        Readable config = create(Readable.class, "first-name", "Luigi");

        assertEquals("Luigi", config.getProperty("first-name"));
        assertNull(config.getProperty("firstName"));
        assertEquals(Origin.Kind.IMPORT, config.originOf("first-name").kind());
        assertNull(config.originOf("firstName"));
    }

    public interface Secrets extends Config, Accessible {
        @Config.Sensitive
        String dbPassword();
    }

    /**
     * What is masked is matched by name against the properties as they were loaded, and relaxed binding
     * is exactly what stopped pinning that name down. A secret read out of the environment and printed in
     * full would be the worst thing this feature could have brought.
     */
    @Test
    public void aSensitivePropertyIsMaskedUnderEverySpellingOfItsKey() {
        Secrets config = create(Secrets.class, "DB_PASSWORD", "hunter2");

        assertEquals("hunter2", config.dbPassword());
        assertFalse(config.toString(), config.toString().contains("hunter2"));
        assertTrue(config.toString(), config.toString().contains(Config.Sensitive.MASK));
    }

    // ------------------------------------------------------------------ prefixes, nesting, parameters

    @Prefix("server.")
    public interface Prefixed extends Config {
        String hostName();
    }

    @Test
    public void aPrefixIsPartOfTheKeyAndTheFormAppliesToTheWhole() {
        assertEquals("h", create(Prefixed.class, "server.host-name", "h").hostName());
        assertEquals("h", create(Prefixed.class, "SERVER.HOST_NAME", "h").hostName());
        assertEquals("h", create(Prefixed.class, "server.hostName", "h").hostName());
    }

    @Test
    public void aPrefixConfiguredOnTheFactoryIsPartOfTheKeyToo() {
        Factory factory = ConfigFactory.newInstance();
        factory.setProperty(KeyPrefix.LITERAL, "app.");
        assertEquals("Luigi", factory.create(Person.class, map("app.first-name", "Luigi")).firstName());
    }

    public interface Db extends Config {
        String userName();

        @DefaultValue("5")
        int poolSize();
    }

    /** No {@link DefaultValue} inside, so that its absence is really an absence. */
    public interface Spare extends Config {
        String userName();
    }

    public interface App extends Config {
        Db myDb();

        Optional<Spare> spareDb();
    }

    /**
     * A section costs nothing extra: the form is applied to the whole key, path and all, so
     * <code>myDb.userName</code> is looked for as <code>my-db.user-name</code> without the path itself
     * ever being resolved — and the default registered under the canonical path is still found.
     */
    @Test
    public void aSectionNamedInAnotherConventionIsFound() {
        App app = create(App.class, "my-db.user-name", "joe");
        assertEquals("joe", app.myDb().userName());
        assertEquals(5, app.myDb().poolSize());
    }

    /**
     * Presence is the one question about a section that has to be relaxed on its own: left alone,
     * <code>Optional&lt;Spare&gt; spareDb()</code> would come back empty while every value inside it
     * answered.
     */
    @Test
    public void anOptionalSectionIsPresentWhenAnySpellingOfItsPathWasWritten() {
        App app = create(App.class, "spare-db.user-name", "joe");
        assertTrue(app.spareDb().isPresent());
        assertEquals("joe", app.spareDb().get().userName());
        assertFalse(create(App.class).spareDb().isPresent());
    }

    public interface Parametrized extends Config {
        @Key("server.${env}.hostName")
        String host();

        @Key("node.%s.hostName")
        String node(String name);
    }

    @Test
    public void aKeyBuiltFromAVariableIsRelaxedOnceItIsExpanded() {
        Parametrized config = create(Parametrized.class, "env", "prod", "server.prod.host-name", "h");
        assertEquals("h", config.host());
    }

    @Test
    public void aKeyBuiltFromTheArgumentsIsRelaxedOnceItIsFormatted() {
        Parametrized config = create(Parametrized.class, "node.alpha.host-name", "h");
        assertEquals("h", config.node("alpha"));
    }

    @Mandatory
    public interface Required extends Config {
        String firstName();
    }

    @Test
    public void aMandatoryPropertyIsSatisfiedByAnySpellingOfIt() {
        assertEquals("Luigi", create(Required.class, "FIRST_NAME", "Luigi").firstName());
        try {
            create(Required.class);
            fail("expected the missing property to be refused");
        } catch (MissingMandatoryPropertyException missing) {
            assertTrue(missing.getMessage(), missing.getMessage().contains("firstName"));
        }
    }

    public interface Grouped extends Config {
        Map<String, String> serverPorts();

        List<String> hostNames();
    }

    /**
     * The documented boundary: a prefix decides which keys <i>are</i> the group, and choosing among four
     * of them could merge two groups or answer with the wrong one. Left as it is written, and asserted so
     * that changing it is a deliberate step.
     */
    @Test
    public void thePrefixOfAGroupIsMatchedAsItIsWritten() {
        assertTrue(create(Grouped.class, "server-ports.http", "80").serverPorts().isEmpty());
        assertEquals(1, create(Grouped.class, "serverPorts.http", "80").serverPorts().size());
        assertNull(create(Grouped.class, "host-names[0]", "a").hostNames());
        assertEquals(1, create(Grouped.class, "hostNames[0]", "a").hostNames().size());
    }
}

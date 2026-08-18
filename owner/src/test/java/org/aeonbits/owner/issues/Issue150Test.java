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
import org.aeonbits.owner.Config.DeclaredOnly;
import org.aeonbits.owner.Config.Prefix;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Mutable;
import org.aeonbits.owner.Reloadable;
import org.aeonbits.owner.Traceable;
import org.junit.After;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/matteobaccan/owner/issues/150
 * <p>
 * gtimbo configured one application with one properties file and several mapping interfaces — some of
 * them supplied by plugins — and found that printing any one of those configurations printed the whole
 * file, everybody else's keys included. He worked out the constraint himself, in the issue, three months
 * later: <b>the loading cannot be restricted</b>, because a <code>${...}</code> is resolved against
 * properties this interface may well not declare. What is left is the view, and an annotation to ask for
 * it, which is what he proposed and what this is.
 * </p>
 * <p>
 * The field agrees, and it took reading the sources to be sure of it: Coat, which has the same shape as
 * this library — an annotated interface, a generated implementation — builds its <code>toString</code>
 * from the declared parameters alone and never shows a key it was not told about. SmallRye and Spring put
 * no whole-store view on the mapping object at all; Typesafe and Commons Configuration have one, and
 * scope it to the subtree that was asked for. Nobody hands back somebody else's keys.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue150Test {

    private static final String FILE = "classpath:org/aeonbits/owner/issues/issue150.properties";

    /** The name of the factory property, spelt as somebody would write it in their own configuration. */
    private static final String DECLARED_ONLY = "owner.declared.only";

    @Sources(FILE)
    @DeclaredOnly
    public interface AppConfig extends Config, Accessible {

        @Key("app.name")
        String name();

        @Key("app.timeout")
        @DefaultValue("10")
        int timeout();
    }

    @Sources(FILE)
    @DeclaredOnly
    public interface DbConfig extends Config, Accessible {

        @Key("db.host")
        String host();

        @Key("db.port")
        int port();
    }

    /** Without the annotation nothing changes, which is what keeps this from breaking anybody. */
    @Sources(FILE)
    public interface WholeFile extends Config, Accessible {

        @Key("app.name")
        String name();
    }

    @After
    public void forgetTheFactoryProperty() {
        ConfigFactory.clearProperty(DECLARED_ONLY);
    }

    /** The report: one file, two interfaces, and each of them holding only its own half. */
    @Test
    public void eachInterfaceShowsItsOwnPropertiesAndNobodyElses() {
        Set<String> app = ConfigFactory.create(AppConfig.class).propertyNames();
        Set<String> db = ConfigFactory.create(DbConfig.class).propertyNames();

        assertEquals(app.toString(), new java.util.HashSet<>(java.util.Arrays.asList("app.name", "app.timeout")), app);
        assertEquals(db.toString(), new java.util.HashSet<>(java.util.Arrays.asList("db.host", "db.port")), db);
    }

    /** And toString, which is where he met it: a log line that named somebody else's database. */
    @Test
    public void andToStringSaysTheSame() {
        String printed = ConfigFactory.create(AppConfig.class).toString();

        assertTrue(printed, printed.contains("app.name"));
        assertFalse(printed, printed.contains("spring.datasource.url"));
        assertFalse(printed, printed.contains("logging.level"));
    }

    /** Nothing is restricted unless it is asked for. */
    @Test
    public void anInterfaceThatDoesNotAskKeepsTheWholeView() {
        Set<String> names = ConfigFactory.create(WholeFile.class).propertyNames();

        assertTrue(names.toString(), names.contains("spring.datasource.url"));
        assertTrue(names.toString(), names.contains("logging.level"));
    }

    /**
     * <b>Asking by name is a question about the file</b>, so it is answered whatever the view shows: the
     * expansion that made the restriction impossible at load time is the same expansion somebody debugs
     * with getProperty, and taking it away would leave no way to look.
     */
    @Test
    public void gettingAPropertyByNameIsNotRestricted() {
        AppConfig config = ConfigFactory.create(AppConfig.class);

        assertEquals("INFO", config.getProperty("logging.level"));
        assertEquals("acme", config.name());
    }

    // ----------------------------------------------------------------------------------------------
    // What an interface declares, when it did not declare it by itself
    // ----------------------------------------------------------------------------------------------

    @Sources(FILE)
    @DeclaredOnly
    public interface Base extends Config, Accessible {

        @Key("app.name")
        String name();
    }

    /** A sub-interface owns what it declares and what it inherits, and inherits the annotation too. */
    public interface Extended extends Base {

        @Key("app.timeout")
        int timeout();
    }

    @Test
    public void anInheritedKeyIsDeclaredAndSoIsAnInheritedAnnotation() {
        Set<String> names = ConfigFactory.create(Extended.class).propertyNames();

        assertTrue(names.toString(), names.contains("app.name"));
        assertTrue(names.toString(), names.contains("app.timeout"));
        assertFalse("the annotation on the interface above is what restricted this one",
                names.contains("logging.level"));
    }

    /** And a sub-interface can say no to what it inherited. */
    @DeclaredOnly(false)
    public interface ExtendedButWhole extends Base {

        @Key("app.timeout")
        int timeout();
    }

    @Test
    public void aSubInterfaceCanRefuseTheRestrictionItInherited() {
        Set<String> names = ConfigFactory.create(ExtendedButWhole.class).propertyNames();

        assertTrue(names.toString(), names.contains("logging.level"));
    }

    @Prefix("db.")
    @Sources(FILE)
    @DeclaredOnly
    public interface Prefixed extends Config, Accessible {

        String host();
    }

    /** The prefix belongs to the interface that declares the method, so what is owned is the whole key. */
    public interface PrefixedAndExtended extends Prefixed {

        @Key("app.name")
        String name();
    }

    @Test
    public void aPrefixedKeyIsOwnedUnderItsPrefix() {
        Set<String> names = ConfigFactory.create(PrefixedAndExtended.class).propertyNames();

        assertTrue(names.toString(), names.contains("db.host"));
        assertTrue("and the method declared here keeps no prefix of its own",
                names.contains("app.name"));
        assertFalse(names.toString(), names.contains("host"));
        assertFalse(names.toString(), names.contains("logging.level"));
    }

    public interface Db extends Config {

        String host();

        int port();
    }

    @Sources(FILE)
    @DeclaredOnly
    public interface WithASection extends Config, Accessible {

        @Key("app.name")
        String name();

        Db db();
    }

    /** A section is part of the configuration, so its keys are part of what the configuration owns. */
    @Test
    public void theKeysOfASectionAreOwnedToo() {
        Set<String> names = ConfigFactory.create(WithASection.class).propertyNames();

        assertTrue(names.toString(), names.contains("db.host"));
        assertTrue(names.toString(), names.contains("db.port"));
        assertFalse("and the accessor itself is a path and not a property", names.contains("db"));
    }

    @Sources(FILE)
    @DeclaredOnly
    public interface Everything extends Mutable, Accessible, Reloadable, Traceable {

        @Key("app.name")
        String name();
    }

    /**
     * <b>The library's own methods are not properties.</b> An interface extending all four of them hands
     * <code>getMethods()</code> back twenty-two names — <code>store</code>, <code>load</code>,
     * <code>clear</code>, <code>reload</code>, <code>origins</code> — and every one of them used to count
     * as a key the interface owns. Nothing had a property by those names, so nothing showed; a file with a
     * key called <code>store</code> in it would have.
     */
    @Test
    public void theMethodsOfTheLibraryItselfAreNotDeclaredKeys() {
        Set<String> names = ConfigFactory.create(Everything.class).propertyNames();

        assertEquals(names.toString(), 1, names.size());
        assertTrue(names.toString(), names.contains("app.name"));
        assertFalse("the file has one, and it is not this interface's to claim",
                names.contains("store"));
        assertFalse(names.toString(), names.contains("clear"));
        assertFalse(names.toString(), names.contains("reload"));
    }

    @Sources(FILE)
    @DeclaredOnly
    public interface WithAVariableInItsKey extends Config, Accessible {

        @Key("myproject.prefix")
        String configPrefix();

        @Key("${myproject.prefix}.debug")
        @DefaultValue("false")
        boolean debug();
    }

    /** The two halves together: what is owned is the key that is read, not the key that is written. */
    @Test
    public void whatIsOwnedIsTheKeyThatIsRead() {
        Set<String> names = ConfigFactory.create(WithAVariableInItsKey.class).propertyNames();

        assertTrue(names.toString(), names.contains("myproject.debug"));
        assertTrue(names.toString(), names.contains("myproject.prefix"));
        assertFalse(names.toString(), names.contains("${myproject.prefix}.debug"));
        assertEquals(names.toString(), 2, names.size());
    }

    @Sources(FILE)
    @DeclaredOnly
    public interface WithAKeyPerCall extends Config, Accessible {

        @Key("app.name")
        String name();

        @Key("server.%s.host")
        String hostOf(String which);
    }

    /**
     * A key that is only known when the method is called cannot be part of a set of keys, so the property
     * it reads is not shown — the same rule {@code @Sensitive} and {@code @EncryptedValue} already follow,
     * and the reason it has to be documented rather than discovered.
     */
    @Test
    public void aKeyThatDependsOnTheArgumentsIsNotShownAndStillWorks() {
        WithAKeyPerCall config = ConfigFactory.create(WithAKeyPerCall.class);

        assertEquals("alpha.example.com", config.hostOf("alpha"));

        Set<String> names = config.propertyNames();
        assertEquals(names.toString(), 1, names.size());
        assertTrue(names.toString(), names.contains("app.name"));
    }

    // ----------------------------------------------------------------------------------------------
    // The factory property, for the interface you did not write
    // ----------------------------------------------------------------------------------------------

    /**
     * gtimbo's interfaces came from plugins, and an annotation is no use on a type somebody else ships.
     * The factory property restricts every configuration it creates.
     */
    @Test
    public void theFactoryCanAskForItOnBehalfOfAnInterfaceThatCannotBeAnnotated() {
        ConfigFactory.setProperty(DECLARED_ONLY, "true");

        Set<String> names = ConfigFactory.create(WholeFile.class).propertyNames();

        assertEquals(names.toString(), 1, names.size());
        assertTrue(names.toString(), names.contains("app.name"));
    }

    /** And an interface that says otherwise is not overruled by it. */
    @Test
    public void anInterfaceThatSaysOtherwiseWinsOverTheFactory() {
        ConfigFactory.setProperty(DECLARED_ONLY, "true");

        Set<String> names = ConfigFactory.create(ExtendedButWhole.class).propertyNames();

        assertTrue(names.toString(), names.contains("logging.level"));
    }

    /** A Config object keeps the answer it was born with, as it does for every other factory setting. */
    @Test
    public void whatTheFactorySaidWhenTheObjectWasCreatedIsWhatItKeeps() {
        ConfigFactory.setProperty(DECLARED_ONLY, "true");
        WholeFile restricted = ConfigFactory.create(WholeFile.class);

        ConfigFactory.clearProperty(DECLARED_ONLY);

        assertEquals(restricted.propertyNames().toString(), 1, restricted.propertyNames().size());
        assertTrue(ConfigFactory.create(WholeFile.class).propertyNames().contains("logging.level"));
    }
}

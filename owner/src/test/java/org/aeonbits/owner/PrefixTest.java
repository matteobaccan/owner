/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.Prefix;
import org.aeonbits.owner.Config.DisableFeature;
import org.junit.Test;

import java.util.Properties;

import static org.aeonbits.owner.Config.DisableableFeature.PREFIX;
import static org.aeonbits.owner.Config.DisableableFeature.VARIABLE_EXPANSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Tests for the {@link Config.Prefix} annotation: the prefix is prepended to the key of every property
 * declared in the annotated interface, it does not leak onto inherited methods, and it can be disabled
 * per method or per interface.
 *
 * @author Matteo Baccan
 */
public class PrefixTest {

    @Prefix("server.")
    interface ServerConfig extends Config, Accessible {
        String name();

        @Key("host.name")
        String host();

        @DefaultValue("8080")
        int port();
    }

    @Test
    public void prefixShouldBePrependedToTheMethodName() {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class, new Properties() {{
            setProperty("server.name", "alpha");
            setProperty("name", "wrong");
        }});
        assertEquals("alpha", cfg.name());
    }

    @Test
    public void prefixShouldBePrependedToTheKeyAnnotation() {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class, new Properties() {{
            setProperty("server.host.name", "alpha.example.org");
            setProperty("host.name", "wrong");
        }});
        assertEquals("alpha.example.org", cfg.host());
    }

    @Test
    public void defaultValueShouldBeRegisteredUnderThePrefixedKey() {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
        assertEquals(8080, cfg.port());
        assertEquals("8080", cfg.getProperty("server.port"));
        assertNull(cfg.getProperty("port"));
    }

    @Test
    public void unprefixedKeyShouldNotBeResolved() {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class, new Properties() {{
            setProperty("name", "alpha");
        }});
        assertNull(cfg.name());
    }

    /**
     * A prefix not ending with a separator is a valid naming scheme, not an error: the concatenation is
     * deliberately literal, and OWNER neither inserts a separator nor reports the missing one.
     */
    @Prefix("server")
    interface SeparatorLessPrefixConfig extends Config {
        String name();

        @Key("host.name")
        String host();
    }

    @Test
    public void noSeparatorShouldBeInsertedBetweenThePrefixAndTheKey() {
        SeparatorLessPrefixConfig cfg = ConfigFactory.create(SeparatorLessPrefixConfig.class, new Properties() {{
            setProperty("servername", "alpha");
            setProperty("server.name", "wrong");
            setProperty("serverhost.name", "alpha.example.org");
            setProperty("server.host.name", "wrong");
        }});
        assertEquals("alpha", cfg.name());
        assertEquals("alpha.example.org", cfg.host());
    }

    /** The <code>db_</code> naming scheme documented in the "Key prefix" chapter of the site. */
    @Prefix("db_")
    interface UnderscorePrefixConfig extends Config, Accessible {
        String url();

        @DefaultValue("5")
        int poolSize();
    }

    @Test
    public void aPrefixEndingWithAnyCharacterShouldBeUsableAsANamingScheme() {
        UnderscorePrefixConfig cfg = ConfigFactory.create(UnderscorePrefixConfig.class, new Properties() {{
            setProperty("db_url", "jdbc:h2:mem:test");
            setProperty("db.url", "wrong");
            setProperty("url", "wrong");
        }});
        assertEquals("jdbc:h2:mem:test", cfg.url());
        assertEquals(5, cfg.poolSize());
        assertEquals("5", cfg.getProperty("db_poolSize"));
    }

    // -- the prefix belongs to the interface where the method is declared -------------------------

    @Prefix("parent.")
    interface ParentConfig extends Config {
        String fromParent();
    }

    @Prefix("child.")
    interface ChildConfig extends ParentConfig {
        String fromChild();
    }

    @Test
    public void childPrefixShouldNotLeakOnInheritedMethods() {
        ChildConfig cfg = ConfigFactory.create(ChildConfig.class, new Properties() {{
            setProperty("parent.fromParent", "from-parent");
            setProperty("child.fromParent", "wrong");
            setProperty("child.fromChild", "from-child");
        }});
        assertEquals("from-parent", cfg.fromParent());
        assertEquals("from-child", cfg.fromChild());
    }

    @Prefix("grandparent.")
    interface GrandParentConfig extends Config {
        String fromGrandParent();
    }

    @Prefix("parent.")
    interface MiddleConfig extends GrandParentConfig {
        String fromParent();
    }

    @Prefix("child.")
    interface LeafConfig extends MiddleConfig {
        String fromChild();
    }

    @Test
    public void grandParentPrefixShouldSurviveThreeLevelsOfInheritance() {
        LeafConfig cfg = ConfigFactory.create(LeafConfig.class, new Properties() {{
            setProperty("grandparent.fromGrandParent", "from-grandparent");
            setProperty("parent.fromGrandParent", "wrong");
            setProperty("child.fromGrandParent", "wrong");
            setProperty("parent.fromParent", "from-parent");
            setProperty("child.fromChild", "from-child");
        }});
        assertEquals("from-grandparent", cfg.fromGrandParent());
        assertEquals("from-parent", cfg.fromParent());
        assertEquals("from-child", cfg.fromChild());
    }

    @Prefix("child.")
    interface RedeclaringChildConfig extends ParentConfig {
        @Override
        String fromParent();
    }

    @Test
    public void redeclaringAMethodShouldMoveItUnderTheDeclaringPrefix() {
        RedeclaringChildConfig cfg = ConfigFactory.create(RedeclaringChildConfig.class, new Properties() {{
            setProperty("child.fromParent", "redeclared");
            setProperty("parent.fromParent", "wrong");
        }});
        assertEquals("redeclared", cfg.fromParent());
    }

    interface UnprefixedChildConfig extends ParentConfig {
        String fromChild();
    }

    @Test
    public void inheritedMethodsShouldKeepTheirPrefixOnAnUnprefixedInterface() {
        UnprefixedChildConfig cfg = ConfigFactory.create(UnprefixedChildConfig.class, new Properties() {{
            setProperty("parent.fromParent", "from-parent");
            setProperty("fromChild", "from-child");
        }});
        assertEquals("from-parent", cfg.fromParent());
        assertEquals("from-child", cfg.fromChild());
    }

    interface UnprefixedParentConfig extends Config {
        String fromParent();

        @Key("also.from.parent")
        String alsoFromParent();
    }

    @Prefix("child.")
    interface PrefixedChildConfig extends UnprefixedParentConfig {
        String fromChild();
    }

    @Test
    public void childPrefixShouldNotReachMethodsOfAnUnprefixedParent() {
        PrefixedChildConfig cfg = ConfigFactory.create(PrefixedChildConfig.class, new Properties() {{
            setProperty("fromParent", "from-parent");
            setProperty("child.fromParent", "wrong");
            setProperty("also.from.parent", "also-from-parent");
            setProperty("child.also.from.parent", "wrong");
            setProperty("child.fromChild", "from-child");
        }});
        assertEquals("from-parent", cfg.fromParent());
        assertEquals("also-from-parent", cfg.alsoFromParent());
        assertEquals("from-child", cfg.fromChild());
    }

    interface UnprefixedParentWithDefaultConfig extends Config {
        @DefaultValue("fallback")
        String fromParent();
    }

    @Prefix("child.")
    interface PrefixedChildWithDefaultConfig extends UnprefixedParentWithDefaultConfig, Accessible {
        @DefaultValue("child-fallback")
        String fromChild();
    }

    @Test
    public void defaultsOfAnUnprefixedParentShouldBeRegisteredWithoutThePrefix() {
        PrefixedChildWithDefaultConfig cfg = ConfigFactory.create(PrefixedChildWithDefaultConfig.class);
        assertEquals("fallback", cfg.fromParent());
        assertEquals("fallback", cfg.getProperty("fromParent"));
        assertNull(cfg.getProperty("child.fromParent"));
        assertEquals("child-fallback", cfg.getProperty("child.fromChild"));
    }

    // -- variable expansion ------------------------------------------------------------------------

    @Prefix("servers.${env}.")
    interface ExpandedPrefixConfig extends Config {
        String name();
    }

    @Test
    public void prefixShouldBeExpanded() {
        ExpandedPrefixConfig cfg = ConfigFactory.create(ExpandedPrefixConfig.class, new Properties() {{
            setProperty("env", "prod");
            setProperty("servers.prod.name", "alpha");
            setProperty("servers.test.name", "wrong");
        }});
        assertEquals("alpha", cfg.name());
    }

    /** The multi-environment example documented in the "Key prefix" chapter of the site. */
    @Prefix("servers.${env}.")
    interface EnvironmentSelectingConfig extends Config {
        @DisableFeature(PREFIX)
        @DefaultValue("dev")
        String env();

        String name();

        Integer port();
    }

    @Test
    public void theSelectorVariableShouldOptOutOfThePrefix() {
        Properties servers = new Properties() {{
            setProperty("servers.dev.name", "Development");
            setProperty("servers.dev.port", "6000");
            setProperty("servers.uat.name", "User Acceptance Test");
            setProperty("servers.uat.port", "60020");
        }};

        EnvironmentSelectingConfig dev = ConfigFactory.create(EnvironmentSelectingConfig.class, servers);
        assertEquals("dev", dev.env());
        assertEquals("Development", dev.name());
        assertEquals(Integer.valueOf(6000), dev.port());

        EnvironmentSelectingConfig uat = ConfigFactory.create(EnvironmentSelectingConfig.class, servers,
                new Properties() {{ setProperty("env", "uat"); }});
        assertEquals("User Acceptance Test", uat.name());
        assertEquals(Integer.valueOf(60020), uat.port());
    }

    @Prefix("servers.${env:dev}.")
    interface DefaultedSelectorConfig extends Config {
        String name();
    }

    @Test
    public void aVariableInThePrefixShouldSupportItsOwnDefaultValue() {
        Properties servers = new Properties() {{
            setProperty("servers.dev.name", "Development");
            setProperty("servers.uat.name", "User Acceptance Test");
        }};

        DefaultedSelectorConfig fallback = ConfigFactory.create(DefaultedSelectorConfig.class, servers);
        assertEquals("Development", fallback.name());

        DefaultedSelectorConfig uat = ConfigFactory.create(DefaultedSelectorConfig.class, servers,
                new Properties() {{ setProperty("env", "uat"); }});
        assertEquals("User Acceptance Test", uat.name());
    }

    @Prefix("servers.${env}.")
    interface UnexpandedPrefixConfig extends Config {
        @DisableFeature(VARIABLE_EXPANSION)
        String name();
    }

    @Test
    public void disablingVariableExpansionShouldLeaveThePrefixLiteral() {
        UnexpandedPrefixConfig cfg = ConfigFactory.create(UnexpandedPrefixConfig.class, new Properties() {{
            setProperty("env", "prod");
            setProperty("servers.${env}.name", "literal");
            setProperty("servers.prod.name", "wrong");
        }});
        assertEquals("literal", cfg.name());
    }

    @Prefix("servers.${owner.test.env}.")
    interface SystemPropertySelectedConfig extends Config {
        String name();
    }

    @Test
    public void thePrefixShouldBeExpandableFromASystemProperty() {
        System.setProperty("owner.test.env", "uat");
        try {
            SystemPropertySelectedConfig cfg = ConfigFactory.create(SystemPropertySelectedConfig.class,
                    System.getProperties(),
                    new Properties() {{
                        setProperty("servers.uat.name", "User Acceptance Test");
                        setProperty("servers.dev.name", "wrong");
                    }});
            assertEquals("User Acceptance Test", cfg.name());
        } finally {
            System.clearProperty("owner.test.env");
        }
    }

    // -- opting out --------------------------------------------------------------------------------

    @Prefix("server.")
    interface PartiallyPrefixedConfig extends Config {
        String name();

        @DisableFeature(PREFIX)
        String global();
    }

    @Test
    public void prefixShouldBeDisableableOnASingleMethod() {
        PartiallyPrefixedConfig cfg = ConfigFactory.create(PartiallyPrefixedConfig.class, new Properties() {{
            setProperty("server.name", "alpha");
            setProperty("global", "shared");
            setProperty("server.global", "wrong");
        }});
        assertEquals("alpha", cfg.name());
        assertEquals("shared", cfg.global());
    }

    @Prefix("server.")
    @DisableFeature(PREFIX)
    interface DisabledPrefixConfig extends Config {
        String name();
    }

    @Test
    public void prefixShouldBeDisableableOnTheWholeInterface() {
        DisabledPrefixConfig cfg = ConfigFactory.create(DisabledPrefixConfig.class, new Properties() {{
            setProperty("name", "alpha");
            setProperty("server.name", "wrong");
        }});
        assertEquals("alpha", cfg.name());
    }

    @DisableFeature(PREFIX)
    @Prefix("child.")
    interface DisabledPrefixChildConfig extends ParentConfig {
        String fromChild();
    }

    @Test
    public void disablingThePrefixOnAnInterfaceShouldNotReachInheritedMethods() {
        DisabledPrefixChildConfig cfg = ConfigFactory.create(DisabledPrefixChildConfig.class, new Properties() {{
            setProperty("parent.fromParent", "from-parent");
            setProperty("fromParent", "wrong");
            setProperty("fromChild", "from-child");
            setProperty("child.fromChild", "wrong");
        }});
        assertEquals("from-parent", cfg.fromParent());
        assertEquals("from-child", cfg.fromChild());
    }

    // -- interaction with other features ------------------------------------------------------------

    @Prefix("server.")
    interface MandatoryPrefixedConfig extends Config {
        @Mandatory
        String name();
    }

    @Test
    public void missingMandatoryPropertyShouldBeReportedWithItsPrefix() {
        try {
            ConfigFactory.create(MandatoryPrefixedConfig.class);
            fail("MissingMandatoryPropertyException is expected");
        } catch (MissingMandatoryPropertyException e) {
            assertEquals("server.name", e.getKeys().get(0));
        }
    }
}

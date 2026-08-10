/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.aeonbits.owner.StrSubstitutor.NESTED_VARIABLE_EXPANSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests for the expansion of nested variables, i.e. a <code>${...}</code> expression whose key is itself built
 * out of variables, as in <code>${servers.${env}.url}</code>.
 *
 * @author Matteo Baccan
 */
public class NestedVariableExpansionTest {

    private String savedSwitch;

    @Before
    public void saveTheSwitch() {
        savedSwitch = System.getProperty(NESTED_VARIABLE_EXPANSION);
    }

    /** Restores whatever the switch was before, so that these tests never clobber a JVM-wide setting. */
    @After
    public void restoreTheSwitch() {
        if (savedSwitch == null)
            System.clearProperty(NESTED_VARIABLE_EXPANSION);
        else
            System.setProperty(NESTED_VARIABLE_EXPANSION, savedSwitch);
    }

    // -- the scenario asked for in #326 --------------------------------------------------------------

    /**
     * The interface from the description of <a href="https://github.com/matteobaccan/owner/pull/326">#326</a>,
     * written exactly as its author wrote it: the switches to use depend on the browser, which in turn depends
     * on the environment.
     */
    interface WebDriverConfig extends Config {
        @Key("environment")
        @DefaultValue("global")
        String env();

        @Key("environments.${environment}.browser")
        @DefaultValue("chrome")
        String usedBrowser();

        @Key("environments.${environment}.webdriver.${environments.${environment}.browser}.switches")
        String webDriverOptions();
    }

    @Test
    public void aKeyShouldBeAllowedToDependOnAKeyThatDependsOnAnotherKey() {
        WebDriverConfig cfg = ConfigFactory.create(WebDriverConfig.class, new Properties() {{
            setProperty("environment", "dev");
            setProperty("environments.dev.browser", "opera");
            setProperty("environments.dev.webdriver.opera.switches", "foo bar");
            setProperty("environments.dev.webdriver.chrome.switches", "foo baz");
        }});
        assertEquals("dev", cfg.env());
        assertEquals("opera", cfg.usedBrowser());
        assertEquals("foo bar", cfg.webDriverOptions());
    }

    @Test
    public void theSelectedBrowserShouldFollowTheSelectedEnvironment() {
        WebDriverConfig cfg = ConfigFactory.create(WebDriverConfig.class, new Properties() {{
            setProperty("environment", "uat");
            setProperty("environments.uat.browser", "chrome");
            setProperty("environments.dev.browser", "opera");
            setProperty("environments.uat.webdriver.chrome.switches", "uat switches");
            setProperty("environments.dev.webdriver.opera.switches", "dev switches");
        }});
        assertEquals("uat switches", cfg.webDriverOptions());
    }

    /**
     * The other form the author of #326 tried, <code>${usedBrowser}</code>, cannot work and is not made to:
     * a variable names a <em>property</em>, not a method, so it resolves against the property named
     * <code>usedBrowser</code> — which is not the key that {@code usedBrowser()} reads.
     */
    interface MethodNameReferenceConfig extends Config {
        @Key("environments.${environment}.browser")
        String usedBrowser();

        @Key("environments.${environment}.webdriver.${usedBrowser}.switches")
        String webDriverOptions();
    }

    @Test
    public void aVariableShouldNameAPropertyNotAMethod() {
        Properties values = new Properties() {{
            setProperty("environment", "dev");
            setProperty("environments.dev.browser", "opera");
            setProperty("environments.dev.webdriver.opera.switches", "foo bar");
        }};

        MethodNameReferenceConfig cfg = ConfigFactory.create(MethodNameReferenceConfig.class, values);
        assertEquals("opera", cfg.usedBrowser());
        assertNull(cfg.webDriverOptions());

        // ...and it does resolve as soon as a property by that name exists
        values.setProperty("usedBrowser", "opera");
        assertEquals("foo bar", ConfigFactory.create(MethodNameReferenceConfig.class, values).webDriverOptions());
    }

    /**
     * The <code>StrSubstitutor</code> case from #326, copied from the pull request as its author wrote it:
     * a template holding two variables, one of which is itself built out of a variable.
     */
    @Test
    public void theTemplateFromTheOriginalPullRequest() {
        Properties values = new Properties();
        values.setProperty("environment", "dev");
        values.setProperty("environments.dev.browser", "chrome");
        values.setProperty("template",
                "environments.${environment}.webdriver.${environments.${environment}.browser}.switches.${environment}");

        assertEquals("environments.dev.webdriver.chrome.switches.dev",
                new StrSubstitutor(values).replace("${template}"));
    }

    /**
     * The other case the author of #326 reported, as a side effect of that implementation: a property whose
     * name contains braces, addressed as <code>${{foo}}</code>. It is <em>not</em> reproduced here, on purpose.
     * Only the <code>${</code> sequence opens a nesting level, so a lone <code>{</code> stays ordinary text and
     * the expression ends at the first <code>}</code>, exactly as in every release so far: the key looked up is
     * <code>{foo</code>, which resolves to nothing, and the trailing brace is left where it is.
     */
    @Test
    public void aPropertyWhoseNameContainsBracesIsStillNotAddressable() {
        Properties values = new Properties();
        values.setProperty("{foo}", "bar");
        values.setProperty("template", "foo.${{foo}}");

        assertEquals("foo.}", new StrSubstitutor(values).replace("${template}"));
    }

    // -- the substitution itself ---------------------------------------------------------------------

    @Test
    public void aNestedVariableShouldBuildTheKeyToLookUp() {
        Properties values = new Properties() {{
            setProperty("env", "dev");
            setProperty("servers.dev.url", "http://devhost");
        }};
        assertEquals("http://devhost", new StrSubstitutor(values).replace("${servers.${env}.url}"));
    }

    @Test
    public void nestingShouldWorkAtAnyDepth() {
        Properties values = new Properties() {{
            setProperty("one", "two");          // ${one}      -> two
            setProperty("two", "three");        // ${${one}}   -> three
            setProperty("three.value", "reached");
        }};
        assertEquals("reached", new StrSubstitutor(values).replace("${${${one}}.value}"));
    }

    @Test
    public void aNestedVariableShouldBeUsableInsideAValueToo() {
        Properties values = new Properties() {{
            setProperty("env", "dev");
            setProperty("servers.dev.url", "http://devhost");
            setProperty("story", "connecting to ${servers.${env}.url}");
        }};
        assertEquals("connecting to http://devhost", new StrSubstitutor(values).replace("${story}"));
    }

    @Test
    public void severalNestedVariablesInTheSameString() {
        Properties values = new Properties() {{
            setProperty("env", "dev");
            setProperty("servers.dev.host", "devhost");
            setProperty("servers.dev.port", "6000");
        }};
        assertEquals("devhost:6000",
                new StrSubstitutor(values).replace("${servers.${env}.host}:${servers.${env}.port}"));
    }

    @Test
    public void aNestedKeyShouldSupportADefaultValue() {
        Properties values = new Properties() {{
            setProperty("env", "test");
            setProperty("servers.dev.url", "http://devhost");
        }};
        assertEquals("http://fallback", new StrSubstitutor(values).replace("${servers.${env}.url:http://fallback}"));

        values.setProperty("servers.test.url", "http://testhost");
        assertEquals("http://testhost", new StrSubstitutor(values).replace("${servers.${env}.url:http://fallback}"));
    }

    @Test
    public void anUnresolvableNestedKeyShouldYieldTheEmptyString() {
        Properties values = new Properties() {{
            setProperty("env", "dev");
        }};
        assertEquals("[]", new StrSubstitutor(values).replace("[${servers.${env}.url}]"));
    }

    /** The example written in the "Nested variables" section of the site. */
    @Test
    public void theExampleDocumentedOnTheSite() {
        WebDriverConfig cfg = ConfigFactory.create(WebDriverConfig.class, new Properties() {{
            setProperty("environment", "dev");
            setProperty("environments.dev.browser", "opera");
            setProperty("environments.dev.webdriver.opera.switches", "--headless");
            setProperty("environments.dev.webdriver.chrome.switches", "--incognito");
        }});
        assertEquals("--headless", cfg.webDriverOptions());
    }

    interface NestedDefaultValueConfig extends Config {
        @DefaultValue("dev")
        String env();

        @DefaultValue("${servers.${env}.url:http://localhost}")
        String url();
    }

    @Test
    public void aNestedVariableShouldBeUsableInADefaultValue() {
        assertEquals("http://localhost", ConfigFactory.create(NestedDefaultValueConfig.class).url());

        assertEquals("http://devhost", ConfigFactory.create(NestedDefaultValueConfig.class, new Properties() {{
            setProperty("servers.dev.url", "http://devhost");
        }}).url());
    }

    /** The same substitution drives the expansion of {@link Config.Sources}, so nesting works there too. */
    @Test
    public void aSourceSpecificationShouldSupportNestingAsWell() {
        Properties props = new Properties() {{
            setProperty("env", "dev");
            setProperty("config.dev", "/etc/myapp/dev");
        }};
        assertEquals("file:/etc/myapp/dev/myconfig.properties",
                new VariablesExpander(props).expand("file:${config.${env}}/myconfig.properties"));
    }

    // -- a value is a value: it must not be looked up a second time -----------------------------------

    @Test
    public void aValueThatHappensToBeAKeyShouldNotBeResolvedAgain() {
        Properties values = new Properties() {{
            setProperty("animal", "dog");
            setProperty("dog", "cat");
        }};
        assertEquals("dog", new StrSubstitutor(values).replace("${animal}"));
    }

    @Test
    public void aValueThatHappensToBeAKeyShouldNotBeResolvedAgainInsideASentence() {
        Properties values = new Properties() {{
            setProperty("environment", "dev");
            setProperty("dev", "SHOULD NOT APPEAR");
            setProperty("story", "running on ${environment}");
        }};
        assertEquals("running on dev", new StrSubstitutor(values).replace("${story}"));
    }

    // -- text that only looks like a variable stays where it is ---------------------------------------

    @Test
    public void anEmptyExpressionIsNotAVariable() {
        Properties values = new Properties() {{
            setProperty("x", "expanded");
        }};
        assertEquals("${} and expanded", new StrSubstitutor(values).replace("${} and ${x}"));
    }

    @Test
    public void anUnbalancedExpressionIsLeftAlone() {
        Properties values = new Properties() {{
            setProperty("x", "expanded");
        }};
        assertEquals("${unclosed and expanded", new StrSubstitutor(values).replace("${unclosed and ${x}"));
    }

    @Test
    public void aLoneBraceInsideAKeyIsOrdinaryText() {
        Properties values = new Properties() {{
            setProperty("a{b", "braced");
        }};
        assertEquals("braced", new StrSubstitutor(values).replace("${a{b}"));
    }

    // -- the global switch ----------------------------------------------------------------------------

    @Test
    public void nestingShouldBeDisableableForTheWholeJvm() {
        Properties values = new Properties() {{
            setProperty("env", "dev");
            setProperty("servers.dev.url", "http://devhost");
        }};

        System.setProperty(NESTED_VARIABLE_EXPANSION, "false");

        // the pre-2.0.0 substitution: the first } closes the expression, so the key looked up is
        // "servers.${env" - which resolves to nothing - and ".url}" is left as it stands
        assertEquals(".url}", new StrSubstitutor(values).replace("${servers.${env}.url}"));
    }

    @Test
    public void disablingNestingShouldLeaveOrdinaryExpansionUntouched() {
        Properties values = new Properties() {{
            setProperty("animal", "quick ${color} fox");
            setProperty("color", "brown");
            setProperty("target", "lazy dog");
            setProperty("db.port", "5432");
        }};

        System.setProperty(NESTED_VARIABLE_EXPANSION, "false");

        StrSubstitutor sub = new StrSubstitutor(values);
        assertEquals("The quick brown fox jumped over the lazy dog.",
                sub.replace("The ${animal} jumped over the ${target}."));
        assertEquals("5432", sub.replace("${db.port:1521}"));
        assertEquals("1521", sub.replace("${db.host:1521}"));
    }

    @Test
    public void theSwitchShouldBeReadWhenTheSubstitutorIsCreated() {
        Properties values = new Properties() {{
            setProperty("env", "dev");
            setProperty("servers.dev.url", "http://devhost");
        }};

        System.setProperty(NESTED_VARIABLE_EXPANSION, "false");
        StrSubstitutor disabled = new StrSubstitutor(values);

        System.clearProperty(NESTED_VARIABLE_EXPANSION);
        StrSubstitutor enabled = new StrSubstitutor(values);

        assertEquals(".url}", disabled.replace("${servers.${env}.url}"));
        assertEquals("http://devhost", enabled.replace("${servers.${env}.url}"));
    }

    @Test
    public void anyValueOtherThanFalseKeepsNestingOn() {
        Properties values = new Properties() {{
            setProperty("env", "dev");
            setProperty("servers.dev.url", "http://devhost");
        }};

        System.setProperty(NESTED_VARIABLE_EXPANSION, "true");
        assertEquals("http://devhost", new StrSubstitutor(values).replace("${servers.${env}.url}"));

        System.setProperty(NESTED_VARIABLE_EXPANSION, "yes please");
        assertEquals("http://devhost", new StrSubstitutor(values).replace("${servers.${env}.url}"));
    }
}

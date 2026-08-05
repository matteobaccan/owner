package org.aeonbits.owner.variableexpansion;

import static org.aeonbits.owner.Config.DisableableFeature.PARAMETER_FORMATTING;
import static org.aeonbits.owner.Config.DisableableFeature.VARIABLE_EXPANSION;
import static org.aeonbits.owner.util.Collections.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.Properties;

/**
 * @author aknopov
 */
public class ParametrizedKeyTest
{
    private static final String DEV_SETUP = "dev";
    private static final String UAT_SETUP = "uat";

    @Config.Sources("classpath:org/aeonbits/owner/variableexpansion/KeyExpansionExample.xml")
    public interface MyConfig extends Config {
        @Key("servers.%s.name")
        String name(String setup);

        @Key("servers.%s.hostname")
        String hostname(String setup);

        @Key("servers.%s.port")
        Integer port(String setup);

        @Key("servers.%s.user")
        String user(String setup);

        @DisableFeature(VARIABLE_EXPANSION)
        @Key("servers.%s.password")
        String password(String setup);
    }

    @Test
    public void testKeyParametrization() {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);

        assertEquals("DEV", cfg.name(DEV_SETUP));
        assertEquals("devhost", cfg.hostname(DEV_SETUP));
        assertEquals(Integer.valueOf(6000), cfg.port(DEV_SETUP));
        assertEquals("myuser1", cfg.user(DEV_SETUP));
        assertNull(cfg.password(DEV_SETUP)); // expansion is disabled on method level

        assertEquals("UAT", cfg.name(UAT_SETUP));
        assertEquals("uathost", cfg.hostname(UAT_SETUP));
        assertEquals(Integer.valueOf(60020), cfg.port(UAT_SETUP));
        assertEquals("myuser2", cfg.user(UAT_SETUP));
        assertNull(cfg.password(UAT_SETUP)); // expansion is disabled on method level
    }

    public enum Colour { GREEN, RED }

    public interface AnyFormatterArgument extends Config {
        @Key("foo.%s")
        String foo(int index);

        @Key("queue.%s")
        String queue(Colour colour);

        @Key("db.%s.port")
        Integer port(String database);
    }

    private static Properties properties(String... keysAndValues) {
        if (keysAndValues.length % 2 != 0)
            throw new IllegalArgumentException(
                    "keys and values must come in pairs, got " + keysAndValues.length + " arguments");
        Properties props = new Properties();
        for (int i = 0; i < keysAndValues.length; i += 2)
            props.setProperty(keysAndValues[i], keysAndValues[i + 1]);
        return props;
    }

    private static Properties anyFormatterArgumentProperties() {
        return properties("foo.1", "a",
                "queue.GREEN", "jms/QueueA",
                "db.users.port", "5432");
    }

    @Test
    public void testKeyParametrizationWithAnyFormatterArgument() {
        AnyFormatterArgument cfg =
                ConfigFactory.create(AnyFormatterArgument.class, anyFormatterArgumentProperties());

        assertEquals("a", cfg.foo(1));                       // numeric index
        assertEquals("jms/QueueA", cfg.queue(Colour.GREEN)); // enums are formatted through toString()
        assertEquals(Integer.valueOf(5432), cfg.port("users"));
    }

    @Test
    public void testKeyParametrizationReturnsNullWhenTheResultingKeyIsUndefined() {
        AnyFormatterArgument cfg =
                ConfigFactory.create(AnyFormatterArgument.class, anyFormatterArgumentProperties());

        assertNull(cfg.foo(2));
        assertNull(cfg.queue(Colour.RED));
        assertNull(cfg.port("orders"));
    }

    public interface ParametrizedKeyWithDefaultValue extends Config {
        @Key("foo.%s")
        @DefaultValue("no value for %s")
        String foo(String index);
    }

    @Test
    public void testDefaultValueIsFormattedWithTheSameParameters() {
        ParametrizedKeyWithDefaultValue cfg = ConfigFactory.create(ParametrizedKeyWithDefaultValue.class,
                map("foo.bar", "value for bar"));

        assertEquals("value for bar", cfg.foo("bar"));
        assertEquals("no value for baz", cfg.foo("baz"));
    }

    public interface ParametersAndVariablesMixedInTheKey extends Config {
        @Key("${prefix}.%s")
        String mixed(int index);
    }

    @Test
    public void testParametersAreNotAppliedToKeysContainingVariables() {
        ParametersAndVariablesMixedInTheKey cfg =
                ConfigFactory.create(ParametersAndVariablesMixedInTheKey.class,
                        properties("prefix", "foo",
                                "foo.1", "a",
                                "foo.%s", "unformatted"));

        // the key is expanded to "foo.%s", the parameter is not applied to it
        assertEquals("unformatted", cfg.mixed(1));
    }

    public interface ParametrizedKeyWithVariableExpansionDisabled extends Config {
        @DisableFeature(VARIABLE_EXPANSION)
        @Key("foo.%s")
        String foo(int index);
    }

    @Test
    public void testDisablingVariableExpansionDisablesParametrizedKeys() {
        ParametrizedKeyWithVariableExpansionDisabled cfg =
                ConfigFactory.create(ParametrizedKeyWithVariableExpansionDisabled.class,
                        properties("foo.1", "a", "foo.%s", "unformatted"));

        // the key is used as it is, '%s' included
        assertEquals("unformatted", cfg.foo(1));
    }

    public interface ParametrizedKeyWithParameterFormattingDisabled extends Config {
        @DisableFeature(PARAMETER_FORMATTING)
        @Key("greet.%s")
        String greet(int index);
    }

    @Test
    public void testDisablingParameterFormattingDoesNotAffectParametrizedKeys() {
        ParametrizedKeyWithParameterFormattingDisabled cfg =
                ConfigFactory.create(ParametrizedKeyWithParameterFormattingDisabled.class,
                        map("greet.1", "hello %s"));

        // the key is still parametrized, the value is not formatted
        assertEquals("hello %s", cfg.greet(1));
    }
}

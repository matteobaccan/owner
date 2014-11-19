package org.aeonbits.owner.variableexpansion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Random;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Substitutor;
import org.junit.Test;


public class SubstitutorTest {
    
    public static class DateSubstitutor implements Substitutor {
        private Date date = new Date();
        public String replace(String strToReplace) {
            return new SimpleDateFormat(strToReplace).format(date);
        }
    }

    @Config.SubstitutorClasses(names = "date", classes = DateSubstitutor.class)
    public interface UseOfCustomeDateSubstitutorConfig extends Config {
        @DefaultValue("Today is: ${date:yyyyMMdd}")
        String today();
    }
    
    @Test
    public void canSubstituteWhenSingleSubstituorClassProvided() {
        UseOfCustomeDateSubstitutorConfig cfg = ConfigFactory.create(UseOfCustomeDateSubstitutorConfig.class);
        assertTrue(cfg.today().matches("Today is: \\d{8}"));
    }
    
    @Test
    public void shouldOnlyInstantiateSubstitutorClassOnce() {
        UseOfCustomeDateSubstitutorConfig cfg = ConfigFactory.create(UseOfCustomeDateSubstitutorConfig.class);
        String today = cfg.today(); 
        assertTrue(today.equals(cfg.today()));
    }

    public static class RandomIntSubstitutor implements Substitutor {
        Random random = new Random();
        public String replace(String strToReplace) {
            return String.valueOf(random.nextInt(Integer.parseInt(strToReplace)));
        }
    }

    @Config.SubstitutorClasses(
            names = { "random", "date" },
            classes = { RandomIntSubstitutor.class, DateSubstitutor.class })
    public interface UseOfMultipleCustomeSubstitutorConfig extends Config {
        @DefaultValue("A random '${random:100}' integer given this year ${date:yyyy}")
        String random();
    }
    
    @Test
    public void canSubstituteWhenMultipleSubstituorClassesProvided() {
        UseOfMultipleCustomeSubstitutorConfig cfg = ConfigFactory.create(UseOfMultipleCustomeSubstitutorConfig.class);
        String expectedPattern = "A random '\\d{1,2}' integer given this year \\d{4}";
        assertTrue(cfg.random().matches(expectedPattern));
    }

    @Config.SubstitutorClasses(
            names = { "random", "date" },
            classes = { RandomIntSubstitutor.class })
    public interface MismatchInNumberOfNamesAndClassesConfig extends Config {
        String key();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldThrowWhenMismatchInNumberOfNamesAndClasses() throws Exception {
        ConfigFactory.create(MismatchInNumberOfNamesAndClassesConfig.class);
    }

    static class NotInstantiatableSubstitutor implements Substitutor {
        public String replace(String strToReplace) {
            return "";
        }
    }

    @Config.SubstitutorClasses(
            names = "uninstantiatable", classes = NotInstantiatableSubstitutor.class)
    public interface WithUninstantiatableSubstitutorConfig extends Config {
        String key();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldThrowWhenSubstitutorNotInstantiatable() throws Exception {
        ConfigFactory.create(WithUninstantiatableSubstitutorConfig.class);
    }

    public interface WithNoSubstitutorConfig extends Config {
        @DefaultValue("Substitutor ${a:value} is missing")
        String key();
    }

    @Test
    public void canSubstituteToEmptyStringWhenSubstitutorIsMissing() throws Exception {
        WithNoSubstitutorConfig cfg = ConfigFactory.create(WithNoSubstitutorConfig.class);
        assertEquals("Substitutor  is missing", cfg.key());
    }

    public static class HandlingEmptyValueSubstitutor implements Substitutor {
        public String replace(String strToReplace) {
            assertTrue(strToReplace.isEmpty());
            return "default-value";
        }
    }

    @Config.SubstitutorClasses(
            names = "a", classes = HandlingEmptyValueSubstitutor.class)
    public interface WithEmptyValueSubstitutorConfig extends Config {
        @DefaultValue("Empty ${a:} value")
        String key();
    }

    @Test
    public void canPassEmptyStringToSubstitutor() throws Exception {
        WithEmptyValueSubstitutorConfig cfg = ConfigFactory.create(WithEmptyValueSubstitutorConfig.class);
        assertEquals("Empty default-value value", cfg.key());
    }

    public interface WithNameStartingByColonConfig extends Config {
        @DefaultValue("${:a}")
        String key();
    }

    @Test
    public void canUseDefaultSubstitutorWhenNameStartsWithColon() throws Exception {
        Properties p = new Properties();
        p.setProperty(":a", "A");
        WithNameStartingByColonConfig cfg = ConfigFactory.create(WithNameStartingByColonConfig.class, p);
        assertEquals("A", cfg.key());
    }

    public interface WithValueContainingColonConfig extends Config {

        @DefaultValue("${na:me}")
        String key();
    }

    @Test
    public void canUseDefaultSubstitutorWhenNameIsNotARegisteredSubstitutor() throws Exception {
        Properties p = new Properties();
        p.setProperty("na:me", "NAME");
        WithValueContainingColonConfig cfg = ConfigFactory.create(WithValueContainingColonConfig.class, p);
        assertEquals("NAME", cfg.key());
    }

}

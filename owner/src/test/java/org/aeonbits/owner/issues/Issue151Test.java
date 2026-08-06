/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * See: https://github.com/lviggiano/owner/issues/151
 * <p>
 * The reporter's properties file selects the Hibernate dialect through the name of the database, which means
 * building the key to read out of the value of another key. Up to 1.0.12 that produced
 * <code>.hibernate.dialect}</code>, since the first <code>}</code> closed the expression; since 1.0.13 the
 * expression inside <code>${...}</code> is expanded before being looked up.
 */
public class Issue151Test {

    /** The properties file from the issue, verbatim. */
    private static Properties dialects(final String database) {
        return new Properties() {{
            setProperty("database", database);
            setProperty("hibernate.dialect", "${${database}.hibernate.dialect}");
            setProperty("hsql.hibernate.dialect", "org.hibernate.dialect.HSQLDialect");
            setProperty("mysql.hibernate.dialect", "org.hibernate.dialect.MySQL5Dialect");
        }};
    }

    public interface HibernateConfig extends Config {
        @Key("hibernate.dialect")
        String dialect();
    }

    @Test
    public void theDialectShouldBeSelectedByTheDatabaseName() {
        assertEquals("org.hibernate.dialect.HSQLDialect",
                ConfigFactory.create(HibernateConfig.class, dialects("hsql")).dialect());

        assertEquals("org.hibernate.dialect.MySQL5Dialect",
                ConfigFactory.create(HibernateConfig.class, dialects("mysql")).dialect());
    }
}

package org.aeonbits.owner;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.hamcrest.CoreMatchers.both;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.contains;

public class CollectionSupportTest {
    private CollectionConfig cfg;

    public interface CollectionConfig extends Config {
        @DefaultValue("pink,black")
        Collection<String> colors();
    }

    @Before
    public void setUp() throws Exception {
        cfg = ConfigFactory.create(CollectionConfig.class);
    }

    @Test
    public void itShouldReadCollection() throws Exception {
        assertThat(cfg.colors(), contains("pink", "black"));
    }

}

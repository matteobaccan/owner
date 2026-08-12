/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config.HotReloadType;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.crypto.Decryptor;
import org.aeonbits.owner.event.TransactionalPropertyChangeListener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests for {@link PropertiesManager} internals: annotations inherited from super-interfaces,
 * encrypted keys handling, invalid sources and property change listeners management.
 *
 * @author Matteo Baccan
 */
@RunWith(MockitoJUnitRunner.class)
public class PropertiesManagerTest {

    @Mock
    private ScheduledExecutorService scheduler;

    @Mock
    private TransactionalPropertyChangeListener transactionalListener;

    @Mock
    private PropertyChangeListener plainListener;

    @Mock
    private PropertyChangeListener anotherListener;

    private final VariablesExpanderForTest expander = new VariablesExpanderForTest(new Properties());

    private final LoadersManagerForTest loaders = new LoadersManagerForTest();

    interface SimpleConfig extends Config {
    }

    @LoadPolicy(LoadType.MERGE)
    interface LoadPolicyParent extends Config {
    }

    @Sources({"classpath:org/aeonbits/owner/first.properties", "classpath:org/aeonbits/owner/second.properties"})
    interface LoadPolicyChild extends Config, LoadPolicyParent {
    }

    @HotReload(value = 1, type = HotReloadType.SYNC)
    interface HotReloadParent extends Config {
    }

    interface HotReloadChild extends Config, HotReloadParent {
    }

    @Sources("this is not a valid uri")
    interface InvalidSourceConfig extends Config {
    }

    public static class ReverseDecryptor implements Decryptor {
        @Override
        public String decrypt(String value) {
            return new StringBuilder(value).reverse().toString();
        }

        @Override
        public String decrypt(String value, String defaultValue) {
            try {
                return decrypt(value);
            } catch (Exception e) {
                return defaultValue;
            }
        }
    }

    interface EncryptedConfig extends Config {
        @EncryptedValue(ReverseDecryptor.class)
        String secret();

        @EncryptedValue
        String sharedSecret();

        String plain();
    }

    private PropertiesManagerForTest newPropertiesManager(Class<? extends Config> clazz, Properties props) {
        return new PropertiesManagerForTest(clazz, props, scheduler, expander, loaders);
    }

    @Test
    public void testLoadPolicyInheritedFromSuperInterface() {
        Properties props = new Properties();
        PropertiesManagerForTest manager = newPropertiesManager(LoadPolicyChild.class, props);

        manager.load();

        // MERGE comes from LoadPolicyParent: with the default FIRST policy 'bar' would not be loaded
        assertEquals("first", props.getProperty("foo"));
        assertEquals("second", props.getProperty("bar"));
        assertEquals("first", props.getProperty("baz"));
    }

    @Test
    public void testHotReloadInheritedFromSuperInterface() {
        PropertiesManagerForTest manager = newPropertiesManager(HotReloadChild.class, new Properties());

        manager.syncReloadCheck();

        // the inherited hot reload is SYNC, so nothing must be scheduled in background
        verifyNoInteractions(scheduler);
    }

    @Test
    public void testInvalidSourceURI() {
        try {
            newPropertiesManager(InvalidSourceConfig.class, new Properties());
            fail("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getCause() instanceof URISyntaxException);
        }
    }

    @Test
    public void testDecryptIfNecessaryUsesMethodLevelDecryptor() throws Exception {
        PropertiesManagerForTest manager = newPropertiesManager(EncryptedConfig.class, new Properties());

        Method secret = EncryptedConfig.class.getMethod("secret");
        Method sharedSecret = EncryptedConfig.class.getMethod("sharedSecret");
        Method plain = EncryptedConfig.class.getMethod("plain");

        assertEquals("terces", manager.decryptIfNecessary(secret, "secret"));
        assertEquals("secret", manager.decryptIfNecessary(sharedSecret, "secret"));
        assertEquals("secret", manager.decryptIfNecessary(plain, "secret"));
    }

    @Test
    public void testRemovePropertyChangeListenerWithWrappedListeners() {
        PropertiesManagerForTest manager = newPropertiesManager(SimpleConfig.class, new Properties());

        manager.addPropertyChangeListener("foo", plainListener);
        manager.addPropertyChangeListener("foo", anotherListener);
        assertEquals(2, manager.getPropertyChangeListeners().size());

        // skips the first wrapper (no match) and removes the second one
        manager.removePropertyChangeListener(anotherListener);
        assertEquals(1, manager.getPropertyChangeListeners().size());

        // no matching wrapper anymore: the list is left untouched
        manager.removePropertyChangeListener(anotherListener);
        assertEquals(1, manager.getPropertyChangeListeners().size());
    }

    @Test
    public void testPropertyChangeListenerWrapperDelegatesHashCodeAndEquals() {
        PropertiesManagerForTest manager = newPropertiesManager(SimpleConfig.class, new Properties());

        manager.addPropertyChangeListener("foo", plainListener);
        PropertyChangeListener wrapper = manager.getPropertyChangeListeners().get(0);

        assertEquals(plainListener.hashCode(), wrapper.hashCode());
        assertEquals(wrapper, plainListener);
        assertNotEquals(wrapper, anotherListener);
    }

    @Test
    public void testSetPropertyNotifiesTransactionalListenersBeforeChange() throws Throwable {
        PropertiesManagerForTest manager = newPropertiesManager(SimpleConfig.class, new Properties());
        manager.setProxy(new Object());
        manager.addPropertyChangeListener(transactionalListener);
        manager.addPropertyChangeListener(plainListener);

        manager.setProperty("primeNumber", "17");

        assertEquals("17", manager.getProperty("primeNumber"));
        verify(transactionalListener, times(1)).beforePropertyChange(any(PropertyChangeEvent.class));
        verify(transactionalListener, times(1)).propertyChange(any(PropertyChangeEvent.class));
        verify(plainListener, times(1)).propertyChange(any(PropertyChangeEvent.class));
    }

}

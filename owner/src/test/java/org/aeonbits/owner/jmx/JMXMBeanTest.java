/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.jmx;

import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Mutable;
import org.aeonbits.owner.Reloadable;
import org.junit.Test;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.DynamicMBean;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Robin Meißner
 */
public class JMXMBeanTest {

	private static interface JMXConfigMutableReloadable extends DynamicMBean, Mutable, Reloadable {
		@DefaultValue("8080")
		int port();

		@DefaultValue("http://localhost")
		String hostname();

		@DefaultValue("42")
		int maxThreads();
	}

	/*
	 * Simple test case for JMX accessible mbeans.
	 * 
	 * Registers a config of JMXConfigMutableReloadable.class
	 * under object name org.aeonbits.owner.jmx:type=testBeanHandling,id=JMXConfigMutableReloadable and
	 * tests getAttribute(s) methods and invokes setProperty actions with it.
	 */
	@Test
	public void testBeanHandling() throws Throwable {
		Properties props = new Properties();
		JMXConfigMutableReloadable config = ConfigFactory.create(JMXConfigMutableReloadable.class, props);
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName mbeanName = new ObjectName(
				"org.aeonbits.owner.jmx:type=testBeanHandling,id=JMXConfigMutableReloadable");
		mbs.registerMBean(config, mbeanName);
		
		assertEquals("8080", mbs.getAttribute(mbeanName, "port"));
		AttributeList attrList =  new AttributeList();
		attrList.add(new Attribute("port", "8080"));
		attrList.add(new Attribute("hostname", "http://localhost"));
		attrList.add(new Attribute("maxThreads", "42"));
		assertEquals(attrList,  mbs.getAttributes(mbeanName, new String[] { "port", "hostname", "maxThreads"}));
		
		mbs.invoke(mbeanName, "setProperty", new String[] { "port", "7878" },
				null);
		assertEquals("7878", mbs.getAttribute(mbeanName, "port"));
		assertEquals("7878", mbs.invoke(mbeanName, "getProperty", new String[] { "port"}, null));		
		
		mbs.invoke(mbeanName, "reload", null, null);
		assertEquals(attrList,  mbs.getAttributes(mbeanName, new String[] { "port", "hostname", "maxThreads"}));
	}
	
	/*
	 * Test case for registering multiple mbeans with same configuration object.
	 */
	@Test
	public void testMultipleBeanHandling() throws Throwable {
		Properties props = new Properties();
		
		JMXConfigMutableReloadable config = ConfigFactory.create(JMXConfigMutableReloadable.class, props);
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName mbeanName1 = new ObjectName(
				"org.aeonbits.owner.jmx:type=testMultipleBeanHandling,id=JMXConfigMutableReloadable");
		ObjectName mbeanName2 = new ObjectName(
				"org.aeonbits.owner.jmx:type=testMultipleBeanHandling2,id=JMXConfigMutableReloadable");		
		mbs.registerMBean(config, mbeanName1);
		mbs.registerMBean(config, mbeanName2);
		mbs.setAttribute(mbeanName1, new Attribute("port", "7878"));
		assertEquals("7878", mbs.getAttribute(mbeanName1, "port"));			
		assertEquals(mbs.getAttribute(mbeanName2, "port"), mbs.getAttribute(mbeanName1, "port"));		
	}
	
}

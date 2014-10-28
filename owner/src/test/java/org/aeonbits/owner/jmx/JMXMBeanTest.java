package org.aeonbits.owner.jmx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import java.lang.management.ManagementFactory;
import java.util.Properties;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import junit.framework.Assert;

import org.aeonbits.owner.ConfigCache;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.JMXBean;
import org.junit.Test;

public class JMXMBeanTest {

	private static interface JMXConfig extends JMXBean {
		@DefaultValue("8080")
		int port();

		@DefaultValue("http://localhost")
		String hostname();

		@DefaultValue("42")
		int maxThreads();
	}

	@Test
	public void testBeanHandling() throws MalformedObjectNameException, AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, InstanceAlreadyExistsException, NotCompliantMBeanException {
		Properties props = new Properties();
		JMXConfig config = ConfigFactory.create(JMXConfig.class, props);
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName mbeanName = new ObjectName(
				"org.aeonbits.owner.jmx:type=JMXMBeanTest,id=JMXMBeanTest");
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
}

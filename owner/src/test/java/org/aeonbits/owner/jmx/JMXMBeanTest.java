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
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.JMXBean;
import org.aeonbits.owner.Mutable;
import org.aeonbits.owner.Reloadable;
import org.junit.Test;

public class JMXMBeanTest {

	private static interface JMXConfigMutableReloadable extends JMXBean, Mutable, Reloadable {
		@DefaultValue("8080")
		int port();

		@DefaultValue("http://localhost")
		String hostname();

		@DefaultValue("42")
		int maxThreads();
	}
	
	private static interface JMXConfigOnlyAccessible extends JMXBean {
		@DefaultValue("1")
		int number();
	}
	
	private static interface JMXConfigMutableNoReload extends JMXBean, Mutable {
		@DefaultValue("1")
		int number();
	}
	
	/**
	 * 
	 * Simple test case for JMX accessible mbeans. 
	 * 
	 * Registers a config of JMXConfigMutableReloadable.class
	 * under object name org.aeonbits.owner.jmx:type=testBeanHandling,id=JMXConfigMutableReloadable and
	 * tests getAttribute(s) methods and invokes setProperty actions with it.
	 * 
	 * @throws MalformedObjectNameException
	 * @throws AttributeNotFoundException
	 * @throws InstanceNotFoundException
	 * @throws MBeanException
	 * @throws ReflectionException
	 * @throws InstanceAlreadyExistsException
	 * @throws NotCompliantMBeanException
	 */
	@Test
	public void testBeanHandling() throws MalformedObjectNameException, AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, InstanceAlreadyExistsException, NotCompliantMBeanException {
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
	
	/**
	 * 
	 * Test case for registering multiple mbeans with same configuration object.
	 * 
	 * @throws MalformedObjectNameException
	 * @throws AttributeNotFoundException
	 * @throws InstanceNotFoundException
	 * @throws MBeanException
	 * @throws ReflectionException
	 * @throws InstanceAlreadyExistsException
	 * @throws NotCompliantMBeanException
	 * @throws InvalidAttributeValueException
	 */
	@Test
	public void testMultipleBeanHandling() throws MalformedObjectNameException, AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, InstanceAlreadyExistsException, NotCompliantMBeanException, InvalidAttributeValueException {
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
	
	/**
	 * Test of MBeanInfo description of immutable (and not reloadable) config instance
	 * 
	 * @throws MalformedObjectNameException
	 * @throws AttributeNotFoundException
	 * @throws InstanceNotFoundException
	 * @throws MBeanException
	 * @throws ReflectionException
	 * @throws InstanceAlreadyExistsException
	 * @throws NotCompliantMBeanException
	 * @throws IntrospectionException 
	 */
	@Test
	public void testBeanNotMutableAndReloadable() throws MalformedObjectNameException, AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, InstanceAlreadyExistsException, NotCompliantMBeanException, IntrospectionException {
		Properties props = new Properties();
		JMXConfigOnlyAccessible config = ConfigFactory.create(JMXConfigOnlyAccessible.class, props);
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName mbeanName = new ObjectName(
				"org.aeonbits.owner.jmx:type=testBeanNotReloadable,id=JMXConfigOnlyAccessible");
		mbs.registerMBean(config, mbeanName);
		MBeanInfo info = mbs.getMBeanInfo(mbeanName);

		MBeanOperationInfo[] operationsInfo = new MBeanOperationInfo[]{
				new MBeanOperationInfo("getProperty", "getProperties", 
						new MBeanParameterInfo[] { new MBeanParameterInfo("Propertykey", "java.lang.String", "Key of the property") }, 
						"java.lang.String", MBeanOperationInfo.INFO)
		};
		assertArrayEquals(operationsInfo, info.getOperations());		
	}
	
	/**
	 * Test of MBeanInfo description of not reloadable config instance
	 * 
	 * @throws MalformedObjectNameException
	 * @throws AttributeNotFoundException
	 * @throws InstanceNotFoundException
	 * @throws MBeanException
	 * @throws ReflectionException
	 * @throws InstanceAlreadyExistsException
	 * @throws NotCompliantMBeanException
	 * @throws IntrospectionException
	 */
	@Test
	public void testBeanNotReloadable() throws MalformedObjectNameException, AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, InstanceAlreadyExistsException, NotCompliantMBeanException, IntrospectionException {
		Properties props = new Properties();
		JMXConfigMutableNoReload config = ConfigFactory.create(JMXConfigMutableNoReload.class, props);
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName mbeanName = new ObjectName(
				"org.aeonbits.owner.jmx:type=testBeanNotReloadable,id=JMXConfigMutableNoReload");	
		mbs.registerMBean(config, mbeanName);
		MBeanInfo info = mbs.getMBeanInfo(mbeanName);
		
		MBeanOperationInfo[] operationsInfo = new MBeanOperationInfo[]{
				new MBeanOperationInfo("getProperty", "getProperties", 
						new MBeanParameterInfo[] { new MBeanParameterInfo("Propertykey", "java.lang.String", "Key of the property") }, 
						"java.lang.String", MBeanOperationInfo.INFO),
				new MBeanOperationInfo("setProperty", "setProperties", 
						new MBeanParameterInfo[] { 
							new MBeanParameterInfo("Propertykey", "java.lang.String", "Key of the property"), 
						 	new MBeanParameterInfo("Propertyvalue", "java.lang.String", "Value of the property")
						},
						"void", MBeanOperationInfo.ACTION)
		};
		assertArrayEquals(operationsInfo, info.getOperations());	
	}
}

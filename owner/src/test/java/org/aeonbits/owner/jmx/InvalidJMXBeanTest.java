/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.jmx;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Properties;

public class InvalidJMXBeanTest {

	private static interface ConfigNoJMX extends Config {
		@DefaultValue("1")
		int number();
	}	
	
	@Test(expected=NotCompliantMBeanException.class)
	public void testConfigWithoutJMXInterface() throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException{
		Properties props = new Properties();
		ConfigNoJMX config = ConfigFactory.create(ConfigNoJMX.class, props);
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName mbeanName = new ObjectName("org.aeonbits.owner.jmx:type=testConfigWithoutJMXInterface,id=ConfigNoJMX");
		mbs.registerMBean(config, mbeanName);
	}
	
}

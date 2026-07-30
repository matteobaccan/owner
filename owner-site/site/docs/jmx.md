---
layout: docs
title: JMX support
prev_section: preprocessors
next_section: features
permalink: /docs/jmx/
---

Every `Config` object created by OWNER is also a [Dynamic MBean][dynamicmbean]: you can register it on an
`MBeanServer` and inspect or modify your configuration at runtime with any JMX client, such as JConsole or
VisualVM.

  [dynamicmbean]: https://docs.oracle.com/javase/8/docs/api/javax/management/DynamicMBean.html

There is nothing to enable: just register the config instance on the MBean server of your choice.

```java
public interface MyConfig extends Mutable, Reloadable {
    @Key("server.port.number")
    @DefaultValue("80")
    int port();

    @Key("server.host.name")
    @DefaultValue("localhost")
    String hostname();
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);

MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
mbs.registerMBean(cfg, new ObjectName("org.aeonbits.owner:type=configuration,name=MyConfig"));
```

What the MBean exposes
----------------------

- **Attributes**: one per property, named after the property key (for instance `server.port.number`). Attributes
  are exposed as strings and are both readable and writable: setting an attribute updates the configuration
  exactly like a `setProperty()` call on a [Mutable]({{ site.url }}/docs/accessible-mutable/) config.
- **Operations**:
  - `getProperty(key)` — gets the value for a property;
  - `setProperty(key, value)` — sets the value for a property;
  - `reload()` — reloads the configuration, like a `reload()` call on a
    [Reloadable]({{ site.url }}/docs/reload/) config.

Changes performed through JMX act on the live configuration object: the next call to a config method will return
the updated value. Combined with the [event support]({{ site.url }}/docs/event-support/), this lets your
application react immediately to changes applied from a JMX console:

```java
cfg.addPropertyChangeListener(new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
        System.out.printf("property %s changed from %s to %s%n",
                evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
    }
});
```

A runnable demo is available in [JMXExample] on GitHub: launch it, attach JConsole to the process, and play with
the attributes and operations of the `org.aeonbits.owner:type=configuration,name=MyConfig` MBean.

  [JMXExample]: https://github.com/matteobaccan/owner/blob/master/owner/src/test/java/org/aeonbits/owner/examples/JMXExample.java

<div class="note">
  <h5>Same instance, multiple names</h5>
  <p>
    The same config instance can be registered on the MBean server under multiple object names; all the
    registrations act on the same underlying configuration.
  </p>
</div>

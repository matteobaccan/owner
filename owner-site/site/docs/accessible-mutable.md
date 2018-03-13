---
layout: docs
title: Accessible and Mutable
prev_section: reload
next_section: debugging
permalink: /docs/accessible-mutable/
---

By default, object created by OWNER are immutable and promote information hiding.

This means that once the Config object is created its properties cannot be modified, and cannot be accessed in any other
way than using the methods that are properties mapping methods.

Those limitations are imposed by design, but sometime users may find this problematic. So here they come in the play the
interfaces [Mutable] and [Accessible].

  [Mutable]: http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Mutable.html
  [Accessible]: http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Accessible.html

This is the hierarchy of the `Mutable` and `Accessible` interfaces:

![config-hierarchy]({{site.url}}/img/config-hierarchy.png)

The Mutable interface
---------------------

The [Mutable] interface allows the developer for runtime modifications of the properties contained in the Config object.

Example:

```java

interface MyConfig extends Mutable {
    @DefaultValue("18")
    public Integer minAge();
    public Integer maxAge();
}


MyConfig cfg = ConfigFactory.create(MyConfig.class);

// this comes from the @DefaultValue
assertEquals(Integer.valueOf(18), cfg.minAge());

// now we change the minAge to 21 using setProperty
String oldValue = cfg.setProperty("minAge", "21");
assertEquals("18", oldValue); // the old value was 18
assertEquals(Integer.valueOf(21), cfg.minAge()); // now is 21

// now we remove the minAge property
oldValue = cfg.removeProperty("minAge");
assertEquals("21", oldValue); // the old value is 21
assertNull(cfg.minAge()); // now is null
```

In the above example we saw `setProperty` and `removeProperty` in action, but the Mutable interface adds even more
methods like `clear()`, `load(InputStream)` and `load(Reader)`, and it should allow you to achieve complete write access
to the properties contained inside a Config object.

The Accessible interface
------------------------

As the [Mutable] interface allows for write access to the properties contained inside a Config object, the [Accessible]
interface allows for read access.

Example:

```java
interface MyConfig extends Accessible {
    @DefaultValue("Bohemian Rapsody - Queen")
    String favoriteSong();

    @Key("salutation.text")
    @DefaultValue("Good Morning")
    String salutation();
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);
assertEquals("Good Morning", cfg.getProperty("salutation.text"));

// print all properties to a PrintWriter
cfg.list(System.out);

// saves properties to an OutputStream
File tmp = File.createTempFile("owner-", ".tmp");
cfg.store(new FileOutputStream(tmp), "no comments");

```

As you can see, [Accessible] is not limited to the `getProperty()` method, but you can also use this
interface to `list()` or `store()` the properties.

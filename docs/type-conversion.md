---
layout: docs
title: Type conversion
permalink: /docs/type-conversion/
---

OWER API supports properties conversion for primitive types and enums.
When you define the *mapping interface* you can use a wide set of return types, 
and they will be automatically converted from `String` to the primitive types 
and enums:

```java
// conversion happens from the value specified in the 
// properties files (if available).
int maxThreads();

// conversion happens also from @DefaultValue
@DefaultValue("3.1415")
double pi();

// enum values are case sensitive!
// java.util.concurrent.TimeUnit is an enum
@DefaultValue("NANOSECONDS");
TimeUnit timeUnit();
```

Since version 1.0.2 it is possible to have configuration interfaces to declare 
business objects as return types, many are compatible and you can also define 
your own objects:

The easiest way is to define your business object with a public constructor 
taking a single parameter of type `java.lang.String`:

```java
public class CustomType {
    private final String text;

    public CustomType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}

public interface SpecialTypes extends Config {
    @DefaultValue("foobar.txt")
    File sampleFile();

    @DefaultValue("http://owner.aeonbits.org")
    URL sampleURL();

    @DefaultValue("example")
    CustomType customType();

    @DefaultValue("Hello %s!")
    CustomType salutation(String name);
}
```

OWNER API will take the value "example" and pass it to the CustomType 
constructor then return it.

Arrays and Collections
----------------------

Version 1.0.4 introduced first class support for Java Arrays and Collections.

So now you can define properties like:

```java
public class MyConfig extends Config {

    @DefaultValue("apple, pear, orange")
    public String[] fruit();

    @Separator(";")
    @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
    public int[] fibonacci();

    @Separator(File.pathSeparator);
    File[] path();

    @DefaultValue("1, 2, 3, 4")
    List<Integer> ints();

    @DefaultValue("http://owner.aeonbits.org, http://www.github.com, http://www.google.com")
    MyOwnCollection<URL> myBookmarks();

    // Concrete class are allowed (in this case java.util.Stack)
    // when type is not specified <String> is assumed as default
    @DefaultValue(
        "The Lord of the Rings, The Little Prince, The Da Vinci Code")
    Stack books();
}
```

You can use array of objects or primitive Java types, as well as Java 
collections, as specified by interfaces [`Collection`][Collection], 
[`List`][List], [`Set`][Set], [`SortedSet`][SortedSet] or concrete 
implementations like [`Vector`][Vector], [`Stack`][Stack], 
[`LinkedList`][LinkedList] etc. or your own concrete implementation of the Java
Collections Framework interfaces, as long as your implementation class defines 
a default no-arg constructor.

  [Collection]: http://docs.oracle.com/javase/7/docs/api/java/util/Collection.html
  [List]: http://docs.oracle.com/javase/7/docs/api/java/util/List.html
  [Set]: http://docs.oracle.com/javase/7/docs/api/java/util/Set.html
  [SortedSet]: http://docs.oracle.com/javase/7/docs/api/java/util/SortedSet.html
  [Vector]: http://docs.oracle.com/javase/7/docs/api/java/util/Vector.html
  [Stack]: http://docs.oracle.com/javase/7/docs/api/java/util/Stack.html
  [LinkedList]: http://docs.oracle.com/javase/7/docs/api/java/util/LinkedList.html

The [`Map`][Map] interface and sub-interfaces are not supported.

  [Map]: http://docs.oracle.com/javase/7/docs/api/java/util/Map.html

By default OWNER uses the comma `","` character to tokenize values for the 
arrays and collections, but you can specify different characters (and regexp) 
with the [`@Separator`][separator] annotation or, if your property format has a 
more complex split logic, you can define your own tokenizer class via the 
[`@TokenizerClass`][tokenizerclass] annotation plus [`Tokenizer`][tokenizer] 
interface.

  [separator]: http://owner.newinstance.it/maven-site/apidocs/org/aeonbits/owner/Config.Separator.html
  [tokenizerclass]: http://owner.newinstance.it/maven-site/apidocs/org/aeonbits/owner/Config.TokenizerClass.html
  [tokenizer]: http://owner.newinstance.it/maven-site/apidocs/org/aeonbits/owner/Config.Tokenizer.html

Example:

```java
public class MyConfig extends Config {

    @Separator(";")
    @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
    public int[] fibonacci();

    @TokenizerClass(CustomDashTokenizer.class)
    @DefaultValue("foo-bar-baz")
    public String[] withSeparatorClass();

}

public class CustomDashTokenizer implements Tokenizer {
    
    // this logic can be as much complex as you need
    @Override
    public String[] tokens(String values) {
        return values.split("-", -1);  
    }
}
```

The [`@Separator`][separator] and [`@TokenizerClass`][tokenizerclass] 
annotations can be specified on method level and on class level. When specified 
on method level, the annotation will affect only that method. When specified on 
class level, the annotation will affect the complete class.

Annotations specified on method level override the setting specified on the 
class level:

```java
@Separator(";")
public interface ArrayExample extends Config {

    // takes the class level @Separator
    @DefaultValue("1; 2; 3; 4")
    public int[] semicolonSeparated(); 

    // overrides the class-level @Separator(";")
    @Separator(",")
    @DefaultValue("1, 2, 3, 4")
    public int[] commaSeparated();

    // overrides the class level @Separator(";")
    @TokenizerClass(CustomDashTokenizer.class)
    @DefaultValue("1-2-3-4")
    public int[] dashSeparated();
}
```

<div class="note warning">
  <h5>@Separator and @TokenizerClass don't go together!</h5>
    Notice that it is invalid to specify together on the same level both 
    <tt>@Separator</tt> and <tt>@TokenizerClass</tt> annotations: 
    you cannot specify two different ways to do the same thing!
</div>

So in following cases you'll get a [`UnsupportedOperationException`][unsupported-ex]:

```java

// @Separator and @TokenizerClass cannot be used together on class level.
@TokenizerClass(CustomCommaTokenizer.class)
@Separator(",")
public interface Wrong extends Config {

    // will throw UnsupportedOperationException!
    @DefaultValue("1, 2, 3, 4")
    public int[] commaSeparated(); 

}

public interface AlsoWrong extends Config {

    // will throw UnsupportedOperationException!
    // @Separator and @TokenizerClass cannot be 
    // used together on method level.
    @Separator(";")
    @TokenizerClass(CustomDashTokenizer.class)
    @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
    public int[] conflictingAnnotationsOnMethodLevel(); 

}
```

  [unsupported-ex]: http://docs.oracle.com/javase/7/docs/api/java/lang/UnsupportedOperationException.html

But even though the following example contains a conflict on class level 
(and should be considered a bug in the example), OWNER is able to resolve things
correctly on method level:

```java

// @Separator and @TokenizerClass cannot be used together 
// on class level.
@Separator(";")
@TokenizerClass(CustomDashTokenizer.class)
public interface WrongButItWorks extends Config {

    // but this overrides the class level annotations
    // hence it will work!
    @Separator(";") 
    @DefaultValue("1, 2, 3, 4")
    public int[] commaSeparated();

}
```

It is not recommended to have above wrong annotations setup: it is considered a bug in the code, and even if this setup
works at the moment, we may change this behavior in future.

But there is more. OWNER API supports automatic conversion for:

  1. Primitive types: boolean, byte, short, integer, long, float, double.
  2. Enums (notice that the conversion is case sensitive, so FOO != foo or Foo).
  3. java.lang.String, of course (no conversion is needed).
  4. java.net.URL, java.net.URI.
  5. java.io.File (the character `~` will be expanded to `user.home` System Property).
  6. java.lang.Class (this can be useful, for instance, if you want to load the jdbc driver, or similar cases).
  7. Any instantiable class declaring a public constructor with a single argument of type `java.lang.String`.
  8. Any instantiable class declaring a public constructor with a single argument of type `java.lang.Object`.
  9. Any class declaring a public *static* method `valueOf(java.lang.String)` that returns an instance of itself.
  10. Any class for which you can register a [`PropertyEditor`][propedit] via
      [`PropertyEditorManager.registerEditor()`][propeditmanager].
  11. Any array having above types as elements.
  12. Any Java Collections of all above types: Set, List, SortedSet or concrete implementations like LinkedHashSet or user
      defined collections having a default no-arg constructor. [`Map`][Map] and sub-interfaces are not supported.

If OWNER API cannot find any way to map your business object, you'll receive a [`UnsupportedOperationException`][unsupported-ex]
with some meaningful description to identify the problem as quickly as possible.

You can also register your custom [`PropertyEditor`][propedit] to convert text properties into your business objects
using the static method [`PropertyEditorManager.registerEditor()`][propeditmanager].  
See also [`PropertyEditorSupport`][propeditsupport], it may be useful if you want to implement a `PropertyEditor`.

  [propeditmanager]: http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditorManager.html#registerEditor
  [propedit]: http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditor.html
  [propeditsupport]:http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditorSupport.html

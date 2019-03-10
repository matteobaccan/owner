# Properties Creator

## Documentation
Properties Creator is an Owner plugin userful to automate properties file creation based on Owner Config class.

It can be useful in case you have lot of properties in your config class and you don't want to waste your time creating the corresponding properties file.

### Config class annotations
These are all available annotations that can be used with Properties Creator:
| Annotation                                      | Description                                   |
| ----------------------------------------------- |-----------------------------------------------|
| @Deprecated                                     | If the property is deprecated                 |
| @Noproperty                                     | Avoid to write property in properties file    |
| @Key("value")                                   | The key used for lookup for the property      |
| @DefaultValue("value")                          | Default value of the property                 |
| @ValorizedAs("value")                           | Real value of the property                    |
| @Description("description")                     | Description of the property                   |
| @Group({"group", "sub group"})                  | Group of the property                         |
| @GroupOrder({"first group", "second group"})    | Order group list                              |

### Plugin configuration
These are all available configuration that can be used with Properties Creator Plugin
| Tag                               | Description                                                                   |
|-----------------------------------|-------------------------------------------------------------------------------|
| outputDirectory **[Required]**    | Output directory where created propertied will be saved                       |
| configurationClass **[Required]** | Class with configuration to parse                                             |
| projectName                       | Project name to print                                                         |
| jarPath                           | Path of custom jar where is **configurationClass**                            |
| librariesFolder                   | Folder where there are all dependency required to load **configurationClass** |
| propertiesTemplate                | Template file to customize created properties file                            |

### Properties Template
If you don't like default template of created properties file you can change it linking a custom template file.
#### Default template
```
${header}
${body}
${footer}
```
You can use these anchor as you wish in your custom template.</br>
Remember that **body** anchor is **required** because of properties otherwise no property will be printed.
#### Custom template example
```
My beautiful header
For an amazing project

${body}


${footer}
```

## Example
### Config class
```JavaScript
@GroupOrder({"second", "first"})
interface MyConfig extends Config {

    @Group({"first"})
    @Key("valuekey")
    @DefaultValue("value")
    String value();

    @DefaultValue("value2")
    String value2();

    @Group({"second"})
    @DefaultValue("value3")
    @ValorizedAs("realvalue3")
    String value3();

    @Group({"second", "sub"})
    @DefaultValue("value4")
    @Deprecated
    String value4();
}
```

### Pom.xml

```Xml
        <plugin>
            <groupId>org.aeonbits.owner</groupId>
            <artifactId>maven-plugin-owner-creator</artifactId>
            <version>1.0.11-SNAPSHOT</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>create</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <outputDirectory>${basedir}/test.properties</outputDirectory>
                <configurationClass>com.tryproject.MyConfig</configurationClass>
                <projectName>test</projectName>
                <!-- <jarPath>possiblecustomjar.jar</jarPath> -->
                <!-- <librariesFolder>dir/libFolder</librariesFolder> -->
                <!-- <propertiesTemplate>${basedir}/template.txt</propertiesTemplate>-->
            </configuration>
        </plugin>
```

### Output properties file

```Properties
# Properties file created for: 'test' 


#------------------------------------------------------------------------------
# second
#------------------------------------------------------------------------------

#
# 
# 
# Default ("value3")
#
value3=realvalue3

# ----------------------------
# - sub -
# ----------------------------

#
# DEPRECATED PROPERTY
# 
# 
# Default ("value4")
#
#value4=value4


#------------------------------------------------------------------------------
# first
#------------------------------------------------------------------------------

#
# 
# 
# Default ("value")
#
#valuekey=value

#------------------------------------------------------------------------------
# GENERIC PROPERTIES
#------------------------------------------------------------------------------

#
# 
# 
# Default ("value2")
#
#value2=value2



# Properties file autogenerated by OWNER :: PropertyFileCreator
# Created [2019/03/08 01:23:30] in 11 ms

```

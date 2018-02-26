package org.aeonbits.owner.loadstrategies;

import static org.junit.Assert.assertEquals;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.LoadersManagerForTest;
import org.aeonbits.owner.VariablesExpanderForTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

@RunWith(MockitoJUnitRunner.class)
public class LoadPathsWithSpacesTest extends LoadStrategyTestBase {

  @Mock
  private ScheduledExecutorService scheduler;
  @Spy
  private final LoadersManagerForTest loaders = new LoadersManagerForTest();
  private final VariablesExpanderForTest expander = new VariablesExpanderForTest(new Properties());


  @Config.Sources({
      "file:${user.dir}/src/test/resources/org/aeonbits/owner/directory with spaces/simple.properties"})
  public static interface FileConfigWithSource extends Config {
    String helloWorld();
  }

  @Config.Sources({
      "classpath:org/aeonbits/owner/directory with spaces/simple.properties"})
  public static interface ClasspathConfigWithSource extends Config {
    String helloWorld();
  }

  @Test
  public void shouldLoadFromFile() throws Exception {
    FileConfigWithSource sample = ConfigFactory.create(FileConfigWithSource.class);
    assertEquals("Hello World!", sample.helloWorld());
  }

  @Test
  public void shouldLoadFromClasspath() throws Exception {
    ClasspathConfigWithSource sample = ConfigFactory.create(ClasspathConfigWithSource.class);
    assertEquals("Hello World!", sample.helloWorld());
  }

}

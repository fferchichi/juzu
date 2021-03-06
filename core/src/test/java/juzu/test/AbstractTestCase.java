/*
 * Copyright 2013 eXo Platform SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package juzu.test;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;
import juzu.impl.common.Name;
import juzu.impl.fs.spi.disk.DiskFileSystem;
import juzu.impl.fs.spi.url.URLFileSystem;
import juzu.impl.inject.spi.InjectorProvider;
import juzu.impl.common.JSON;
import juzu.impl.common.Tools;
import juzu.test.protocol.mock.MockApplication;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a> */
//@RunWith(JUnit38ClassRunner.class)
public abstract class AbstractTestCase extends Assert {

  /** Encoding proof euro sign. */
  public static final String EURO = "\u20AC";

  @Before
  public void setUp() throws Exception {
    Registry.clear();
  }

  @After
  public void tearDown() {
    if (application != null) {
      Tools.safeClose(application);
      application = null;
    }
  }

  /**
   * Wait for at least one millisecond, based on the current time clock.
   *
   * @return the time captured after the wait
   */
  public static long waitForOneMillis() {
    long snapshot = System.currentTimeMillis();
    while (true) {
      try {
        long now = System.currentTimeMillis();
        if (snapshot < now) {
          return now;
        }
        else {
          snapshot = now;
          Thread.sleep(1);
        }
      }
      catch (InterruptedException e) {
        AssertionFailedError afe = new AssertionFailedError("Was not expecting interruption");
        afe.initCause(e);
        throw afe;
      }
    }
  }

  public static void fail(Throwable t) {
    throw failure(t);
  }

  public static AssertionFailedError failure(Throwable t) {
    AssertionFailedError afe = new AssertionFailedError();
    afe.initCause(t);
    return afe;
  }

  public static AssertionFailedError failure(String msg, Throwable t) {
    AssertionFailedError afe = new AssertionFailedError(msg);
    afe.initCause(t);
    return afe;
  }

  public static AssertionFailedError failure(String msg) {
    return new AssertionFailedError(msg);
  }

  public static <T> T assertInstanceOf(Class<T> expectedInstance, Object o) {
    if (expectedInstance.isInstance(o)) {
      return expectedInstance.cast(o);
    }
    else {
      throw failure("Was expecting " + o + " to be an instance of " + expectedInstance.getName());
    }
  }

  public static <T> T assertNotInstanceOf(Class<?> expectedInstance, T o) {
    if (expectedInstance.isInstance(o)) {
      throw failure("Was expecting " + o + " not to be an instance of " + expectedInstance.getName());
    }
    else {
      return o;
    }
  }

  public static void assertDelete(File f) {
    if (!f.exists()) {
      throw failure("Was expecting file " + f.getAbsolutePath() + " to exist");
    }
    if (!f.delete()) {
      throw failure("Was expecting file " + f.getAbsolutePath() + " to be deleted");
    }
  }

  public static DiskFileSystem diskFS(Name packageName) {
    File root = new File(System.getProperty("juzu.test.resources.path"));
    return new DiskFileSystem(root, packageName);
  }

  public static DiskFileSystem diskFS(String packageName) {
    File root = new File(System.getProperty("juzu.test.resources.path"));
    return new DiskFileSystem(root, packageName);
  }

  @Rule
  public TestName name = new TestName();

  /** The currently deployed mock application. */
  private MockApplication<File> application;

  public final String getName() {
    return name.getMethodName();
  }

  public final CompilerAssert<File, File> compiler(String packageName) {
    return compiler(false, packageName);
  }

  public final CompilerAssert<File, File> incrementalCompiler(String packageName) {
    return compiler(true, packageName);
  }

  private CompilerAssert<File, File> compiler(boolean incremental, String packageName) {
    return compiler(incremental, Name.parse(packageName), getQualifiers());

  }

  /**
   * Override to append additional qualifiers.
   *
   * @return the qualifiers
   */
  protected ArrayList<String> getQualifiers() {
    ArrayList<String> qualifiers = new ArrayList<String>();
    String methodName = name.getMethodName();
    if (methodName != null) {
      qualifiers.add(methodName);
    }
    return qualifiers;
  }

  public static CompilerAssert<File, File> compiler(boolean incremental, Name packageName, String... qualifiers) {
    return compiler(incremental, packageName, Arrays.asList(qualifiers));
  }

  private static CompilerAssert<File, File> compiler(boolean incremental, Name packageName, List<String> qualifiers) {
    if (packageName.isEmpty()) {
      throw failure("Cannot compile empty package");
    }

    //
    String outputPath = System.getProperty("juzu.test.workspace.path");
    File a = new File(outputPath);
    if (a.exists()) {
      if (a.isFile()) {
        throw failure("File " + outputPath + " already exist and is a file");
      }
    }
    else {
      if (!a.mkdirs()) {
        throw failure("Could not create test generated source directory " + outputPath);
      }
    }

    // Find
    StringBuilder pkg = Tools.join(new StringBuilder(), '_', packageName);
    if (qualifiers != null) {
      for (String qualifier : qualifiers) {
        pkg.append('#').append(qualifier);
      }
    }

    File f2 = new File(a, pkg.toString());
    for (int count = 0;;count++) {
      if (!f2.exists()) {
        break;
      }
      else {
        f2 = new File(a, pkg + "-" + count);
      }
    }

    //
    if (!f2.mkdirs()) {
      throw failure("Could not create test generated source directory " + f2.getAbsolutePath());
    }

    //
    File sourceOutputDir = new File(f2, "source-output");
    assertTrue(sourceOutputDir.mkdir());
    DiskFileSystem sourceOutput = new DiskFileSystem(sourceOutputDir);

    //
    File classOutputDir = new File(f2, "class-output");
    assertTrue(classOutputDir.mkdir());
    DiskFileSystem classOutput = new DiskFileSystem(classOutputDir);

    //
    File sourcePathDir = new File(f2, "source-path");
    String relativePath = packageName.toString().replace('.', '/') + '/';
    assertTrue(new File(sourcePathDir, relativePath).mkdirs());
    DiskFileSystem sourcePath = new DiskFileSystem(sourcePathDir);
    try {
      URL url = Thread.currentThread().getContextClassLoader().getResource(relativePath);
      URLFileSystem fs = new URLFileSystem();
      fs.add(url);
      fs.copy(sourcePath, sourcePath.getPath(packageName));
    }
    catch (Exception e) {
      throw failure(e);
    }

    //
    return new CompilerAssert<File, File>(incremental, sourcePath, sourceOutput, classOutput);
  }

  public MockApplication<File> application(InjectorProvider injectorProvider, String packageName) {
    if (application != null) {
      throw failure("An application is already deployed");
    }
    CompilerAssert<File, File> helper = compiler(packageName);
    helper.assertCompile();
    try {
      return application = new MockApplication<File>(
          helper.getClassOutput(),
          helper.getClassLoader(),
          injectorProvider,
          Name.parse(packageName));
    }
    catch (Exception e) {
      throw AbstractTestCase.failure(e);
    }
  }

  public static void assertEquals(JSON expected, JSON test) {
    if (expected != null) {
      if (test == null) {
        throw failure("Was expected " + expected + " to be not null");
      }
      else if (!equalsIgnoreNull(expected, test)) {
        StringBuilder sb;
        try {
          sb = new StringBuilder("expected <");
          expected.toString(sb, 2);
          sb.append(">  but was:<");
          test.toString(sb, 2);
          sb.append(">");
          throw failure(sb.toString());
        }
        catch (IOException e) {
          throw failure("Unexpected", e);
        }
      }
    }
    else {
      if (test != null) {
        throw failure("Was expected " + test + " to be null");
      }
    }
  }

  private static boolean equalsIgnoreNull(Object o1, Object o2) {
    if (o1 == null || o2 == null) {
      return true;
    } else if (o1 instanceof List && o2 instanceof List) {
      Iterator i1 = ((List)o1).iterator();
      Iterator i2 = ((List)o2).iterator();
      while (true) {
        boolean n1 = i1.hasNext();
        boolean n2 = i2.hasNext();
        if (n1 && n2) {
          if (!equalsIgnoreNull(i1.next(), i2.next())) {
            return false;
          }
        } else {
          return n1 == n2;
        }
      }
    } else {
      if (o1 instanceof JSON && o2 instanceof JSON) {
        JSON js1 = (JSON)o1;
        JSON js2 = (JSON)o2;
        HashSet<String> names = new HashSet<String>(js1.names());
        names.addAll(js2.names());
        for (String name : names) {
          js1.getArray("", Object.class);
          Object v1 = js1.get(name);
          Object v2 = js2.get(name);
          if (!equalsIgnoreNull(v1, v2)) {
            return false;
          }
        }
        return true;
      } else {
        return o1.equals(o2);
      }
    }
  }

  public static void assertNoSuchElement(Iterator<?> iterator) {
    try {
      Object next = iterator.next();
      fail("Was not expecting to obtain " + next + " element from an iterator");
    }
    catch (NoSuchElementException expected) {
    }
  }

  public static <E> void assertEquals(List<? extends E> expected, Iterable<? extends E> test) {
    int index = 0;
    Iterator<? extends E> expectedIterator = expected.iterator();
    Iterator<? extends E> testIterator = test.iterator();
    while (true) {
      if (expectedIterator.hasNext()) {
        if (testIterator.hasNext()) {
          E expectedNext = expectedIterator.next();
          E testNext = testIterator.next();
          if (!Tools.safeEquals(expectedNext, testNext)) {
            throw failure("Elements at index " + index + " are not equals: " + expectedNext + "!=" + testNext);
          } else {
            index++;
          }
        } else {
          StringBuilder buffer = new StringBuilder("Expected iterable has more elements (");
          while (expectedIterator.hasNext()) {
            buffer.append(expectedIterator.next());
            buffer.append(',');
          }
          buffer.setCharAt(buffer.length() - 1, ')');
          buffer.append(" than the tested iterable at index ").append(index);
          throw failure(buffer.toString());
        }
      } else {
        if (testIterator.hasNext()) {
          StringBuilder buffer = new StringBuilder("Tested iterable has more elements (");
          while (testIterator.hasNext()) {
            buffer.append(testIterator.next());
            buffer.append(',');
          }
          buffer.setCharAt(buffer.length() - 1, ')');
          buffer.append(" than the expected iterable at index ").append(index);
          throw failure(buffer.toString());
        } else {
          break;
        }
      }
    }
  }
}

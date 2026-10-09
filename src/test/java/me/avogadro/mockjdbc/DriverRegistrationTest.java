package me.avogadro.mockjdbc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.util.Properties;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Registration in DriverManager: done outside Boomi, skipped inside Boomi (detected by the presence of
 * com.boomi.execution.ExecutionManager) so that the driver's class loader can be released.
 */
public final class DriverRegistrationTest {

	/** directory with a fake com.boomi.execution.ExecutionManager, compiled for these tests only */
	private static File fakeBoomi;

	@BeforeClass
	public static void compileFakeBoomi() throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		Assume.assumeNotNull(compiler);
		fakeBoomi = java.nio.file.Files.createTempDirectory("mockjdbc-fake-boomi").toFile();
		File source = new File(fakeBoomi, "com/boomi/execution/ExecutionManager.java");
		Assert.assertTrue(source.getParentFile().mkdirs());
		Writer writer = new FileWriter(source);
		writer.write("package com.boomi.execution;\npublic class ExecutionManager {\n"
				+ "  public static Object getCurrent() { return null; }\n}\n");
		writer.close();
		Assert.assertEquals(0, compiler.run(null, null, null, "-d", fakeBoomi.getPath(), source.getPath()));
	}

	@AfterClass
	public static void cleanup() {
		delete(fakeBoomi);
	}

	private static void delete(File file) {
		if (file == null) {
			return;
		}
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				delete(child);
			}
		}
		file.delete();
	}

	private static Class<?> driverClass(URLClassLoader loader) throws ClassNotFoundException {
		return Class.forName("me.avogadro.mockjdbc.MockJdbcDriver", true, loader);
	}

	private static boolean isRegistered(Class<?> driverClass) throws Exception {
		return (Boolean) driverClass.getMethod("isRegistered").invoke(null);
	}

	private static String query(Class<?> driverClass) throws Exception {
		driverClass.getMethod("addInMemoryTableResource", String.class, String.class).invoke(null, "users", "name\nJohn");
		Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
		Connection connection = driver.connect("any", new Properties());
		ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM users");
		return resultSet.next() ? resultSet.getString("name") : null;
	}

	@Test
	public void testRegisteredOutsideBoomi() throws Exception {
		URLClassLoader loader = IsolatedDriver.newClassLoader();
		try {
			Class<?> driverClass = driverClass(loader);
			Assert.assertTrue(isRegistered(driverClass));
			driverClass.getMethod("deregister").invoke(null);
			Assert.assertFalse(isRegistered(driverClass));
			// a second deregister does nothing
			driverClass.getMethod("deregister").invoke(null);
		} finally {
			loader.close();
		}
	}

	@Test
	public void testNotRegisteredInsideBoomi() throws Exception {
		URLClassLoader loader = IsolatedDriver.newClassLoader(fakeBoomi.toURI().toURL());
		try {
			Class<?> driverClass = driverClass(loader);
			Assert.assertFalse(isRegistered(driverClass));
			// deregister() without registration: no exception, no effect
			driverClass.getMethod("deregister").invoke(null);
			Assert.assertFalse(isRegistered(driverClass));
			// the driver works when instantiated by class name, as Boomi does
			Assert.assertEquals("John", query(driverClass));
		} finally {
			loader.close();
		}
	}

	private static WeakReference<ClassLoader> useWithoutDeregister(URL... extraUrls) throws Exception {
		URLClassLoader loader = IsolatedDriver.newClassLoader(extraUrls);
		Assert.assertEquals("John", query(driverClass(loader)));
		WeakReference<ClassLoader> reference = new WeakReference<ClassLoader>(loader);
		loader.close();
		return reference;
	}

	private static boolean collected(WeakReference<ClassLoader> reference) throws InterruptedException {
		for (int i = 0; i < 50 && reference.get() != null; i++) {
			System.gc();
			Thread.sleep(20);
		}
		return reference.get() == null;
	}

	/** inside Boomi nothing keeps the class loader alive, even without calling deregister() */
	@Test
	public void testClassLoaderReleasedInsideBoomiWithoutDeregister() throws Exception {
		Assert.assertTrue("the class loader is still referenced", collected(useWithoutDeregister(fakeBoomi.toURI().toURL())));
	}

	/** outside Boomi the registration in DriverManager keeps the class loader alive until deregister() */
	@Test
	public void testClassLoaderKeptByDriverManagerOutsideBoomi() throws Exception {
		WeakReference<ClassLoader> reference = useWithoutDeregister();
		try {
			Assert.assertFalse(collected(reference));
		} finally {
			ClassLoader loader = reference.get();
			if (loader != null) {
				driverClass((URLClassLoader) loader).getMethod("deregister").invoke(null);
			}
		}
	}
}

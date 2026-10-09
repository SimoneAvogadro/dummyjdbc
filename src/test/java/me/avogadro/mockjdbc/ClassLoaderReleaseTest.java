package me.avogadro.mockjdbc;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

/**
 * A runtime that replaces the driver jar (e.g. Boomi) can only release the jar file once the driver's class loader is
 * no longer referenced. The driver must not keep it alive by itself: in particular no value of a driver class may stay
 * in a ThreadLocal of a long-lived (pooled) thread, like the thread running this test.
 */
public final class ClassLoaderReleaseTest {

	/** location (directory or jar) of the classes of the given class, null if not available */
	private static URL locationOf(String className) {
		try {
			return Class.forName(className).getProtectionDomain().getCodeSource().getLocation();
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	private static URLClassLoader newDriverClassLoader() {
		List<URL> urls = new ArrayList<URL>();
		for (String className : new String[] { "me.avogadro.mockjdbc.MockJdbcDriver", "au.com.bytecode.opencsv.CSVReader",
				"org.slf4j.LoggerFactory", "org.aspectj.lang.JoinPoint" }) {
			URL location = locationOf(className);
			if (location != null && !urls.contains(location)) {
				urls.add(location);
			}
		}
		// parent = platform class loader: the driver classes are loaded again, isolated from the test's copy
		return new URLClassLoader(urls.toArray(new URL[0]), getPlatformClassLoader());
	}

	private static ClassLoader getPlatformClassLoader() {
		try {
			return (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
		} catch (Exception e) {
			return null; // Java 8: bootstrap class loader, which also provides java.sql
		}
	}

	/** uses the isolated driver on the current thread: queries, numbered resources, writes, reset */
	private static WeakReference<ClassLoader> useDriverAndRelease() throws Exception {
		URLClassLoader loader = newDriverClassLoader();
		Class<?> driverClass = Class.forName("me.avogadro.mockjdbc.MockJdbcDriver", true, loader);
		Assert.assertNotSame(MockJdbcDriver.class, driverClass);

		Method addResource = driverClass.getMethod("addInMemoryTableResource", String.class, String.class);
		addResource.invoke(null, "users#1", "name\nJohn");
		Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
		Connection connection = driver.connect("any", new Properties());
		ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM users");
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("John", resultSet.getString("name"));
		PreparedStatement insert = connection.prepareStatement("INSERT INTO users (name) VALUES (?)");
		insert.setString(1, "Mary");
		insert.executeUpdate();
		driverClass.getMethod("resetCounters").invoke(null);

		// what a container does when it unloads the driver
		driverClass.getMethod("deregister").invoke(null);
		WeakReference<ClassLoader> reference = new WeakReference<ClassLoader>(loader);
		loader.close();
		return reference;
	}

	@Test
	public void testClassLoaderCanBeReleasedAfterUseOnALongLivedThread() throws Exception {
		WeakReference<ClassLoader> reference = useDriverAndRelease();

		for (int i = 0; i < 50 && reference.get() != null; i++) {
			System.gc();
			Thread.sleep(20);
		}
		Assert.assertNull("the driver keeps its class loader alive", reference.get());
	}
}

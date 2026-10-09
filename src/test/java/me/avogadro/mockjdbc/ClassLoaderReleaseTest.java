package me.avogadro.mockjdbc;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

/**
 * A runtime that replaces the driver jar (e.g. Boomi) can only release the jar file once the driver's class loader is
 * no longer referenced. The driver must not keep it alive by itself: in particular no value of a driver class may stay
 * in a ThreadLocal of a long-lived (pooled) thread, like the thread running this test.
 */
public final class ClassLoaderReleaseTest {

	/** uses the isolated driver on the current thread: queries, numbered resources, writes, reset */
	private static WeakReference<ClassLoader> useDriverAndRelease() throws Exception {
		URLClassLoader loader = IsolatedDriver.newClassLoader();
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

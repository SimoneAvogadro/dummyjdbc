package me.avogadro.dummyjdbc.boomi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access to the Boomi runtime ({@value #EXECUTION_UTIL_CLASS}) through reflection.
 * <p>
 * The driver has no compile time dependency on Boomi: when the Boomi classes are not on the classpath
 * {@link #isBoomi()} is <code>false</code> and every method of this class does nothing.
 *
 * @author Simone Avogadro
 */
public final class BoomiExecutionUtil {

	private static final Logger LOGGER = LoggerFactory.getLogger(BoomiExecutionUtil.class);

	/** Boomi class exposing the static methods for Dynamic Process Properties */
	public static final String EXECUTION_UTIL_CLASS = "com.boomi.execution.ExecutionUtil";

	/** Prefix of the Dynamic Process Properties read and written by the driver */
	public static final String PROPERTY_PREFIX = "dummyjdbc_";

	private static boolean initialized = false;
	private static Method getDynamicProcessPropertyMethod;
	private static Method setDynamicProcessPropertyMethod;

	private BoomiExecutionUtil() {
		// static utility
	}

	/**
	 * @return <code>true</code> if the Boomi runtime classes have been found, i.e. the driver is running inside Boomi
	 */
	public static boolean isBoomi() {
		init();
		return getDynamicProcessPropertyMethod != null && setDynamicProcessPropertyMethod != null;
	}

	/**
	 * Name of the Dynamic Process Property used for a driver resource, keeping the case of the resource name, e.g.
	 * <code>users_PARAMS</code> becomes <code>dummyjdbc_users_PARAMS</code>
	 *
	 * @param resourceName name of a table, test case, step or captured parameters
	 * @return the property name (prefix + resource name as written)
	 */
	public static String propertyName(String resourceName) {
		return PROPERTY_PREFIX + resourceName.trim();
	}

	/**
	 * Reads the Dynamic Process Property of a driver resource. Boomi property names are case sensitive while the
	 * driver resources are not, so the name is tried as written first (e.g. <code>dummyjdbc_T_014a</code>) and then in
	 * lower case (<code>dummyjdbc_t_014a</code>). Empty or blank values count as not defined.
	 *
	 * @param resourceName name of a table, test case, step or captured parameters
	 * @return the trimmed property value, or <code>null</code> if not defined or not running inside Boomi
	 */
	public static String getResourceProperty(String resourceName) {
		if (!isBoomi()) {
			return null;
		}
		String exactName = propertyName(resourceName);
		String value = trimToNull(getDynamicProcessProperty(exactName));
		if (value == null) {
			String lowerCaseName = propertyName(resourceName.toLowerCase());
			if (!lowerCaseName.equals(exactName)) {
				value = trimToNull(getDynamicProcessProperty(lowerCaseName));
			}
		}
		return value;
	}

	/**
	 * Writes the Dynamic Process Property of a driver resource (never persisted) both with the name as written (e.g.
	 * <code>dummyjdbc_Users_PARAMS</code>) and in lower case (<code>dummyjdbc_users_params</code>).
	 *
	 * @param resourceName name of a table, test case or captured parameters
	 * @param value the value to set
	 * @return <code>true</code> if the properties have been set, <code>false</code> when not running inside Boomi or in
	 *         case of errors
	 */
	public static boolean setResourceProperty(String resourceName, String value) {
		if (!isBoomi()) {
			return false;
		}
		String exactName = propertyName(resourceName);
		boolean ok = setDynamicProcessProperty(exactName, value);
		String lowerCaseName = propertyName(resourceName.toLowerCase());
		if (!lowerCaseName.equals(exactName)) {
			ok &= setDynamicProcessProperty(lowerCaseName, value);
		}
		return ok;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * Calls <code>ExecutionUtil.getDynamicProcessProperty(name)</code>
	 *
	 * @param name property name (case sensitive)
	 * @return the property value, or <code>null</code> when not running inside Boomi or in case of errors
	 */
	public static String getDynamicProcessProperty(String name) {
		if (!isBoomi()) {
			return null;
		}
		try {
			Object value = getDynamicProcessPropertyMethod.invoke(null, name);
			return value == null ? null : value.toString();
		} catch (IllegalAccessException | InvocationTargetException e) {
			LOGGER.warn("Unable to read Dynamic Process Property '{}'", name, unwrap(e));
			return null;
		}
	}

	/**
	 * Calls <code>ExecutionUtil.setDynamicProcessProperty(name, value, false)</code>: the value is never persisted
	 * across executions.
	 *
	 * @param name property name (case sensitive)
	 * @param value property value
	 * @return <code>true</code> if the property has been set, <code>false</code> when not running inside Boomi or in
	 *         case of errors
	 */
	public static boolean setDynamicProcessProperty(String name, String value) {
		if (!isBoomi()) {
			return false;
		}
		try {
			setDynamicProcessPropertyMethod.invoke(null, name, value, Boolean.FALSE);
			return true;
		} catch (IllegalAccessException | InvocationTargetException e) {
			LOGGER.warn("Unable to set Dynamic Process Property '{}'", name, unwrap(e));
			return false;
		}
	}

	private static synchronized void init() {
		if (!initialized) {
			lookup(EXECUTION_UTIL_CLASS);
		}
	}

	/**
	 * Looks up the Boomi methods on the given class. Package visible so that tests can provide a fake Boomi class.
	 *
	 * @param className fully qualified name of the class exposing the static property methods
	 */
	static synchronized void lookup(String className) {
		initialized = true;
		getDynamicProcessPropertyMethod = null;
		setDynamicProcessPropertyMethod = null;

		Class<?> executionUtil = findClass(className);
		if (executionUtil == null) {
			LOGGER.debug("{} not found: not running inside Boomi", className);
			return;
		}

		for (Method method : executionUtil.getMethods()) {
			if (!Modifier.isStatic(method.getModifiers())) {
				continue;
			}
			Class<?>[] params = method.getParameterTypes();
			if ("getDynamicProcessProperty".equals(method.getName()) && params.length == 1
					&& params[0].isAssignableFrom(String.class)) {
				getDynamicProcessPropertyMethod = method;
			} else if ("setDynamicProcessProperty".equals(method.getName()) && params.length == 3
					&& params[0].isAssignableFrom(String.class) && params[1].isAssignableFrom(String.class)
					&& (params[2] == boolean.class || params[2] == Boolean.class)) {
				setDynamicProcessPropertyMethod = method;
			}
		}

		if (getDynamicProcessPropertyMethod == null || setDynamicProcessPropertyMethod == null) {
			LOGGER.warn("{} found but its Dynamic Process Property methods are missing: Boomi support disabled",
					className);
			getDynamicProcessPropertyMethod = null;
			setDynamicProcessPropertyMethod = null;
		} else {
			LOGGER.info("Boomi runtime detected: Dynamic Process Properties enabled");
		}
	}

	private static Class<?> findClass(String className) {
		ClassLoader[] loaders = { Thread.currentThread().getContextClassLoader(),
				BoomiExecutionUtil.class.getClassLoader() };
		for (ClassLoader loader : loaders) {
			if (loader == null) {
				continue;
			}
			try {
				return Class.forName(className, true, loader);
			} catch (ClassNotFoundException e) {
				// try the next class loader
			} catch (LinkageError e) {
				LOGGER.debug("Unable to load {}", className, e);
			}
		}
		return null;
	}

	private static Throwable unwrap(Exception e) {
		return e instanceof InvocationTargetException ? ((InvocationTargetException) e).getCause() : e;
	}
}

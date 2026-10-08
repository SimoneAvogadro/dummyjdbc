package me.avogadro.mockjdbc.boomi;

import java.util.HashMap;
import java.util.Map;

/**
 * Stands in for com.boomi.execution.ExecutionUtil in tests: same static method signatures.
 */
public final class FakeExecutionUtil {

	static final Map<String, String> PROPERTIES = new HashMap<String, String>();
	static final Map<String, Boolean> PERSIST_FLAGS = new HashMap<String, Boolean>();
	static String executionId;

	private FakeExecutionUtil() {
	}

	public static String getDynamicProcessProperty(String key) {
		return PROPERTIES.get(key);
	}

	public static void setDynamicProcessProperty(String key, String value, Boolean persist) {
		PROPERTIES.put(key, value);
		PERSIST_FLAGS.put(key, persist);
	}

	public static String getRuntimeExecutionProperty(String key) {
		return "EXECUTION_ID".equals(key) ? executionId : null;
	}

	static void clear() {
		PROPERTIES.clear();
		PERSIST_FLAGS.clear();
		executionId = null;
	}
}

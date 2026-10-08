package me.avogadro.mockjdbc.boomi;

/**
 * Stands in for com.boomi.execution.ExecutionManager in tests: getCurrent() returns the current execution task.
 */
public final class FakeExecutionManager {

	/** returned by getCurrent(); null = no current execution */
	static Object current;

	private FakeExecutionManager() {
	}

	public static Object getCurrent() {
		return current;
	}

	/** like com.boomi.execution.ExecutionTask */
	public static final class Task {
		private final String topLevelExecutionId;
		private final String executionId;

		Task(String topLevelExecutionId, String executionId) {
			this.topLevelExecutionId = topLevelExecutionId;
			this.executionId = executionId;
		}

		public String getTopLevelExecutionId() {
			return topLevelExecutionId;
		}

		public String getExecutionId() {
			return executionId;
		}
	}

	/** a task without getTopLevelExecutionId() */
	public static final class OldTask {
		public String getExecutionId() {
			return "old";
		}
	}
}

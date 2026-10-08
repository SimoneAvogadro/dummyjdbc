package me.avogadro.mockjdbc.statement.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.mockjdbc.MockJdbcDriver;

/**
 * The same resource used several times: users#1, users#2 for queries and users_PARAMS#1, users_PARAMS#2 for writes.
 */
public final class OccurrencesTest {

	private Connection connection;

	@Before
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();
		connection = DriverManager.getConnection("any");
	}

	private String name(String sql) throws SQLException {
		ResultSet resultSet = connection.createStatement().executeQuery(sql);
		return resultSet.next() ? resultSet.getString("name") : null;
	}

	@Test
	public void testSameQueryGivesTheNumberedResults() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("users#1", "name\nfirst");
		MockJdbcDriver.addInMemoryTableResource("users#2", "name\nsecond");
		MockJdbcDriver.addInMemoryTableResource("users", "name\ndefault");

		Assert.assertEquals("first", name("SELECT * FROM users"));
		Assert.assertEquals("second", name("SELECT * FROM users"));
		// no users#3: back to users
		Assert.assertEquals("default", name("SELECT * FROM users"));
	}

	@Test
	public void testOnlySomeOccurrencesDefined() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("users#2", "name\nsecond");
		MockJdbcDriver.addInMemoryTableResource("users", "name\ndefault");

		Assert.assertEquals("default", name("SELECT * FROM users"));
		Assert.assertEquals("second", name("SELECT * FROM users"));
	}

	@Test
	public void testCounterIsCaseInsensitiveAndPerName() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("users#2", "name\nsecond users");
		MockJdbcDriver.addInMemoryTableResource("orders#1", "name\nfirst orders");

		name("SELECT * FROM Users");
		Assert.assertEquals("first orders", name("SELECT * FROM orders"));
		Assert.assertEquals("second users", name("SELECT * FROM USERS"));
	}

	@Test
	public void testLookupOrderWithParameters() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("users?Smith#2", "name\nSmith second");
		MockJdbcDriver.addInMemoryTableResource("users?Smith", "name\nSmith");
		MockJdbcDriver.addInMemoryTableResource("users#3", "name\nthird");
		MockJdbcDriver.addInMemoryTableResource("users", "name\ndefault");

		Assert.assertEquals("Smith", byName("Smith"));			// users?Smith#1 missing -> users?Smith
		Assert.assertEquals("Smith second", byName("Smith"));	// users?Smith#2
		Assert.assertEquals("third", byName("Rossi"));			// users?Rossi... missing -> users#3
		Assert.assertEquals("default", byName("Rossi"));		// users#4 missing -> users
	}

	private String byName(String value) throws SQLException {
		PreparedStatement statement = connection.prepareStatement("SELECT * FROM users WHERE surname = ?");
		statement.setString(1, value);
		ResultSet resultSet = statement.executeQuery();
		return resultSet.next() ? resultSet.getString("name") : null;
	}

	@Test
	public void testTestCaseOccurrences() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("T_014a#2", "name\nsecond");
		MockJdbcDriver.addInMemoryTableResource("T_014a", "name\ndefault");

		Assert.assertEquals("default", name("-- TESTCASE: T_014a\nSELECT * FROM x"));
		Assert.assertEquals("second", name("-- TESTCASE: t_014A\nSELECT * FROM x"));
	}

	@Test
	public void testStepsAreNotCounted() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource(0, "name\nstep zero");
		MockJdbcDriver.addInMemoryTableResource("users#1", "name\nfirst");

		Assert.assertEquals("step zero", name("SELECT * FROM users"));
		// step 1 is not defined: first counted use of users
		Assert.assertEquals("first", name("SELECT * FROM users"));
	}

	@Test
	public void testWritesKeepEveryOccurrenceAndTheLatest() throws SQLException {
		for (String value : new String[] { "a", "b" }) {
			PreparedStatement insert = connection.prepareStatement("INSERT INTO users (name) VALUES (?)");
			insert.setString(1, value);
			insert.executeUpdate();
		}

		Assert.assertEquals("a", MockJdbcDriver.getInMemoryTableResource("users_PARAMS#1"));
		Assert.assertEquals("b", MockJdbcDriver.getInMemoryTableResource("users_PARAMS#2"));
		Assert.assertEquals("b", MockJdbcDriver.getInMemoryTableResource("users_PARAMS"));
	}

	@Test
	public void testReadsAndWritesShareTheCounter() throws SQLException {
		name("SELECT * FROM users");
		PreparedStatement insert = connection.prepareStatement("INSERT INTO users (name) VALUES (?)");
		insert.setString(1, "a");
		insert.executeUpdate();

		Assert.assertNull(MockJdbcDriver.getInMemoryTableResource("users_PARAMS#1"));
		Assert.assertEquals("a", MockJdbcDriver.getInMemoryTableResource("users_PARAMS#2"));
	}

	@Test
	public void testStoredProcedureExecuteCountsOnce() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("my_proc#1", "result\nfirst");
		MockJdbcDriver.addInMemoryTableResource("my_proc#2", "result\nsecond");

		for (String value : new String[] { "a", "b" }) {
			PreparedStatement call = connection.prepareStatement("EXEC my_proc ?");
			call.setString(1, value);
			call.execute();
			ResultSet resultSet = call.getResultSet();
			Assert.assertTrue(resultSet.next());
		}

		Assert.assertEquals("a", MockJdbcDriver.getInMemoryTableResource("my_proc_PARAMS#1"));
		Assert.assertEquals("b", MockJdbcDriver.getInMemoryTableResource("my_proc_PARAMS#2"));
		Assert.assertNull(MockJdbcDriver.getInMemoryTableResource("my_proc_PARAMS#3"));
	}

	@Test
	public void testStoredProcedureResultsByOccurrence() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("my_proc#1", "result\nfirst");
		MockJdbcDriver.addInMemoryTableResource("my_proc#2", "result\nsecond");

		StringBuilder results = new StringBuilder();
		for (int i = 0; i < 2; i++) {
			PreparedStatement call = connection.prepareStatement("EXEC my_proc ?");
			call.setInt(1, i);
			call.execute();
			ResultSet resultSet = call.getResultSet();
			Assert.assertTrue(resultSet.next());
			results.append(resultSet.getString("result")).append(' ');
		}
		Assert.assertEquals("first second ", results.toString());
	}

	@Test
	public void testBatchRowsAreNumbered() throws SQLException {
		PreparedStatement insert = connection.prepareStatement("INSERT INTO users (name) VALUES (?)");
		insert.setString(1, "a");
		insert.addBatch();
		insert.setString(1, "b");
		insert.addBatch();
		insert.executeBatch();

		Assert.assertEquals("a", MockJdbcDriver.getInMemoryTableResource("users_PARAMS#1"));
		Assert.assertEquals("b", MockJdbcDriver.getInMemoryTableResource("users_PARAMS#2"));
		Assert.assertEquals("b", MockJdbcDriver.getInMemoryTableResource("users_PARAMS"));
	}

	@Test
	public void testResetCountersKeepsTheResources() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("users#1", "name\nfirst");
		MockJdbcDriver.addInMemoryTableResource("users#2", "name\nsecond");

		Assert.assertEquals("first", name("SELECT * FROM users"));
		MockJdbcDriver.resetCounters();
		Assert.assertEquals("first", name("SELECT * FROM users"));
		Assert.assertEquals("second", name("SELECT * FROM users"));
	}

	@Test
	public void testResetAlsoRestartsTheCounters() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("users#2", "name\nsecond");
		name("SELECT * FROM users");
		MockJdbcDriver.reset();
		MockJdbcDriver.addInMemoryTableResource("users#1", "name\nfirst again");

		Assert.assertEquals("first again", name("SELECT * FROM users"));
	}

	@Test
	public void testCountersArePerThread() throws Exception {
		MockJdbcDriver.addInMemoryTableResource("users#1", "name\nfirst");
		MockJdbcDriver.addInMemoryTableResource("users#2", "name\nsecond");
		Assert.assertEquals("first", name("SELECT * FROM users"));

		final String[] other = new String[1];
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					other[0] = name("SELECT * FROM users");
				} catch (SQLException e) {
					other[0] = e.toString();
				}
			}
		});
		thread.start();
		thread.join();

		Assert.assertEquals("first", other[0]);
		Assert.assertEquals("second", name("SELECT * FROM users"));
	}

	@Test
	public void testMetadataDoesNotCount() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("users#1", "name\nfirst");
		MockJdbcDriver.addInMemoryTableResource("users", "name");

		connection.getMetaData().getColumns(null, null, "users", null);
		Assert.assertEquals("first", name("SELECT * FROM users"));
	}
}

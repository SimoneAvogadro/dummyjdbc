package me.avogadro.mockjdbc.boomi;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.mockjdbc.MockJdbcDriver;

public final class BoomiExecutionUtilTest {

	@Before
	public void setup() throws ClassNotFoundException {
		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();
		FakeExecutionUtil.clear();
	}

	@After
	public void restoreRealDetection() {
		BoomiExecutionUtil.lookup(BoomiExecutionUtil.EXECUTION_UTIL_CLASS);
	}

	@Test
	public void testOutsideBoomiEverythingIsDisabled() {
		BoomiExecutionUtil.lookup(BoomiExecutionUtil.EXECUTION_UTIL_CLASS);

		Assert.assertFalse(BoomiExecutionUtil.isBoomi());
		Assert.assertNull(BoomiExecutionUtil.getDynamicProcessProperty("mockjdbc_users"));
		Assert.assertFalse(BoomiExecutionUtil.setDynamicProcessProperty("mockjdbc_users", "x"));
	}

	@Test
	public void testIncompleteClassIsNotBoomi() {
		BoomiExecutionUtil.lookup(String.class.getName());

		Assert.assertFalse(BoomiExecutionUtil.isBoomi());
	}

	@Test
	public void testPropertyName() {
		Assert.assertEquals("mockjdbc_users_PARAMS", BoomiExecutionUtil.propertyName(" users_PARAMS "));
	}

	@Test
	public void testTableFromDynamicProcessProperty() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());
		Assert.assertTrue(BoomiExecutionUtil.isBoomi());

		FakeExecutionUtil.PROPERTIES.put("mockjdbc_users", "name, age\nJohn, 20");

		Connection connection = DriverManager.getConnection("any");
		ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM USERS");
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("John", resultSet.getString("name"));
		Assert.assertEquals(20, resultSet.getInt("age"));
		Assert.assertFalse(resultSet.next());
	}

	@Test
	public void testTestCaseFoundWithExactCase() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());

		FakeExecutionUtil.PROPERTIES.put("mockjdbc_T_014a", "name\nexact");

		Assert.assertEquals("name\nexact", queryTestCase("T_014a"));
	}

	@Test
	public void testTestCaseFoundInLowerCase() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());

		FakeExecutionUtil.PROPERTIES.put("mockjdbc_t_014a", "name\nlower");

		Assert.assertEquals("name\nlower", queryTestCase("T_014a"));
	}

	@Test
	public void testExactCaseWinsOverLowerCase() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());

		FakeExecutionUtil.PROPERTIES.put("mockjdbc_T_014a", "name\nexact");
		FakeExecutionUtil.PROPERTIES.put("mockjdbc_t_014a", "name\nlower");

		Assert.assertEquals("name\nexact", queryTestCase("T_014a"));
	}

	@Test
	public void testBlankExactCaseFallsBackToLowerCase() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());

		FakeExecutionUtil.PROPERTIES.put("mockjdbc_T_014a", " ");
		FakeExecutionUtil.PROPERTIES.put("mockjdbc_t_014a", "name\nlower");

		Assert.assertEquals("name\nlower", queryTestCase("T_014a"));
	}

	@Test
	public void testCapturedParamsKeepTableCaseAsInSql() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());

		PreparedStatement statement = DriverManager.getConnection("any")
				.prepareStatement("-- TESTCASE: T_014a\nUPDATE Users SET name=?");
		statement.setString(1, "bob");
		statement.executeUpdate();

		Assert.assertEquals("bob", FakeExecutionUtil.PROPERTIES.get("mockjdbc_T_014a_PARAMS"));
		Assert.assertEquals("bob", FakeExecutionUtil.PROPERTIES.get("mockjdbc_t_014a_params"));
	}

	/** runs a query selected by an explicit test case and returns the value of its NAME column, as "name\n<value>" */
	private static String queryTestCase(String testCase) throws Exception {
		ResultSet resultSet = DriverManager.getConnection("any").createStatement()
				.executeQuery("-- TESTCASE: " + testCase + "\nSELECT * FROM something");
		Assert.assertTrue(resultSet.next());
		return "name\n" + resultSet.getString("name");
	}

	@Test
	public void testInMemoryResourceWinsOverDynamicProcessProperty() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());

		FakeExecutionUtil.PROPERTIES.put("mockjdbc_users", "name\nfrom Boomi");
		MockJdbcDriver.addInMemoryTableResource("users", "name\nfrom memory");

		ResultSet resultSet = DriverManager.getConnection("any").createStatement().executeQuery("SELECT * FROM users");
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("from memory", resultSet.getString("name"));
	}

	@Test
	public void testEmptyPropertyIsIgnored() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());

		FakeExecutionUtil.PROPERTIES.put("mockjdbc_users", "  ");

		Assert.assertNull(MockJdbcDriver.getInMemoryTableResource("users"));
	}

	@Test
	public void testStepFromDynamicProcessProperty() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());

		FakeExecutionUtil.PROPERTIES.put("mockjdbc_##step0", "id\n1");
		FakeExecutionUtil.PROPERTIES.put("mockjdbc_##step1", "id\n2");

		Connection connection = DriverManager.getConnection("any");
		ResultSet first = connection.prepareStatement("SELECT * FROM anything").executeQuery();
		ResultSet second = connection.prepareStatement("SELECT * FROM anything").executeQuery();
		Assert.assertTrue(first.next());
		Assert.assertEquals(1, first.getInt("id"));
		Assert.assertTrue(second.next());
		Assert.assertEquals(2, second.getInt("id"));
	}

	@Test
	public void testCapturedParamsAreSetAsNotPersistedProperty() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());

		PreparedStatement statement = DriverManager.getConnection("any")
				.prepareStatement("INSERT INTO users (name,age) VALUES (?,?)");
		statement.setString(1, "hello");
		statement.setInt(2, 30);
		statement.execute();

		Assert.assertEquals("hello,30", FakeExecutionUtil.PROPERTIES.get("mockjdbc_users_PARAMS"));
		Assert.assertEquals(Boolean.FALSE, FakeExecutionUtil.PERSIST_FLAGS.get("mockjdbc_users_PARAMS"));
		// lower case variant, suggested when working with Boomi
		Assert.assertEquals("hello,30", FakeExecutionUtil.PROPERTIES.get("mockjdbc_users_params"));
		Assert.assertEquals(Boolean.FALSE, FakeExecutionUtil.PERSIST_FLAGS.get("mockjdbc_users_params"));
		Assert.assertEquals(2, FakeExecutionUtil.PROPERTIES.size());
		// still available through the driver API as before
		Assert.assertEquals("hello,30", MockJdbcDriver.getInMemoryTableResource("users_PARAMS"));
	}

	@Test
	public void testCapturedParamsOutsideBoomiSetNoProperty() throws Exception {
		BoomiExecutionUtil.lookup(BoomiExecutionUtil.EXECUTION_UTIL_CLASS);

		PreparedStatement statement = DriverManager.getConnection("any")
				.prepareStatement("INSERT INTO users (name,age) VALUES (?,?)");
		statement.setString(1, "hello");
		statement.setInt(2, 30);
		statement.execute();

		Assert.assertTrue(FakeExecutionUtil.PROPERTIES.isEmpty());
		Assert.assertEquals("hello,30", MockJdbcDriver.getInMemoryTableResource("users_PARAMS"));
	}

	@Test
	public void testSelectSetsNoProperty() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());
		FakeExecutionUtil.PROPERTIES.put("mockjdbc_t_014a", "name\nJohn");

		PreparedStatement statement = DriverManager.getConnection("any")
				.prepareStatement("-- TESTCASE: T_014a\nSELECT * FROM users WHERE id = ?");
		statement.setInt(1, 42);
		statement.executeQuery();

		Assert.assertEquals(1, FakeExecutionUtil.PROPERTIES.size());
	}

	@Test
	public void testStoredProcedureParamsAreSetAsProperties() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());

		PreparedStatement statement = DriverManager.getConnection("any").prepareStatement("EXEC My_Proc ?");
		statement.setString(1, "a");
		statement.execute();

		Assert.assertEquals("a", FakeExecutionUtil.PROPERTIES.get("mockjdbc_My_Proc_PARAMS"));
		Assert.assertEquals("a", FakeExecutionUtil.PROPERTIES.get("mockjdbc_my_proc_params"));
	}

	@Test
	public void testGetColumnsOfTableDefinedAsProperty() throws Exception {
		BoomiExecutionUtil.lookup(FakeExecutionUtil.class.getName());
		FakeExecutionUtil.PROPERTIES.put("mockjdbc_payments", "id|integer, amount|double");

		ResultSet columns = DriverManager.getConnection("any").getMetaData().getColumns(null, null, "payments", null);

		Assert.assertTrue(columns.next());
		Assert.assertEquals("id", columns.getString("COLUMN_NAME"));
		Assert.assertEquals(java.sql.Types.INTEGER, columns.getInt("DATA_TYPE"));
		Assert.assertTrue(columns.next());
		Assert.assertEquals("amount", columns.getString("COLUMN_NAME"));
		Assert.assertFalse(columns.next());
	}
}

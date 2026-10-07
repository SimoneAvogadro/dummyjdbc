package me.avogadro.dummyjdbc.statement.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.dummyjdbc.DummyJdbcDriver;

/**
 * Statement features used by the Boomi Database V2 connector: generated keys, batches ("Commit By Rows"), max rows.
 */
public final class StatementFeaturesTest {

	private Connection connection;

	@Before
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName(DummyJdbcDriver.class.getCanonicalName());
		DummyJdbcDriver.reset();
		DummyJdbcDriver.addInMemoryTableResource("customers", "id|integer, name\n1, John\n2, Mary\n3, Paul");
		connection = DriverManager.getConnection("any");
	}

	@Test
	public void testGeneratedKeysAreAnEmptyResultSet() throws SQLException {
		PreparedStatement insert = connection.prepareStatement("INSERT INTO customers (name) VALUES (?)",
				Statement.RETURN_GENERATED_KEYS);
		insert.setString(1, "Anna");
		insert.executeUpdate();

		ResultSet keys = insert.getGeneratedKeys();
		Assert.assertNotNull(keys);
		Assert.assertFalse(keys.next());
		Assert.assertEquals(1, keys.getMetaData().getColumnCount());

		Assert.assertNotNull(connection.createStatement().getGeneratedKeys());
	}

	@Test
	public void testBatchRunsEveryParameterSet() throws SQLException {
		PreparedStatement insert = connection.prepareStatement("INSERT INTO customers (id, name) VALUES (?, ?)");
		insert.setInt(1, 4);
		insert.setString(2, "Anna");
		insert.addBatch();
		insert.setInt(1, 5);
		insert.setString(2, "Luca");
		insert.addBatch();

		int[] results = insert.executeBatch();

		Assert.assertEquals("[1, 1]", Arrays.toString(results));
		// as for single executions, the latest parameters are kept
		Assert.assertEquals("5,Luca", DummyJdbcDriver.getInMemoryTableResource("customers_PARAMS"));
		// the batch is emptied
		Assert.assertEquals(0, insert.executeBatch().length);
	}

	@Test
	public void testBatchKeepsACopyOfTheParameters() throws SQLException {
		PreparedStatement update = connection.prepareStatement("UPDATE customers SET name = ? WHERE id = ?");
		update.setString(1, "first");
		update.setInt(2, 1);
		update.addBatch();
		update.setString(1, "second");	// the parameter 2 stays set, as in JDBC
		update.addBatch();

		Assert.assertEquals("[1, 1]", Arrays.toString(update.executeBatch()));
		Assert.assertEquals("second,1", DummyJdbcDriver.getInMemoryTableResource("customers_PARAMS"));
	}

	@Test
	public void testClearBatch() throws SQLException {
		PreparedStatement insert = connection.prepareStatement("INSERT INTO customers (id) VALUES (?)");
		insert.setInt(1, 4);
		insert.addBatch();
		insert.clearBatch();

		Assert.assertEquals(0, insert.executeBatch().length);
		Assert.assertNull(DummyJdbcDriver.getInMemoryTableResource("customers_PARAMS"));
	}

	@Test
	public void testBatchOfUnknownStatementsReturnsZeros() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("MERGE something ?");
		statement.setInt(1, 1);
		statement.addBatch();

		Assert.assertEquals("[0]", Arrays.toString(statement.executeBatch()));
	}

	@Test
	public void testStatementBatch() throws SQLException {
		Statement statement = connection.createStatement();
		statement.addBatch("UPDATE customers SET name = 'x'");
		statement.addBatch("DELETE FROM customers");

		Assert.assertEquals(2, statement.executeBatch().length);
	}

	@Test
	public void testMaxRowsOnPreparedStatement() throws SQLException {
		PreparedStatement select = connection.prepareStatement("SELECT * FROM customers");
		select.setMaxRows(2);
		Assert.assertEquals(2, select.getMaxRows());

		ResultSet resultSet = select.executeQuery();

		Assert.assertTrue(resultSet.next());
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals(2, resultSet.getRow());
		Assert.assertFalse(resultSet.next());
		Assert.assertFalse(resultSet.next());
		Assert.assertEquals(0, resultSet.getRow());
	}

	@Test
	public void testMaxRowsOnStatement() throws SQLException {
		Statement statement = connection.createStatement();
		statement.setMaxRows(1);

		ResultSet resultSet = statement.executeQuery("SELECT * FROM customers");

		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("John", resultSet.getString("name"));
		Assert.assertFalse(resultSet.next());
	}

	@Test
	public void testMaxRowsZeroMeansNoLimit() throws SQLException {
		Statement statement = connection.createStatement();
		statement.setMaxRows(0);

		ResultSet resultSet = statement.executeQuery("SELECT * FROM customers");
		int rows = 0;
		while (resultSet.next()) {
			rows++;
		}
		Assert.assertEquals(3, rows);
	}

	@Test(expected = SQLException.class)
	public void testNegativeMaxRows() throws SQLException {
		connection.createStatement().setMaxRows(-1);
	}
}

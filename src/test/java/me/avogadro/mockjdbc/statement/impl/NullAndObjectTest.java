package me.avogadro.mockjdbc.statement.impl;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.mockjdbc.MockJdbcDriver;

/**
 * NULL convention (an empty value of a column with a declared type other than varchar is SQL NULL) and getObject.
 */
public final class NullAndObjectTest {

	private ResultSet resultSet;

	@Before
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();
		MockJdbcDriver.setDateFormat("yyyy-MM-dd");
		MockJdbcDriver.setTimeFormat("HH:mm:ss");
		MockJdbcDriver.setTimestampFormat("yyyy-MM-dd HH:mm:ss");
		MockJdbcDriver.addInMemoryTableResource("t",
				"id|integer, big|bigint, price|double, total|decimal, ok|boolean, day|date, at|time, ts|timestamp, name, label|varchar\n"
						+ "1, 9000000000, 1.5, 12.50, true, 2012-05-17, 14:30:15, 2012-05-17 14:30:15, John, x\n"
						+ ", , , , , , , , , \n"
						+ "2, 3, 4, 5, TRUE, 2020-01-02, 01:02:03, 2020-01-02 01:02:03, NULL,\"\"\n");
		resultSet = DriverManager.getConnection("any").createStatement().executeQuery("SELECT * FROM t");
	}

	@After
	public void restoreFormats() {
		MockJdbcDriver.setDateFormat("dd-MMM-yy");
		MockJdbcDriver.setTimeFormat("HH:mm");
		MockJdbcDriver.setTimestampFormat("yyyyMMdd HHmmss.SSS");
	}

	@Test
	public void testGetObjectUsesTheDeclaredType() throws SQLException {
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals(Integer.valueOf(1), resultSet.getObject("id"));
		Assert.assertEquals(Long.valueOf(9000000000L), resultSet.getObject("big"));
		Assert.assertEquals(Double.valueOf(1.5), resultSet.getObject("price"));
		Assert.assertEquals(new BigDecimal("12.50"), resultSet.getObject("total"));
		Assert.assertEquals(Boolean.TRUE, resultSet.getObject("ok"));
		Assert.assertEquals(Date.valueOf("2012-05-17"), resultSet.getObject("day"));
		Assert.assertEquals(Time.valueOf("14:30:15"), resultSet.getObject("at"));
		Assert.assertEquals(Timestamp.valueOf("2012-05-17 14:30:15"), resultSet.getObject("ts"));
		Assert.assertEquals("John", resultSet.getObject("name"));
		Assert.assertEquals("x", resultSet.getObject("label"));
		// by index and by full header text
		Assert.assertEquals(Integer.valueOf(1), resultSet.getObject(1));
		Assert.assertEquals(Integer.valueOf(1), resultSet.getObject("id|integer"));
		Assert.assertFalse(resultSet.wasNull());
	}

	@Test
	public void testEmptyTypedValuesAreNull() throws SQLException {
		Assert.assertTrue(resultSet.next());
		Assert.assertTrue(resultSet.next());

		for (String column : new String[] { "id", "big", "price", "total", "ok", "day", "at", "ts" }) {
			Assert.assertNull(column, resultSet.getObject(column));
			Assert.assertTrue(column, resultSet.wasNull());
			Assert.assertNull(column, resultSet.getString(column));
			Assert.assertTrue(column, resultSet.wasNull());
		}
		Assert.assertEquals(0, resultSet.getInt("id"));
		Assert.assertTrue(resultSet.wasNull());
		Assert.assertEquals(0L, resultSet.getLong("big"));
		Assert.assertEquals(0d, resultSet.getDouble("price"), 0d);
		Assert.assertNull(resultSet.getBigDecimal("total"));
		Assert.assertFalse(resultSet.getBoolean("ok"));
		Assert.assertTrue(resultSet.wasNull());
		Assert.assertNull(resultSet.getDate("day"));
		Assert.assertNull(resultSet.getTime("at"));
		Assert.assertNull(resultSet.getTimestamp("ts"));
		Assert.assertNull(resultSet.getObject(1));
		Assert.assertTrue(resultSet.wasNull());
	}

	@Test
	public void testEmptyVarcharValuesAreEmptyStrings() throws SQLException {
		Assert.assertTrue(resultSet.next());
		Assert.assertTrue(resultSet.next());

		Assert.assertEquals("", resultSet.getString("name"));
		Assert.assertFalse(resultSet.wasNull());
		Assert.assertEquals("", resultSet.getObject("label"));
		Assert.assertFalse(resultSet.wasNull());
		// untyped column: unchanged behaviour of getBigDecimal on empty text
		Assert.assertEquals(BigDecimal.ZERO, resultSet.getBigDecimal("name"));
	}

	@Test
	public void testWasNullFollowsTheLastRead() throws SQLException {
		Assert.assertTrue(resultSet.next());
		Assert.assertTrue(resultSet.next());
		resultSet.getInt("id");
		Assert.assertTrue(resultSet.wasNull());
		resultSet.getString("name");
		Assert.assertFalse(resultSet.wasNull());
	}

	@Test
	public void testTextNullAndQuotedEmpty() throws SQLException {
		for (int i = 0; i < 3; i++) {
			Assert.assertTrue(resultSet.next());
		}
		Assert.assertEquals("NULL", resultSet.getObject("name"));
		Assert.assertEquals("", resultSet.getObject("label"));
		Assert.assertEquals(Boolean.TRUE, resultSet.getObject("ok"));
	}

	@Test
	public void testGetObjectWithType() throws SQLException {
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals(Integer.valueOf(1), resultSet.getObject("id", Integer.class));
		Assert.assertEquals("1", resultSet.getObject(1, String.class));
	}

	@Test(expected = SQLException.class)
	public void testGetObjectWithIncompatibleType() throws SQLException {
		Assert.assertTrue(resultSet.next());
		resultSet.getObject("id", Date.class);
	}
}

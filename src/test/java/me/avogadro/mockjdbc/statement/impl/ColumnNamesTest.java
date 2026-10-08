package me.avogadro.mockjdbc.statement.impl;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.mockjdbc.MockJdbcDriver;

/**
 * Column names keep the case of the CSV header, like a real database: tools such as the Boomi Database V2 connector
 * use getColumnLabel() as key of the produced documents.
 */
public final class ColumnNamesTest {

	@Before
	public void setup() throws ClassNotFoundException {
		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();
	}

	private static ResultSet query(String csv) throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("t", csv);
		return DriverManager.getConnection("any").createStatement().executeQuery("SELECT * FROM t");
	}

	@Test
	public void testNamesKeepTheHeaderCase() throws SQLException {
		ResultSetMetaData metaData = query("id, Name, CITY, mixedCase|integer\n1, John, Milano, 3").getMetaData();

		Assert.assertEquals(4, metaData.getColumnCount());
		Assert.assertEquals("id", metaData.getColumnName(1));
		Assert.assertEquals("Name", metaData.getColumnName(2));
		Assert.assertEquals("CITY", metaData.getColumnName(3));
		Assert.assertEquals("mixedCase", metaData.getColumnName(4));
		Assert.assertEquals("id", metaData.getColumnLabel(1));
		Assert.assertEquals("mixedCase", metaData.getColumnLabel(4));
		Assert.assertEquals(Types.INTEGER, metaData.getColumnType(4));
		Assert.assertEquals(Types.VARCHAR, metaData.getColumnType(1));
	}

	@Test
	public void testSpacesAroundTypeSeparator() throws SQLException {
		ResultSetMetaData metaData = query("  price | double , qty|INTEGER\n1.5, 2").getMetaData();

		Assert.assertEquals("price", metaData.getColumnName(1));
		Assert.assertEquals(Types.DOUBLE, metaData.getColumnType(1));
		Assert.assertEquals("qty", metaData.getColumnName(2));
		Assert.assertEquals(Types.INTEGER, metaData.getColumnType(2));
	}

	@Test
	public void testLookupByLabelStaysCaseInsensitive() throws SQLException {
		ResultSet resultSet = query("id, name|varchar\n1, John");

		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("John", resultSet.getString("name"));
		Assert.assertEquals("John", resultSet.getString("NAME"));
		Assert.assertEquals("John", resultSet.getString("Name"));
		Assert.assertEquals(1, resultSet.getInt("ID"));
	}

	/** what the Boomi Database V2 connector does: label from the metadata, then value by label */
	@Test
	public void testReadingRowsThroughTheMetadataLabels() throws SQLException {
		ResultSet resultSet = query("id, name, city\n1, John, Milano\n2, \"Rossi, Mario\", Roma");
		ResultSetMetaData metaData = resultSet.getMetaData();

		StringBuilder documents = new StringBuilder();
		while (resultSet.next()) {
			documents.append('{');
			for (int i = 1; i <= metaData.getColumnCount(); i++) {
				String label = metaData.getColumnLabel(i);
				documents.append(i > 1 ? "," : "").append('"').append(label).append("\":\"")
						.append(resultSet.getString(label)).append('"');
			}
			documents.append('}');
		}

		Assert.assertEquals("{\"id\":\"1\",\"name\":\"John\",\"city\":\"Milano\"}"
				+ "{\"id\":\"2\",\"name\":\"Rossi, Mario\",\"city\":\"Roma\"}", documents.toString());
	}
}

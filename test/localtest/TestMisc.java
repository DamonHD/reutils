package localtest;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.hd.d.edh.DataUtils;
import org.hd.d.edh.MainProperties;

/**Miscellaneous tests.
 *
 */
public final class TestMisc extends TestCase
    {
    /**Verify that test harness is sane... */
    public static void testSanity() { }

    /**Test loading and other aspects of the main properties/config.
     */
    public static void testMainProperties()
        {
        assertTrue("Must be able to load the main properties", MainProperties.getTimestamp() > 0);


        }

    /**Test handling of bmreports-style CSV files. */
    public static void testParseBMRCSV()
        throws Exception
        {
        // Tiny synthetic test CSV (with CRLF line ends).
        final String tinyTestCSV = "HDR,type\r\nTEST,1,2,,42\r\nFTR,1\r\n";

        // Parse without checking the header text (ie data type)...
        final List<List<String>> result1 = DataUtils.parseBMRCSV(new StringReader(tinyTestCSV), null);
        assertEquals("Should be exactly one result row", 1, result1.size());
        assertEquals("Row should have correct number of columns/fields", 5, result1.get(0).size());
        assertEquals("Result data should match", "TEST", result1.get(0).get(0));
        assertEquals("Result data should match", "1", result1.get(0).get(1));
        assertEquals("Result data should match", "2", result1.get(0).get(2));
        assertEquals("Result data should match", "", result1.get(0).get(3));
        assertEquals("Result data should match", "42", result1.get(0).get(4));

        // Parse again, but this time checking for a bogus type/heading to reject the input.
        try
            {
            DataUtils.parseBMRCSV(new StringReader(tinyTestCSV), "bogus type");
            fail("Incorrectly accepted bogus name/type");
            }
        catch(final IOException e) { /* Correctly rejected parse attempt. */ }

        // Parse again, but this time checking for the correct type/heading.
        assertNotNull(DataUtils.parseBMRCSV(new StringReader(tinyTestCSV), "type"));
        }

    /**Test the extraction of fields from parsed CSV to named values.
     */
    public static void testExtractNamedFieldsByPosition()
        {
        final List<String> rowData = Arrays.asList(new String[]{"SOMETYPENAME","1","two","verymany","other","stuff",""});
        final String template = "type,ONE,TWO,THREE,,STUFF";
        final Map<String, String> namedFields = DataUtils.extractNamedFieldsByPositionFromRow(template, rowData);

        assertEquals("must extract correct number of fields", 5, namedFields.size());
        assertEquals("must extract correct value for named field", "SOMETYPENAME", namedFields.get("type"));
        assertEquals("must extract correct value for named field", "stuff", namedFields.get("STUFF"));
        }
    }

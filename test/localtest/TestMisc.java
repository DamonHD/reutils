/*
Copyright (c) 2008-2013, Damon Hart-Davis
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the
    distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package localtest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.hd.d.edh.DataUtils;
import org.hd.d.edh.FUELINSTUtils;
import org.hd.d.edh.MainProperties;
import org.hd.d.edh.TrafficLight;
import org.json.JSONArray;
import org.json.JSONObject;

import junit.framework.TestCase;

/**Miscellaneous tests.
 *
 */
public final class TestMisc extends TestCase
    {
    /**Verify that test harness is sane... */
    public static void testSanity() { }

    /**Test loading of the main properties/config. */
    public static void testMainPropertiesLoad()
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

    /**Test serialising/saving of BMRCSV data. */
    public static void testSaveBMRCSV()
        throws Exception
	    {
	    // No-data case.
    	final String nodataOutExpected = "HDR\nFTR,0\n";
    	final byte[] nodataOut = DataUtils.saveBMRCSV(new ArrayList<List<String>>(), false);
        assertTrue("Should generate expected ASCII output", Arrays.equals(nodataOut, nodataOutExpected.getBytes(FUELINSTUtils.FUELINST_CHARSET)));
        assertTrue("Should parse as empty list", DataUtils.parseBMRCSV(new StringReader(nodataOutExpected), null).isEmpty());
        assertTrue("Should parse as empty list", DataUtils.parseBMRCSV(new InputStreamReader(new ByteArrayInputStream(nodataOut)), null).isEmpty());
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

    /**Test ordering of traffic light status values.
     */
    public static void testTrafficLightStatusOrder()
        {
        assertTrue("YELLOW must be better than RED", TrafficLight.YELLOW.betterThan(TrafficLight.RED));
        assertTrue("GREEN must be better than YELLOW", TrafficLight.GREEN.betterThan(TrafficLight.YELLOW));
        assertTrue("GREEN must be better than RED", TrafficLight.GREEN.betterThan(TrafficLight.RED));
        assertFalse("RED must not be better than GREEN", TrafficLight.RED.betterThan(TrafficLight.GREEN));
        assertFalse("RED must not be better than YELLOW", TrafficLight.RED.betterThan(TrafficLight.YELLOW));
        assertFalse("YELLOW must not be better than GREEN", TrafficLight.YELLOW.betterThan(TrafficLight.GREEN));
        }

    /**Test writing of intensity log.
     * @throws IOException
     */
    public static void testAppendToRetailIntensityLog() throws IOException
	    {
    	final Path tempDirectory = Files.createTempDirectory("testAppendToRetailIntensityLog");
//    	System.err.println("tempDir = " + tempDirectory);

    	final long now = System.currentTimeMillis();

    	// Write first row (and create file with header), and validate.
    	final int retailIntensity1 = 123;
    	final File logFile = FUELINSTUtils.appendToRetailIntensityLog(tempDirectory.toFile(), now, retailIntensity1);
    	try(BufferedReader br = new BufferedReader(new FileReader(logFile)))
    	    {
    	    assertEquals(FUELINSTUtils.RETAIL_INTENSITY_LOG_HEADER_LINE_1, br.readLine());
    	    assertEquals(FUELINSTUtils.RETAIL_INTENSITY_LOG_HEADER_LINE_2, br.readLine());
    	    final String intensities = br.readLine();
    	    assertNotNull(intensities);
    	    assertTrue(intensities.startsWith(FUELINSTUtils.RETAIL_INTENSITY_LOG_HEADER_LINE_3_PREFIX));
    	    // Check for presence of NUCLEAR at intensity 0, mid string.
    	    assertTrue(-1 != intensities.indexOf(" NUCLEAR=0 "));
    	    final String datarow1 = br.readLine();
    	    assertNotNull(datarow1);
    	    // TODO: check timestamp.
    	    assertTrue(datarow1.endsWith(" "+retailIntensity1));
    	    assertNull(br.readLine());
    	    }

    	// Write second row and validate.
    	final long later = now + 1;
    	final int retailIntensity2 = 321;
    	final File logFile2 = FUELINSTUtils.appendToRetailIntensityLog(tempDirectory.toFile(), now, retailIntensity2);
    	assertEquals(logFile, logFile2);
    	try(BufferedReader br = new BufferedReader(new FileReader(logFile2)))
		    {
		    assertEquals(FUELINSTUtils.RETAIL_INTENSITY_LOG_HEADER_LINE_1, br.readLine());
		    assertEquals(FUELINSTUtils.RETAIL_INTENSITY_LOG_HEADER_LINE_2, br.readLine());
    	    final String intensities = br.readLine();
    	    assertNotNull(intensities);
    	    assertTrue(intensities.startsWith(FUELINSTUtils.RETAIL_INTENSITY_LOG_HEADER_LINE_3_PREFIX));
            // Omit more detailed checking of intensities line this time.
    	    final String datarow1 = br.readLine();
    	    assertNotNull(datarow1);
    	    final String datarow2 = br.readLine();
    	    assertNotNull(datarow2);
    	    // TODO: check timestamp.
    	    assertTrue(datarow2.endsWith(" "+retailIntensity2));
    	    assertNull(br.readLine());
		    }

    	// Tidy up.
    	if(null != logFile) { logFile.delete(); }
    	Files.delete(tempDirectory);
	    }

    /**Significant small JSON streaming FUELINST sample. */
    public static final String FUELINST_JSON_sample_20240212 = "[{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"BIOMASS\",\"generation\":2249},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"CCGT\",\"generation\":15842},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"COAL\",\"generation\":478},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"INTELEC\",\"generation\":19},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"INTEW\",\"generation\":-83},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"INTFR\",\"generation\":267},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"INTIFA2\",\"generation\":-4},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"INTIRL\",\"generation\":-26},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"INTNED\",\"generation\":-170},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"INTNEM\",\"generation\":-353},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"INTNSL\",\"generation\":612},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"INTVKL\",\"generation\":-790},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"NPSHYD\",\"generation\":721},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"NUCLEAR\",\"generation\":3696},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"OCGT\",\"generation\":2},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"OIL\",\"generation\":0},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"OTHER\",\"generation\":1461},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"PS\",\"generation\":1457},{\"dataset\":\"FUELINST\",\"publishTime\":\"2024-02-12T17:50:00Z\",\"startTime\":\"2024-02-12T17:45:00Z\",\"settlementDate\":\"2024-02-12\",\"settlementPeriod\":36,\"fuelType\":\"WIND\",\"generation\":12829}]";

    /**Test basic JSON support. */
    public static void testJSONBasics()
	    {
    	final JSONObject jo0 = new JSONObject("{}");
	    assertNotNull(jo0);
	    assertEquals(0, jo0.length());
	    final String sample1 = "[{\"dataset\":\"FUELINST\",\"publishTime\":\"2022-06-20T00:00:00Z\",\"startTime\":\"2022-06-19T23:55:00Z\",\"settlementDate\":\"2022-06-20\",\"settlementPeriod\":2,\"fuelType\":\"PS\",\"generation\":-360},{\"dataset\":\"FUELINST\",\"publishTime\":\"2022-06-20T00:00:00Z\",\"startTime\":\"2022-06-19T23:55:00Z\",\"settlementDate\":\"2022-06-20\",\"settlementPeriod\":2,\"fuelType\":\"WIND\",\"generation\":6400}]";
    	final JSONArray ja1 = new JSONArray(sample1);
	    assertNotNull(ja1);
	    assertEquals(2, ja1.length());
    	final JSONArray ja2 = new JSONArray(FUELINST_JSON_sample_20240212);
	    assertNotNull(ja2);
	    assertEquals(19, ja2.length());
	    }
    }

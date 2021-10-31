/*
Copyright (c) 2008-2021, Damon Hart-Davis
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

import java.io.File;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import junit.framework.TestCase;

import org.hd.d.edh.DataUtils;
import org.hd.d.edh.FUELINST;
import org.hd.d.edh.FUELINSTUtils;
import org.hd.d.edh.MainProperties;
import org.hd.d.edh.TrafficLight;
import org.hd.d.edh.TwitterUtils;
import org.hd.d.edh.FUELINST.CurrentSummary;
import org.w3c.dom.Document;

/**Test the FUELINST handler. */
public final class TestFUELINST extends TestCase
    {
    public static void testCSVExtract()
        throws Exception
        {
        final String template = "type,date,settlementperiod,timestamp,CCGT,OIL,COAL,NUCLEAR,WIND,PS,NPSHYD,OCGT,OTHER,INTFR,INTIRL";

        // Try extract from single-record synthetic CSV data.
        final String sampleCSV = "HDR\r\nFUELINST,20011111,34,20011111165500,16000,123,20100,7800,1000,400,700,0,0,0,0\r\nFTR,1";
        final List<List<String>> rows = DataUtils.parseBMRCSV(new StringReader(sampleCSV), null);
        final List<Map<String, String>> namedFieldss = DataUtils.extractNamedFieldsByPositionFromRows(template, rows);
        assertEquals("must produce exactly one row", namedFieldss.size(), 1);
        final Map<String, String> namedFields = namedFieldss.get(0);
//System.out.println(namedFields); // {CCGT=16000, COAL=20100, OTHER=0, INTIRL=0, NPSHYD=700, OCGT=0, settlementperiod=34, type=FUELINST, date=20011111, NUCLEAR=7800, timestamp=20011111165500, INTFR=0, PS=400, OIL=123, WIND=1000}
        // Check that some important fields have been correctly extracted.
        assertEquals("FUELINST", namedFields.get("type"));
        assertEquals("16000", namedFields.get("CCGT"));
        assertEquals("20100", namedFields.get("COAL"));
        assertEquals("700", namedFields.get("NPSHYD"));
        assertEquals("7800", namedFields.get("NUCLEAR"));
        assertEquals("1000", namedFields.get("WIND"));
        assertEquals("123", namedFields.get("OIL"));

        // Insert some semi-sane intensities (tCO2/MWh).
        final Map<String,Float> intensities = new HashMap<String,Float>();
        intensities.put("CCGT",0.36f);
        intensities.put("OCGT",0.48f);
        intensities.put("OIL",0.61f);
        intensities.put("COAL",0.91f);
        intensities.put("NUCLEAR",0f);
        intensities.put("WIND",0f);
        intensities.put("NPSHYD",0f);
        intensities.put("OTHER",0.61f);
        final Map<String,Integer> generationByFuel = new HashMap<String,Integer>();
        // Convert MW values to Integer.
        for(final String name : intensities.keySet())
            { generationByFuel.put(name, Integer.parseInt(namedFields.get(name), 10)); }
        final float weightedIntensity = FUELINSTUtils.computeWeightedIntensity(intensities, generationByFuel, 0);
        assertTrue("Computed intensity must be correct/close", Math.abs(0.5276563f - weightedIntensity) < 1e-5);

//        // Open our sample CVS data file.
//        final Reader sampleFile1 = new FileReader("data-samples/20090311T0806-FUELINST.csv");
//        try
//            {
//            final List<List<String>> sdRows = DataUtils.parseBMRCSV(sampleFile1, "INSTANTANEOUS GENERATION BY FUEL TYPE DATA");
//            assertEquals("must extract correct number of rows from sample data", 288, sdRows.size());
//            for(final List<String> sdRow : sdRows)
//                {
//                final Map<String, String> nF = DataUtils.extractNamedFieldsByPositionFromRow(template, sdRow);
//                final Map<String,Integer> gBF = new HashMap<String,Integer>();
//                for(final String name : intensities.keySet())
//                    { gBF.put(name, Integer.parseInt(nF.get(name), 10)); }
//                final float wi = FUELINST.computeWeightedIntensity(intensities, gBF, 0);
//                // Intensity change throughout the day varies < 10% on this sample.
//                if(wi != 0) { assertTrue("Computed intensity must be close-ish to central value", Math.abs(0.5 - wi) < 0.4); }
//                }
//            }
//        finally { sampleFile1.close(); }
        }
    
    /**Test extended to check use of INTIFA2 interconnector name in CSV with template. */
    public static void testCSVExtractINTIFA2()
        throws Exception
        {
        final String template = "type,date,settlementperiod,timestamp,CCGT,OIL,COAL,NUCLEAR,WIND,PS,NPSHYD,OCGT,OTHER,INTFR,INTIRL,INTNED,INTEW,BIOMASS,INTNEM,INTELEC,INTIFA2,INTNSL";

        // Try extract from single-record synthetic CSV data.
        final String sampleCSV = "HDR\r\nFUELINST,20211028,22,20211028094500,11167,0,500,5242,8422,560,828,0,209,610,0,1043,0,2309,999,0,880,693\r\nFTR,1";
        final List<List<String>> rows = DataUtils.parseBMRCSV(new StringReader(sampleCSV), null);
        final List<Map<String, String>> namedFieldss = DataUtils.extractNamedFieldsByPositionFromRows(template, rows);
        assertEquals("must produce exactly one row", namedFieldss.size(), 1);
        final Map<String, String> namedFields = namedFieldss.get(0);
//System.out.println(namedFields); // {WIND=8422, date=20211028, INTNEM=999, PS=560, NUCLEAR=5242, OCGT=0, INTEW=0, COAL=500, NPSHYD=828, type=FUELINST, INTIRL=0, OTHER=209, INTFR=610, CCGT=11167, OIL=0, settlementperiod=22, INTELEC=0, BIOMASS=2309, INTNED=1043, INTNSL=693, INTIFA2=880, timestamp=20211028094500}
        // Check that some important fields have been correctly extracted.
        assertEquals("FUELINST", namedFields.get("type"));
        assertEquals("11167", namedFields.get("CCGT"));
        assertEquals("500", namedFields.get("COAL"));
        assertEquals("828", namedFields.get("NPSHYD"));
        assertEquals("5242", namedFields.get("NUCLEAR"));
        assertEquals("8422", namedFields.get("WIND"));
        assertEquals("0", namedFields.get("OIL"));
        assertEquals("880", namedFields.get("INTIFA2"));  
        }
    
    /**Test loading of the fuel names from main properties/config. */
    public static void testConfiguredFuelNames()
        {
        assertTrue("Must be able to load the main properties", MainProperties.getTimestamp() > 0);
        final Map<String, String> configuredFuelNames = FUELINSTUtils.getConfiguredFuelNames();
        assertNotNull(configuredFuelNames);
        assertTrue(configuredFuelNames.size() > 0);
//System.out.println(configuredFuelNames); // {PS=Pumped Storage Hydro, INTNEM=Nemo (Belgian) Interconnector, OCGT=Open-Cycle Gas Turbine, INTEW=East-West (Irish) Interconnector, NPSHYD=Non-Pumped-Storage Hydro, INTIRL=Irish (Moyle) Interconnector, OTHER=Other (including biomass), CCGT=Combined-Cycle Gas Turbine, INTFR=French Interconnector, INTELEC=INTELEC (France) Interconnector, INTNED=Netherlands Interconnector, INTNSL=North Sea Link (Norway), INTIFA2=INTIFA2 (France) Interconnector}

        // Test for presence of one fuel/interconnector with name which is not pure-alpha.
        assertNotNull(configuredFuelNames.get("INTIFA2"));
        // It should have a non-empty description.
        assertTrue(configuredFuelNames.get("INTIFA2").length() > 0);
        
        // Ensure that only valid fuel names are loaded.
        for(String k : configuredFuelNames.keySet())
            {
        	// Ensure that name is generally valid.
            assertTrue(FUELINSTUtils.FUEL_NAME_REGEX.matcher(k).matches());
            }
        }
    
    /**Test loading of the fuel intensities from main properties/config. */
    public static void testConfiguredIntensities()
        {
        assertTrue("Must be able to load the main properties", MainProperties.getTimestamp() > 0);
        final Map<String, Float> configuredIntensitiesDefault = FUELINSTUtils.getConfiguredIntensities();
        assertNotNull(configuredIntensitiesDefault);
        assertTrue(configuredIntensitiesDefault.size() > 0);
System.out.println(configuredIntensitiesDefault); // {PS=Pumped Storage Hydro, INTNEM=Nemo (Belgian) Interconnector, OCGT=Open-Cycle Gas Turbine, INTEW=East-West (Irish) Interconnector, NPSHYD=Non-Pumped-Storage Hydro, INTIRL=Irish (Moyle) Interconnector, OTHER=Other (including biomass), CCGT=Combined-Cycle Gas Turbine, INTFR=French Interconnector, INTELEC=INTELEC (France) Interconnector, INTNED=Netherlands Interconnector, INTNSL=North Sea Link (Norway), INTIFA2=INTIFA2 (France) Interconnector}

        // Test for presence of one fuel/interconnector with name which is not pure-alpha.
        assertNotNull(configuredIntensitiesDefault.get("INTIFA2"));
        // It should have a greater-than-zero intensity.
        assertTrue(configuredIntensitiesDefault.get("INTIFA2") > 0);
        
        // Ensure that date-qualified intensities are not being treated as literals,
        // eg there should not be entries such as INTIRL.2009--2011=0.7 present.
        // Ensure that only valid fuel names are loaded.
        for(String k : configuredIntensitiesDefault.keySet())
            {
        	// Ensure no '-' left in due to mis-parse.
        	assertEquals("should be no '/' in key/name", -1, k.indexOf('/'));
        	// Ensure that name is generally valid.
            assertTrue(FUELINSTUtils.FUEL_NAME_REGEX.matcher(k).matches());
            }
 
/* With the following in main.properties...
#intensity.fuel.INTIRL.2009/2011=0.7
intensity.fuel.INTIRL.2009=0.7
intensity.fuel.INTIRL.2010=0.7
intensity.fuel.INTIRL.2011=0.7
intensity.fuel.INTIRL.2012/=0.45
#intensity.fuel.INTIRL.2012=0.45
#intensity.fuel.INTIRL.2013=0.45
#intensity.fuel.INTIRL.2014=0.45
#intensity.fuel.INTIRL.2015=0.45
#intensity.fuel.INTIRL.2016=0.45
#intensity.fuel.INTIRL.2017=0.45
#intensity.fuel.INTIRL.2018=0.45
#intensity.fuel.INTIRL.2019=0.45
#intensity.fuel.INTIRL.2020=0.45
#intensity.fuel.INTIRL.2021=0.45
#intensity.fuel.INTIRL.2022=0.45
intensity.fuel.INTIRL=0.45
intensity.fuelname.INTIRL=Irish (Moyle) Interconnector
 */
        final float eps = 0.001f;
        assertEquals(0.45f, configuredIntensitiesDefault.get("INTIRL") , eps);
        
        assertEquals(0.7f, FUELINSTUtils.getConfiguredIntensities(2009).get("INTIRL"), eps);
        assertEquals(0.7f, FUELINSTUtils.getConfiguredIntensities(2010).get("INTIRL"), eps);
        assertEquals(0.7f, FUELINSTUtils.getConfiguredIntensities(2011).get("INTIRL"), eps);
        
        assertEquals(0.45f, FUELINSTUtils.getConfiguredIntensities(2012).get("INTIRL"), eps);
        assertEquals(0.45f, FUELINSTUtils.getConfiguredIntensities(2013).get("INTIRL"), eps);
        assertEquals(0.45f, FUELINSTUtils.getConfiguredIntensities(2014).get("INTIRL"), eps);
        assertEquals(0.45f, FUELINSTUtils.getConfiguredIntensities(2015).get("INTIRL"), eps);
        assertEquals(0.45f, FUELINSTUtils.getConfiguredIntensities(2016).get("INTIRL"), eps);
        assertEquals(0.45f, FUELINSTUtils.getConfiguredIntensities(2017).get("INTIRL"), eps);
        assertEquals(0.45f, FUELINSTUtils.getConfiguredIntensities(2018).get("INTIRL"), eps);
        assertEquals(0.45f, FUELINSTUtils.getConfiguredIntensities(2019).get("INTIRL"), eps);
        assertEquals(0.45f, FUELINSTUtils.getConfiguredIntensities(2020).get("INTIRL"), eps);
        assertEquals(0.45f, FUELINSTUtils.getConfiguredIntensities(2021).get("INTIRL"), eps);
        assertEquals(0.45f, FUELINSTUtils.getConfiguredIntensities(2022).get("INTIRL"), eps);
        
        assertEquals(0.45f, FUELINSTUtils.getConfiguredIntensities(2023).get("INTIRL"), eps);
        }

    /**Test that all possible grid status Tweets are legal with the current property set. */
    public static void testTweetValidity()
        {
        for(final boolean isDataStale : new boolean[]{ true, false } )
            {
            for(final TrafficLight status : TrafficLight.values())
                {
                final String message = FUELINSTUtils.generateTweetMessage(isDataStale, status);
                assertNotNull("Generated Tweet must not be null", message);
System.out.println("LENGTH="+message.length()+": "+message);
                assertFalse("Generated Tweet must not be empty", message.trim().isEmpty());
                assertTrue("Generated Tweet must not be over-long: was "+message.length()+" vs max "+TwitterUtils.MAX_TWEET_CHARS+" message: "+message,
                        message.length() <= TwitterUtils.MAX_TWEET_CHARS);
                }
            }
        }

    /**Cached summary information. */
    public static final File CACHE1 = new File("_gridCarbonIntensityGB.201503231711.cache");

    /**Test that XML output is generated correctly. */
    public void testXMLOutput() throws Exception
        {
        final File tmpFile = File.createTempFile("XMLOutputTest", ".xml");
        try
            {
            final DocumentBuilderFactory factory =  DocumentBuilderFactory.newInstance();

//            // Fail on initially-empty file.
//            final DocumentBuilder builderBad = factory.newDocumentBuilder();
//            try { builderBad.parse(tmpFile); fail("XML parse should fail"); }
//            catch(final SAXException e) { /* expected. */ }

            // Test that a minimal generated XML file is parseable at least.
            FUELINSTUtils.updateXMLFile(System.currentTimeMillis(),
                                        tmpFile.toString(),
                                        new FUELINST.CurrentSummary(),
                                        true,
                                        1,
                                        TrafficLight.YELLOW);
            final DocumentBuilder builderEmpty = factory.newDocumentBuilder();
            final Document parsedEmpty = builderEmpty.parse(tmpFile);
            assertNotNull(parsedEmpty);

//            // Try with a more realistic cached entry.
//            // Test that a minimal generated XML file is parseable at least.
//            FUELINSTUtils.updateXMLFile(System.currentTimeMillis(),
//                                        tmpFile.toString(),
//                                        FUELINSTUtils.computeCurrentSummary(CACHE1),
//                                        true,
//                                        1,
//                                        TrafficLight.YELLOW);
//            final DocumentBuilder builderCached1 = factory.newDocumentBuilder();
//            final Document parsedCached1 = builderCached1.parse(tmpFile);
//            assertNotNull(parsedCached1);
            }
        catch(final Exception e)
            {
            final File bad = new File("out/bad.xml.tmp");
            System.err.println("Bad file moved to " + bad.getAbsolutePath());
            tmpFile.renameTo(bad);
            throw e;
            }
        finally { tmpFile.delete(); }
        }
    }

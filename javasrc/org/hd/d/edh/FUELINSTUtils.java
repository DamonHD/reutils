/*
Copyright (c) 2008-2021, Damon Hart-Davis,
                         Ecotricity (Rob Clews).
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

package org.hd.d.edh;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.hd.d.edh.FUELINST.CurrentSummary;


/**Supporting routines of general interest for handling FUELINST data.
 */
public final class FUELINSTUtils
    {
	private FUELINSTUtils() { /* Prevent creation of an instance. */ }

    /**Longest edge of graphics building block components in pixels for HTML generation; strictly positive. */
    static final int GCOMP_PX_MAX = 100;

    /**If true then when data is stale then cautiously never normally show a GREEN status, but YELLOW at best. */
    private static final boolean NEVER_GREEN_WHEN_STALE = true;

    /**If the basic colour is GREEN but we're using pumped storage then we can indicate that with a yellowish green instead (ie mainly green, but not fully). */
    static final String LESS_GREEN_STORAGE_DRAWDOWN = "olive";

    /**If true then reject points with too few fuel types in mix since this is likely an error. */
    static final int MIN_FUEL_TYPES_IN_MIX = 2;
    
//    /**If true then enable cacheing of 24h result. */
//    private static final boolean ENABLE_24H_RESULT_CACHE = false;
//    /**If true, compress (GZIP) any persisted 24h state. */
//    private static final boolean GZIP_24H_RESULT_CACHE = true;

    /**Immutable regex pattern for matching a valid fuel name (all upper-case ASCII first char, digits also allowed subsequently); non-null. */
    public static final Pattern FUEL_NAME_REGEX = Pattern.compile("[A-Z][A-Z0-9]+");

    /**Immutable regex pattern for matching a valid fuel intensity year 20XX; non-null. */
    public static final Pattern FUEL_INTENSITY_YEAR_REGEX = Pattern.compile("20[0-9][0-9]");

    /**SimpleDateFormat pattern to parse TIBCO FUELINST timestamp down to seconds (all assumed GMT/UTC); not null.
     * Example TIBCO timestamp: 2009:03:09:23:57:30:GMT
     * Note that SimpleDateFormat is not immutable nor thread-safe.
     */
    public static final String TIBCOTIMESTAMP_FORMAT = "yyyy:MM:dd:HH:mm:ss:zzz";

    /**SimpleDateFormat pattern to parse CSV FUELINST timestamp down to seconds (all assumed GMT/UTC); not null.
     * Note that SimpleDateFormat is not immutable nor thread-safe.
     */
    public static final String CSVTIMESTAMP_FORMAT = "yyyyMMddHHmmss";

    /**SimpleDateFormat pattern to generate UTC date down to days; not null.
     * Note that SimpleDateFormat is not immutable nor thread-safe.
     */
    public static final String UTCDAYFILENAME_FORMAT = "yyyyMMdd";

    /**SimpleDateFormat pattern to generate ISO 8601 UTC timestamp down to minutes; not null.
     * Note that SimpleDateFormat is not immutable nor thread-safe.
     */
    public static final String UTCMINTIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm'Z'";

    /**SimpleDateFormat pattern to generate/parse compact HH:mm timestamp down to seconds (all assumed GMT/UTC); not null.
     * Note that SimpleDateFormat is not immutable nor thread-safe.
     */
    public static final String HHMMTIMESTAMP_FORMAT = "HH:mm";

    /**GMT TimeZone; never null.
     * Only package-visible because it may be mutable though we never attempt to mutate it.
     * <p>
     * We may share this (read-only) between threads and within this package.
     */
    static final TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT");
    
    /**Charset for FUELINST data (ASCII 7-bit). */
    public static final Charset FUELINST_CHARSET = StandardCharsets.US_ASCII;

    /**Number of hours in a day. */
    public static final int HOURS_PER_DAY = 24;

    /**Number of hours in a week. */
    public static final int HOURS_PER_WEEK = 7 * 24;

    /**Suffix to use for (serialised, gzipped) cache of last non-stale (24h) result. */
    public static final String RESULT_CACHE_SUFFIX = ".cache";

    /**Suffix to use for (gzipped, ASCII, CSV, pseudo-FUELINST format) longish-term (7d+) store. */
    public static final String LONG_STORE_SUFFIX = ".longstore.csv.gz";

    /**If true, generate an XML data file alongside the HTML5 and XHTML pages.
     * DHD20221213: last used some time ago by Ecotricity, so disabled.
     */
	private static final boolean GENERATE_XML_DATA_FILE = false;


    /**Compute current status of fuel intensity; never null, but may be empty/default if data not available.
     * If cacheing is enabled, then this may revert to cache in case of
     * difficulty retrieving new data.
     * <p>
     * Uses fuel intensities as of this year, ie when this call is made.
     * <p>
     * Purely functional other than some writes to stdout/stderr:
     * has no side-effects and does not alter the input.
     *
     * @param parsedBMRCSV  parsed (as strings) BMR CSV file data, or null if unavailable
     * @return summary; never null
     * @throws IOException in case of data unavailabilty or corruption
     */
    public static FUELINST.CurrentSummary computeCurrentSummary(
    		final List<List<String>> parsedBMRCSV)
        throws IOException
        {
        // If passed-in data is obviously broken
        // then return an empty/default result.
        if((null == parsedBMRCSV) || parsedBMRCSV.isEmpty())
            { return(new FUELINST.CurrentSummary()); }

        final long start = System.currentTimeMillis();

        // Get as much set up as we can before pestering the data source...
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
//        final String dataURL = rawProperties.get(FUELINST.FUEL_INTENSITY_MAIN_PROPNAME_CURRENT_DATA_URL);
//        if(null == dataURL)
//            { throw new IllegalStateException("Property undefined for data source URL: " + FUELINST.FUEL_INTENSITY_MAIN_PROPNAME_CURRENT_DATA_URL); }
        final String template = rawProperties.get(FUELINST.FUELINST_MAIN_PROPNAME_ROW_FIELDNAMES);
        if(null == template)
            { throw new IllegalStateException("Property undefined for FUELINST row field names: " + FUELINST.FUELINST_MAIN_PROPNAME_ROW_FIELDNAMES); }
        // Use fuel intensities as of this year, ie when this call is made.
        final LocalDate todayUTC = LocalDate.now(ZoneOffset.UTC);
        final Map<String, Float> configuredIntensities = FUELINSTUtils.getConfiguredIntensities(todayUTC.getYear());
        if(configuredIntensities.isEmpty())
            { throw new IllegalStateException("Properties undefined for fuel intensities: " + FUELINST.FUEL_INTENSITY_MAIN_PROPNAME_PREFIX + "*"); }
        final String maxIntensityAgeS = rawProperties.get(FUELINST.FUELINST_MAIN_PROPNAME_MAX_AGE);
        if(null == maxIntensityAgeS)
            { throw new IllegalStateException("Property undefined for FUELINST acceptable age (s): " + FUELINST.FUELINST_MAIN_PROPNAME_MAX_AGE); }
        final long maxIntensityAge = Math.round(1000 * Double.parseDouble(maxIntensityAgeS));
        final String distLossS = rawProperties.get(FUELINST.FUELINST_MAIN_PROPNAME_MAX_DIST_LOSS);
        if(null == distLossS)
            { throw new IllegalStateException("Property undefined for FUELINST distribution loss: " + FUELINST.FUELINST_MAIN_PROPNAME_MAX_DIST_LOSS); }
        final float distLoss = Float.parseFloat(distLossS);
        if(!(distLoss >= 0) && (distLoss <= 1))
            { throw new IllegalStateException("Bad value outside range [0.0,1.0] for FUELINST distribution loss: " + FUELINST.FUELINST_MAIN_PROPNAME_MAX_DIST_LOSS); }
        final String tranLossS = rawProperties.get(FUELINST.FUELINST_MAIN_PROPNAME_MAX_TRAN_LOSS);
        if(null == tranLossS)
            { throw new IllegalStateException("Property undefined for FUELINST transmission loss: " + FUELINST.FUELINST_MAIN_PROPNAME_MAX_TRAN_LOSS); }
        final float tranLoss = Float.parseFloat(tranLossS);
        if(!(tranLoss >= 0) && (tranLoss <= 1))
            { throw new IllegalStateException("Bad value outside range [0.0,1.0] for FUELINST transmission loss: " + FUELINST.FUELINST_MAIN_PROPNAME_MAX_TRAN_LOSS); }
        // Extract all fuel categories.
        final Map<String, Set<String>> fuelsByCategory = getFuelsByCategory();
        // Extract Set of zero-or-more 'storage'/'fuel' types/names; never null but may be empty.
        final Set<String> storageTypes = (fuelsByCategory.containsKey(FUELINST.FUELINST_CATNAME_STORAGE) ?
                fuelsByCategory.get(FUELINST.FUELINST_CATNAME_STORAGE) :
                Collections.<String>emptySet());

        // All intensity sample values from good records (assuming roughly equally spaced).
        final List<Integer> allIntensitySamples = new ArrayList<Integer>(parsedBMRCSV.size());

        // Compute summary.
        final SimpleDateFormat timestampParser = FUELINSTUtils.getCSVTimestampParser();
        int goodRecordCount = 0;
        int totalIntensity = 0;
        long firstGoodRecordTimestamp = 0;
        long lastGoodRecordTimestamp = 0;
        long minIntensityRecordTimestamp = 0;
        long maxIntensityRecordTimestamp = 0;
        int minIntensity = Integer.MAX_VALUE;
        int maxIntensity = Integer.MIN_VALUE;
        int currentIntensity = 0;
        long currentMW = 0;
        long currentStorageDrawdownMW = 0;
        Map<String,Integer> currentGenerationByFuel = Collections.emptyMap();
        final int[] sampleCount = new int[FUELINSTUtils.HOURS_PER_DAY]; // Count of all good timestamped records.
        final long[] totalIntensityByHourOfDay = new long[FUELINSTUtils.HOURS_PER_DAY]; // Use long to avoid overflow if many samples.
        final long[] totalGenerationByHourOfDay = new long[FUELINSTUtils.HOURS_PER_DAY]; // Use long to avoid overflow if many samples.
        final long[] totalZCGenerationByHourOfDay = new long[FUELINSTUtils.HOURS_PER_DAY]; // Use long to avoid overflow if many samples.
        final long[] totalStorageDrawdownByHourOfDay = new long[FUELINSTUtils.HOURS_PER_DAY]; // Use long to avoid overflow if many samples.
        // Set of all usable fuel types encountered.
        final Set<String> usableFuels = new HashSet<String>();
        // Sample-by-sample list of map of generation by fuel type (in MW) and from "" to weighted intensity (gCO2/kWh).
        final List<Map<String, Integer>> sampleBySampleGenForCorr = new ArrayList<Map<String,Integer>>(parsedBMRCSV.size());
        // Compute (crude) correlation between fuel use and intensity.
        for(final List<String> row : parsedBMRCSV)
            {
            // Extract fuel values for this row and compute a weighted intensity...
            final Map<String, String> namedFields = DataUtils.extractNamedFieldsByPositionFromRow(template, row);
            // Special case after BMRS upgrade 2016/12/30: ignore trailing row starting "FTR ".
            if(namedFields.get("type").startsWith("FTR")) { continue; }
            // Reject malformed/unexpected data.
            if(!"FUELINST".equals(namedFields.get("type")))
                { throw new IOException("Expected FUELINST data but got: " + namedFields.get("type")); }
            final Map<String,Integer> generationByFuel = new HashMap<String,Integer>();
            long thisMW = 0; // Total MW generation in this slot.
            long thisStorageDrawdownMW = 0; // Total MW storage draw-down in this slot.
            long thisZCGenerationMW = 0; // Total zero-carbon generation in this slot.
            // Retain any field that is all caps so that we can display it.
            for(final String name : namedFields.keySet())
                {
                // Skip if something other than a valid fuel name.
                if(!FUELINSTUtils.FUEL_NAME_REGEX.matcher(name).matches())
                    {
                	// DHD20211031: all inspected were benign 'date', 'type', 'settlementperiod', 'timestamp'.
//                    System.err.println("Skipping invalid 'fuel' name "+name+" at " + namedFields.get("timestamp") + " from row " + row);
                	continue;
                	}
                // Store the MW for this fuel.
                final int fuelMW = Integer.parseInt(namedFields.get(name), 10);
                if(fuelMW < 0) { continue; } // NB: -ve INTerconnector values in TIBCO data as of 2012 // { throw new IOException("Bad (-ve) fuel generation MW value: "+row); }
                thisMW += fuelMW;
                generationByFuel.put(name, fuelMW);
                // Slices of generation/demand.
                if(storageTypes.contains(name)) { thisStorageDrawdownMW += fuelMW; }
                final Float fuelInt = configuredIntensities.get(name);
                final boolean usableFuel = null != fuelInt;
                if(usableFuel) { usableFuels.add(name); }
                if(usableFuel && (fuelInt <= 0)) { thisZCGenerationMW += fuelMW; }
                }
            // Compute weighted intensity as gCO2/kWh for simplicity of representation.
            // 'Bad' fuels such as coal are ~1000, natural gas is <400, wind and nuclear are roughly 0.
            final int weightedIntensity = Math.round(1000 * FUELINSTUtils.computeWeightedIntensity(configuredIntensities, generationByFuel, MIN_FUEL_TYPES_IN_MIX));
            // Reject bad (-ve) records.
            if(weightedIntensity < 0)
                {
                System.err.println("ERROR: skipping non-positive weighed intensity record at " + namedFields.get("timestamp"));
                continue;
                }

            allIntensitySamples.add(weightedIntensity);

            // For computing correlations...
            // Add entry only iff both a valid weighted intensity and at least one by-fuel number.
            if(!generationByFuel.isEmpty())
                {
                final Map<String, Integer> corrEntry = new HashMap<String, Integer>(generationByFuel);
                corrEntry.put("", weightedIntensity);
                sampleBySampleGenForCorr.add(corrEntry);
                }

            currentMW = thisMW;
            currentIntensity = weightedIntensity; // Last (good) record we process is the 'current' one as they are in date order.
            currentGenerationByFuel = generationByFuel;
            currentStorageDrawdownMW = thisStorageDrawdownMW; // Last (good) record is 'current'.

            ++goodRecordCount;
            totalIntensity += weightedIntensity;

            // Extract timestamp field as defined in the template, format YYYYMMDDHHMMSS.
            final String rawTimestamp = namedFields.get("timestamp");
            long recordTimestamp = 0; // Will be non-zero after a successful parse.
            if(null == rawTimestamp)
                { System.err.println("ERROR: missing FUELINST row timestamp"); }
            else
                {
                try
                    {
                    final Date d = timestampParser.parse(rawTimestamp);
                    recordTimestamp = d.getTime();
                    lastGoodRecordTimestamp = recordTimestamp;
                    if(firstGoodRecordTimestamp == 0) { firstGoodRecordTimestamp = recordTimestamp; }

                    // Extract raw GMT hour from YYYYMMDDHH...
                    final int hour = Integer.parseInt(rawTimestamp.substring(8, 10), 10);
//System.out.println("H="+hour+": int="+weightedIntensity+", MW="+currentMW+" time="+d);
                    ++sampleCount[hour];
                    // Accumulate intensity by hour...
                    totalIntensityByHourOfDay[hour] += weightedIntensity;
                    // Accumulate generation by hour...
                    totalGenerationByHourOfDay[hour] += currentMW;
                    // Note zero-carbon generation.
                    totalZCGenerationByHourOfDay[hour] += thisZCGenerationMW;
                    // Note storage draw-down, if any.
                    totalStorageDrawdownByHourOfDay[hour] += thisStorageDrawdownMW;
                    }
                catch(final ParseException e)
                    {
                    System.err.println("ERROR: unable to parse FUELINST record timestamp " + rawTimestamp + ": " + e.getMessage());
                    }
                }

            if(weightedIntensity < minIntensity)
                { minIntensity = weightedIntensity; minIntensityRecordTimestamp = recordTimestamp; }
            if(weightedIntensity > maxIntensity)
                { maxIntensity = weightedIntensity; maxIntensityRecordTimestamp = recordTimestamp; }
            }
//System.out.println("INFO: first good record timestamp "+(new Date(firstGoodRecordTimestamp)));
//System.out.println("INFO: last good record timestamp "+(new Date(lastGoodRecordTimestamp))+" vs now "+(new Date(System.currentTimeMillis())));

        // Note if the intensity dropped/improved in the final samples.
        TrafficLight recentChange = null;
        if(allIntensitySamples.size() > 1)
            {
            final Integer prev = allIntensitySamples.get(allIntensitySamples.size() - 2);
            final Integer last = allIntensitySamples.get(allIntensitySamples.size() - 1);
            if(prev < last) { recentChange = TrafficLight.RED; }
            else if(prev > last) { recentChange = TrafficLight.GREEN; }
            else { recentChange = TrafficLight.YELLOW; }
            }

        // Compute traffic light status: defaults to 'unknown'.
        TrafficLight status = null;

        final int aveIntensity = totalIntensity / Math.max(goodRecordCount, 1);

        // Always set the outputs and let the caller decide what to do with aged data.
        int lowerThreshold = 0;
        int upperThreshold = 0;
        final int allSamplesSize = allIntensitySamples.size();
        if(allSamplesSize > 3) // Only useful above some minimal set size.
            {
            // Normally we expect bmreports to give us 24hrs' data.
            // RED will be where the current value is in the upper quartile of the last 24hrs' intensities,
            // GREEN when in the lower quartile (and below the mean to be safe), so is fairly conservative,
            // YELLOW otherwise.
            // as long as we're on better-than-median intensity compared to the last 24 hours.
            final List<Integer> sortedIntensitySamples = new ArrayList<Integer>(allIntensitySamples);
            Collections.sort(sortedIntensitySamples);
            upperThreshold = sortedIntensitySamples.get(allSamplesSize-1 - (allSamplesSize / 4));
            lowerThreshold = Math.min(sortedIntensitySamples.get(allSamplesSize / 4), aveIntensity);
            if(currentIntensity > upperThreshold) { status = TrafficLight.RED; }
            else if(currentIntensity < lowerThreshold) { status = TrafficLight.GREEN; }
            else { status = TrafficLight.YELLOW; }
            }
        //else { System.err.println("Newest data point too old"); }
        else { System.err.println("Too few samples: " + allSamplesSize); }

        // Compute mean intensity by time slot.
        final List<Integer> aveIntensityByHourOfDay = new ArrayList<Integer>(24);
        for(int h = 0; h < 24; ++h)
            { aveIntensityByHourOfDay.add((sampleCount[h] < 1) ? null : Integer.valueOf((int) (totalIntensityByHourOfDay[h] / sampleCount[h]))); }

        // Compute mean generation by time slot.
        final List<Integer> aveGenerationByHourOfDay = new ArrayList<Integer>(24);
        for(int h = 0; h < 24; ++h)
            { aveGenerationByHourOfDay.add((sampleCount[h] < 1) ? null : Integer.valueOf((int) (totalGenerationByHourOfDay[h] / sampleCount[h]))); }

        // Compute mean zero-carbon generation by time slot.
        final List<Integer> aveZCGenerationByHourOfDay = new ArrayList<Integer>(24);
        for(int h = 0; h < 24; ++h)
            { aveZCGenerationByHourOfDay.add((sampleCount[h] < 1) ? null : Integer.valueOf((int) (totalZCGenerationByHourOfDay[h] / sampleCount[h]))); }

        // Compute mean draw-down from storage by time slot.
        final List<Integer> aveStorageDrawdownByHourOfDay = new ArrayList<Integer>(24);
        for(int h = 0; h < 24; ++h)
            { aveStorageDrawdownByHourOfDay.add((sampleCount[h] < 1) ? null : Integer.valueOf((int) (totalStorageDrawdownByHourOfDay[h] / sampleCount[h]))); }

        // Compute fuel/intensity correlation.
        final Map<String,Float> correlationIntensityToFuel = new HashMap<String,Float>(usableFuels.size());
        if(!sampleBySampleGenForCorr.isEmpty())
            {
            // Compute correlation by fuel, where there are enough samples.
            for(final String fuel : usableFuels)
                {
                final List<Double> fuelMW = new ArrayList<Double>(sampleBySampleGenForCorr.size());
                final List<Double> gridIntensity = new ArrayList<Double>(sampleBySampleGenForCorr.size());

                for(int i = sampleBySampleGenForCorr.size(); --i >= 0; )
                    {
                    final Map<String, Integer> s = sampleBySampleGenForCorr.get(i);
                    // Only use matching pairs of intensity and MW values to keep lists matching by position.
                    if(s.containsKey("") && s.containsKey(fuel))
                        {
                        fuelMW.add(s.get(fuel).doubleValue());
                        gridIntensity.add(s.get("").doubleValue());
                        }
                    }

                // Do not attempt unless enough samples.
                if(fuelMW.size() > 1)
                    {
                    final float corr = (float) StatsUtils.ComputePearsonCorrelation(gridIntensity, fuelMW);
                    // Retain correlation only if sane / finite.
                    if(!Float.isNaN(corr) && !Float.isInfinite(corr))
                        { correlationIntensityToFuel.put(fuel, corr); }
                    }
                }
            }

        // Construct summary status...
        final FUELINST.CurrentSummary result =
            new FUELINST.CurrentSummary(status, recentChange,
                                  lastGoodRecordTimestamp, lastGoodRecordTimestamp + maxIntensityAge,
                                  currentMW,
                                  currentIntensity,
                                  currentGenerationByFuel,
                                  currentStorageDrawdownMW,
                                  minIntensity,
                                  minIntensityRecordTimestamp,
                                  aveIntensity,
                                  maxIntensity,
                                  maxIntensityRecordTimestamp, (lastGoodRecordTimestamp - firstGoodRecordTimestamp),
                                  goodRecordCount,
                                  lowerThreshold, upperThreshold,
                                  aveIntensityByHourOfDay,
                                  aveGenerationByHourOfDay,
                                  aveZCGenerationByHourOfDay,
                                  aveStorageDrawdownByHourOfDay,
                                  tranLoss + distLoss,
                                  correlationIntensityToFuel,
                                  System.currentTimeMillis() - start);

        return(result);
        }


    /**Compute variability % of a set as a function of its (non-negative) min and max values; always in range [0,100]. */
    static int computeVariability(final int min, final int max)
        {
        if((min < 0) || (max < 0)) { throw new IllegalArgumentException(); }
        if(max == 0) { return(0); }
        return(100 - ((100*min)/max));
        }

    /**Compute variability % of a set as a function of its min and max values; always in range [0,100]. */
    static int computeVariability(final List<FUELINSTHistorical.TimestampedNonNegInt> intensities)
        {
        if(null == intensities) { throw new IllegalArgumentException(); }
        int min = Integer.MAX_VALUE;
        int max = 0;
        for(final FUELINSTHistorical.TimestampedNonNegInt ti : intensities)
            {
            if(ti.value > max) { max = ti.value; }
            if(ti.value < min) { min = ti.value; }
            }
        return(computeVariability(min, max));
        }

    /**Given a set of relative fuel usages and carbon intensities, computes an overall intensity; never null.
     * This computes an intensity in the same units as the supplied values.
     * Fuels whose keys are not in the intensities Map will be ignored.
     * <p>
     * Inputs must not be altered while this is in progress.
     * <p>
     * This will not attempt to alter its inputs.
     *
     * @param intensities  Map from fuel name to CO2 per unit of energy; never null
     * @param generationByFuel  Map from fuel name to power being generated from that fuel; never null
     * @param minFuelTypesInMix  minimum number of fuel types in mix else return -1; non-negative
     *
     * @return weighted intensity of specified fuel mix for fuels with known intensity,
     *         or -1 if too few fuels in mix
     */
    public static float computeWeightedIntensity(final Map<String, Float> intensities,
                                                 final Map<String, Integer> generationByFuel,
                                                 final int minFuelTypesInMix)
        {
        if(null == intensities) { throw new IllegalArgumentException(); }
        if(null == generationByFuel) { throw new IllegalArgumentException(); }
        if(minFuelTypesInMix < 0) { throw new IllegalArgumentException(); }

        // Compute set of keys common to both Maps.
        final Set<String> commonKeys = new HashSet<String>(intensities.keySet());
        commonKeys.retainAll(generationByFuel.keySet());
        // If too few fuels in the mix then quickly return -1 as a distinguished value.
        if(commonKeys.size() < minFuelTypesInMix) { return(-1); }

        int nonZeroFuelCount = 0;
        float totalGeneration = 0;
        float totalCO2 = 0;

        for(final String fuelName : commonKeys)
            {
            final float power = generationByFuel.get(fuelName);
            if(power < 0) { throw new IllegalArgumentException(); }
            if(power == 0) { continue; }
            ++nonZeroFuelCount;
            totalGeneration += power;
            totalCO2 += power * intensities.get(fuelName);
            }

        // If too few (non-zero) fuels in the mix then quickly return -1 as a distinguished value.
        if(nonZeroFuelCount < minFuelTypesInMix) { return(-1); }

        final float weightedIntensity = (totalGeneration == 0) ? 0 : totalCO2 / totalGeneration;
        return(weightedIntensity);
        }

    /**Handle the flag files that can be tested by remote servers.
     * The basic ".flag" file is present unless status is green
     * AND we have live data.
     * <p>
     * The more robust ".predicted.flag" file is present unless status is green.
     * Live data is used if present, else a prediction is made from historical data.
     * <p>
     * The keen ".supergreen.flag" file is present unless status is green
     * AND we have live data
     * AND no storage is being drawn down on the grid.
     * This means that we can be pretty sure that there is a surplus of energy available.
     *
     * @param baseFileName  base file name to make flags; if null then don't do flags.
     * @param statusCapped  status capped to YELLOW if there is no live data
     * @param statusUncapped  uncapped status (can be green from prediction even if no live data)
     * @param status7dCapped  7d status capped to YELLOW if there is no live data
     * @throws IOException  in case of problems
     */
    static void doFlagFiles(final String baseFileName,
    		final boolean isDataStale,
            final TrafficLight statusCapped, final TrafficLight statusUncapped,
            final long currentStorageDrawdownMW,
            final TrafficLight status7dCapped)
        throws IOException
        {
        if(null == baseFileName) { return; }

        // In the absence of current data,
        // then create/clear the flag based on historical data (ie predictions) where possible.
        // The flag file has terminating extension (from final ".") replaced with ".flag".
        // (If no extension is present then ".flag" is simply appended.)
        final File outputFlagFile = new File(baseFileName + ".flag");
        final boolean basicFlagState = TrafficLight.GREEN != statusCapped;
        System.out.println("INFO: basic (green) flag file is " + outputFlagFile + ": " + (basicFlagState ? "set" : "clear"));
        // Remove power-low/grid-poor flag file when status is GREEN, else create it (for RED/YELLOW/unknown).
        FUELINSTUtils.doPublicFlagFile(outputFlagFile, basicFlagState);
        // 7d version.
        final File output7dFlagFile = new File(baseFileName + ".7d.flag");
        final boolean basic7dFlagState = TrafficLight.GREEN != status7dCapped;
        System.out.println("INFO: basic 7d (green) flag file is " + output7dFlagFile + ": " + (basic7dFlagState ? "set" : "clear"));
        FUELINSTUtils.doPublicFlagFile(output7dFlagFile, basic7dFlagState);

        // Now deal with the flag that is prepared to make predictions from historical data,
        // ie helps to ensure that the flag will probably be cleared some time each day
        // even if our data source is unreliable.
        // When live data is available then this should be the same as the basic flag.
        final File outputPredictedFlagFile = new File(baseFileName + ".predicted.flag");
        final boolean predictedFlagState = TrafficLight.GREEN != statusUncapped;
        System.out.println("INFO: predicted flag file is " + outputPredictedFlagFile + ": " + (predictedFlagState ? "set" : "clear"));
        // Remove power-low/grid-poor flag file when status is GREEN, else create it (for RED/YELLOW/unknown).
        FUELINSTUtils.doPublicFlagFile(outputPredictedFlagFile, predictedFlagState);

        // Present unless 'capped' value is green (and thus must also be from live data)
        // AND storage is not being drawn from.
        final File outputSupergreenFlagFile = new File(baseFileName + ".supergreen.flag");
        final boolean supergreenFlagState = (basicFlagState) || (currentStorageDrawdownMW > 0);
        System.out.println("INFO: supergreen flag file is " + outputSupergreenFlagFile + ": " + (supergreenFlagState ? "set" : "clear"));
        // Remove power-low/grid-poor flag file when status is GREEN, else create it (for RED/YELLOW/unknown).
        FUELINSTUtils.doPublicFlagFile(outputSupergreenFlagFile, supergreenFlagState);
        // 7d version.
        final File outputSupergreen7dFlagFile = new File(baseFileName + ".7d.supergreen.flag");
        final boolean supergreen7dFlagState = (TrafficLight.GREEN != status7dCapped) ||
    		(isDataStale) || (basic7dFlagState) || (currentStorageDrawdownMW > 0);
        System.out.println("INFO: supergreen 7d flag file is " + outputSupergreen7dFlagFile + ": " + (supergreen7dFlagState ? "set" : "clear"));
        FUELINSTUtils.doPublicFlagFile(outputSupergreen7dFlagFile, supergreen7dFlagState);

        // Present when red, ie not in most carbon-intensive part of the day.
        // Flag is computed even with stale data.
        final File outputRedFlagFile = new File(baseFileName + ".red.flag");
        final boolean redFlagState = TrafficLight.RED == statusUncapped;
        System.out.println("INFO: red flag file is " + outputRedFlagFile + ": " + (redFlagState ? "set" : "clear"));
        // Remove power-low/grid-poor flag file when status is not RED, else create it (for GREEN/YELLOW/unknown).
        FUELINSTUtils.doPublicFlagFile(outputRedFlagFile, redFlagState);
        // 7d version.
        final File output7dRedFlagFile = new File(baseFileName + ".7d.red.flag");
        final boolean red7dFlagState = TrafficLight.RED == status7dCapped;
        System.out.println("INFO: 7d red flag file is " + output7dRedFlagFile + ": " + (red7dFlagState ? "set" : "clear"));
        FUELINSTUtils.doPublicFlagFile(output7dRedFlagFile, red7dFlagState);
        }

    /**Create/remove public (readable by everyone) flag file as needed to match required state.
     * @param outputFlagFile  flag file to create (true) or remove (false) if required; non-null
     * @param flagRequiredPresent  desired state for flag: true indicates present, false indicates absent
     * @throws IOException  in case of difficulty
     */
    static void doPublicFlagFile(final File outputFlagFile,
            final boolean flagRequiredPresent)
        throws IOException
        {
        if(flagRequiredPresent)
            {
            if(outputFlagFile.createNewFile())
                {
                outputFlagFile.setReadable(true);
                System.out.println("INFO: flag file created: "+outputFlagFile);
                }
            }
        else
            { if(outputFlagFile.delete()) { System.out.println("INFO: flag file deleted: "+outputFlagFile); } }
        }


    /**Implement the 'traffic lights' command line option.
     * @param args  optional (though usual) trailing argument (output HTML file name); never null
     */
    static void doTrafficLights(final String[] args)
        throws IOException
        {
        if(null == args) { throw new IllegalArgumentException(); }

        final long startTime = System.currentTimeMillis();

        System.out.println("INFO: generating traffic-light summary "+Arrays.asList(args)+"...");

        // A pool of threads to process less critical tasks concurrently.
        // Loading fresh data and composing the HTML page and updating flags are critical.
        // Anything else, especially including potentially-slow I/O, can be done in the pool.
        final ExecutorService executor = Executors.newCachedThreadPool();

        final String outputHTMLFileName = (args.length < 1) ? null : args[0];
        final int lastDot = (outputHTMLFileName == null) ? -1 : outputHTMLFileName.lastIndexOf(".");
        // Base/prefix onto which to append specific extensions.
        final String baseFileName = (-1 == lastDot) ? outputHTMLFileName : outputHTMLFileName.substring(0, lastDot);

        // Compute relative paths for caches/stores.
        final File longStoreFile = (null == baseFileName) ? null : (new File(baseFileName + LONG_STORE_SUFFIX));        

//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");
        // Load long store concurrently with fetching new data.
        // As well as overlapping the I/O
        // this may get JIT compilation done fairly optimally in parallel.
        final Future<List<List<String>>> longStoreLoad = executor.submit(() ->
	    	{ return(DataUtils.loadBMRCSV(longStoreFile)); });


        // Fetch and parse the FUELINST CSV file from the data source.
        // Will be null in case of inability to fetch or parse.
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        final String dataURL = rawProperties.get(FUELINST.FUEL_INTENSITY_MAIN_PROPNAME_CURRENT_DATA_URL);
        if(null == dataURL)
            { throw new IllegalStateException("Property undefined for data source URL: " + FUELINST.FUEL_INTENSITY_MAIN_PROPNAME_CURRENT_DATA_URL); }
        List<List<String>> parsedBMRCSV = null;
        URL url = null;
        try
            {
            // Set up URL connection to fetch the data.
            url = new URL(dataURL.trim()); // Trim to avoid problems with trailing whitespace...
            final long dataFetchStart = System.currentTimeMillis();
            parsedBMRCSV = DataUtils.parseBMRCSV(url, null);
            final long dataFetchEnd = System.currentTimeMillis();
System.out.println("INFO: record/row count of CSV FUELINST data: "+(dataFetchEnd-dataFetchStart)+"ms, " + parsedBMRCSV.size() + " records from source: " + url + " fetch and parse");
            }
        catch(final IOException e)
            {
            // Could not get data, so status is unknown.
System.err.println("ERROR: could not fetch data from " + url + " error: " + e.getMessage());
            }
        // Validate parsedBMRCSV (correct ordering, no dates in future, etc).
        // If containable defects are found then repair on the fly.
        // Be prepared to reject entirely if unrepairable problem found.
        final DataUtils.ValidBMRDataResultError validationError =
    		DataUtils.isValidBMRData(parsedBMRCSV, System.currentTimeMillis(), HOURS_PER_DAY+1, true);
        if(null != validationError)
            {
System.err.println("WARNING: *** invalid CSV FUELINST data as received: " + validationError.errorMessage);
        	// Dump troublesome data, as received...
        	if(parsedBMRCSV != null)
	        	{
	        	for(List<String> row : parsedBMRCSV)
		        	{ System.err.println("WARNING: " + row); }
	        	}
            // Use repaired version if any.
            if(null != validationError.repairedBMRCSV)
                {
            	parsedBMRCSV = validationError.repairedBMRCSV;
System.err.println("WARNING: *** invalid CSV FUELINST data as repaired.");
				// Dump after repair...
				if(parsedBMRCSV != null)
					{
					for(List<String> row : parsedBMRCSV)
				    	{ System.err.println("WARNING: " + row); }
					}
                }
            // Reject unrepairable data...
            else
	            {
            	parsedBMRCSV = null;
System.err.println("ERROR: invalid CSV FUELINST data not repaired so rejected: " + validationError.errorMessage);
	            }            
        	}
System.out.println("INFO: doTrafficLights(): CHECKPOINT: fresh data fetched, parsed, validated/fixed: timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");


        // Collect results of long store load.
//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");
		if(!longStoreLoad.isDone()) { System.out.println("INFO: long store load still running: "+(System.currentTimeMillis()-startTime)+"ms."); }
		List<List<String>> _ls = null;
        try { _ls = longStoreLoad.get(); }
        catch(final ExecutionException|InterruptedException e)
	        {
System.err.println("WARNING: could not load long store "+longStoreFile+" error: " + e.getMessage());
	        }
if(null != _ls) { System.out.println("INFO: long store records loaded: "+_ls.size()); }

        // Sometimes last few live records are omitted apparently when server is busy.
        // Attempt to patch them up from the long store if so...
        if((null != parsedBMRCSV) && (null != longStoreFile))
	        {
        	final List<List<String>> appendedNewData = DataUtils.appendNewBMRDataRecords(
        			parsedBMRCSV, _ls);
        	if(null != appendedNewData)
	        	{
System.err.println("WARNING: some recent records omitted from this data fetch: patched back in.");
				parsedBMRCSV = appendedNewData;
	        	}
	        }
//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");
        // Attempt to update the long store with the new data received, then save it.
        // Keep the store length trimmed.
        Future<Long> taskLongStoreSave = null;
        // Update the long store only if there is something valid to update it with.
        if(null != parsedBMRCSV)
            {
	        // Append any new records to long store.
	        final List<List<String>> appendedlongStore = DataUtils.appendNewBMRDataRecords(
	        		_ls, parsedBMRCSV);
	        if(null != appendedlongStore) { _ls = appendedlongStore; }
	        // Trim history in long store to maximum of 7 days.
	        final List<List<String>> trimmedLongStore = DataUtils.trimBMRData(
	        		_ls, HOURS_PER_WEEK);
	        if(null != trimmedLongStore) { _ls = trimmedLongStore; }
	        // Save long store (asynchronously, atomically, world-readable).
	        // The long store must not be mutated.
	        final List<List<String>> lsf = _ls;
	        taskLongStoreSave = executor.submit(() ->
	            {
				final long longStoreSaveStart = System.currentTimeMillis();
	        	DataUtils.saveBMRCSV(lsf, longStoreFile);
		        final long longStoreSaveEnd = System.currentTimeMillis();
		        return(longStoreSaveEnd - longStoreSaveStart);
	            });
            }
        // Immutable view.
        final List<List<String>> longStore = _ls;
//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");


        // Start compute of 7-day summary if long store is available.
        // Try to do it largely concurrently with the main 24h view.
        final Future<CurrentSummary> taskSummary7d =
            ((null == longStore) || longStore.isEmpty()) ? null :
            executor.submit(() -> {return(FUELINSTUtils.computeCurrentSummary(longStore));});


        // Compute 24hr summary if we have fresh data, and cache.
        // Will be empty rather than null if not able to be computed/loaded.
//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");
        //
        // If parsedBMRCSV is null or empty
        // then this will be a empty/default (non-null) result.
        final CurrentSummary summary24h = ((null != parsedBMRCSV) && !parsedBMRCSV.isEmpty()) ?
        		FUELINSTUtils.computeCurrentSummary(parsedBMRCSV) :	
        		new FUELINST.CurrentSummary();
//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");
System.out.println("INFO: doTrafficLights(): CHECKPOINT: 24h summmary computed: timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");


        // Is the data stale?
        final boolean isDataStale = summary24h.useByTime < startTime;

        // Compute intensity as seen by typical GB domestic consumer, gCO2/kWh.
        final int retailIntensity = Math.round((isDataStale ?
        		summary24h.histAveIntensity :
        	    summary24h.currentIntensity) * (1 + summary24h.totalGridLosses));


        // Update button(s)/icon(s).
        // Start ASAP, because likely the last task to finish.
        // NOTE: currently based on 24h summary only.
        Future<Long> taskButtons = null;
        final File bd = new File(DEFAULT_BUTTON_BASE_DIR);
        if(bd.isDirectory() && bd.canWrite())
            {
        	taskButtons = executor.submit(() ->
        		{
        		final long s = System.currentTimeMillis();
        		// DHD20221213: 48x48 generation dropped as not apparently used at all.
                GraphicsUtils.writeSimpleIntensityIconPNG(DEFAULT_BUTTON_BASE_DIR, 32, summary24h.timestamp, summary24h.status, retailIntensity);
                GraphicsUtils.writeSimpleIntensityIconPNG(DEFAULT_BUTTON_BASE_DIR, 64, summary24h.timestamp, summary24h.status, retailIntensity);
        		final long e = System.currentTimeMillis();
        		return(e - s);
        		});
        	}
        else { System.err.println("ERROR: missing directory for icons: " + DEFAULT_BUTTON_BASE_DIR); }


        // Append to the intensity log.
        // Only do this for current/live data, ie if not stale.
        Future<Long> taskIntensityLogUpdate = null;
        final File intensityLogFile = new File(DEFAULT_INTENSITY_LOG_BASE_DIR);
        if(isDataStale || (0 == summary24h.timestamp))
            { System.err.println("WARNING: will not update intensity log, input data is stale."); }
        else if(!intensityLogFile.isDirectory() || !intensityLogFile.canWrite())
            { System.err.println("ERROR: missing directory for intensity log: " + DEFAULT_INTENSITY_LOG_BASE_DIR); }
        else
        	{ 	
        	taskIntensityLogUpdate = executor.submit(() ->
        	    {
        		final long s = System.currentTimeMillis();
	            appendToRetailIntensityLog(intensityLogFile, summary24h.timestamp, retailIntensity);
        		final long e = System.currentTimeMillis();
                return(e - s);
	            });
        	}


        // Dump summaries...
        System.out.println("INFO: 24h summary: " + summary24h);
        // Gather 7d result.
        CurrentSummary summary7d = null;
        if(null != taskSummary7d)
 	    	{
        	if(!taskSummary7d.isDone()) { System.out.println("INFO: summary7d still running: "+(System.currentTimeMillis()-startTime)+"ms."); }
 	    	try { summary7d = taskSummary7d.get(); }
 	        catch(final ExecutionException|InterruptedException e)
 		        { System.err.println("ERROR: could not compute 7d summary: " + e.getMessage()); }
 	    	}
        System.out.println("INFO: 7d summary: " + summary7d);


        // Compute retail mean intensity from over 7d if available.
        final int retailMeanIntensity = Math.round(
		    ((null != summary7d) ? summary7d.histAveIntensity : summary24h.histAveIntensity) *
		        (1 + summary24h.totalGridLosses));


        // Update intensity files.
        Future<Long> taskIntensityFiles = null;
        final String outputTXTFileName = (-1 != lastDot) ?
                (outputHTMLFileName.substring(0, lastDot) + ".txt") :
                (outputHTMLFileName + ".txt");
        final String outputMeanTXTFileName = (-1 != lastDot) ?
                (outputHTMLFileName.substring(0, lastDot) + ".mean.txt") :
                (outputHTMLFileName + ".mean.txt");
    	System.out.println("INFO: retail intensity of "+retailIntensity+"gCO2e/kWh being saved to "+outputTXTFileName+"...");
    	System.out.println("INFO: retail mean intensity of "+retailMeanIntensity+"gCO2e/kWh being saved to "+outputMeanTXTFileName+"...");
        taskIntensityFiles = executor.submit(() -> {
        	final long s = System.currentTimeMillis();
        	FUELINSTUtils.updateTXTFile(startTime, outputTXTFileName, Integer.toString(retailIntensity), isDataStale);
        	FUELINSTUtils.updateTXTFile(startTime, outputMeanTXTFileName, Integer.toString(retailMeanIntensity), isDataStale);  
        	final long e = System.currentTimeMillis();
        	return(e - s);
            });


        // Update HTML page, and XHTML mobile page.
        // Also post to social media if enabled.
//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");
        Future<Long> taskTootSend = null;
        Future<Long> taskTweetSend = null;
        Future<Long> taskXHTMLsave = null;
        if(outputHTMLFileName != null)
            {
            // Status to use to drive traffic-light measure.
            // If the data is current then use the latest data point,
            // else extract a suitable historical value to use in its place.
            final int hourOfDayHistorical = CurrentSummary.getGMTHourOfDay(startTime);
            final Integer intensityHistoric = summary24h.histAveIntensityByHourOfDay.get(hourOfDayHistorical);
			// If no historic intensity is available for this hour, use YELLOW.
            final TrafficLight statusHistorical = (null == intensityHistoric) ? TrafficLight.YELLOW : summary24h.selectColour(intensityHistoric);
            final TrafficLight statusHistoricalCapped = (TrafficLight.GREEN != statusHistorical) ? statusHistorical : TrafficLight.YELLOW;
            final TrafficLight statusUncapped = (!isDataStale) ? summary24h.status : statusHistorical;
            final TrafficLight status = (!isDataStale) ? summary24h.status :
                (NEVER_GREEN_WHEN_STALE ? statusHistoricalCapped : statusHistorical);
            // Status over (up to) 7d; reverts to 24h versions if no live data.
            final TrafficLight status7d = (!isDataStale) ? summary7d.status :
                (NEVER_GREEN_WHEN_STALE ? statusHistoricalCapped : statusHistorical);

//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");
            // Handle the flag files that can be tested by remote servers.
            try { FUELINSTUtils.doFlagFiles(baseFileName, isDataStale, status, statusUncapped, summary24h.currentStorageDrawdownMW, status7d); }
            catch(final IOException e) { e.printStackTrace(); }
System.out.println("INFO: doTrafficLights(): CHECKPOINT: flags written: timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");
//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");

            // Update the (mobile-friendly) XHTML page (in the background).
            taskXHTMLsave = executor.submit(() ->
                {
                final long s = System.currentTimeMillis();
                final String outputXHTMLFileName = (-1 != lastDot) ? (outputHTMLFileName.substring(0, lastDot) + ".xhtml") :
                    (outputHTMLFileName + ".xhtml");
                FUELINSTUtils.updateXHTMLFile(startTime, outputXHTMLFileName, summary24h, isDataStale,
                    hourOfDayHistorical, status);
                final long e = System.currentTimeMillis();
                return(e - s);
                });

            // Collect social media details.
            final TwitterUtils.TwitterDetails td = TwitterUtils.getTwitterHandle(false);
            final TwitterUtils.MastodonDetails md = TwitterUtils.getMastodonDetails();

            // Update the HTML page.
//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");
            try
                {
                FUELINSTUtils.updateHTMLFile(startTime, outputHTMLFileName, summary24h, summary7d,
                    isDataStale, hourOfDayHistorical, status, td);
                }
            catch(final IOException e) { e.printStackTrace(); }
System.out.println("INFO: doTrafficLights(): CHECKPOINT: HTML page written: timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");
//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");


            // Update the XML data dump.
            if(GENERATE_XML_DATA_FILE)
	            {
	            try
	                {
	                final String outputXMLFileName = (-1 != lastDot) ? (outputHTMLFileName.substring(0, lastDot) + ".xml") :
	                    (outputHTMLFileName + ".xml");
	                if(null != outputXMLFileName)
	                    {
	                    FUELINSTUtils.updateXMLFile(startTime, outputXMLFileName, summary24h, isDataStale,
	                        hourOfDayHistorical, status);
	                    }
	                }
	            catch(final IOException e) { e.printStackTrace(); }
	            }
            
            // Update social media if set up ONCE THE HTML PAGE IS UPDATED
            // and there is a change from any previous status posted.
            // There are different messages when working from historical data
            // because real-time / live data is not available.
            // *** Only tweet/toot once the HTML is updated since the messages link to it.
            if((null != td) || (null != md))
	            {
	            // Compute name of file in which to cache last status posted to social media.
	            final File socialMediaPostStatusCacheFile = new File(
	            		(-1 != lastDot) ? (outputHTMLFileName.substring(0, lastDot) + ".postStatusCache") :
	                (outputHTMLFileName + ".postStatusCache"));
	
	            // Should an update be posted (yet)?
	        	final boolean canPost = TwitterUtils.canPostNewStatusMessage(
	            		socialMediaPostStatusCacheFile,
	                    status,
	            		false); // Log if/why a post should be deferred.
	
	        	if(canPost)
		        	{
	                final String statusMessage = FUELINSTUtils.generateSocialMediaStatus(
		                    isDataStale, statusUncapped, retailIntensity);
	
	                // Send toot, if set up...
	                // DHD20230111: time to toot ~1100ms, tweet ~800ms.
	                if(md != null)
		                {
System.out.println("INFO: sending toot...");
						taskTootSend = executor.submit(() ->
						    { return(TwitterUtils.timeSetMastodonStatus(md, statusMessage)); });
		                }
	                
	                // Send tweet, if set up...
		            if(td != null)
		                {
System.out.println("INFO: sending tweet...");
		                taskTweetSend = executor.submit(() ->
		                	{ return(TwitterUtils.timeSetTwitterStatus(td, statusMessage)); });
		                }
	
		            // Assume that status updates will almost always succeed.
		            // And that it is not a disaster if an update (silently) fails...
		            // Cache the new status as plain ASCII "RED" or "YELLOW" or "GREEN".
		            // Only do this for non-null statuses.
		            if(null != status)
		                {
		            	try { DataUtils.replacePublishedFile(socialMediaPostStatusCacheFile.getPath(), status.toString().getBytes(StandardCharsets.US_ASCII), false); }
		                catch(final Exception e) { e.printStackTrace(); /* Absorb errors for robustness but whinge. */ }
		                }
		        	}
	            }
            }


        // Wait for/reap any side tasks.
//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");
        if(null != taskLongStoreSave)
	    	{
        	if(!taskLongStoreSave.isDone()) { System.out.println("INFO: long store still running: "+(System.currentTimeMillis()-startTime)+"ms."); }
	    	try {
	        	final Long lsT = taskLongStoreSave.get();
	        	System.out.println("INFO: long store save in "+lsT+"ms.");
	        	}
	        catch(final ExecutionException|InterruptedException e)
		        {
	        	System.err.println("ERROR: could not update/save long store "+longStoreFile+" error: " + e.getMessage());
		        }
	    	}
        if(null != taskIntensityLogUpdate)
	    	{
	    	try {
	        	final Long ilT = taskIntensityLogUpdate.get();
	        	System.out.println("INFO: intensity log update in "+ilT+"ms.");
	        	}
	        catch(final ExecutionException|InterruptedException e)
		        {
	        	System.err.println("ERROR: could not update intensity log, error: " + e.getMessage());
		        }
	    	}
        if(null != taskIntensityFiles)
	    	{
	    	try {
	        	final Long ifT = taskIntensityFiles.get();
	        	System.out.println("INFO: intensity files save in "+ifT+"ms.");
	        	}
	        catch(final ExecutionException|InterruptedException e)
		        {
	        	System.err.println("ERROR: could not update/save intensity files, error: " + e.getMessage());
		        }
	    	}
        if(null != taskXHTMLsave)
	    	{
	    	try {
	        	final Long xhT = taskXHTMLsave.get();
	        	System.out.println("INFO: XHTML generate and save in "+xhT+"ms.");
	    	    }
	        catch(final ExecutionException|InterruptedException e)
		        {
	        	System.err.println("ERROR: could not generate/save XHTML, error: " + e.getMessage());
		        }
	    	}
        if(null != taskTweetSend)
	    	{
        	if(!taskTweetSend.isDone()) { System.out.println("INFO: tweet still running: "+(System.currentTimeMillis()-startTime)+"ms."); }
	    	try {
	        	final Long tsT = taskTweetSend.get();
	        	System.out.println("INFO: tweet sent in "+tsT+"ms.");
	        	}
	        catch(final ExecutionException|InterruptedException e)
		        {
	        	System.err.println("ERROR: could not send tweet: " + e.getMessage());
		        }
	    	}
        if(null != taskTootSend)
	    	{
        	if(!taskTootSend.isDone()) { System.out.println("INFO: toot still running: "+(System.currentTimeMillis()-startTime)+"ms."); }
	    	try {
	        	final Long tsT = taskTootSend.get();
	        	System.out.println("INFO: toot sent in "+tsT+"ms.");
	        	}
	        catch(final ExecutionException|InterruptedException e)
		        {
	        	System.err.println("ERROR: could not send toot: " + e.getMessage());
		        }
	    	}
        if(null != taskButtons)
            {
        	if(!taskButtons.isDone()) { System.out.println("INFO: buttons draw and save still running: "+(System.currentTimeMillis()-startTime)+"ms."); }
	    	try {
	        	final Long bT = taskButtons.get();
	        	System.out.println("INFO: buttons draw and save in "+bT+"ms.");
	        	}
	        catch(final ExecutionException|InterruptedException e)
		        {
	        	System.err.println("ERROR: could not create/save buttons, error: " + e.getMessage());
		        }
	    	}
//System.out.println("INFO: doTrafficLights(): timestamp: "+(System.currentTimeMillis()-startTime)+"ms.");

        // Shut down the thread pool, completing any running task(s).
        // TODO: should probably be part of a finally for robustness.
        executor.shutdown();
        try { executor.awaitTermination(10, TimeUnit.SECONDS); }
        catch (InterruptedException e) { e.printStackTrace(); }

        final long endTime = System.currentTimeMillis();
System.out.println("INFO: doTrafficLights(): "+(endTime-startTime)+"ms.");
        }


    /**First (comment) line of retail intensity log. */
    public static final String RETAIL_INTENSITY_LOG_HEADER_LINE_1 = "# Retail GB electricity carbon intensity as computed by earth.org.uk.";
    /**Second (comment) line of retail intensity log. */
    public static final String RETAIL_INTENSITY_LOG_HEADER_LINE_2 = "# Time gCO2e/kWh";
    /**Third (comment, intensities) line prefix of retail intensity log. */
    public static final String RETAIL_INTENSITY_LOG_HEADER_LINE_3_PREFIX = "# Intensities gCO2/kWh:";

    /**Append to (or create if necessary) the (retail) intensity log, US_ASCII.
     * If run more often than new data is available
     * this may produce duplicate/repeated records.
     * <p>
     * Public for testability.
     * 
     * @param id  non-null writable directory for the log file
     * @param timestamp  positive timestamp of latest available source data point
     * @param retailIntensity  non-negative retail/domestic intensity gCO2e/kWh
     * @return handle of log file, or null if none written
     */
    public static File appendToRetailIntensityLog(final File id,
    		final long timestamp,
    		final int retailIntensity)
        throws IOException
        {
        if(null == id) { throw new IllegalArgumentException(); }
        if(0 >= timestamp) { throw new IllegalArgumentException(); }
        if(0 > retailIntensity) { throw new IllegalArgumentException(); }

        // Compute the log filename.
        final SimpleDateFormat fsDF = new SimpleDateFormat(UTCDAYFILENAME_FORMAT);
        fsDF.setTimeZone(FUELINSTUtils.GMT_TIME_ZONE); // All timestamps should be GMT/UTC.
        final String dateUTC = fsDF.format(new Date(timestamp));
//System.out.println("UTC date for log: " + dateUTC);
        final File logFile = new File(id, dateUTC + ".log");
//System.out.println("Intensity log filename: " + logFile);

        // Compute the timestamp string for the log record.
        final SimpleDateFormat tsDF = new SimpleDateFormat(UTCMINTIMESTAMP_FORMAT);
        tsDF.setTimeZone(FUELINSTUtils.GMT_TIME_ZONE); // All timestamps should be GMT/UTC.
        final String timestampUTC = tsDF.format(new Date(timestamp));

        // Refuse to write to a log other than today's for safety.
        // This may possibly wrongly drop records at either end of the day.
        final String todayDateUTC = fsDF.format(new Date());
        if(!dateUTC.equals(todayDateUTC))
            {
        	System.err.println("WARNING: will not write to intensity log for "+dateUTC+" ("+timestampUTC+") at "+(new Date()));
        	return(null);
            }

        // If multiple copies of this code run at once
        // then there may be a race creating/updating the file.
        // This especially applies to the header(s).
        final boolean logFileExists = logFile.exists();
        try(PrintWriter pw = new PrintWriter(
        	    new BufferedWriter(new FileWriter(logFile, StandardCharsets.US_ASCII, true))))
	        {
        	// Write a header if the file was new.
	        if(!logFileExists)
	            {
	        	pw.println(RETAIL_INTENSITY_LOG_HEADER_LINE_1);
	        	pw.println("# Time gCO2e/kWh");
	        	// DHD20211031: write out intensities based on today's year (parsed for consistency!)
	        	final Map<String, Float> configuredIntensities = getConfiguredIntensities(Integer.parseInt(todayDateUTC.substring(0, 4)));
	        	final SortedSet<String> fuels = new TreeSet<String>(configuredIntensities.keySet());
	        	final StringBuilder isb = new StringBuilder(RETAIL_INTENSITY_LOG_HEADER_LINE_3_PREFIX.length() + 16*fuels.size());
	        	isb.append(RETAIL_INTENSITY_LOG_HEADER_LINE_3_PREFIX);
	        	for(final String f : fuels)
	        	    { isb.append(" "+f+"="+(Math.round(1000*configuredIntensities.get(f)))); }
	        	pw.println(isb);
//System.err.println("isb: " + isb);
	        	}
	        // Append the new record <timestamp> <intensity>.
	        pw.print(timestampUTC); pw.print(' '); pw.println(retailIntensity);
	        }
        // Attempt to ensure that the log file is readable by all.
        logFile.setReadable(true, false);

        return(logFile);
	    }

	/**Base directory for embeddable intensity buttons/icons; not null.
     * Under 'out' directory of suitable vintage to get correct expiry.
     */
    private static final String DEFAULT_BUTTON_BASE_DIR = "../out/hourly/button/";

    /**Base directory for log of integer gCO2e/kWh intensity values; not null.
     * Under 'data' directory.
     * Intensity values are 'retail', ie as at a typical domestic consumer,
     * after transmission and distribution losses, based on non-embedded
     * generation seen on the GB national grid.
     * 
     * The log is line-oriented with lines of the form (no leading spaces)
     *     [ISO8601UTCSTAMPTOMIN] [kgCO2e/kWh]
     * ie two space-separated columns, eg:
     *     # Other comment and one-of-data here.
     *     # Time gCO2e/kWh
     *     2019-11-17T16:02Z 352
     *     2019-11-17T16:12Z 351
     *     
     * Initial lines may be headers, starting with # in in column 1,
     * and may be ignored for data purposes.
     * 
     * This may contain repeat records if data is sampled more often
     * than it is updated at the source.
     * 
     * Records will not be generated when data is 'stale',
     * ie when fresh data is not available from the source.
     * 
     * Log files will be named with the form YYYYMMDD.log
     * eg 20191117.log.
     */
    private static final String DEFAULT_INTENSITY_LOG_BASE_DIR = "../data/FUELINST/log/live/";

    /**Generate the text of the social media status/post, eg for Twitter.
     * Public to allow testing that returned Tweets are always valid.
     *
     * @param isDataStale  true if working on historical/predicted (non-live) data
     * @param statusUncapped  the uncapped current or predicted status; never null
     * @param retailIntensity  intensity in gCO2/kWh as seen by retail customer, non-negative
     * @return human-readable valid Tweet message; non-null
     */
    public static String generateSocialMediaStatus(
    		final boolean isDataStale,
            final TrafficLight statusUncapped,
            final int retailIntensity)
        {
        if(null == statusUncapped) { throw new IllegalArgumentException(); }
        final String statusTemplate = MainProperties.getRawProperties().get((isDataStale ? TwitterUtils.PNAME_PREFIX_TWITTER_TRAFFICLIGHT_PREDICTION_MESSAGES : TwitterUtils.PNAME_PREFIX_TWITTER_TRAFFICLIGHT_STATUS_MESSAGES) + statusUncapped);
        final String tweetMessage = ((statusTemplate != null) && !statusTemplate.isEmpty()) ? String.format(statusTemplate, retailIntensity).trim() :
            ("Grid status " + statusUncapped);
        return(tweetMessage);
        }

    /**Extract (immutable) intensity map from configuration information; never null but may be empty.
     * @return map from fuel name to kgCO2/kWh non-negative intensity; never null
     */
    public static Map<String, String> getConfiguredFuelNames()
        {
        final Map<String, String> result = new HashMap<String, String>();

        // Have to scan through all keys, which may be inefficient...
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        for(final String key : rawProperties.keySet())
            {
            if(!key.startsWith(FUELINST.FUELNAME_INTENSITY_MAIN_PROPNAME_PREFIX)) { continue; }
            final String fuelname = key.substring(FUELINST.FUELNAME_INTENSITY_MAIN_PROPNAME_PREFIX.length());
            final String descriptiveName = rawProperties.get(key).trim();

            if(!FUEL_NAME_REGEX.matcher(fuelname).matches())
                {
            	// Stop things dead if a name is used that may break things later.
            	throw new IllegalArgumentException("Invalid 'fuel' name " + fuelname);
                }

            if(descriptiveName.isEmpty()) { continue; }
            result.put(fuelname, descriptiveName);
            }

        return(Collections.unmodifiableMap(result));
        }

    /**Extract (immutable) map from fuel category to set of fuel names; never null but may be empty.
     * The result only contains keys with non-empty fuelname sets.
     */
    public static Map<String, Set<String>> getFuelsByCategory()
        {
        final Map<String, Set<String>> result = new HashMap<String, Set<String>>();

        // Have to scan through all keys, which may be inefficient...
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        for(final String key : rawProperties.keySet())
            {
            if(!key.startsWith(FUELINST.FUELINST_MAIN_PROPPREFIX_STORAGE_TYPES)) { continue; }
            final String category = key.substring(FUELINST.FUELINST_MAIN_PROPPREFIX_STORAGE_TYPES.length());
            final String fuelnames = rawProperties.get(key).trim();
            if(fuelnames.isEmpty()) { continue; }
            final HashSet<String> fuels = new HashSet<String>(Arrays.asList(fuelnames.trim().split(",")));
            result.put(category, Collections.unmodifiableSet(fuels));
            }

        return(Collections.unmodifiableMap(result));
        }


    /**Extract (immutable) intensity map from configuration information for a given year; never null but may be empty.
     * @param year  if non-null preferred year for intensity and must be [2000,];
     *     this will use intensity values including the given year if possible,
     *     else the default as for the no-argument call
     *
     * <p>
     * A default undated form such as <code>intensity.fuel.INTEW=0.45</code> is permitted,
     * in part for backward compatibility.
     * <p>
     * Other forms allowed have a suffix of:
     * <ul>
     * <li><code>.year</code> the given year, eg <code>intensity.fuel.INTEW.2021=0.45</code></li>
     * <li>[TODO] <code>.startYear/endYear</code> in given year range, inclusive</li>
     * <li>[TODO] <code>.startYear/</code> from given year, inclusive</li>
     * <li>[TODO] <code>./endYear</code> up to given year, inclusive</li>
     * </ul>
     * Dates specified must be unique and non-overlapping,
     * and startYear must not be after endYear.
     * <p>
     * This date format is potentially partly extensible to ISO8601 including ranges.
     *
     * TODO
     * 
     * @return map from fuel name to kgCO2/kWh non-negative intensity; never null
     *
     */
    public static Map<String, Float> getConfiguredIntensities(final Integer year)
        {
        final Map<String, Float> result = new HashMap<String, Float>();

        // Have to scan through all keys, which may be inefficient...
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        for(final String key : rawProperties.keySet())
            {
            if(!key.startsWith(FUELINST.FUEL_INTENSITY_MAIN_PROPNAME_PREFIX)) { continue; }
            // Simple verification that fuel name may be valid, else reject.
            final String keytail = key.substring(FUELINST.FUEL_INTENSITY_MAIN_PROPNAME_PREFIX.length());
            if(keytail.length() < 2)
	            {
            	System.err.println("Trivially invalid fuel name " + key);
                continue;	
	            }
            
            // Extract fuel name.
            final String fuel;
            
            // Is the whole keytail an unqualified fule name (no date range).
            final boolean isUnqualified = FUELINSTUtils.FUEL_NAME_REGEX.matcher(keytail).matches();
            
            // For the case where year is null, the entire tail must be a valid fuel name.
            if(year == null)
	            {
            	if(!isUnqualified)
		            {
	            	// Cannot use unqualified entry with null argument.
	                continue;
		            }
            	fuel = keytail;
	            }
            else if(isUnqualified)   
            	{
            	// This is a default (no date-range) default value.
            	// Usable with a non-null year iff no value already captured for this fuel.
            	if(!result.containsKey(keytail)) { fuel = keytail; }
            	else { continue; } 
            	}	
            else // year != null and this is not an unqualified entry...
            	{
            	// Split key tail in two at '.'.
            	final String parts[] = keytail.split("[.]");
            	if(2 != parts.length)
	            	{
            		System.err.println("Invalid fuel intensity key " + key);
	                continue;	
	            	}
            	fuel = parts[0];
            	if(!FUELINSTUtils.FUEL_NAME_REGEX.matcher(fuel).matches())
		            {
	            	System.err.println("Invalid fuel name " + key);
	                continue;
		            }
	            final int y = year;
	            if((y < 2000) || (y >= 3000))
	                { throw new IllegalArgumentException("bad year " + y); }
	            // Deal with date range cases.
	            final int slashPos = parts[1].indexOf('/');
	            if(-1 != slashPos)
		            {
	            	// Note:
//	                assertEquals(1, "2012/".split("/").length);
//	                assertEquals("2012", "2012/".split("/")[0]);
//	                assertEquals(2, "/2012".split("/").length);
//	                assertEquals("", "/2012".split("/")[0]);
//	                assertEquals("2012", "/2012".split("/")[1]);
//	                assertEquals(2, "2011/2012".split("/").length);
//	                assertEquals("2011", "2011/2012".split("/")[0]);
//	                assertEquals("2012", "2011/2012".split("/")[1]);

	            	final String slashParts[] = parts[1].split("/");
	            	if(slashParts.length > 2)
	                    {
	                    System.err.println("Unable to parse data range for intensity value for " + key);
	                    continue;
	                    }
	                if(!"".equals(slashParts[0]) && !FUELINSTUtils.FUEL_INTENSITY_YEAR_REGEX.matcher(slashParts[0]).matches())
	                    {
	                    System.err.println("Unable to parse data range start for intensity value for " + key);
	                    continue;
	                    }
	                final short isYear = "".equals(slashParts[0]) ? 0 : Short.parseShort(slashParts[0]);
	                if(isYear > y)
		                {
		                // Range start year is after current year, so does not apply.
	                	continue;
		                }
	                
	                if(slashParts.length > 1)
		                {
		                if(!FUELINSTUtils.FUEL_INTENSITY_YEAR_REGEX.matcher(slashParts[1]).matches())
		                    {
		                    System.err.println("Unable to parse data range end for intensity value for " + key);
		                    continue;
		                    }
		                final short ieYear = Short.parseShort(slashParts[1]);
		                if(ieYear < isYear)
		                    {
		                    System.err.println("Unable to parse data range (start>end) for intensity value for " + key);
		                    continue;
		                    }
		                if(ieYear < y)
			                {
			                // Range end year is before current year, so does not apply.
		                	continue;
			                }
		                }
		            }
	            // Deal with simple fuelname.year case.
	            else if(FUELINSTUtils.FUEL_INTENSITY_YEAR_REGEX.matcher(parts[1]).matches())
	            	{
	                final short iYear = Short.parseShort(parts[1]);
	                if(iYear != y) { continue; } // Wrong year.
            	    }
	            }

            // Reject non-parseable and illegal (eg -ve) values.
            final Float intensity;
            try { intensity = new Float(rawProperties.get(key)); }
            catch(final NumberFormatException e)
                {
                System.err.println("Unable to parse kgCO2/kWh intensity value for " + key);
                continue;
                }
            if(!(intensity >= 0) || Float.isInfinite(intensity) || Float.isNaN(intensity))
                {
                System.err.println("Invalid (non-positive) kgCO2/kWh intensity value for " + key);
                continue;
                }
            result.put(fuel, intensity);
            }

        return(Collections.unmodifiableMap(result));
        }

    /**Extract (immutable) intensity map from configuration information; never null but may be empty.
     * This will use the default (eg undated) intensity value for each fuel such as
     * <code>intensity.fuel.INTEW=0.45</code>
     * else the latest-dated value.
     * 
     * @return map from each fuel name to kgCO2/kWh non-negative intensity; never null
     */
    @Deprecated
    public static Map<String, Float> getConfiguredIntensities()
        {
        return(getConfiguredIntensities(null));
        }

    /**Fall-back category to assign uncategorised fuels to; single token not null nor empty. */
    public static final String UNCATEGORISED_FUELS = "uncategorised";

    /**If true, show recent changes in intensity, though they can be very noisy. */
    private static final boolean SHOW_INTENSITY_DELTA = false;

    /**Extract fuel use (in MW) by category from the current summary given the fuels-by-category table; never null but may be empty.
     * TODO: construct 'uncategorised' component automatically
     */
    public static Map<String,Integer> getFuelMWByCategory(final Map<String,Integer> currentGenerationMWByFuel, final Map<String,Set<String>> fuelByCategory)
        {
        if(null == currentGenerationMWByFuel) { throw new IllegalArgumentException(); }
        if(null == fuelByCategory) { throw new IllegalArgumentException(); }

        final Map<String,Integer> result = new HashMap<String, Integer>((fuelByCategory.size()*2) + 3);

        // Construct each category's total generation....
        for(final Map.Entry<String, Set<String>> c : fuelByCategory.entrySet())
            {
            final String category = c.getKey();
            final Set<String> fuels = c.getValue();

            long total = 0;
            for(final String fuel : fuels)
                {
                final Integer q = currentGenerationMWByFuel.get(fuel);
                if(null == q) { System.err.println("no per-fuel MW value for "+fuel); continue; }
                if(q < 0) { throw new IllegalArgumentException("invalid negative per-fuel MW value"); }
                total += q;
                }

            // Check for overflow.
            if(total > Integer.MAX_VALUE) { throw new ArithmeticException("overflow"); }

            result.put(category, (int) total);
            }

        return(Collections.unmodifiableMap(result));
        }


    /**Get a format for the BM timestamps in at least FUELINST data; never null.
     * A returned instance is not safe to share between threads.
     */
    public static SimpleDateFormat getCSVTimestampParser()
        {
        final SimpleDateFormat sDF = new SimpleDateFormat(FUELINSTUtils.CSVTIMESTAMP_FORMAT);
        sDF.setTimeZone(FUELINSTUtils.GMT_TIME_ZONE); // All bmreports timestamps are GMT/UTC.
        return(sDF);
        }

    /**Get a format for the BM timestamps in at least FUELINST data; never null.
     * A returned instance is not safe to share between threads.
     */
    public static SimpleDateFormat getTIBCOTimestampParser()
        {
        final SimpleDateFormat sDF = new SimpleDateFormat(FUELINSTUtils.TIBCOTIMESTAMP_FORMAT);
        sDF.setTimeZone(FUELINSTUtils.GMT_TIME_ZONE); // All timestamps should be GMT/UTC.
        return(sDF);
        }

    /**Get a format compact (HH:MM) timestamps; never null.
     * A returned instance is not safe to share between threads.
     */
    public static SimpleDateFormat getHHMMTimestampParser()
        {
        final SimpleDateFormat sDF = new SimpleDateFormat(FUELINSTUtils.HHMMTIMESTAMP_FORMAT);
        sDF.setTimeZone(FUELINSTUtils.GMT_TIME_ZONE); // All timestamps should be GMT/UTC.
        return(sDF);
        }

    /**Update (atomically if possible) the HTML traffic-light page. */
    public static void updateHTMLFile(final long startTime,
                                           final String outputHTMLFileName,
                                           final FUELINST.CurrentSummary summary24h,
                                           final FUELINST.CurrentSummary summary7d,
                                           final boolean isDataStale,
                                           final int hourOfDayHistorical,
                                           final TrafficLight status,
                                           final TwitterUtils.TwitterDetails td)
        throws IOException
        {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(32768); 
        try ( final PrintWriter w = new PrintWriter(baos); )
            {
            final Map<String, String> rawProperties = MainProperties.getRawProperties();

            // Write the preamble with the status text dropped in.
            final String statusColour = (status == null) ? null : status.toString().toLowerCase();
            w.write(rawProperties.get("trafficLightPage.HTML.preamble").
                replace("<!-- STATUS -->", (status == null) ? "UNKNOWN" :
                    "<span style=\"color:"+statusColour+";background-color:black\">" + status + "</span>" + (isDataStale ? "*" : "") ));
            w.println();

            if(isDataStale)
                { w.println("<p><em>*WARNING: cannot obtain current data so this is partly based on predictions from historical data (for "+hourOfDayHistorical+":XX GMT).</em></p>"); }

            // Write out crude 'lights' with only appropriate lamp lit
            // and some appropriate text.
            final int sidePixels = GCOMP_PX_MAX; // Edge length of each 'lamp'.
            final String open = "<tr><th style=\"border:3px solid;height:"+sidePixels+"px;width:"+((3*sidePixels)/2)+"px";
            final String close = "</th></tr>";
            w.write("<div><table style=\"margin-left:auto;margin-right:auto\">");
            final String weaselWord = isDataStale ? "probably " : "";
            w.write(open+((status == TrafficLight.RED) ? ";background-color:red\">Grid carbon intensity is "+weaselWord+"high; please do not run big appliances such as a dishwasher or washing machine now if you can postpone" : "\">&nbsp;")+close);
            w.write(open+((status == TrafficLight.YELLOW) ? ";background-color:yellow\">Grid is "+weaselWord+"OK; but you could still avoid CO2 emissions by postponing running big appliances such as dishwashers or washing machines" : ((status == null) ? "\">Status is unknown" : "\">&nbsp;"))+close);
            w.write(open+((status == TrafficLight.GREEN) ? ";background-color:green\">Grid is "+weaselWord+"good; you might run major loads such as your dishwasher and/or washing machine now to minimise CO2 emissions" : "\">&nbsp;")+close);
            w.write("</table></div>");
            w.println();

            // Note very gently when the 7d status view is different.
            if(summary24h.status != summary7d.status)
	            {
                w.println("<p style=\"text-align:center\">(Over a longer period, the current status is "+summary7d.status+".)</p>");	
	            }

            // Note carbon savings that were available.
            if(summary24h.histMinIntensity < summary24h.histMaxIntensity)
                {
                w.println("<p style=\"text-align:center\">You might have saved as much as <strong style=\"font-size:xx-large\">"+FUELINSTUtils.computeVariability(summary24h.histMinIntensity, summary24h.histMaxIntensity)+"%</strong> carbon emissions by choosing the best time to run your washing and other major loads.</p>");
                }

            // Note any recent change/delta iff the data is not stale.
            if(SHOW_INTENSITY_DELTA)
                {
				if(!isDataStale)
				    {
	                if(summary24h.recentChange == TrafficLight.GREEN)
	                    { w.println("<p style=\"color:green\">Good: carbon intensity (CO2 per kWh) is currently dropping.</p>"); }
	                else if(summary24h.recentChange == TrafficLight.RED)
	                    { w.println("<p style=\"color:red\">Bad: carbon intensity (CO2 per kWh) is currently rising.</p>"); }
                    }
			    }

            w.println("<p>Latest data is from <strong>"+(new Date(summary24h.timestamp))+"</strong>. This page should be updated every few minutes: use your browser's refresh/reload button if you need to check again.</p>");

            // If we have a Twitter account set up then brag about it here,
            // but only if we believe that we actually have write access to be doing updates...
            if(td != null)
                {
                w.print("<p>Follow this grid status on Twitter <a href=\"http://twitter.com/");
                w.print(td.username);
                w.print("\">@");
                w.print(td.username);
                w.print("</a>");
                w.println(".</p>");
                }

            // A bit of explanation...
            w.println(rawProperties.get("trafficLightPage.HTML.midamble"));

            // ------------------------------------------------------
            // Now for the numbers...
            w.println("<h2>Technical Stuff</h2><p>You don't need to understand the numbers below, but some people like to see them!</p>");

            // Replace estimate of end-user intensity with recent historical mean if the data is stale.
            w.write("<p>");
                w.write(isDataStale ?
                     "Recent effective carbon intensity for a domestic user at this time of day was " :
                     "Effective grid carbon intensity for a domestic user is currently ");
                if(null != status) { w.write("<span style=\"font-size:xx-large;color:"+statusColour+";background-color:black\">"); }
                w.write(String.valueOf(Math.round((isDataStale ? summary24h.histAveIntensity : summary24h.currentIntensity) * (1 + summary24h.totalGridLosses))));
                w.write("gCO2/kWh");
                if(null != status) { w.write("</span>"); }
                w.write(" including transmission and distribution losses of ");
                w.write(String.valueOf(Math.round(100 * summary24h.totalGridLosses)));
                w.write("%.</p>");
            w.println();

            w.println("<p>Latest available grid <strong>generation</strong> carbon intensity (ignoring transmission/distribution losses) is approximately <strong>"+summary24h.currentIntensity+"gCO2/kWh</strong> at "+(new Date(summary24h.timestamp))+" over "+
                        summary24h.currentMW+"MW of generation, with a rolling average over "+((summary24h.histWindowSize+1800000) / 3600000)+"h of <strong>"+summary24h.histAveIntensity+"gCO2/kWh</strong>.</p>");
            w.println("<p>Minimum grid <strong>generation</strong> carbon intensity (ignoring transmission/distribution losses) was approximately <strong>"+summary24h.histMinIntensity+"gCO2/kWh</strong> at "+(new Date(summary24h.minIntensityRecordTimestamp))+".</p>");
            w.println("<p>Maximum grid <strong>generation</strong> carbon intensity (ignoring transmission/distribution losses) was approximately <strong>"+summary24h.histMaxIntensity+"gCO2/kWh</strong> at "+(new Date(summary24h.maxIntensityRecordTimestamp))+".</p>");
            w.println("<p>Average/mean grid <strong>generation</strong> carbon intensity (ignoring transmission/distribution losses) was approximately <strong>"+summary24h.histAveIntensity+"gCO2/kWh</strong> over the sample data set, with an effective end-user intensity including transmission and distribution losses of <strong>"+(Math.round(summary24h.histAveIntensity * (1 + summary24h.totalGridLosses)))+"gCO2/kWh</strong>.</p>");

            // Intensity (and generation) by hour of day.
            final int newSlot = FUELINST.CurrentSummary.getGMTHourOfDay(startTime);
            w.write("<div><table style=\"margin-left:auto;margin-right:auto\">");
            w.write("<tr><th colspan=\"24\">");
                w.write(isDataStale ? "Last available historical" : "Recent");
                w.write(" mean GMT hourly generation intensity gCO2/kWh (average="+summary24h.histAveIntensity+"); *now (="+summary24h.currentIntensity+")</th></tr>");
            w.write("<tr>");
            // Always start at midnight GMT if the data is stale.
            final int startSlot = isDataStale ? 0 : (1 + Math.max(0, newSlot)) % 24;
            for(int h = 0; h < 24; ++h)
                {
                final StringBuffer sbh = new StringBuffer(2);
                final int displayHourGMT = (h + startSlot) % 24;
                sbh.append(displayHourGMT);
                if(sbh.length() < 2) { sbh.insert(0, '0'); }
                if(hourOfDayHistorical == displayHourGMT) { sbh.append('*'); }
                w.write("<th style=\"border:1px solid\">"+sbh+"</th>");
                }
            w.write("</tr>");
            w.write("<tr>");
            boolean usedLessGreen = false;
            final int maxHourlyIntensity = summary24h.histAveIntensityByHourOfDay.max0();
            for(int h = 0; h < 24; ++h)
                {
                final int displayHourGMT = (h + startSlot) % 24;
                final Integer hIntensity = summary24h.histAveIntensityByHourOfDay.get(displayHourGMT);
                if((null == hIntensity) || (0 == hIntensity)) { w.write("<td></td>"); continue; /* Skip empty slot. */ }
                final TrafficLight rawHourStatus = summary24h.selectColour(hIntensity);
                // But if the colour is GREEN but we're using pumped storage
                // then switch to a paler shade instead (ie mainly green, but not fully)...
                final boolean lessGreen = ((TrafficLight.GREEN == rawHourStatus) && (summary24h.histAveStorageDrawdownByHourOfDay.get(displayHourGMT) > 0));
                if(lessGreen) { usedLessGreen = true; }
                final String barColour = lessGreen ? FUELINSTUtils.LESS_GREEN_STORAGE_DRAWDOWN :
                    rawHourStatus.toString().toLowerCase();
                final int height = (GCOMP_PX_MAX*hIntensity) / Math.max(1, maxHourlyIntensity);
                w.write("<td style=\"width:30px\"><ul class=\"barGraph\">");
                    w.write("<li style=\"background-color:"+barColour+";height:"+height+"px;left:0\">");
                    w.write(String.valueOf(hIntensity));
                    w.write("</li>");
                    w.write("</ul></td>");
                }
            w.write("</tr>");
            w.write("<tr><th colspan=\"24\">Mean GMT hourly generation GW (<span style=\"color:gray\">all</span>, <span style=\"color:green\">zero-carbon</span>)</th></tr>");
            w.write("<tr>");
            // Compute the maximum generation in any of the hourly slots
            // to give us maximum scaling of the displayed bars.
            final int maxGenerationMW = summary24h.histAveGenerationByHourOfDay.max0();
            for(int h = 0; h < 24; ++h)
                {
                final int displayHourGMT = (h + startSlot) % 24;
                final Integer hGeneration = summary24h.histAveGenerationByHourOfDay.get(displayHourGMT);
                if((null == hGeneration) || (0 == hGeneration)) { w.write("<td></td>"); continue; /* Skip empty slot. */ }
                final int height = (GCOMP_PX_MAX*hGeneration) / Math.max(1, maxGenerationMW);
                final int scaledToGW = (hGeneration + 500) / 1000;
                w.write("<td style=\"width:30px\"><ul class=\"barGraph\">");
                    w.write("<li style=\"background-color:gray;height:"+height+"px;left:0\">");
                    w.write(String.valueOf(scaledToGW));
                    w.write("</li>");
                    final int hZCGeneration = summary24h.histAveZCGenerationByHourOfDay.get0(displayHourGMT);
                    if(0 != hZCGeneration)
                        {
                        w.write("<li style=\"background-color:green;height:"+((GCOMP_PX_MAX*hZCGeneration) / Math.max(1, maxGenerationMW))+"px;left:0\">");
                        if(hZCGeneration >= (maxGenerationMW/8)) { w.write(String.valueOf((hZCGeneration + 500) / 1000)); }
                        w.write("</li>");
                        }
//                    final int hDrawdown = summary.histAveStorageDrawdownByHourOfDay.get0(displayHourGMT);
//                    if(0 != hDrawdown)
//                        {
//                        w.write("<li style=\"background-color:yellow;height:"+((GCOMP_PX_MAX*hDrawdown) / Math.max(1, maxGenerationMW))+"px;left:0px;\">");
//                        if(hDrawdown >= maxGenerationMW/8) { w.write(String.valueOf((hDrawdown + 500) / 1000)); }
//                        w.write("</li>");
//                        }
                    w.write("</ul></td>");
                }
            w.write("</tr>");
            w.write("</table></div>");
            w.println();
            // Footnotes
            if(usedLessGreen)
                { w.println("<p>Hours that are basically <span style=\"color:green\">green</span>, but in which there is draw-down from grid-connected storage with its attendant energy losses and also suggesting that little or no excess non-dispatchable generation is available, ie that are marginally green, are shaded <span style=\"color:"+FUELINSTUtils.LESS_GREEN_STORAGE_DRAWDOWN+"\">"+FUELINSTUtils.LESS_GREEN_STORAGE_DRAWDOWN+"</span>.</p>"); }

            // TODO: Show cumulative MWh and tCO2.

            if(!isDataStale)
                {
                // Show some stats only relevant for live data...

                w.write("<p>Current/latest fuel mix at ");
                    w.write(String.valueOf(new Date(summary24h.timestamp)));
                    w.write(':');
                    final SortedMap<String,Integer> power = new TreeMap<String, Integer>(summary24h.currentGenerationMWByFuelMW);
                    for(final String fuel : power.keySet())
                        {
                        w.write(' '); w.write(fuel);
                        w.write("@"+power.get(fuel)+"MW");
                        }
                    w.write(".</p>");
                w.println();

                if(summary24h.currentStorageDrawdownMW > 0)
                    {
                    w.write("<p>Current draw-down from storage is ");
                        w.write(Long.toString(summary24h.currentStorageDrawdownMW));
                        w.write("MW.</p>");
                    w.println();
                    }

                // Show fuels broken down by category, if categories are assigned.
                final Map<String, Set<String>> byCategory = getFuelsByCategory();
                if(!byCategory.isEmpty())
                    {
                    final Map<String,Integer> byCat = getFuelMWByCategory(summary24h.currentGenerationMWByFuelMW, byCategory);
                    w.write("<p>Generation by fuel category (may overlap):</p><dl>");
                    final SortedMap<String,Integer> powerbyCat = new TreeMap<String, Integer>(byCat);
                    for(final String category : powerbyCat.keySet())
                        {
                        final Integer genMW = powerbyCat.get(category);
                        final int percent = Math.round((100.0f * genMW) / Math.max(1, summary24h.currentMW));
                        w.write("<dt>"); w.write(category); w.write(" @ "); w.write(Integer.toString(percent)); w.write("%</dt>");
                        w.write("<dd>");
                        // Write MW under this category.
                        w.write(String.valueOf(genMW)); w.write("MW");
                        // Write sorted fuel list...
                        w.write(" "); w.write((new ArrayList<String>(new TreeSet<String>(byCategory.get(category)))).toString()); w.write("");
                        w.write("</dd>");
                        }
                    w.write("</dl>");
                    w.println();
                    }
                }

            final LocalDate todayUTC = LocalDate.now(ZoneOffset.UTC);
            final int intensityYear = todayUTC.getYear();
            w.write("<p>Overall generation intensity (kgCO2/kWh) computed using the following fuel year-"+intensityYear+" intensities (other fuels/sources are ignored):");
            final SortedMap<String,Float> intensities = new TreeMap<String, Float>(FUELINSTUtils.getConfiguredIntensities(intensityYear));
            for(final String fuel : intensities.keySet())
                {
                w.write(' '); w.write(fuel);
                w.write("="+intensities.get(fuel));
                }
            w.write(".</p>");
            w.println();

            w.write("<p>Rolling correlation of fuel use against grid intensity (-ve implies that this fuel reduces grid intensity for non-callable sources):");
            final SortedMap<String,Float> goodness = new TreeMap<String, Float>(summary24h.correlationIntensityToFuel);
            for(final String fuel : goodness.keySet())
                {
                w.format(" %s=%.4f", fuel, goodness.get(fuel));
                }
            w.write(".</p>");
            w.println();

            // Key for fuel names/codes if available.
            final SortedMap<String,String> fullFuelNames = new TreeMap<String,String>(FUELINSTUtils.getConfiguredFuelNames());
            if(!fullFuelNames.isEmpty())
                {
                w.write("<p>Key to fuel codes:</p><dl>");
                    for(final String fuel : fullFuelNames.keySet())
                        {
                        w.write("<dt>"); w.write(fuel); w.write("</dt>");
                        w.write("<dd>"); w.write(fullFuelNames.get(fuel)); w.write("</dd>");
                        }
                    w.write("</dl>");
                w.println();
                }

            // Some coverage information from the summaries.
            w.write("<p>(Histogram input windows: ");
            w.write(Long.toString((summary24h.histWindowSize + (1800*1000)) / (3600*1000)));
            	w.write("h, ");
            w.write(Long.toString((summary7d.histWindowSize + (1800*1000)) / (3600*1000)));
            	w.write("h");
            w.write(".)</p>");
            w.println();            

            w.println("<h3>Methodology</h3>");
            w.println(rawProperties.get("methodology.HTML"));

            w.println("<p>This page updated at "+(new Date())+"; generation time "+(System.currentTimeMillis()-startTime)+"ms.</p>");

            w.println(rawProperties.get("trafficLightPage.HTML.postamble"));
            }

        // Attempt atomic replacement of HTML page...
        DataUtils.replacePublishedFile(outputHTMLFileName, baos.toByteArray());
        }
    
    /**Update (atomically if possible) the plain-text US_ASCII bare gCO2e/kWh intensity value.
     * The file will be removed if the data is stale.
     * Predicted values are not published, only live fresh ones.
     */
    static void updateTXTFile(final long startTime,
    									final String outputTXTFileName,
    									final String content,
    									final boolean isDataStale)
        throws IOException
    	{
    	// In case of stale/missing data remove any result file.
    	if(isDataStale || (null == content) || content.isBlank())
    	    {
    		(new File(outputTXTFileName)).delete();
    		return;
    	    }

    	final ByteArrayOutputStream baos = new ByteArrayOutputStream(8);
        try ( final PrintWriter w = new PrintWriter(baos, false, StandardCharsets.US_ASCII); )
            { w.write(content); }

        // Attempt atomic replacement of HTML page...
        DataUtils.replacePublishedFile(outputTXTFileName, baos.toByteArray(), true);
    	}

    /**Update (atomically if possible) the mobile-friendly XHTML traffic-light page.
     * The generated page is designed to be very light-weight
     * and usable by a mobile phone (eg as if under the .mobi TLD).
     */
    static void updateXHTMLFile(final long startTime,
                                        final String outputXHTMLFileName,
                                        final FUELINST.CurrentSummary summary,
                                        final boolean isDataStale,
                                        final int hourOfDayHistorical,
                                        final TrafficLight status)
        throws IOException
        {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
        try ( PrintWriter w = new PrintWriter(baos); )
            {
            final Map<String, String> rawProperties = MainProperties.getRawProperties();

            w.println(rawProperties.get("trafficLightPage.XHTML.preamble"));

            w.println("<div style=\"background:"+((status == null) ? "gray" : status.toString().toLowerCase())+"\">");
            final String weaselWord = isDataStale ? "probably " : "";

            if(status == TrafficLight.RED)
                { w.println("Status RED: grid carbon intensity is "+weaselWord+"high; please do not run big appliances such as a dishwasher or washing machine now if you can postpone."); }
            else if(status == TrafficLight.GREEN)
                { w.println("Status GREEN: grid is "+weaselWord+"good; run appliances now to minimise CO2 emissions."); }
            else if(status == TrafficLight.YELLOW)
                { w.println("Status YELLOW: grid is "+weaselWord+"OK; but you could still avoid CO2 emissions by postponing running big appliances such as dishwashers or washing machines."); }
            else
                { w.println("Grid status is UNKNOWN."); }
            w.println("</div>");

            if(isDataStale)
                { w.println("<p><em>*WARNING: cannot obtain current data so this is partly based on predictions from historical data (for "+hourOfDayHistorical+":XX GMT).</em></p>"); }

            w.println("<p>This page updated at "+(new Date())+".</p>");

            w.println(rawProperties.get("trafficLightPage.XHTML.postamble"));
            }

        // Attempt atomic replacement of XHTML page (quietly)...
        DataUtils.replacePublishedFile(outputXHTMLFileName, baos.toByteArray(), true);
        }

    /**Update (atomically if possible) the XML traffic-light data dump.
     * Dumps current-year (at time call is run) fuel intensities.
     */
    public static void updateXMLFile(final long startTime,
                                           final String outputXMLFileName,
                                           final FUELINST.CurrentSummary summary,
                                           final boolean isDataStale,
                                           final int hourOfDayHistorical,
                                           final TrafficLight status)
        throws IOException
        {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(16384);
        final PrintWriter w = new PrintWriter(baos);
        try
            {
//            final Map<String, String> rawProperties = MainProperties.getRawProperties();
            w.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            w.println("<results>");

            if(isDataStale)
                { w.println("<warning>*WARNING: cannot obtain current data so this is partly based on predictions from historical data (for "+hourOfDayHistorical+":XX GMT).</warning>"); }

            w.println("<stale_data>"+isDataStale+"</stale_data>");

//            if(status == TrafficLight.RED)
//                { w.println("<status>1</status>"); }
//            else if(status == TrafficLight.YELLOW)
//                { w.println("<status>0</status>"); }
//            else if(status == TrafficLight.GREEN)
//                { w.println("<status>-1</status>"); }
            w.print("<status>");
            if(null != status) { w.print(status); }
            w.println("</status>");

            if(summary.histMinIntensity < summary.histMaxIntensity)
                { w.println("<saving>"+FUELINSTUtils.computeVariability(summary.histMinIntensity, summary.histMaxIntensity)+"</saving>"); }

            // Note any recent change/delta if the data is not stale.
            if(!isDataStale)
                {
                if(summary.recentChange == TrafficLight.GREEN)
//                    { w.println("<carbon_intensity>-1</carbon_intensity>"); }
                    { w.println("<carbon_intensity>GREEN</carbon_intensity>"); }
                else if(summary.recentChange == TrafficLight.RED)
//                    { w.println("<carbon_intensity>1</carbon_intensity>"); }
                    { w.println("<carbon_intensity>RED</carbon_intensity>"); }
                }

            w.println("<timestamp>"+summary.timestamp+"</timestamp>");

            w.write("<grid_carbon_intensity>");
            w.write(String.valueOf(Math.round((isDataStale ? summary.histAveIntensity : summary.currentIntensity) * (1 + summary.totalGridLosses))));
            w.write("</grid_carbon_intensity>");
            w.println();

            w.write("<transmission_losses>");
            w.write(String.valueOf(Math.round(100 * summary.totalGridLosses)));
            w.write("</transmission_losses>");
            w.println();

            w.println("<latest>");
            w.println("<carbon_intensity>"+ summary.currentIntensity +"</carbon_intensity>");
            w.println("<timestamp>"+ summary.timestamp +"</timestamp>");
            w.println("<generation>"+ summary.currentMW+"</generation>");
            w.println("<rolling_average_period>"+((summary.histWindowSize+1800000) / 3600000)+"</rolling_average_period>");
            w.println("<rolling_average_carbon_intensity>"+ summary.histAveIntensity+"</rolling_average_carbon_intensity>");
            w.println("</latest>");

            w.println("<minimum>");
            w.println("<carbon_intensity>"+ summary.histMinIntensity +"</carbon_intensity>");
            w.println("<timestamp>"+ summary.minIntensityRecordTimestamp +"</timestamp>");
            w.println("</minimum>");

            w.println("<maximum>");
            w.println("<carbon_intensity>"+ summary.histMaxIntensity +"</carbon_intensity>");
            w.println("<timestamp>"+ summary.maxIntensityRecordTimestamp +"</timestamp>");
            w.println("</maximum>");


            // Intensity (and generation) by hour of day.
            final int newSlot = FUELINST.CurrentSummary.getGMTHourOfDay(startTime);
            w.println("<generation_intensity>");
            w.println("<average>"+summary.histAveIntensity+"</average>");
            w.println("<current>"+summary.currentIntensity+"</current>");

            // Always start at midnight GMT if the data is stale.
            final int startSlot = isDataStale ? 0 : (1 + Math.max(0, newSlot)) % 24;
//            final int maxHourlyIntensity = summary.histAveIntensityByHourOfDay.max0();
            for(int h = 0; h < 24; ++h)
                {
                final StringBuffer sbh = new StringBuffer(2);
                final int displayHourGMT = (h + startSlot) % 24;
                sbh.append(displayHourGMT);
                if(sbh.length() < 2) { sbh.insert(0, '0'); }
                final Integer hIntensity = summary.histAveIntensityByHourOfDay.get(displayHourGMT);

                w.println("<sample>");
                w.println("<hour>"+sbh+"</hour>");
                w.println("<carbon_intensity>");
                if((null == hIntensity) || (0 == hIntensity))
                    { /* Empty slot. */ }
                else
                    { w.println(String.valueOf(hIntensity)); }
                w.println("</carbon_intensity>");
                w.println("</sample>");
                }
            w.println("</generation_intensity>");

            w.println("<generation>");
            // Compute the maximum generation in any of the hourly slots
            // to give us maximum scaling of the displayed bars.
            final int maxGenerationMW = summary.histAveGenerationByHourOfDay.max0();
            for(int h = 0; h < 24; ++h)
                {
                final int displayHourGMT = (h + startSlot) % 24;
                final StringBuffer sbh = new StringBuffer(2);
                sbh.append(displayHourGMT);
                if(sbh.length() < 2) { sbh.insert(0, '0'); }

                final Integer hGeneration = summary.histAveGenerationByHourOfDay.get(displayHourGMT);
                if((null == hGeneration) || (0 == hGeneration)) { continue; /* Skip empty slot. */ }
//                final int height = (GCOMP_PX_MAX*hGeneration) / Math.max(1, maxGenerationMW);
                final int scaledToGW = (hGeneration + 500) / 1000;

                w.println("<sample>");
                w.println("<hour>"+sbh+"</hour>");
                w.println("<all>"+String.valueOf(scaledToGW)+"</all>");

                final int hZCGeneration = summary.histAveZCGenerationByHourOfDay.get0(displayHourGMT);
                if(0 != hZCGeneration)
                    {
                    if(hZCGeneration >= (maxGenerationMW/8)) { w.println("<zero_carbon>"+String.valueOf((hZCGeneration + 500) / 1000)+"</zero_carbon>"); }
                    }
                w.println("</sample>");
                }
            w.println("</generation>");

            // TODO: Show cumulative MWh and tCO2.

            // FIXME: DHD20090608: I suggest leaving the fuel names as-is (upper case) in the XML as those are the 'formal' Elexon names; convert for display if need be.
            // FIXME: DHD20090608: As fuel names may not always be XML-token-safe, maybe <fuel name="NNN">amount</fuel> would be better?

            if(!isDataStale)
                {
                w.println("<fuel_mix>");
                w.println("<timestamp>"+summary.timestamp+"</timestamp>");
                final SortedMap<String,Integer> power = new TreeMap<String, Integer>(summary.currentGenerationMWByFuelMW);
                for(final String fuel : power.keySet()) { w.println("<"+fuel.toLowerCase()+">"+power.get(fuel)+"</"+fuel.toLowerCase()+">"); }
                w.println("</fuel_mix>");
                }

            w.println("<fuel_intensities>");
            w.println("<timestamp>"+summary.timestamp+"</timestamp>");
            // Note: current-year intensities are used.
            final LocalDate todayUTC = LocalDate.now(ZoneOffset.UTC);
            final int intensityYear = todayUTC.getYear();
            final SortedMap<String,Float> intensities = new TreeMap<String, Float>(FUELINSTUtils.getConfiguredIntensities(intensityYear));
            for(final String fuel : intensities.keySet()) { w.println("<"+fuel.toLowerCase()+">"+intensities.get(fuel)+"</"+fuel.toLowerCase()+">"); }
            w.println("</fuel_intensities>");

            w.println("</results>");

            w.flush();
            }
        finally { w.close(); /* Ensure file is flushed/closed.  Release resources. */ }

        // Attempt atomic replacement of XML page...
        DataUtils.replacePublishedFile(outputXMLFileName, baos.toByteArray());
        }
    }

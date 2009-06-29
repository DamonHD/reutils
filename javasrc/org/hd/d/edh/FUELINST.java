/*
Copyright (c) 2008-2009, Damon Hart-Davis,
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**Handle FUELINST data.
 */
public final class FUELINST
    {
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

    /**Prefix in main properties of fuel intensity information; never null. */
    public static final String FUEL_INTENSITY_MAIN_PROPNAME_PREFIX = "intensity.fuel.";

    /**Extract (immutable) intensity map from configuration information; never null but may be empty.
     * @return map from fuel name to kgCO2/kWh non-negative intensity; never null
     */
    public static Map<String, Float> getConfiguredIntensities()
        {
        final Map<String, Float> result = new HashMap<String, Float>();

        // Have to scan through all keys, which may be inefficient...
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        for(final String key : rawProperties.keySet())
            {
            if(!key.startsWith(FUEL_INTENSITY_MAIN_PROPNAME_PREFIX)) { continue; }
            // TODO: verify that fuel name is all upper-case ASCII and of reasonable length, else reject.
            final String fuelname = key.substring(FUEL_INTENSITY_MAIN_PROPNAME_PREFIX.length());
            // Reject non-parsable and illegal (eg -ve) values.
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
            result.put(fuelname, intensity);
            }

        return(Collections.unmodifiableMap(result));
        }

    /**Prefix in main properties of fuel name information (for non-obvious code names); never null. */
    public static final String FUELNAME_INTENSITY_MAIN_PROPNAME_PREFIX = "intensity.fuelname.";

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
            if(!key.startsWith(FUELNAME_INTENSITY_MAIN_PROPNAME_PREFIX)) { continue; }
            final String fuelname = key.substring(FUELNAME_INTENSITY_MAIN_PROPNAME_PREFIX.length());
            final String descriptiveName = rawProperties.get(key).trim();
            if(descriptiveName.isEmpty()) { continue; }
            result.put(fuelname, descriptiveName);
            }

        return(Collections.unmodifiableMap(result));
        }

    /**Number of hours in a day. */
    public static final int HOURS_PER_DAY = 24;

    /**Immutable summary by hour (GMT) of a single Integer parameter.
     * Behaves as if a List of Integer with exactly 24 slots.
     * <p>
     * Slots with no entries return null.
     * <p>
     * Serialisable so as to be persistent;
     * not expected to have to be especially efficient or security-conscious.
     */
    public static final class SummaryByHour extends AbstractList<Integer>
                                            implements List<Integer>, Serializable
        {
        /**Unique Serialisation class ID generated by http://random&#46;hd&#46;org/. */
        private static final long serialVersionUID = -8701522495858911120L;

        /**Internal fixed-size store; never null but may contain nulls. */
        private final Integer[] data = new Integer[HOURS_PER_DAY];

        /**Fixed size. */
        public int size() { return(HOURS_PER_DAY); }

        /* (non-Javadoc)
         * @see java.util.AbstractList#get(int)
         */
        @Override public Integer get(final int index) { return(data[index]); }

        /**Construct an empty (all-nulls) instance. */
        public SummaryByHour() { }

        /**Construct an instance with data copied from the supplied List.
         * @param values  List of length exactly 24 or null for all nulls-result
         */
        public SummaryByHour(final List<Integer> values)
            {
            if(null == values) { return; }
            if(HOURS_PER_DAY != values.size()) { throw new IllegalArgumentException(); }
            for(int i = HOURS_PER_DAY; --i >= 0; )
                { data[i] = values.get(i); }
            }

        /**Returns the value at the given index, or 0 if the slot is null. */
        public int get0(final int i)
            {
            final Integer v = data[i];
            if(null == v) { return(0); }
            return(v);
            }

        /**Returns the maximum value from the data set, never negative.
         * If there are no data items at all,
         * or the maximum value present is negative,
         * then this returns zero.
         */
        public int max0()
            {
            int maxGenerationMW = 0;
            for(int h = HOURS_PER_DAY; --h >= 0; )
                {
                final Integer hGeneration = data[h];
                if(null == hGeneration) { continue; }
                if(hGeneration > maxGenerationMW) { maxGenerationMW = hGeneration; }
                }
            return(maxGenerationMW);
            }
        }

    /**Immutable summary of current/recent fuel mix.
     * Serialisable so as to be persistent;
     * not expected to have to be especially efficient or security-conscious.
     */
    public static final class CurrentSummary implements Serializable
        {
        /**Unique serialisation ID.*/
        private static final long serialVersionUID = 1L;

        /**Status good (green), yellow (neutral), red (bad), or null for unknown. */
        public final TrafficLight status;

        /**GREEN if intensity is falling, RED if rising, YELLOW if steady, null if not known.
         * Measured from most recent available samples.
         */
        public final TrafficLight recentChange;

        /**Timestamp of latest 'current' sample; 0 if none. */
        public final long timestamp;
        /**'Use by' time for this summary, after this the data may not be useable; 0 if none. */
        public final long useByTime;
        /**Current/latest MW of generation covered by this summary; non-negative. */
        public final long currentMW;
        /**Current/latest intensity of generation in gCO2/kWh (kgCO2/MWh); non-negative. */
        public final int currentIntensity;
        /**Current/latest generation (MW) by fuel type, immutable; may be empty but not null. */
        public final Map<String,Integer> currentGenerationMWByFuelMW;
        /**Current/latest storage-drawdown MW; non-negative. */
        public final long currentStorageDrawdownMW;

        /**Historical minimum intensity of generation in gCO2/kWh (kgCO2/MWh); non-negative. */
        public final int histMinIntensity;
        /**Historical average (arithmetic mean) intensity of generation in gCO2/kWh (kgCO2/MWh); non-negative. */
        public final int histAveIntensity;
        /**Historical maximum intensity of generation in gCO2/kWh (kgCO2/MWh); non-negative. */
        public final int histMaxIntensity;

        /**Timestamp of historical minimum intensity of generation in gCO2/kWh (kgCO2/MWh); non-negative. */
        public final long minIntensityRecordTimestamp;
        /**Timestamp of historical maximum intensity of generation in gCO2/kWh (kgCO2/MWh); non-negative. */
        public final long maxIntensityRecordTimestamp;

        /**Historical window in milliseconds from oldest (good) sample to latest; non-negative. */
        public final long histWindowSize;
        /**Count of good samples including current one; non-negative. */
        public final int histSamples;

        /**Lower threshold; intensities less than or equal to this are GREEN; non-negative. */
        public final int lowerThreshold;
        /**Upper threshold; intensities greater than this are RED; non-negative. */
        public final int upperThreshold;

        /**Average (arithmetic mean) intensity (gCO2/kWh) by GMT/UTC hour of day [0,23], immutable, always length 24; never null. */
        public final SummaryByHour histAveIntensityByHourOfDay;

        /**Average (arithmetic mean) generation (MW) by GMT/UTC hour of day [0,23], immutable, always length 24; never null. */
        public final SummaryByHour histAveGenerationByHourOfDay;

        /**Average (arithmetic mean) zero-carbon generation (MW) by GMT/UTC hour of day [0,23], immutable, always length 24; never null. */
        public final SummaryByHour histAveZCGenerationByHourOfDay;

        /**Average (arithmetic mean) storage-drawdown (MW) by GMT/UTC hour of day [0,23], immutable, always length 24; never null. */
        public final SummaryByHour histAveStorageDrawdownByHourOfDay;

        /**Total grid distribution and transmission losses in range [0.0,1.0]. */
        public final float totalGridLosses;

        /**Construct a default (empty) instance.
         * Could be made private an accessed via a public static field...
         */
        public CurrentSummary()
            {
            this(null, null, 0L, 0L, 0L, 0, Collections.<String,Integer>emptyMap(), 0L, 0, 0L, 0, 0, 0L, 0L, 0, 0, 0,
                    null, null, null, null,
                    0f);
            }

        /**Construct an instance.
         */
        public CurrentSummary(final TrafficLight status, final TrafficLight recentChange,
                final long timestamp,
                final long useByTime,
                final long currentMW,
                final int currentIntensity,
                final Map<String,Integer> currentGenerationMWByFuelMW,
                final long currentStorageDrawdownMW,
                final int histMinIntensity,
                final long minIntensityRecordTimestamp,
                final int histAveIntensity,
                final int histMaxIntensity,
                final long maxIntensityRecordTimestamp, final long histWindowSize,
                final int histSamples,
                final int lowerThreshold, final int upperThreshold,
                final List<Integer> histAveIntensityByHourOfDay,
                final List<Integer> histAveGenerationByHourOfDay,
                final List<Integer> histAveZCGenerationByHourOfDay,
                final List<Integer> histAveStorageDrawdownByHourOfDay,
                final float totalGridLosses)
            {
            this.status = status;
            this.recentChange = recentChange;
            this.timestamp = timestamp;
            this.useByTime = useByTime;
            this.currentMW = currentMW;
            this.currentIntensity = currentIntensity;
            if(currentGenerationMWByFuelMW == null) { throw new IllegalArgumentException(); }
            this.currentGenerationMWByFuelMW = Collections.unmodifiableMap(new HashMap<String, Integer>(currentGenerationMWByFuelMW)); // Defensive copy.
            this.currentStorageDrawdownMW = currentStorageDrawdownMW;
            this.histMinIntensity = histMinIntensity;
            this.histAveIntensity = histAveIntensity;
            this.histMaxIntensity = histMaxIntensity;
            this.minIntensityRecordTimestamp = minIntensityRecordTimestamp;
            this.maxIntensityRecordTimestamp = maxIntensityRecordTimestamp;
            this.histWindowSize = histWindowSize;
            this.histSamples = histSamples;
            if(lowerThreshold > upperThreshold) { throw new IllegalArgumentException(); }
            this.lowerThreshold = lowerThreshold;
            this.upperThreshold = upperThreshold;
            this.histAveIntensityByHourOfDay = new SummaryByHour(histAveIntensityByHourOfDay); // Defensive copy.
            this.histAveGenerationByHourOfDay = new SummaryByHour(histAveGenerationByHourOfDay); // Defensive copy.
            this.histAveZCGenerationByHourOfDay = new SummaryByHour(histAveZCGenerationByHourOfDay); // Defensive copy.
            this.histAveStorageDrawdownByHourOfDay = new SummaryByHour(histAveStorageDrawdownByHourOfDay); // Defensive copy.
            this.totalGridLosses = totalGridLosses;
            }

        /**Select the traffic-light colour for a given intensity (gCO2/kWh); never null.
         * Below the lowerThreshold is green,
         * above the upperThreshold is red,
         * otherwise yellow.
         */
        public TrafficLight selectColour(final int intensity)
            {
            if(intensity > upperThreshold) { return(TrafficLight.RED); }
            if(intensity < lowerThreshold) { return(TrafficLight.GREEN); }
            return(TrafficLight.YELLOW);
            }

        /**Get the GMT hour (ie intensity slot/index) for the given time; -1 if the input is zero.
         * If the timestamp is 0 then this routine returns -1.
         */
        public static int getGMTHourOfDay(final long time)
            {
            if(time == 0) { return(-1); }
            final Calendar cal = Calendar.getInstance(GMT_TIME_ZONE);
            cal.setTimeInMillis(time);
            return(cal.get(Calendar.HOUR_OF_DAY));
            }

        /**Generate human-readable summary of most of the data; never null. */
        @Override public String toString()
            {
            final StringBuilder sb = new StringBuilder(256);
            sb.append("FUELINST.CurrentSummary");

            sb.append(":status=").append(status);
            sb.append(":recentChange=").append(recentChange);
            sb.append(":timestamp=").append((timestamp == 0) ? 0 : (new SimpleDateFormat(CSVTIMESTAMP_FORMAT)).format(new Date(timestamp)));
            sb.append(":currentMW=").append(currentMW);
            sb.append(":currentIntensity=").append(currentIntensity);
            sb.append(":currentStorageDrawdownMW=").append(currentStorageDrawdownMW);
            sb.append(":histMinIntensity=").append(histMinIntensity);
            sb.append(":histAveIntensity=").append(histAveIntensity);
            sb.append(":histMaxIntensity=").append(histMaxIntensity);
            sb.append(":minIntensityRecordTimestamp=").append((minIntensityRecordTimestamp == 0) ? 0 : (new SimpleDateFormat(CSVTIMESTAMP_FORMAT)).format(new Date(minIntensityRecordTimestamp)));
            sb.append(":maxIntensityRecordTimestamp=").append((maxIntensityRecordTimestamp == 0) ? 0 : (new SimpleDateFormat(CSVTIMESTAMP_FORMAT)).format(new Date(maxIntensityRecordTimestamp)));
            sb.append(":histWindowSize=").append(histWindowSize);
            sb.append(":histSamples=").append(histSamples);
            sb.append(":lowerThreshold=").append(lowerThreshold);
            sb.append(":upperThreshold=").append(upperThreshold);
            sb.append(":totalGridLosses=").append(totalGridLosses);
            sb.append(":histAveIntensityByHourOfDay=");
               for(final Integer i : histAveIntensityByHourOfDay) { sb.append(i).append(","); }
               sb.deleteCharAt(sb.length() - 1);

            return(sb.toString());
            }
        }

    /**URL of source of 'current' FUELINST data (and up to 24h of history); not null.
     * May be http: or file:, for example.
     */
    public static final String FUEL_INTENSITY_MAIN_PROPNAME_CURRENT_DATA_URL = "intensity.URL.current.csv";

    /**Field names for FUELINST csv file row; not null.
     */
    public static final String FUELINST_MAIN_PROPNAME_ROW_FIELDNAMES = "intensity.csv.fueltype";

    /**Field name for maximum age (in seconds) of FUELINST data we will regard as 'current'; not null. */
    public static final String FUELINST_MAIN_PROPNAME_MAX_AGE = "timescale.intensity.max";

    /**Field name for fractional loss in local distribution in range [0.0,1.0]; not null. */
    public static final String FUELINST_MAIN_PROPNAME_MAX_DIST_LOSS = "intensity.loss.distribution";

    /**Field name for fractional loss in grid transmission in range [0.0,1.0]; not null. */
    public static final String FUELINST_MAIN_PROPNAME_MAX_TRAN_LOSS = "intensity.loss.transmission";

    /**Field name for set of 'fuel' types/names for storage sources on the grid; not null. */
    public static final String FUELINST_MAIN_PROPNAME_STORAGE_TYPES = "intensity.storageTypes";

    /**GMT TimeZone; never null.
     * Marked private because it may be mutable though we never attempt to mutate it.
     * <p>
     * We may share this (read-only) between threads.
     */
    private static final TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT");

    /**SimpleDateFormat pattern to parse CSV FUELINST timestamp down to seconds (all assumed GMT/UTC); not null.
     * Note that SimpleDateFormat is not immutable nor thread-safe.
     */
    private static final String CSVTIMESTAMP_FORMAT = "yyyyMMddHHmmss";

    /**SimpleDateFormat pattern to parse TIBCO FUELINST timestamp down to seconds (all assumed GMT/UTC); not null.
     * Example TIBCO timestamp: 2009:03:09:23:57:30:GMT
     * Note that SimpleDateFormat is not immutable nor thread-safe.
     */
    private static final String TIBCOTIMESTAMP_FORMAT = "yyyy:MM:dd:HH:mm:ss:zzz";

    /**If true, compress (GZIP) any persisted state. */
    private static final boolean GZIP_CACHE = true;

    /**Regex pattern for matching a valid fuel name (all upper-case ASCII); non-null. */
    private static final Pattern FUEL_NAME_REGEX = Pattern.compile("[A-Z]+");

    /**Compute current status of fuel intensity; never null, but may be empty/default if data not available.
     * If cacheing is enabled, then this may revert to cache in case of
     * difficulty retrieving new data.
     *
     * @param cacheFile  if non-null, file to cache (parsed) data in between calls in case of data-source problems
     * @throws IOException in case of data corruption
     */
    public static CurrentSummary computeCurrentSummary(final File cacheFile)
        throws IOException
        {
        // Get as much set up as we can before pestering the data source...
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        final String dataURL = rawProperties.get(FUEL_INTENSITY_MAIN_PROPNAME_CURRENT_DATA_URL);
        if(null == dataURL)
            { throw new IllegalStateException("Property undefined for data source URL: " + FUEL_INTENSITY_MAIN_PROPNAME_CURRENT_DATA_URL); }
        final String template = rawProperties.get(FUELINST_MAIN_PROPNAME_ROW_FIELDNAMES);
        if(null == template)
            { throw new IllegalStateException("Property undefined for FUELINST row field names: " + FUELINST_MAIN_PROPNAME_ROW_FIELDNAMES); }
        final Map<String, Float> configuredIntensities = FUELINST.getConfiguredIntensities();
        if(configuredIntensities.isEmpty())
            { throw new IllegalStateException("Properties undefined for fuel intensities: " + FUEL_INTENSITY_MAIN_PROPNAME_PREFIX + "*"); }
        final String maxIntensityAgeS = rawProperties.get(FUELINST_MAIN_PROPNAME_MAX_AGE);
        if(null == maxIntensityAgeS)
            { throw new IllegalStateException("Property undefined for FUELINST acceptable age (s): " + FUELINST_MAIN_PROPNAME_MAX_AGE); }
        final long maxIntensityAge = Math.round(1000 * Double.parseDouble(maxIntensityAgeS));
        final String distLossS = rawProperties.get(FUELINST_MAIN_PROPNAME_MAX_DIST_LOSS);
        if(null == distLossS)
            { throw new IllegalStateException("Property undefined for FUELINST distribution loss: " + FUELINST_MAIN_PROPNAME_MAX_DIST_LOSS); }
        final float distLoss = Float.parseFloat(distLossS);
        if(!(distLoss >= 0) && (distLoss <= 1))
            { throw new IllegalStateException("Bad value outside range [0.0,1.0] for FUELINST distribution loss: " + FUELINST_MAIN_PROPNAME_MAX_DIST_LOSS); }
        final String tranLossS = rawProperties.get(FUELINST_MAIN_PROPNAME_MAX_TRAN_LOSS);
        if(null == tranLossS)
            { throw new IllegalStateException("Property undefined for FUELINST transmission loss: " + FUELINST_MAIN_PROPNAME_MAX_TRAN_LOSS); }
        final float tranLoss = Float.parseFloat(tranLossS);
        if(!(tranLoss >= 0) && (tranLoss <= 1))
            { throw new IllegalStateException("Bad value outside range [0.0,1.0] for FUELINST transmission loss: " + FUELINST_MAIN_PROPNAME_MAX_TRAN_LOSS); }
        // Extract Set of zero-or-more 'storage'/'fuel' types/names.
        final String storageTypesS = rawProperties.get(FUELINST_MAIN_PROPNAME_STORAGE_TYPES);
        final Set<String> storageTypes = ((null == storageTypesS) || storageTypesS.isEmpty()) ? Collections.<String>emptySet() :
            new HashSet<String>(Arrays.asList(storageTypesS.trim().split(",")));

        final List<List<String>> parsedBMRCSV;

        // Fetch and parse the CSV file from the data source.
        // Return an empty summary instance in case of IO error here.
        URL url = null;
        try
            {
            // Set up URL connection to fetch the data.
            url = new URL(dataURL.trim()); // Trim to avoid problems with trailing whitespace...
            final URLConnection conn = url.openConnection();
            conn.setAllowUserInteraction(false);
            conn.setUseCaches(true); // Ensure that we get non-stale values each time.
            conn.setConnectTimeout(60000); // Set a long-ish connection timeout.
            conn.setReadTimeout(60000); // Set a long-ish read timeout.

            final InputStreamReader is = new InputStreamReader(conn.getInputStream());
            try { parsedBMRCSV = DataUtils.parseBMRCSV(is, null); }
            finally { is.close(); }
            }
        catch(final IOException e)
            {
            // Could not get data, so status is unknown.
            System.err.println("Could not fetch data from " + url + " error: " + e.getMessage());
            // Try to retrieve from cache...
            CurrentSummary cached = null;
            try { cached = (CurrentSummary) DataUtils.deserialiseFromFile(cacheFile, GZIP_CACHE); }
            catch(final IOException err) { /* Fall through... */ }
            catch(final Exception err) { e.printStackTrace(); }
            if(null != cached)
                {
                System.err.println("WARNING: using previous response from cache...");
                return(cached);
                }
            // Return empty place-holder value.
            return(new CurrentSummary());
            }

        final int rawRowCount = parsedBMRCSV.size();
        System.out.println("Record/row count of CSV FUELINST data: " + rawRowCount + " from source: " + url);

        // All intensity sample values from good records (assuming roughly equally spaced).
        final List<Integer> allIntensitySamples = new ArrayList<Integer>(rawRowCount);

        // Compute summary.
        final SimpleDateFormat timestampParser = getCSVTimestampParser();
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
        final int[] sampleCount = new int[HOURS_PER_DAY]; // Count of all good timestamped records.
        final long[] totalIntensityByHourOfDay = new long[HOURS_PER_DAY]; // Use long to avoid overflow if many samples.
        final long[] totalGenerationByHourOfDay = new long[HOURS_PER_DAY]; // Use long to avoid overflow if many samples.
        final long[] totalZCGenerationByHourOfDay = new long[HOURS_PER_DAY]; // Use long to avoid overflow if many samples.
        final long[] totalStorageDrawdownByHourOfDay = new long[HOURS_PER_DAY]; // Use long to avoid overflow if many samples.
        for(final List<String> row : parsedBMRCSV)
            {
            // Extract fuel values for this row and compute a weighted intensity...
            final Map<String, String> namedFields = DataUtils.extractNamedFieldsByPositionFromRow(template, row);
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
                if(!FUEL_NAME_REGEX.matcher(name).matches()) { continue; }
                // Store the MW for this fuel.
                final int fuelMW = Integer.parseInt(namedFields.get(name), 10);
                if(fuelMW < 0) { throw new IOException("Bad (-ve) fuel generation MW value: "+row); }
                thisMW += fuelMW;
                generationByFuel.put(name, fuelMW);
                // Slices of generation/demand.
                if(storageTypes.contains(name)) { thisStorageDrawdownMW += fuelMW; }
                final Float fuelInt = configuredIntensities.get(name);
                if((null != fuelInt) && (fuelInt <= 0)) { thisZCGenerationMW += fuelMW; }
                }
            // Compute weighted intensity as gCO2/kWh for simplicity of representation.
            // 'Bad' fuels such as coal are ~1000, natural gas is <400, wind and nuclear are roughly 0.
            final int weightedIntensity = Math.round(1000 * FUELINST.computeWeightedIntensity(configuredIntensities, generationByFuel, MIN_FUEL_TYPES_IN_MIX));
            // Reject bad (-ve) records.
            if(weightedIntensity < 0)
                {
                System.err.println("Skipping non-positive weighed intensity record at " + namedFields.get("timestamp"));
                continue;
                }

            allIntensitySamples.add(weightedIntensity);

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
                { System.err.println("missing FUELINST row timestamp"); }
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
                    System.err.println("Unable to parse FUELINST record timestamp " + rawTimestamp + ": " + e.getMessage());
                    }
                }

            if(weightedIntensity < minIntensity)
                { minIntensity = weightedIntensity; minIntensityRecordTimestamp = recordTimestamp; }
            if(weightedIntensity > maxIntensity)
                { maxIntensity = weightedIntensity; maxIntensityRecordTimestamp = recordTimestamp; }
            }

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
        else { System.err.println("Newest data point too old"); }

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

        // Construct summary status...
        final CurrentSummary result =
            new CurrentSummary(status, recentChange,
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
                                  tranLoss + distLoss);

        // If cacheing is enabled then persist this result, compressed.
        if(null != cacheFile)
            { DataUtils.serialiseToFile(result, cacheFile, GZIP_CACHE, true); }

        return(result);
        }

    /**Get a parser for the BM timestamps in at least FUELINST data; never null.
     * Not suitable to share between threads.
     */
    public static SimpleDateFormat getCSVTimestampParser()
        {
        final SimpleDateFormat sDF = new SimpleDateFormat(CSVTIMESTAMP_FORMAT);
        sDF.setTimeZone(GMT_TIME_ZONE); // All BM timestamps are GMT/UTC.
        return sDF;
        }

    /**Get a parser for the BM timestamps in at least FUELINST data; never null.
     * Not suitable to share between threads.
     */
    public static SimpleDateFormat getTIBCOTimestampParser()
        {
        final SimpleDateFormat sDF = new SimpleDateFormat(TIBCOTIMESTAMP_FORMAT);
        sDF.setTimeZone(GMT_TIME_ZONE); // All timestamps should be GMT/UTC.
        return sDF;
        }

    /**Longest edge of graphics building block components in pixels; strictly positive. */
    private static final int GCOMP_PX_MAX = 100;

    /**If true then when data is stale then cautiously never show a GREEN status, but YELLOW at best. */
    private static final boolean NEVER_GREEN_WHEN_STALE = true;

    /**Implement the 'traffic lights' command line option.
     */
    static void doTrafficLights(final String[] args) throws IOException
        {
        final long startTime = System.currentTimeMillis();

        System.out.println("Generating traffic-light summary "+Arrays.asList(args)+"...");

        final String outputHTMLFileName = (args.length < 2) ? null : args[1];
        final int lastDot = (outputHTMLFileName == null) ? -1 : outputHTMLFileName.lastIndexOf(".");
        // Base/prefix onto which to append specific extensions.
        final String baseFileName = (-1 == lastDot) ? outputHTMLFileName : outputHTMLFileName.substring(0, lastDot);

        final File cacheFile = (null == baseFileName) ? null : (new File(baseFileName + ".cache"));

        final CurrentSummary summary = computeCurrentSummary(cacheFile);

        // Dump a summary of the current status re fuel.
        System.out.println(summary);

        if(outputHTMLFileName != null)
            {
            // Is the data stale?
            final boolean isDataStale = summary.useByTime < startTime;

            // Status to use to drive traffic-light measure.
            // If the data is current then use the latest data point,
            // else extract a suitable historical value to use in its place.
            final int hourOfDayHistorical = CurrentSummary.getGMTHourOfDay(startTime);
            final TrafficLight statusHistorical = summary.selectColour(summary.histAveIntensityByHourOfDay.get(hourOfDayHistorical));
            final TrafficLight statusHistoricalCapped = (TrafficLight.GREEN != statusHistorical) ? statusHistorical : TrafficLight.YELLOW;
            final TrafficLight statusUncapped = (!isDataStale) ? summary.status : statusHistorical;
            final TrafficLight status = (!isDataStale) ? summary.status :
                (NEVER_GREEN_WHEN_STALE ? statusHistoricalCapped : statusHistorical);

            // Handle the flag files that can be tested by remote servers.
            try { doFlagFiles(baseFileName, status, statusUncapped, summary.currentStorageDrawdownMW); }
            catch(final IOException e) { e.printStackTrace(); }

            final TwitterUtils.TwitterDetails td = TwitterUtils.getTwitterHandle(false);

            // Update the HTML page.
            try {
                updateHTMLFile(startTime, outputHTMLFileName, summary, isDataStale,
                    hourOfDayHistorical, status, td);
                }
            catch(final IOException e) { e.printStackTrace(); }

            // Update the (mobile-friendly) XHTML page.
            try {
                final String outputXMLFileName = (-1 != lastDot) ? (outputHTMLFileName.substring(0, lastDot) + ".xml") :
                    (outputHTMLFileName + ".xml");
                if(null != outputXMLFileName)
                    {
                    updateXMLFile(startTime, outputXMLFileName, summary, isDataStale,
                        hourOfDayHistorical, status);
                    }
                }
            catch(final IOException e) { e.printStackTrace(); }

            // Update the (mobile-friendly) XHTML page.
            try {
                final String outputXHTMLFileName = (-1 != lastDot) ? (outputHTMLFileName.substring(0, lastDot) + ".xhtml") :
                    (outputHTMLFileName + ".xhtml");
                if(null != outputXHTMLFileName)
                    {
                    updateXHTMLFile(startTime, outputXHTMLFileName, summary, isDataStale,
                        hourOfDayHistorical, status);
                    }
                }
            catch(final IOException e) { e.printStackTrace(); }

            // Update Twitter if it is set up
            // and if this represents a change from the previous status.
            // We may have different messages when we're working from historical data
            // because real-time / live data is not available.
            try
                {
                if(td != null)
                    {
                    // Compute name of file in which to cache last status we sent to Twitter.
                    final String TwitterCacheFileName = (-1 != lastDot) ? (outputHTMLFileName.substring(0, lastDot) + ".twittercache") :
                        (outputHTMLFileName + ".twittercache");
                    // Attempt to update the displayed Twitter status as necessary
                    // only if we think the status changed since we last sent it
                    // and it has actually changed compared to what is at Twitter...
                    // If we can't get a hand-crafted message then we create a simple one on the fly...
                    // We use different messages for live and historical (stale) data.
                    final String tweetMessage = generateTweetMessage(isDataStale, statusUncapped);
                    TwitterUtils.setTwitterStatusIfChanged(td, new File(TwitterCacheFileName), tweetMessage);
                    }
                }
            catch(final IOException e) { e.printStackTrace(); }
            }
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
     * @throws IOException  in case of problems
     */
    private static void doFlagFiles(final String baseFileName,
            final TrafficLight statusCapped, final TrafficLight statusUncapped,
            final long currentStorageDrawdownMW)
        throws IOException
        {
        if(null == baseFileName) { return; }

        // In the absence of current data,
        // then create/clear the flag based on historical data (ie predictions) where possible.
        // The flag file has terminating extension (from final ".") replaced with ".flag".
        // (If no extension is present then ".flag" is simply appended.)
        final File outputFlagFile = new File(baseFileName + ".flag");
        final boolean basicFlagState = TrafficLight.GREEN != statusCapped;
        System.out.println("Basic flag file is " + outputFlagFile + ": " + (basicFlagState ? "set" : "clear"));
        // Remove power-low/grid-poor flag file when status is GREEN, else create it (for RED/YELLOW/unknown).
        doPublicFlagFile(outputFlagFile, basicFlagState);

        // Now deal with the flag that is prepared to make predictions from historical data,
        // ie helps to ensure that the flag will probably be cleared some time each day
        // even if our data source is unreliable.
        // When live data is available then this should be the same as the basic flag.
        final File outputPredictedFlagFile = new File(baseFileName + ".predicted.flag");
        final boolean predictedFlagState = TrafficLight.GREEN != statusUncapped;
        System.out.println("Predicted flag file is " + outputPredictedFlagFile + ": " + (predictedFlagState ? "set" : "clear"));
        // Remove power-low/grid-poor flag file when status is GREEN, else create it (for RED/YELLOW/unknown).
        doPublicFlagFile(outputPredictedFlagFile, predictedFlagState);

        // Present unless 'capped' value is green (and thus must also be from live data)
        // AND there storage is not being drawn from.
        final File outputSupergreenFlagFile = new File(baseFileName + ".supergreen.flag");
        final boolean supergreenFlagState = (basicFlagState) || (currentStorageDrawdownMW > 0);
        System.out.println("Supergreen flag file is " + outputSupergreenFlagFile + ": " + (supergreenFlagState ? "set" : "clear"));
        // Remove power-low/grid-poor flag file when status is GREEN, else create it (for RED/YELLOW/unknown).
        doPublicFlagFile(outputSupergreenFlagFile, supergreenFlagState);
        }

    /**Create/remove public (readable by everyone) flag file as needed to match required state.
     * @param outputFlagFile  flag file to create (true) or remove (false) if required; non-null
     * @param flagRequiredPresent  desired state for flag: true indicates present, false indicates absent
     * @throws IOException  in case of difficulty
     */
    private static void doPublicFlagFile(final File outputFlagFile,
            final boolean flagRequiredPresent)
        throws IOException
        {
        if(flagRequiredPresent)
            {
            if(outputFlagFile.createNewFile())
                {
                outputFlagFile.setReadable(true);
                System.out.println("Flag file created: "+outputFlagFile);
                }
            }
        else
            { if(outputFlagFile.delete()) { System.out.println("Flag file deleted: "+outputFlagFile); } }
        }

    /**Generate the text of the status Tweet.
     * Public to allow testing that returned Tweets are always valid.
     *
     * @param isDataStale  true if we are working on historical/predicted (non-live) data
     * @param statusUncapped  the uncapped current or predicted status; never null
     * @return human-readable valid Tweet message
     */
    public static String generateTweetMessage(final boolean isDataStale,
            final TrafficLight statusUncapped)
        {
        if(null == statusUncapped) { throw new IllegalArgumentException(); }
        final String statusMessage = MainProperties.getRawProperties().get((isDataStale ? TwitterUtils.PNAME_PREFIX_TWITTER_TRAFFICLIGHT_PREDICTION_MESSAGES : TwitterUtils.PNAME_PREFIX_TWITTER_TRAFFICLIGHT_STATUS_MESSAGES) + statusUncapped);
        final String tweetMessage = ((statusMessage != null) && !statusMessage.isEmpty()) ? statusMessage.trim() :
            ("Grid status " + statusUncapped);
        return(tweetMessage);
        }


    /**Update (atomically if possible) the mobile-friendly XHTML traffic-light page.
     * The generated page is designed to be very light-weight
     * and usable by a mobile phone (eg as if under the .mobi TLD).
     */
    private static void updateXHTMLFile(final long startTime,
                                        final String outputXHTMLFileName,
                                        final CurrentSummary summary,
                                        final boolean isDataStale,
                                        final int hourOfDayHistorical,
                                        final TrafficLight status)
        throws IOException
        {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
        final PrintWriter w = new PrintWriter(baos);
        try
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

            w.flush();
            }
        finally { w.close(); /* Ensure file is flushed/closed.  Release resources. */ }

        // Attempt atomic replacement of XHTML page...
        DataUtils.replacePublishedFile(outputXHTMLFileName, baos.toByteArray());
        }


    /**If the basic colour is GREEN but we're using pumped storage then we can indicate that with a yellowish green instead (ie mainly green, but not fully). */
    private static final String LESS_GREEN_STORAGE_DRAWDOWN = "olive";

    /**Update (atomically if possible) the HTML traffic-light page.
     */
    private static void updateHTMLFile(final long startTime,
                                       final String outputHTMLFileName,
                                       final CurrentSummary summary,
                                       final boolean isDataStale,
                                       final int hourOfDayHistorical,
                                       final TrafficLight status,
                                       final TwitterUtils.TwitterDetails td)
        throws IOException
        {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(16384);
        final PrintWriter w = new PrintWriter(baos);
        try
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
            final String open = "<tr><th height=\""+sidePixels+"\" width=\""+((3*sidePixels)/2)+"\"";
            final String close = "</th></tr>";
            w.write("<table border=\"3\" align=\"center\">");
            final String weaselWord = isDataStale ? "probably " : "";
            w.write(open+((status == TrafficLight.RED) ? " bgcolor=\"red\">Grid carbon intensity is "+weaselWord+"high; please do not run big appliances such as a dishwasher or washing machine now if you can postpone" : ">&nbsp;")+close);
            w.write(open+((status == TrafficLight.YELLOW) ? " bgcolor=\"yellow\">Grid is "+weaselWord+"OK; but you could still avoid CO2 emissions by postponing running big appliances such as dishwashers or washing machines" : ((status == null) ? ">Status is unknown" : ">&nbsp;"))+close);
            w.write(open+((status == TrafficLight.GREEN) ? " bgcolor=\"green\">Grid is "+weaselWord+"good; you might run major loads such as your dishwasher and/or washing machine now to minimise CO2 emissions" : ">&nbsp;")+close);
            w.write("</table>");
            w.println();

            if(summary.histMinIntensity < summary.histMaxIntensity)
                {
                w.println("<p align=\"center\">You might have saved as much as <big><big><big><strong>"+computeVariability(summary.histMinIntensity, summary.histMaxIntensity)+"%</strong></big></big></big> carbon emissions by chosing the best time to run your washing and other major loads.</p>");
                }

            // Note any recent change/delta iff the data is not stale.
            if(!isDataStale)
                {
                if(summary.recentChange == TrafficLight.GREEN)
                    { w.println("<p style=\"color: green\">Good: carbon intensity (CO2 per kWh) is currently dropping.</p>"); }
                else if(summary.recentChange == TrafficLight.RED)
                    { w.println("<p style=\"color: red\">Bad: carbon intensity (CO2 per kWh) is currently rising.</p>"); }
                }

            w.println("<p>Latest data is from <strong>"+(new Date(summary.timestamp))+"</strong>. This page should be updated every few minutes: use your browser's refresh/reload button if you need to check again.</p>");

            // If we have a Twitter account set up then brag about it here,
            // but only if we believe that we actually have write access to be doing updates...
            if(td != null)
                {
                int followers = -1;
                try { followers = td.handle.getFollowers().size(); } catch(final Exception e) { e.printStackTrace(); }
                w.print("<p>Follow this grid status on Twitter <a href=\"http://twitter.com/");
                w.print(td.username);
                w.print("\">@");
                w.print(td.username);
                w.print("</a>");
                if(followers > 1) { w.print("; "+followers+" currently following"); }
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
                w.write("<big><big><big>");
                if(null != status) { w.write("<span style=\"color:"+statusColour+";background-color:black\">"); }
                w.write(String.valueOf(Math.round((isDataStale ? summary.histAveIntensity : summary.currentIntensity) * (1 + summary.totalGridLosses))));
                w.write("gCO2/kWh");
                if(null != status) { w.write("</span>"); }
                w.write("</big></big></big>");
                w.write(" including transmission and distribution losses of ");
                w.write(String.valueOf(Math.round(100 * summary.totalGridLosses)));
                w.write("%.</p>");
            w.println();

            w.println("<p>Latest available grid <strong>generation</strong> carbon intensity (ignoring transmission/distribution losses) is approximately <strong>"+summary.currentIntensity+"gCO2/kWh</strong> at "+(new Date(summary.timestamp))+" over "+
                        summary.currentMW+"MW of generation, with a rolling average over "+((summary.histWindowSize+1800000) / 3600000)+"h of <strong>"+summary.histAveIntensity+"gCO2/kWh</strong>.</p>");
            w.println("<p>Minimum grid <strong>generation</strong> carbon intensity (ignoring transmission/distribution losses) was approximately <strong>"+summary.histMinIntensity+"gCO2/kWh</strong> at "+(new Date(summary.minIntensityRecordTimestamp))+".</p>");
            w.println("<p>Maximum grid <strong>generation</strong> carbon intensity (ignoring transmission/distribution losses) was approximately <strong>"+summary.histMaxIntensity+"gCO2/kWh</strong> at "+(new Date(summary.maxIntensityRecordTimestamp))+".</p>");
            w.println("<p>Average/mean grid <strong>generation</strong> carbon intensity (ignoring transmission/distribution losses) was approximately <strong>"+summary.histAveIntensity+"gCO2/kWh</strong> over the sample data set, with an effective end-user intensity including transmission and distribution losses of <strong>"+(Math.round(summary.histAveIntensity * (1 + summary.totalGridLosses)))+"gCO2/kWh</strong>.</p>");

            // Intensity (and generation) by hour of day.
            final int newSlot = CurrentSummary.getGMTHourOfDay(startTime);
            w.write("<table border=\"1\" align=\"center\">");
            w.write("<tr><th colspan=\"24\">");
                w.write(isDataStale ? "Last available historical" : "Recent");
                w.write(" mean GMT hourly generation intensity gCO2/kWh (average="+summary.histAveIntensity+"); *now (="+summary.currentIntensity+")</th></tr>");
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
                w.write("<th>"+sbh+"</th>");
                }
            w.write("</tr>");
            w.write("<tr>");
            boolean usedLessGreen = false;
            final int maxHourlyIntensity = summary.histAveIntensityByHourOfDay.max0();
            for(int h = 0; h < 24; ++h)
                {
                final int displayHourGMT = (h + startSlot) % 24;
                final Integer hIntensity = summary.histAveIntensityByHourOfDay.get(displayHourGMT);
                if((null == hIntensity) || (0 == hIntensity)) { w.write("<td></td>"); continue; /* Skip empty slot. */ }
                final TrafficLight rawHourStatus = summary.selectColour(hIntensity);
                // But if the colour is GREEN but we're using pumped storage
                // then switch to a paler shade instead (ie mainly green, but not fully)...
                final boolean lessGreen = ((TrafficLight.GREEN == rawHourStatus) && (summary.histAveStorageDrawdownByHourOfDay.get(displayHourGMT) > 0));
                if(lessGreen) { usedLessGreen = true; }
                final String barColour = lessGreen ? LESS_GREEN_STORAGE_DRAWDOWN :
                    rawHourStatus.toString().toLowerCase();
                final int height = (GCOMP_PX_MAX*hIntensity) / Math.max(1, maxHourlyIntensity);
                w.write("<td width=\"30\"><ul class=\"barGraph\">");
                    w.write("<li style=\"background-color:"+barColour+";height:"+height+"px;left:0px;\">");
                    w.write(String.valueOf(hIntensity));
                    w.write("</li>");
                    w.write("</ul></td>");
                }
            w.write("</tr>");
            w.write("<tr><th colspan=\"24\">Mean GMT hourly generation GW (<span style=\"color:gray\">all</span>, <span style=\"color:green\">zero-carbon</span>)</th></tr>");
            w.write("<tr>");
            // Compute the maximum generation in any of the hourly slots
            // to give us maximum scaling of the displayed bars.
            final int maxGenerationMW = summary.histAveGenerationByHourOfDay.max0();
            for(int h = 0; h < 24; ++h)
                {
                final int displayHourGMT = (h + startSlot) % 24;
                final Integer hGeneration = summary.histAveGenerationByHourOfDay.get(displayHourGMT);
                if((null == hGeneration) || (0 == hGeneration)) { w.write("<td></td>"); continue; /* Skip empty slot. */ }
                final int height = (GCOMP_PX_MAX*hGeneration) / Math.max(1, maxGenerationMW);
                final int scaledToGW = (hGeneration + 500) / 1000;
                w.write("<td width=\"30\"><ul class=\"barGraph\">");
                    w.write("<li style=\"background-color:gray;height:"+height+"px;left:0px;\">");
                    w.write(String.valueOf(scaledToGW));
                    w.write("</li>");
                    final int hZCGeneration = summary.histAveZCGenerationByHourOfDay.get0(displayHourGMT);
                    if(0 != hZCGeneration)
                        {
                        w.write("<li style=\"background-color:green;height:"+((GCOMP_PX_MAX*hZCGeneration) / Math.max(1, maxGenerationMW))+"px;left:0px;\">");
                        if(hZCGeneration >= maxGenerationMW/8) { w.write(String.valueOf((hZCGeneration + 500) / 1000)); }
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
            w.write("</table>");
            w.println();
            // Footnotes
            if(usedLessGreen)
                { w.println("<p>Hours that are basically <span style=\"color:green\">green</span>, but are using grid-connected storage with its attendant losses and suggesting that little or no excess non-dispatchable generation is available, ie that are marginally green, are shaded <span style=\"color:"+LESS_GREEN_STORAGE_DRAWDOWN+"\">"+LESS_GREEN_STORAGE_DRAWDOWN+"</span>.</p>"); }

            // TODO: Show cumulative MWh and tCO2.

            if(!isDataStale)
                {
                // Show some stats only relevant for live data...

                w.write("<p>Current/latest fuel mix at ");
                    w.write(String.valueOf(new Date(summary.timestamp)));
                    w.write(':');
                    final SortedMap<String,Integer> power = new TreeMap<String, Integer>(summary.currentGenerationMWByFuelMW);
                    for(final String fuel : power.keySet())
                        {
                        w.write(' '); w.write(fuel);
                        w.write("@"+power.get(fuel)+"MW");
                        }
                    w.write(".</p>");
                w.println();

                if(summary.currentStorageDrawdownMW > 0)
                    {
                    w.write("<p>Current draw-down from storage is ");
                        w.write(Long.toString(summary.currentStorageDrawdownMW));
                        w.write("MW.</p>");
                    w.println();
                    }
                }

            w.write("<p>Overall generation intensity (kgCO2/kWh) computed using the following fuel intensities (other fuels/sources are ignored):");
            final SortedMap<String,Float> intensities = new TreeMap<String, Float>(getConfiguredIntensities());
            for(final String fuel : intensities.keySet())
                {
                w.write(' '); w.write(fuel);
                w.write("="+intensities.get(fuel));
                }
            w.write(".</p>");
            w.println();

            // Key for fuel names/codes if available.
            final SortedMap<String,String> fullFuelNames = new TreeMap<String,String>(getConfiguredFuelNames());
            if(!fullFuelNames.isEmpty())
                {
                w.write("<p>Key to fuel types/names:<dl>");
                    for(final String fuel : fullFuelNames.keySet())
                        {
                        w.write("<dt>"); w.write(fuel); w.write("</dt>");
                        w.write("<dd>"); w.write(fullFuelNames.get(fuel)); w.write("</dd>");
                        }
                    w.write("</dl></p>");
                w.println();
                }

            w.println("<h3>Methodology</h3>");
            w.println(rawProperties.get("methodology.HTML"));

            w.println("<p>This page updated at "+(new Date())+"; generation time "+(System.currentTimeMillis()-startTime)+"ms.</p>");

            w.println(rawProperties.get("trafficLightPage.HTML.postamble"));

            w.flush();
            }
        finally { w.close(); /* Ensure file is flushed/closed.  Release resources. */ }

        // Attempt atomic replacement of HTML page...
        DataUtils.replacePublishedFile(outputHTMLFileName, baos.toByteArray());
        }
    private static void updateXMLFile(final long startTime,
                                       final String outputXMLFileName,
                                       final CurrentSummary summary,
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
                { w.println("<saving>"+computeVariability(summary.histMinIntensity, summary.histMaxIntensity)+"</saving>"); }

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
            final int newSlot = CurrentSummary.getGMTHourOfDay(startTime);
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
                if((null == hIntensity) || (0 == hIntensity))
                    { w.println("<carbon_intensity></carbon_intensity>"); continue; /* Skip empty slot. */ }
                else
                    { w.println("<carbon_intensity>"+ String.valueOf(hIntensity)+"</carbon_intensity>"); }
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
                    if(hZCGeneration >= maxGenerationMW/8) { w.println("<zero_carbon>"+String.valueOf((hZCGeneration + 500) / 1000)+"</zero_carbon>"); }
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
            final SortedMap<String,Float> intensities = new TreeMap<String, Float>(getConfiguredIntensities());
            for(final String fuel : intensities.keySet()) { w.println("<"+fuel.toLowerCase()+">"+intensities.get(fuel)+"</"+fuel.toLowerCase()+">"); }
            w.println("</fuel_intensities>");

            w.println("</results>");

            w.flush();
            }
        finally { w.close(); /* Ensure file is flushed/closed.  Release resources. */ }

        // Attempt atomic replacement of XML page...
        DataUtils.replacePublishedFile(outputXMLFileName, baos.toByteArray());
        }

    /**Compute variability % of a set as a function of its (non-negative) min and max values; always in range [0,100]. */
    private static int computeVariability(final int min, final int max)
        {
        if((min < 0) || (max < 0)) { throw new IllegalArgumentException(); }
        if(max == 0) { return(0); }
        return(100 - ((100*min)/max));
        }

    /**Compute variability % of a set as a function of its min and max values; always in range [0,100]. */
    private static int computeVariability(final List<TimestampedNonNegInt> intensities)
        {
        if(null == intensities) { throw new IllegalArgumentException(); }
        int min = Integer.MAX_VALUE;
        int max = 0;
        for(final TimestampedNonNegInt ti : intensities)
            {
            if(ti.value > max) { max = ti.value; }
            if(ti.value < min) { min = ti.value; }
            }
        return(computeVariability(min, max));
        }

    /**Immutable store of non-negative int value, eg carbon intensity (gCO2/kWh), for a specific time point.
     */
    public static final class TimestampedNonNegInt
        {
        /**Timestamp; non-zero. */
        public final long timestamp;
        /**Int value, eg intensity in gCO2/kWh; non-negative. */
        public final int value;

        /**Construct directly from numeric values.
         * @param timestamp  non-zero timestamp
         * @param value  eg intensity in gCO2/kWh, non-negative
         */
        public TimestampedNonNegInt(final long timestamp, final int value)
            {
            if(timestamp == 0) { throw new IllegalArgumentException(); }
            if(value < 0) { throw new IllegalArgumentException(); }
            this.timestamp = timestamp;
            this.value = value;
            }

        /**Construct from TIBCO-style timestamp and numeric intensity.
         * @param timestamp  TIBCO-style timestamp, never null
         * @param value  eg intensity in gCO2/kWh, non-negative
         * @throws ParseException  if the timestamp cannot be parsed
         */
        public TimestampedNonNegInt(final String timestamp, final int value)
            throws ParseException
            {
            this(getTIBCOTimestampParser().parse(timestamp).getTime(), value);
            }

        /**Render as a human-readable string; never null. */
        @Override public String toString()
            { return(String.valueOf(value) + " @ " + (new Date(timestamp))); }
        }

    /**The field name of the timestamp to use from the FUELINST message in TIBCO format; non-null.
     * The two choices are "TS" (period start?) and "TP" (period end?).
     */
    private final static String TIMESTAMP_FIELD = "TP";

    /**The field name of the fuel type from the FUELINST message in TIBCO format; non-null. */
    private final static String FUELTYPE_FIELD = "FT";

    /**The field name of the fuel generation (MW) from the FUELINST message in TIBCO format; non-null. */
    private final static String FUELGEN_FIELD = "FG";

    /**If true then reject points with too few fuel types in mix since this is likely an error. */
    private final static int MIN_FUEL_TYPES_IN_MIX = 2;

    /**Convert an in-order list of TIBCO FUELINST messages to an immutable intensities List; never null.
     * This assumes that related messages, ie with the same timestamp, are adjacent.
     * <p>
     * This implicitly assumes that samples are taken at regular intervals.
     * <p>
     * Does not attempt to alter its input.
     */
    public static List<TimestampedNonNegInt> msgsToIntensity(final List<Map<String,String>> msgs)
        throws ParseException
        {
        final Map<String, Float> configuredIntensities = FUELINST.getConfiguredIntensities();
        if(configuredIntensities.isEmpty())
            { throw new IllegalStateException("Properties undefined for fuel intensities: " + FUEL_INTENSITY_MAIN_PROPNAME_PREFIX + "*"); }

        final List<TimestampedNonNegInt> result = new ArrayList<TimestampedNonNegInt>(msgs.size() / 8);

        String prevTimestamp = ""; // Force new computation on first value...
        final Map<String, Integer> generationByFuel = new HashMap<String, Integer>(2 * configuredIntensities.size());

        for(int record = msgs.size(); --record >= 0; )
            {
            final boolean atEnd = (record == 0);
            final Map<String, String> mappings = atEnd ?  null : msgs.get(record);
            final String timestamp = atEnd ? null : mappings.get(TIMESTAMP_FIELD);
            if(!atEnd && (null == timestamp))
                { throw new ParseException("Missing timestamp field "+TIMESTAMP_FIELD, record); }
            // Time to close off previous intensity calculation
            if(atEnd || !prevTimestamp.equals(timestamp))
                {
                // Check if we had some/enough fuel values collected.
                if(generationByFuel.size() >= MIN_FUEL_TYPES_IN_MIX)
                    {
                    final int weightedIntensity = Math.round(1000 * FUELINST.computeWeightedIntensity(configuredIntensities, generationByFuel, MIN_FUEL_TYPES_IN_MIX));
                    // Check that we have a real intensity value.
                    if(weightedIntensity >= 0)
                        {
                        final TimestampedNonNegInt timestampedIntensity = new TimestampedNonNegInt(prevTimestamp, weightedIntensity);
                        result.add(timestampedIntensity);
                        }
                    }
                else if(!"".equals(prevTimestamp))
                    {
                    System.err.println("Rejecting suspicious data (fuel count "+generationByFuel.size()+") at time " + prevTimestamp);
                    }
                // Clear out old fuel values.
                generationByFuel.clear();
                // Set the new timestamp if any...
                if(!atEnd) { prevTimestamp = timestamp; }
                }

            if(!atEnd)
                {
                // Add current fuel generation mapping
                // *iff* the fuel usage is non-zero.
                final String fuelGen = mappings.get(FUELGEN_FIELD);
                if(null == fuelGen)
                    { throw new ParseException("Missing fuel generation field "+FUELGEN_FIELD, record); }
                final int fg = Integer.parseInt(fuelGen, 10); // Expect it to be an integer in the data...
                if(fg == 0) { continue; }
                if(fg < 0)
                    { throw new ParseException("Invalid (-ve) fuel generation field "+FUELGEN_FIELD, record); }
                final String fuelType = mappings.get(FUELTYPE_FIELD);
                if(null == fuelType)
                    { throw new ParseException("Missing fuel type field "+FUELTYPE_FIELD, record); }
                generationByFuel.put(fuelType, fg);
                }
            }

        return(Collections.unmodifiableList(result)); // Keep result immutable.
        }

    /**Interface to allow bucketing of data by time by various criteria.
     * Any implementation of this interface should be immutable.
     */
    public interface BucketAlg
        {
        /**Returns the human-readable title of this bucket mechanism; never null. */
        public String getTitle();

        /**Returns the bucket for this timestamp one of a small enumeration of values; never null.
         * These values should sort lexically in the order for them to be displayed.
         */
        public String getBucket(final long timestamp);

        /**Returns true if the result is of capped size however big (and over whatever period) the data set. */
        public boolean cappedSize();

        /**Returns sub-bucket to use, if any, within main bucket; null if none. */
        public BucketAlg getSubBucketAlg();
        }

    /**Class to bucket data.
     * This is thread-safe.
     */
    public static final class Bucketer
        {
        /**The bucketing algorithm; never null. */
        public final BucketAlg bucketAlg;

        /**If true then the data can not be updated, ie the data is read-only.
         * Cannot be set false once it is true.
         * <p>
         * Only accessed under the instance lock.
         */
        private boolean readOnly;

        /**The bucketed data; never null.
         * The keys are the outputs from the bucketing algorithm,
         * each mapping to a Set of all the points in that bucket.
         * <p>
         * Only accessed under the instance lock.
         */
        private final Map<String, List<TimestampedNonNegInt>> data = new HashMap<String, List<TimestampedNonNegInt>>();

        /**The sub-bucketed data; never null.
         * The primary keys are the outputs from the bucketing algorithm,
         * each mapping to a Set of all the points in that bucket.
         * <p>
         * Only accessed under the instance lock.
         */
        private final Map<String, Map<String,List<TimestampedNonNegInt>>> dataSubBuckets = new HashMap<String, Map<String,List<TimestampedNonNegInt>>>();

        public Bucketer(final BucketAlg bucketAlg)
            {
            if(null == bucketAlg) { throw new IllegalArgumentException(); }
            this.bucketAlg = bucketAlg;
            }

        /**Add a new item to the appropriate bucket (and sub-bucket where appropriate).
         * Takes the instance lock to exclude conflicting operations while working.
         */
        public synchronized void add(final TimestampedNonNegInt intensity)
            {
            // Veto any update once read-only.
            if(readOnly) { throw new IllegalStateException("data is now read-only"); }

            if(null == intensity) { throw new IllegalArgumentException(); }
            final String bucket = bucketAlg.getBucket(intensity.timestamp);
            if(!data.containsKey(bucket)) { data.put(bucket, new ArrayList<TimestampedNonNegInt>()); }
            data.get(bucket).add(intensity);

            // Sub-bucket if appropriate (iff there is a sub-bucket algorithm).
            final BucketAlg sba = bucketAlg.getSubBucketAlg();
            if(null == sba) { return; }
            final String subBucket = sba.getBucket(intensity.timestamp);
            if(!dataSubBuckets.containsKey(bucket)) { dataSubBuckets.put(bucket, new HashMap<String,List<TimestampedNonNegInt>>()); }
            final Map<String, List<TimestampedNonNegInt>> subBuckets = dataSubBuckets.get(bucket);
            if(!subBuckets.containsKey(subBucket))  { subBuckets.put(subBucket, new ArrayList<TimestampedNonNegInt>()); }
            subBuckets.get(subBucket).add(intensity);
            }

        /**Gets a read-only copy of the bucketed data; never null.
         * The keys are sorted.
         * <p>
         * Takes the instance lock to exclude conflicting operations while working.
         */
        public synchronized SortedMap<String, List<TimestampedNonNegInt>> getDataByBucket()
            {
            final SortedMap<String, List<TimestampedNonNegInt>> result = new TreeMap<String, List<TimestampedNonNegInt>>();
            for(final String key : data.keySet())
                { result.put(key, Collections.unmodifiableList(new ArrayList<TimestampedNonNegInt>(data.get(key)))); }
            return(Collections.unmodifiableSortedMap(result));
            }

        /**Gets a read-only copy of the sub-bucketed data; never null.
         * The primary and secondary keys are sorted.
         * <p>
         * Takes the instance lock to exclude conflicting operations while working.
         */
        public synchronized SortedMap<String, SortedMap<String,List<TimestampedNonNegInt>>> getSubBucketDataByBucket()
            {
            final SortedMap<String, SortedMap<String,List<TimestampedNonNegInt>>> result = new TreeMap<String, SortedMap<String,List<TimestampedNonNegInt>>>();
            for(final String key : dataSubBuckets.keySet())
                {
                final SortedMap<String,List<TimestampedNonNegInt>> subMapResult = new TreeMap<String,List<TimestampedNonNegInt>>();
                final Map<String,List<TimestampedNonNegInt>> subMap = dataSubBuckets.get(key);
                for(final String subKey : subMap.keySet())
                    { subMapResult.put(subKey, Collections.unmodifiableList(new ArrayList<TimestampedNonNegInt>(subMap.get(subKey)))); }
                result.put(key, Collections.unmodifiableSortedMap(subMapResult));
                }
            return(Collections.unmodifiableSortedMap(result));
            }

        /**If true then the data can not be updated, ie the data is read-only. */
        public synchronized boolean isReadOnly() { return(readOnly); }

        /**Once called the data cannot be updated again. */
        public synchronized void setReadOnly() { this.readOnly = true; }
        }

    /**Immutable functor to bucket by year and day-of-year (GMT), ie unique day; non-null.
     * This is suitable for looking for daily variability within other (larger) bucket sizes.
     * <p>
     * Note that this can lead to an indefinitely large number of buckets
     * so needs to be handled differently to capped-size buckets.
     */
    public static final BucketAlg BUCKET_BY_YEAR_AND_DAY_GMT = new BucketAlg(){
        public String getBucket(final long timestamp)
            {
            final Calendar c = new GregorianCalendar(GMT_TIME_ZONE);
            c.setTimeInMillis(timestamp);
            // TODO: fix for correct lexical sort...
            return(String.valueOf(c.get(Calendar.YEAR)) + "-" + String.valueOf(c.get(Calendar.DAY_OF_YEAR)));
            }
        public String getTitle() { return("Day"); }
        public boolean cappedSize() { return(false); }
        /**Sub-buckets don't really make sense for this... */
        public BucketAlg getSubBucketAlg() { return(null); }
        };

    /**Immutable functor to bucket all data into a single bucket; non-null. */
    public static final BucketAlg BUCKET_SINGLETON = new BucketAlg(){
        public String getBucket(final long timestamp) { return("ALL"); }
        public String getTitle() { return("ALL"); }
        public boolean cappedSize() { return(true); }
        public BucketAlg getSubBucketAlg() { return(BUCKET_BY_YEAR_AND_DAY_GMT); }
        };

    /**Immutable functor to bucket by week/weekend (GMT); non-null. */
    public static final BucketAlg BUCKET_BY_WEEKEND_GMT = new BucketAlg(){
        public String getBucket(final long timestamp)
            {
            final Calendar c = new GregorianCalendar(GMT_TIME_ZONE);
            c.setTimeInMillis(timestamp);
            final int d = c.get(Calendar.DAY_OF_WEEK);
            final boolean isWeekend = (d == Calendar.SATURDAY) || (d == Calendar.SUNDAY);
            return(isWeekend ? "Weekend" : "Week");
            }
        public String getTitle() { return("Week/Weekend"); }
        public boolean cappedSize() { return(true); }
        public BucketAlg getSubBucketAlg() { return(BUCKET_BY_YEAR_AND_DAY_GMT); }
        };

    /**Immutable functor to bucket by hour of day (GMT); non-null. */
    public static final BucketAlg BUCKET_BY_HOUR_GMT = new BucketAlg(){
        public String getBucket(final long timestamp)
            {
            final Calendar c = new GregorianCalendar(GMT_TIME_ZONE);
            c.setTimeInMillis(timestamp);
            final int h = c.get(Calendar.HOUR_OF_DAY);
            if(h < 10) { return("0" + h); }
            return(String.valueOf(h));
            }
        public String getTitle() { return("Hour-of-Day (GMT)"); }
        public boolean cappedSize() { return(true); }
        /**Sub-buckets don't really make sense for this... */
        public BucketAlg getSubBucketAlg() { return(null); }
        };

    /**Immutable functor to bucket by month (GMT); non-null. */
    public static final BucketAlg BUCKET_BY_MONTH_GMT = new BucketAlg(){
        public String getBucket(final long timestamp)
            {
            final Calendar c = new GregorianCalendar(GMT_TIME_ZONE);
            c.setTimeInMillis(timestamp);
            final int m = 1 + c.get(Calendar.MONTH);
            if(m < 10) { return("0" + m); }
            return(String.valueOf(m));
            }
        public String getTitle() { return("Month"); }
        public boolean cappedSize() { return(true); }
        public BucketAlg getSubBucketAlg() { return(BUCKET_BY_YEAR_AND_DAY_GMT); }
        };

    /**Immutable functor to bucket by month (GMT); non-null. */
    public static final BucketAlg BUCKET_BY_YEAR_GMT = new BucketAlg(){
        public String getBucket(final long timestamp)
            {
            final Calendar c = new GregorianCalendar(GMT_TIME_ZONE);
            c.setTimeInMillis(timestamp);
            final int m = c.get(Calendar.YEAR);
            return(String.valueOf(m));
            }
        public String getTitle() { return("Year"); }
        public boolean cappedSize() { return(true); }
        public BucketAlg getSubBucketAlg() { return(BUCKET_BY_YEAR_AND_DAY_GMT); }
        };

    /**If true then allow (discard) duplicate samples, else throw an exception and stop. */
    private static final boolean DISCARD_DUP_SAMPLES = true;

    /**Do a historical analysis of TIBCO daily data dumps.
     */
    public static void doHistoricalAnalysis(final String[] args)
        throws IOException, ParseException
        {
        final long startTime = System.currentTimeMillis();

        System.out.println("Generating historical analysis "+Arrays.asList(args)+"...");

        if(args.length < 3)
            { throw new IllegalStateException("must have output file name and at least one input file to process"); }

        final String outputHTMLFileName = (args.length < 2) ? null : args[1];

        final File first = new File(args[2]);
        if(!first.exists() || !first.canRead())
            { throw new IOException("third arg must be a readable extant directory or plain file"); }
        final boolean isFileList = first.isFile();

        // Gather file list...
        final List<File> files = new ArrayList<File>(isFileList ? args.length-2 : 64);
        if(isFileList)
            {
            files.add(first);
            for(int i = 3; i < args.length; ++i)
                { files.add(new File(args[i])); }
            }
        else
            {
            // By default accept only files ending in ".gz", else used the specified pattern.
            final Pattern acceptPattern = Pattern.compile((args.length >= 4) ? args[3] : ".*[.]gz$");
            files.addAll(Arrays.asList(first.listFiles(new FilenameFilter() {
                public boolean accept(final File dir, final String name)
                    { return(acceptPattern.matcher(name).matches()); }
                })));
            }
        // Verify that all files are superficially acceptable (normal files, readable, etc).
        for(final File f : files)
            {
            if(!f.exists() || !f.isFile() || !f.canRead() || (f.length() == 0))
                { throw new IOException("specified input file is not plain file and/or not readable: "+f); }
            }
        System.out.println("Files to process: "+files.size());

        // Gather all FUELINST messages and convert them to timestamped intensities,
        // based on the TP (end timestamp), FT (fuel type) and FG (generation MW) fields.
        final Set<String> fieldKeys = new HashSet<String>(Arrays.asList(new String[]{TIMESTAMP_FIELD,FUELTYPE_FIELD,FUELGEN_FIELD}));
        final List<TimestampedNonNegInt> intensities = new ArrayList<TimestampedNonNegInt>(512 * files.size());
        for(final File f : files)
            {
            final Reader r = new InputStreamReader(new GZIPInputStream(new FileInputStream(f)));
            try { intensities.addAll(msgsToIntensity(DataUtils.extractTIBCOMessages(r, "BMRA.SYSTEM.FUELINST", fieldKeys))); }
            finally { r.close(); /* Release resources. */ }
            }
        final int nIntensitiesRaw = intensities.size();
        System.out.println("Intensities to process: " + nIntensitiesRaw);

        if(intensities.isEmpty())
            {
            System.err.println("No intensity values to process, terminating.");
            return;
            }

        // Sort into order by timestamp only.
        Collections.sort(intensities, new Comparator<TimestampedNonNegInt>() {
            public int compare(final TimestampedNonNegInt o1, final TimestampedNonNegInt o2)
                {
                final long t1 = o1.timestamp;
                final long t2 = o2.timestamp;
                if(t1 < t2) { return(-1); }
                if(t1 > t2) { return(+1); }
                return(0);
                }
            });
        // Check for dups.
        // There shouldn't be any, but we'll remove them with a warning.
        // (This might be replayed data to fill in gaps or replace invalid items,
        // so remove the earlier more-likely-dodgy one.)
        for(int i = nIntensitiesRaw; --i > 0; )
            {
            final long ts = intensities.get(i).timestamp;
            if(ts == intensities.get(i-1).timestamp)
                {
                if(!DISCARD_DUP_SAMPLES)
                    { throw new ParseException("Duplicate timestamp "+(new Date(ts)), i); }
                else
                    {
                    System.err.println("Discarding duplicate data point at "+(new Date(ts)));
                    intensities.remove(i-1);
                    }
                }
            }
        final int nIntensities = intensities.size();

        System.out.println("Data runs from "+intensities.get(0)+" via "+intensities.get(nIntensities/2)+" to "+intensities.get(nIntensities-1)+".");

        // Prepare sets of buckets to be displayed, in order.
        final Bucketer bucketers[] =
            {
            new Bucketer(BUCKET_BY_HOUR_GMT),
            new Bucketer(BUCKET_BY_WEEKEND_GMT),
            new Bucketer(BUCKET_BY_MONTH_GMT),
            new Bucketer(BUCKET_BY_YEAR_GMT),
            new Bucketer(BUCKET_SINGLETON),
            };

        // Fill all the buckets given our full data set.
        for(final TimestampedNonNegInt ti : intensities)
            {
            for(final Bucketer b : bucketers)
                {
                b.add(ti);
                }
            }

        // Generate the HTML page...
        final Map<String, String> rawProperties = MainProperties.getRawProperties();

        final ByteArrayOutputStream baos = new ByteArrayOutputStream(16384);
        final PrintWriter w = new PrintWriter(baos);
        try
            {
            w.println(rawProperties.get("dataAnalysisPage.HTML.preamble"));

            w.println("<p>Input data runs from "+new Date(intensities.get(0).timestamp)+" to "+new Date(intensities.get(nIntensities-1).timestamp)+".</p>");

            // Display the buckets' data, in order.
            for(final Bucketer b : bucketers)
                {
                final String title = b.bucketAlg.getTitle();
                if(BUCKET_SINGLETON.getTitle().equals(title))
                    { w.println("<h3>All</h3>"); }
                else
                    { w.println("<h3>Data Analysed By " + title + "</h3>"); }
                final SortedMap<String, List<TimestampedNonNegInt>> dataByBucket = b.getDataByBucket();
                final Set<String> keys = dataByBucket.keySet();
                final int cols = keys.size();
                if(cols < 1)
                    {
                    w.println("<p>No data.</p>");
                    continue;
                    }
                w.println("<table border=\"1\" align=\"center\">");
                w.println("<tr><th>Qty</th><th colspan=\""+cols+"\">"+title+"</th></tr>");

                // First show the keys for each bucket...
                w.print("<tr><th>Bucket</th>");
                for(final String key : keys)
                    { w.print("<th>" + key + "</th>"); }
                w.println("</tr>");

                // Show the sample count in each bucket...
                w.print("<tr align=\"right\"><th align=\"center\">Sample Count</th>");
                for(final String key : keys)
                    { w.print("<td>" + dataByBucket.get(key).size() + "</td>"); }
                w.println("</tr>");

                // Show/store the max for each bucket and overall.
                final SortedMap<String, Integer> maxIntensity = new TreeMap<String, Integer>();
                int maxMax = Integer.MIN_VALUE;
                w.print("<tr align=\"right\"><th align=\"center\">Max gCO2/kWh</th>");
                for(final String key : keys)
                    {
                    final List<TimestampedNonNegInt> tis = dataByBucket.get(key);
                    int max = Integer.MIN_VALUE;
                    for(final TimestampedNonNegInt ti : tis)
                        {
                        if(ti.value > max)
                            {
                            max = ti.value;
                            if(max > maxMax) { maxMax = max; }
                            }
                        }
                    maxIntensity.put(key, max);
                    w.print("<td>" + max + "</td>");
                    }
                w.println("</tr>");

                // Show/store the mean/average for each bucket.
                w.print("<tr align=\"right\"><th align=\"center\">Mean gCO2/kWh</th>");
                final SortedMap<String, Integer>aveIntensity = new TreeMap<String, Integer>();
                for(final String key : keys)
                    {
                    final List<TimestampedNonNegInt> tis = dataByBucket.get(key);
                    final int n = tis.size();
                    long total = 0;
                    for(final TimestampedNonNegInt ti : tis)
                        { total += ti.value; }
                    final int mean = Math.round(total / (float) n);
                    aveIntensity.put(key, mean);
                    //w.print("<td>" + mean + "</td>");

                    final int height = (GCOMP_PX_MAX * mean) / maxMax;
                    w.write("<td width=\"30\"><ul class=\"barGraph\">");
                    w.write("<li style=\"background-color:gray;height:"+height+"px;left:0px;\">");
                    w.write(String.valueOf(mean));
                    w.write("</li>");
                    w.write("</ul></td>");
                    }
                w.println("</tr>");

                // Show/store the min for each bucket.
                final SortedMap<String, Integer> minIntensity = new TreeMap<String, Integer>();
                w.print("<tr align=\"right\"><th align=\"center\">Min gCO2/kWh</th>");
                for(final String key : keys)
                    {
                    final List<TimestampedNonNegInt> tis = dataByBucket.get(key);
                    int min = Integer.MAX_VALUE;
                    for(final TimestampedNonNegInt ti : tis)
                        { if(ti.value < min) { min = ti.value; } }
                    minIntensity.put(key, min);
                    w.print("<td>" + min + "</td>");
                    }
                w.println("</tr>");

                // Show the variability for each bucket.
                w.print("<tr align=\"right\"><th align=\"center\">Variability</th>");
                for(final String key : keys)
                    {
                    final int var = computeVariability(minIntensity.get(key), maxIntensity.get(key));
                    w.print("<td>" + var + "%</td>");
                    }
                w.println("</tr>");

                // If this has sub-buckets then show the mean variance (and max CO2 saving per kWh) from those sub-buckets.
                final BucketAlg subBucketAlg = b.bucketAlg.getSubBucketAlg();
                if(null != subBucketAlg)
                    {
                    final SortedMap<String, SortedMap<String, List<TimestampedNonNegInt>>> subBucketDataByBucket = b.getSubBucketDataByBucket();
                    w.print("<tr align=\"right\"><th align=\"center\">Mean variability (available CO2 savings from load-shifting) during each "+subBucketAlg.getTitle()+"</th>");
                    for(final String key : keys)
                        {
                        final SortedMap<String, List<TimestampedNonNegInt>> subBucket = subBucketDataByBucket.get(key);
                        if((null == subBucket) || subBucket.isEmpty())
                            {
                            w.print("<td></td>");
                            continue;
                            }
//System.out.println("key="+key + " keySet="+subBucket.keySet());
                        long varSum = 0;
                        for(final String subKey : subBucket.keySet())
                            {
                            varSum += computeVariability(subBucket.get(subKey));
                            }
                        w.print("<td>"+ (varSum / subBucket.size()) + "%</td>");
                        }
                    w.println("</tr>");
                    w.print("<tr align=\"right\"><th align=\"center\">Mean available CO2 savings per kWh from load-shifting, eg ~1 wash load, during each "+subBucketAlg.getTitle()+"</th>");
                    for(final String key : keys)
                        {
                        final SortedMap<String, List<TimestampedNonNegInt>> subBucket = subBucketDataByBucket.get(key);
                        if((null == subBucket) || subBucket.isEmpty())
                            {
                            w.print("<td></td>");
                            continue;
                            }
//System.out.println("key="+key + " keySet="+subBucket.keySet());
                        long maxSavingSum = 0;
                        for(final String subKey : subBucket.keySet())
                            {
                            int min = Integer.MAX_VALUE;
                            int max = 0;
                            for(final TimestampedNonNegInt ti : subBucket.get(subKey))
                                {
                                if(ti.value > max) { max = ti.value; }
                                if(ti.value < min) { min = ti.value; }
                                }
                            final int maxSaving = Math.max(0, max - min);
//System.out.println("maxSaving="+maxSaving+" max|min="+max+"|"+min + " @ subKey="+subKey);
                            maxSavingSum += maxSaving;
                            }
                        w.print("<td>"+ (maxSavingSum / subBucket.size()) + "g</td>");
                        }
                    w.println("</tr>");
                    }

                w.println("</table>");
                }

            w.println("<h3>Methodology</h3>");
            w.println(rawProperties.get("methodology.HTML"));

            w.println("<p>Report generated at "+(new Date())+", generation time "+(System.currentTimeMillis()-startTime)+"ms.</p>");

            w.println(rawProperties.get("dataAnalysisPage.HTML.postamble"));

            w.flush();
            }
        finally { w.close(); /* Ensure file is flushed/closed.  Release resources. */ }

        // Attempt atomic replacement of HTML page...
        DataUtils.replacePublishedFile(outputHTMLFileName, baos.toByteArray());

        System.out.println("Report run took "+(System.currentTimeMillis() - startTime)+"ms.");
        }
    }

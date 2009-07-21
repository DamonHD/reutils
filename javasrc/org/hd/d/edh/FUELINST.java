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

import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**Handle FUELINST data.
 */
public final class FUELINST
    {
    /**Prefix in main properties of fuel intensity information; never null. */
    public static final String FUEL_INTENSITY_MAIN_PROPNAME_PREFIX = "intensity.fuel.";

    /**Prefix in main properties of fuel name information (for non-obvious code names); never null. */
    public static final String FUELNAME_INTENSITY_MAIN_PROPNAME_PREFIX = "intensity.fuelname.";

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
    public static final String FUELINST_MAIN_PROPNAME_STORAGE_TYPES = "intensity.category.storage";

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
        private final Integer[] data = new Integer[FUELINSTUtils.HOURS_PER_DAY];

        /**Fixed size. */
        public int size() { return(FUELINSTUtils.HOURS_PER_DAY); }

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
            if(FUELINSTUtils.HOURS_PER_DAY != values.size()) { throw new IllegalArgumentException(); }
            for(int i = FUELINSTUtils.HOURS_PER_DAY; --i >= 0; )
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
            for(int h = FUELINSTUtils.HOURS_PER_DAY; --h >= 0; )
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
            final Calendar cal = Calendar.getInstance(FUELINSTUtils.GMT_TIME_ZONE);
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
            sb.append(":timestamp=").append((timestamp == 0) ? 0 : (new SimpleDateFormat(FUELINSTUtils.CSVTIMESTAMP_FORMAT)).format(new Date(timestamp)));
            sb.append(":currentMW=").append(currentMW);
            sb.append(":currentIntensity=").append(currentIntensity);
            sb.append(":currentStorageDrawdownMW=").append(currentStorageDrawdownMW);
            sb.append(":histMinIntensity=").append(histMinIntensity);
            sb.append(":histAveIntensity=").append(histAveIntensity);
            sb.append(":histMaxIntensity=").append(histMaxIntensity);
            sb.append(":minIntensityRecordTimestamp=").append((minIntensityRecordTimestamp == 0) ? 0 : (new SimpleDateFormat(FUELINSTUtils.CSVTIMESTAMP_FORMAT)).format(new Date(minIntensityRecordTimestamp)));
            sb.append(":maxIntensityRecordTimestamp=").append((maxIntensityRecordTimestamp == 0) ? 0 : (new SimpleDateFormat(FUELINSTUtils.CSVTIMESTAMP_FORMAT)).format(new Date(maxIntensityRecordTimestamp)));
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

    /**Generic interface to implement 'traffic lights' command-line option.
     * There will generally be a default implementation
     * and one or more customised implementations.
     */
    public static interface TrafficLightsInterface
        {
        /**Generates outputs from the arguments and the current summary.
         * When called from the command-line the supplied args
         * will be the trailing/optional parameters after initial parsing.
         * <p>
         * The arguments will not include any leading 'trafficLights' and class-name tag.
         *
         * @param args   arguments/parameters; never null but may be empty
         * @throws IOException  in case of difficulty generating the output(s)
         */
        public void doTrafficLights(final String[] args) throws IOException;
        }

    /**Default implementation of traffic-lights code, generating HTML, XHTML and various flags.
     */
    public static final class TrafficLightsDEFAULT implements TrafficLightsInterface
        {
        /**Generates outputs from the arguments and the current summary.
         * When called from the command-line the supplied args
         * will be the trailing/optional parameters after initial parsing.
         * <p>
         * The arguments will not include any leading 'trafficLights' and class-name tag.
         *
         * @param args   arguments/parameters; never null but may be empty
         * @throws IOException  in case of difficulty generating the output(s)
         */
        public void doTrafficLights(final String[] args)
            throws IOException
            {
            // Delegate to static utilities method!
            FUELINSTUtils.doTrafficLights(args);
            }
        }
    }

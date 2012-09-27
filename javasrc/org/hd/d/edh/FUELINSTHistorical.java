/*
Copyright (c) 2008-2012, Damon Hart-Davis,
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
import java.text.ParseException;
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
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.hd.d.edh.Tuple.Pair;

/**Handle FUELINST historical data.
 */
public final class FUELINSTHistorical
    {
    private FUELINSTHistorical() { /* Prevent creation of an instance. */ }

    /**Interface to allow bucketing of data by time by various criteria.
     * Any implementation of this interface should be immutable.
     */
    public interface BucketAlg
        {
        /**Returns true if the result is of capped size however big (and over whatever period) the data set. */
        public boolean cappedSize();

        /**Returns the bucket for this timestamp one of a small enumeration of values; never null.
         * These values should sort lexically in the order for them to be displayed.
         */
        public String getBucket(final long timestamp);

        /**Returns sub-bucket to use, if any, within main bucket; null if none. */
        public BucketAlg getSubBucketAlg();

        /**Returns the human-readable title of this bucket mechanism; never null. */
        public String getTitle();
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
        public synchronized void setReadOnly() { readOnly = true; }
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
            this(FUELINSTUtils.getTIBCOTimestampParser().parse(timestamp).getTime(), value);
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

    /**Immutable functor to bucket by year and day-of-year (GMT), ie unique day; non-null.
     * This is suitable for looking for daily variability within other (larger) bucket sizes.
     * <p>
     * Note that this can lead to an indefinitely large number of buckets
     * so needs to be handled differently to capped-size buckets.
     */
    public static final BucketAlg BUCKET_BY_YEAR_AND_DAY_GMT = new BucketAlg(){
        public boolean cappedSize() { return(false); }
        public String getBucket(final long timestamp)
            {
            final Calendar c = new GregorianCalendar(FUELINSTUtils.GMT_TIME_ZONE);
            c.setTimeInMillis(timestamp);
            // TODO: fix for correct lexical sort...
            return(String.valueOf(c.get(Calendar.YEAR)) + "-" + String.valueOf(c.get(Calendar.DAY_OF_YEAR)));
            }
        /**Sub-buckets don't really make sense for this... */
        public BucketAlg getSubBucketAlg() { return(null); }
        public String getTitle() { return("Day"); }
        };

    /**Immutable functor to bucket all data into a single bucket; non-null. */
    public static final BucketAlg BUCKET_SINGLETON = new BucketAlg(){
        public boolean cappedSize() { return(true); }
        public String getBucket(final long timestamp) { return("ALL"); }
        public BucketAlg getSubBucketAlg() { return(BUCKET_BY_YEAR_AND_DAY_GMT); }
        public String getTitle() { return("ALL"); }
        };

    /**Immutable functor to bucket by week/weekend (GMT); non-null. */
    public static final BucketAlg BUCKET_BY_WEEKEND_GMT = new BucketAlg(){
        public boolean cappedSize() { return(true); }
        public String getBucket(final long timestamp)
            {
            final Calendar c = new GregorianCalendar(FUELINSTUtils.GMT_TIME_ZONE);
            c.setTimeInMillis(timestamp);
            final int d = c.get(Calendar.DAY_OF_WEEK);
            final boolean isWeekend = (d == Calendar.SATURDAY) || (d == Calendar.SUNDAY);
            return(isWeekend ? "Weekend" : "Week");
            }
        public BucketAlg getSubBucketAlg() { return(BUCKET_BY_YEAR_AND_DAY_GMT); }
        public String getTitle() { return("Week/Weekend"); }
        };

    /**Immutable functor to bucket by hour of day (GMT); non-null. */
    public static final BucketAlg BUCKET_BY_HOUR_GMT = new BucketAlg(){
        public boolean cappedSize() { return(true); }
        public String getBucket(final long timestamp)
            {
            final Calendar c = new GregorianCalendar(FUELINSTUtils.GMT_TIME_ZONE);
            c.setTimeInMillis(timestamp);
            final int h = c.get(Calendar.HOUR_OF_DAY);
            if(h < 10) { return("0" + h); }
            return(String.valueOf(h));
            }
        /**Sub-buckets don't really make sense for this... */
        public BucketAlg getSubBucketAlg() { return(null); }
        public String getTitle() { return("Hour-of-Day (GMT)"); }
        };

    /**Immutable functor to bucket by month (GMT); non-null. */
    public static final BucketAlg BUCKET_BY_MONTH_GMT = new BucketAlg(){
        public boolean cappedSize() { return(true); }
        public String getBucket(final long timestamp)
            {
            final Calendar c = new GregorianCalendar(FUELINSTUtils.GMT_TIME_ZONE);
            c.setTimeInMillis(timestamp);
            final int m = 1 + c.get(Calendar.MONTH);
            if(m < 10) { return("0" + m); }
            return(String.valueOf(m));
            }
        public BucketAlg getSubBucketAlg() { return(BUCKET_BY_YEAR_AND_DAY_GMT); }
        public String getTitle() { return("Month"); }
        };

    /**Immutable functor to bucket by month (GMT); non-null. */
    public static final BucketAlg BUCKET_BY_YEAR_GMT = new BucketAlg(){
        public boolean cappedSize() { return(true); }
        public String getBucket(final long timestamp)
            {
            final Calendar c = new GregorianCalendar(FUELINSTUtils.GMT_TIME_ZONE);
            c.setTimeInMillis(timestamp);
            final int m = c.get(Calendar.YEAR);
            return(String.valueOf(m));
            }
        public BucketAlg getSubBucketAlg() { return(BUCKET_BY_YEAR_AND_DAY_GMT); }
        public String getTitle() { return("Year"); }
        };

    /**If true then allow (discard) duplicate samples, else throw an exception and stop. */
    private static final boolean DISCARD_DUP_SAMPLES = true;

    /**Do a historical analysis of TIBCO daily data dumps. */
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
        System.out.println("Count of files to process: "+files.size());

        // Gather all FUELINST messages and convert them to timestamped intensities,
        // based on the TP (end timestamp), FT (fuel type) and FG (generation MW) fields.
        final Set<String> fieldKeys = new HashSet<String>(Arrays.asList(new String[]{TIMESTAMP_FIELD,FUELTYPE_FIELD,FUELGEN_FIELD}));
        final List<TimestampedNonNegInt> intensities = new ArrayList<TimestampedNonNegInt>(512 * files.size());
        // Also collect underlying generation figures to be able to compute correlations.
        final Map<String, Float> configuredIntensities = FUELINSTUtils.getConfiguredIntensities();
        final Map<Long, Tuple.Pair<Map<String,Float>, Map<String,Integer>>> fuelinstCorrGrist = new HashMap<Long, Tuple.Pair<Map<String,Float>, Map<String,Integer>>>(512 * files.size());
        for(final File f : files)
            {
            final Reader r = new InputStreamReader(new GZIPInputStream(new FileInputStream(f)));
            try
                {
                final List<Pair<Long, Map<String, Integer>>> l = msgsToTimestampedGenByFuel(DataUtils.extractTIBCOMessages(r, "BMRA.SYSTEM.FUELINST", fieldKeys), true);
                intensities.addAll(timestampedGenByFuelToIntensity(l));
                for(final Pair<Long, Map<String, Integer>> entry : l)
                    { fuelinstCorrGrist.put(entry.first, new Tuple.Pair<Map<String,Float>, Map<String,Integer>>(configuredIntensities, entry.second)); }
                }
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

        // Compute per-fuel correlations to grid intensity across whole data set.
        final Tuple.Triple<Map<String,Float>, Map<String,Float>, Float> correlations = StatsUtils.computeFuelCorrelations(fuelinstCorrGrist, FUELINSTUtils.MIN_FUEL_TYPES_IN_MIX);


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

                    final int height = (FUELINSTUtils.GCOMP_PX_MAX * mean) / maxMax;
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
                    final int var = FUELINSTUtils.computeVariability(minIntensity.get(key), maxIntensity.get(key));
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
                            varSum += FUELINSTUtils.computeVariability(subBucket.get(subKey));
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

            w.write("<p>Correlation of demand against grid intensity: ");
            w.format("%.4f", correlations.third);
            w.write(".</p>");

            w.write("<p>Correlation of fuel use against demand (+ve implies that this fuel use corresponds to demand):");
            final SortedMap<String,Float> timely = new TreeMap<String, Float>(correlations.first);
            for(final String fuel : timely.keySet())
                {
                w.format(" %s=%.4f", fuel, timely.get(fuel));
                }
            w.write(".</p>");
            w.println();

            w.write("<p>Correlation of fuel use against grid intensity (-ve implies that this fuel reduces grid intensity for non-callable sources):");
            final SortedMap<String,Float> goodness = new TreeMap<String, Float>(correlations.second);
            for(final String fuel : goodness.keySet())
                {
                w.format(" %s=%.4f", fuel, goodness.get(fuel));
                }
            w.write(".</p>");
            w.println();


            w.println("<h3>Generation Fuel Intensities Used</h3>");
            final SortedMap<String,Float> fuelIntensities = new TreeMap<String, Float>(FUELINSTUtils.getConfiguredIntensities());
            for(final String fuel : fuelIntensities.keySet())
                {
                w.write(' '); w.write(fuel);
                w.write("="+fuelIntensities.get(fuel));
                }
            w.write(".</p>");
            w.println();

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

    /**Convert an in-order list of TIBCO FUELINST messages to an immutable timestamped generation-by-fuel List; never null nor containing nulls nor empty maps.
     * This assumes that related messages, ie with the same timestamp, are adjacent.
     * <p>
     * This implicitly assumes that samples are taken at regular intervals.
     * <p>
     * Does not attempt to alter its input.
     * <p>
     * Each map will have at least one fuel value (indeed, at least MIN_FUEL_TYPES_IN_MIX values, as a sanity filter).
     *
     * @param dropNonPositive  if true then ignore all non-positive FUELINST values;
     *     this results in the same behaviour as up to at least end 2011 for interconnectors which never showed negative flows
     */
    public static List<Tuple.Pair<Long, Map<String,Integer>>> msgsToTimestampedGenByFuel(final List<Map<String,String>> msgs, final boolean dropNonPositive)
        throws ParseException
        {
        final List<Tuple.Pair<Long, Map<String,Integer>>> result = new ArrayList<Tuple.Pair<Long, Map<String,Integer>>>(msgs.size() / 8);

        String prevTimestamp = ""; // Force new computation on first value...
        final Map<String, Integer> generationByFuel = new HashMap<String, Integer>(); // Re-used accumulator for current set of generation values.

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
                if(generationByFuel.size() >= FUELINSTUtils.MIN_FUEL_TYPES_IN_MIX)
                    {
                    final long ts = FUELINSTUtils.getTIBCOTimestampParser().parse(prevTimestamp).getTime();
                    final HashMap<String, Integer> e = new HashMap<String, Integer>(generationByFuel);
                    result.add(new Tuple.Pair<Long, Map<String, Integer>>(ts, Collections.unmodifiableMap(e))); // Immutable copy.
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
                // *iff* the fuel usage is non-zero/non-positive
                final String fuelGen = mappings.get(FUELGEN_FIELD);
                if(null == fuelGen)
                    { throw new ParseException("Missing fuel generation field "+FUELGEN_FIELD, record); }
                final int fg = Integer.parseInt(fuelGen, 10); // Expect it to be an integer in the data...
                if(fg == 0) { continue; }
                if(fg < 0)
                    {
                    if(dropNonPositive) { continue; }
                    throw new ParseException("Invalid (-ve) fuel generation value in field "+FUELGEN_FIELD+" in map "+record, record);
                    }
                final String fuelType = mappings.get(FUELTYPE_FIELD);
                if(null == fuelType)
                    { throw new ParseException("Missing fuel type field "+FUELTYPE_FIELD, record); }
                generationByFuel.put(fuelType, fg);
                }
            }

        return(Collections.unmodifiableList(result)); // Keep result immutable.
        }



    /**Convert an in-order list of expanded TIBCO FUELINST messages to an immutable timestamped grid-intensities List; never null.
     * This assumes that related messages, ie with the same timestamp, are adjacent.
     * <p>
     * This implicitly assumes that samples are taken at regular intervals.
     * <p>
     * Does not attempt to alter its input.
     */
    public static List<TimestampedNonNegInt> timestampedGenByFuelToIntensity(final List<Tuple.Pair<Long, Map<String,Integer>>> timestampedGenByFuel)
        throws ParseException
        {
        final Map<String, Float> configuredIntensities = FUELINSTUtils.getConfiguredIntensities();
        if(configuredIntensities.isEmpty())
            { throw new IllegalStateException("Properties undefined for fuel intensities: " + FUELINST.FUEL_INTENSITY_MAIN_PROPNAME_PREFIX + "*"); }

        final List<TimestampedNonNegInt> result = new ArrayList<TimestampedNonNegInt>(timestampedGenByFuel.size());

        for(final Tuple.Pair<Long, Map<String,Integer>> e : timestampedGenByFuel)
            {
            final int weightedIntensity = Math.round(1000 * FUELINSTUtils.computeWeightedIntensity(configuredIntensities, e.second, FUELINSTUtils.MIN_FUEL_TYPES_IN_MIX));
            // Check that we have a real intensity value.
            if(weightedIntensity >= 0)
                {
                final TimestampedNonNegInt timestampedIntensity = new TimestampedNonNegInt(e.first, weightedIntensity);
                result.add(timestampedIntensity);
                }
            }

        return(Collections.unmodifiableList(result)); // Keep result immutable.
        }
    }

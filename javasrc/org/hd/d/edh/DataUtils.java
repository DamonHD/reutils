/*
Copyright (c) 2008-2024, Damon Hart-Davis
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;


/**Data utilities.
 * Handles the old bmreports style of CSV (with an initial HDR row and a trailing FTR row),
 * and new data.elexon.co.uk (as of 2024) CSV API V1 response style,
 * along with TIBCO data and other records.
 */
public final class DataUtils
    {
    /**Prevent creation of an instance. */
    private DataUtils() { }

    /**Extracts immutable in-order List of line-oriented TIBCO messages from a Reader, filtering if required.
     * The input must be in uncompressed ASCII form.
     * <p>
     * Each returned List entry is a Map from name to value, eg from the record:
<pre>
2009:03:09:23:55:00:GMT: subject=MESSAGE.TYPE, message={TP=2009:03:09:23:55:00:GMT,SD=2009:03:09:00:00:00:GMT,SP=12,TS=2009:03:09:23:50:00:GMT,FT=XXXX,FG=123}
</pre>
     * is returned a Map containing 6 entries, eg from "FT" to "XXXX".
     * <p>
     * Note that message field values may contain embedded line-ends but not embedded commas,
     * and a line ending with '}' ends a message.
     * <p>
     * Does most of the donkey-work of parsing.
     * <p>
     * Key values are automatically intern()ed to save space
     * as likely to be repeated between records.
     *
     * @param r  stream to read from, not closed by this routine; never null
     * @param subject  if non-null, only return messages of this exact subject
     * @param fieldKeys  if non-null, only message fields/mappings with these keys will be retained.
     */
    public static List<Map<String,String>> extractTIBCOMessages(final Reader r,
                                                                final String subject, final Set<String> fieldKeys)
        throws IOException
        {
        if(null == r) { throw new IllegalArgumentException(); }

        // Wrap a buffered reader around the input if not already so.
        final BufferedReader br = (r instanceof BufferedReader) ? (BufferedReader)r : new BufferedReader(r, 8192);

        final ArrayList<Map<String,String>> result = new ArrayList<>(1024); // Allow for reasonable number of messages...

        String line;
        while(null != (line = br.readLine()))
            {
            // Chop first with spaces into 3 parts: timestamp, subject and message.
            // The message section may contain embedded spaces and even new-lines,
            // so if the message component doesn't end with a '}'
            // then we go on appending input lines to the message part until we get the terminator.
            final String[] sections = delimTR.split(line, 3);
            // Validate record structure.
            if(sections.length != 3)
                { throw new IOException("Should be exactly 3 sections in record: "+line); }
            if(!sections[0].endsWith(":"))
                { throw new IOException("Invalid timestamp: "+line); }
            if(!sections[1].endsWith(","))
                { throw new IOException("Invalid timestamp: "+line); }
            if(!sections[1].startsWith("subject="))
                { throw new IOException("Invalid timestamp: "+line); }
            if(!sections[2].startsWith("message={"))
                { throw new IOException("Invalid timestamp: "+line); }

            // Gather up any continuation lines for multi-line messages.
            String message = sections[2];
            while(!message.endsWith("}"))
                {
                final String continuation = br.readLine();
                if(null == continuation)
                    { throw new EOFException("Unterminated multi-line message"); }
                message = message + "\n" + continuation;
                }

            // If a subject was specified to filter by and this record does not match it
            // then skip this record.
            if(null != subject)
                {
                final String msgSubject = sections[1].substring(8, sections[1].length()-1);
                if(!subject.equals(msgSubject)) { continue; }
                }

            final String messageBody = message.substring(9, message.length() - 1);
            final String mappingsRaw[] = delimTM.split(messageBody);
            final Map<String,String> m = new HashMap<>(mappingsRaw.length*2);
            for(final String mr : mappingsRaw)
                {
                final int ei = mr.indexOf('=');
                if((ei < 1) || (ei != mr.lastIndexOf('=')))
                    { throw new IOException("malformed message body: "+messageBody); }
                final String key = mr.substring(0, ei);
                // Skip unwanted fields.
                if((null != fieldKeys) && !fieldKeys.contains(key)) { continue; }
                // Store mapping.
                m.put(key.intern(), mr.substring(ei+1));
                }
            result.add(Collections.unmodifiableMap(m)); // Keep result immutable.
            }

        result.trimToSize(); // May persist for a while, so free up excess space.
        return(Collections.unmodifiableList(result)); // Return immutable result.
        }

    /**Immutable regex pattern used to split TIBCO line-oriented records; never null.
     * This is basically just a simple " "
     * which with split() should preserve empty fields.
     */
    public static final Pattern delimTR = Pattern.compile(" ");

    /**Immutable regex pattern used to split TIBCO message component; never null.
     * This is basically just a simple ","
     * which with split() should preserve empty fields.
     */
    public static final Pattern delimTM = Pattern.compile(",");


    /**Immutable regex pattern used to split CSV lines; never null.
     * This is basically just a simple ","
     * which with split() should preserve empty fields.
     */
    public static final Pattern delimCSV = delimTM; // We can use delimTR to save an extra instance! // Pattern.compile(",");


    /**Append newer records to existing data.
     * This can be used new data to an existing archive,
     * and fill in temporarily-missing records from new data.
     * <p>
     * No existing records will be altered,
     * nor will anything older than the newest one be inserted.
     * <p>
     * If the existing record set is null
     * then the new record set is returned as-is.
     *
     * @return  new List if the records were appended, else null
     */
    public static List<List<String>> appendNewBMRDataRecords(
    		final List<List<String>> existingRecords,
    		final List<List<String>> newRecords)
	    {
    	// If no new data then nothing to append.
    	if(null == newRecords) { return(null); }
    	if(newRecords.isEmpty()) { return(null); }
    	// If no existing data then return the new data as-is.
	    if(null == existingRecords) { return(newRecords); }
		final int nRowsExisting = existingRecords.size();
		if(0 == nRowsExisting) { return(newRecords); }

		// Compute newest/last timestamp in existing data.
        final SimpleDateFormat timestampParser = FUELINSTUtils.getCSVTimestampParser();
		final String lastExistingTimestampRaw = existingRecords.get(nRowsExisting-1).get(3);
        Date dle;
		try { dle = timestampParser.parse(lastExistingTimestampRaw); }
		catch (final ParseException e) { e.printStackTrace(); return(null); }
        final long lastExistingTimestamp = dle.getTime();

		// Compute newest/last timestamp in new data.
        // If it is not newer than the newest existing record
        // then there is nothing to append.
        final int nRowsNew = newRecords.size();
        final String lastNewTimestampRaw = newRecords.get(nRowsNew-1).get(3);
        Date dln;
        try { dln = timestampParser.parse(lastNewTimestampRaw); }
		catch (final ParseException e) { e.printStackTrace(); return(null); }
        final long lastNewTimestamp = dln.getTime();
        if(lastNewTimestamp <= lastExistingTimestamp)
        	{ return(null); }

        // Work backwards through the new records,
        // finding the oldest one that can be appended.
        // The assumption is that usually only a few trailing records
        // will be appended.
        //
        // For speed a lexical timestamp comparison suffices
        // to find the number of new records to append.
        int oldestNewRecordToInsert = nRowsNew - 1;
        for(int i = nRowsNew - 2; i >= 0; --i)
	        {
	        final String rawTimestamp = newRecords.get(i).get(3);
	        // Stop when this record is not newer than the last existing record.
	        if(rawTimestamp.compareTo(lastExistingTimestampRaw) <= 0) { break; }
	        oldestNewRecordToInsert = i;
	        }
        // Sub-list of new records to append.
        final List<List<String>> newRecordsToAppend =
    		newRecords.subList(oldestNewRecordToInsert, nRowsNew);

        // Initially-empty result...
        final ArrayList<List<String>> result =
            new ArrayList<>(nRowsExisting + newRecordsToAppend.size());
        result.addAll(existingRecords);
        result.addAll(newRecordsToAppend);

        result.trimToSize(); // Free resources...
        return(Collections.unmodifiableList(result)); // Make outer list immutable...
	    }

    /**Trim BMR FUELINST data to span at most the specified number of hours.
     * Trims away the oldest records until the limit is met.
     *
     * @param parsedBMRCSV  BMR FUELINST list to trim
     * @param maxHoursSpan  maximum span of hours between newest and oldest record;
     *             strictly positive
     *
     * @return  new List if the result was trimmed, else null
     */
    public static List<List<String>> trimBMRData(
			final List<List<String>> parsedBMRCSV,
			final int maxHoursSpan)
	    {
    	// Nothing to do if null store or at most 1 record.
		if(null == parsedBMRCSV) { return(null); }
		final int nRows = parsedBMRCSV.size();
		if(nRows < 2) { return(null); }

		// Compute oldest timestamp allowed.
        final SimpleDateFormat timestampParser = FUELINSTUtils.getCSVTimestampParser();
		final String lastTimestampRaw = parsedBMRCSV.get(nRows-1).get(3);
        Date dl;
		try { dl = timestampParser.parse(lastTimestampRaw); }
		catch (final ParseException e) { e.printStackTrace(); return(null); }
        final long lastTimestamp = dl.getTime();
        final long oldestAllowedTimestamp = lastTimestamp - (maxHoursSpan*3600*1000) + 1;

        // Find timestamp of existing first record.
		final String firstTimestampRaw = parsedBMRCSV.get(0).get(3);
        Date df;
		try { df = timestampParser.parse(firstTimestampRaw); }
		catch (final ParseException e) { e.printStackTrace(); return(null); }
        final long firstTimestamp = df.getTime();

        // If oldest existing record is not too old then there is nothing to do.
        if(firstTimestamp >= oldestAllowedTimestamp)
        	{ return(null); }

        // Discard records that are too old.
        // Return the rest in a new immutable List.
        // Find first record that is new enough to retain.
        // (All the rest should be newer, and the last record is a sentinel.)
        // Skip the first record, since it is already known to be too old.
        int firstRecordOldEnough = nRows - 1;
        for(int i = 2; i < nRows-1; ++i)
	        {
        	// Find timestamp of next record.
    		final String timestampRaw = parsedBMRCSV.get(i).get(3);
            Date d;
    		try { d = timestampParser.parse(timestampRaw); }
    		catch (final ParseException e) { e.printStackTrace(); return(null); }
            final long timestamp = d.getTime();
	        if(timestamp >= oldestAllowedTimestamp)
		        {
	        	firstRecordOldEnough = i;
	        	break;
		        }
	        }

		// Make outer list immutable...
		return(Collections.unmodifiableList(
			parsedBMRCSV.subList(firstRecordOldEnough, nRows)));
	    }

    /**Return from isValidBMRData.
     * The error should never be null or empty for an instance of this.
     */
    static final class ValidBMRDataResultError
	    {
    	public ValidBMRDataResultError(final String errorMessage)
    	    { this(errorMessage, null); }
    	public ValidBMRDataResultError(final String errorMessage, final List<List<String>> repairedBMRCSV)
    	    {
    		this.errorMessage = errorMessage;
    		this.repairedBMRCSV = repairedBMRCSV;
    		}
    	/**Human-readable error message; never null. */
    	public final String errorMessage;
    	/**Immutable repaired data where a repair is requested and is possible, else null. */
    	public List<List<String>> repairedBMRCSV;
	    }

    /**Validate an and possibly fix up BMR FUELINST data on a number of key points.
     *      *
     * @return non-null error if any problem found (usually first error).
     */
	public static ValidBMRDataResultError isValidBMRData(
			final List<List<String>> parsedBMRCSV,
			final long newestPossibleValidRecord,
			final int maxHoursSpan)
	    { return(isValidBMRData(parsedBMRCSV, newestPossibleValidRecord, maxHoursSpan, false)); }

    /**Validate (and possibly fix up) BMR FUELINST data on a number of key points.
     *
     * @param parsedBMRCSV  parsed FUELINST records (minus HDR and FTR), never modified;
     *             never null
     * @param newestPossibleValidRecord  no record may be newer than (timestamped after) this;
     *             positive and can be set to maximum long value to avoid the check
     * @param maxHoursSpan  maximum span of hours between newest and oldest record;
     *             strictly positive
     * @param attemptRepair if true and any repairable error or errors are found,
     *             attempt to create a repaired version in the result object
     *             and if the repaired version is non-null it can be used
     *
     * @return non-null error if any problem found (usually first error).
     */
	public static ValidBMRDataResultError isValidBMRData(
			final List<List<String>> parsedBMRCSV,
			final long newestPossibleValidRecord,
			final int maxHoursSpan,
			final boolean attemptRepair)
	    {
		if(null == parsedBMRCSV) { return(new ValidBMRDataResultError("null input")); }

		// Null if no repair is performed.
		String lastErrorRepaired = null;
		// A mutable (must be wrapped for return) repaired result so far.
		// If repairs are not to be attempted this is null.
		// If repairs are to be attempted this is initially a copy of the input rows,
		// then bad rows are nulled out,
		// then null entries are deleted,
		// and an immutable copy is returned.
		final ArrayList<List<String>> repairedBMRCSV = !attemptRepair ? null :
			new ArrayList<>(parsedBMRCSV);

// Sample data...
//FUELINST,20221104,20,20221104095000,14429,0,0,4649,8379,0,901,0,123,0,0,406,0,2235,122,0,0,1257
//FUELINST,20221104,20,20221104095500,14289,0,0,4642,8513,0,899,1,122,0,0,406,0,2235,122,0,0,1257
//FUELINST,20221104,20,20221104100000,14269,0,0,4638,8646,0,883,0,125,0,0,301,0,2233,111,0,0,1257
//FUELINST,20221104,21,20221104100500,14217,0,0,4638,8788,0,849,0,146,0,0,129,0,2230,0,0,0,1257
//FUELINST,20221104,21,20221104101000,14209,0,0,4641,8936,0,848,0,141,0,0,129,0,2225,0,0,0,1257
//FUELINST,20221104,21,20221104101500,14133,0,0,4639,9047,0,848,0,136,0,0,130,0,2224,0,0,0,1257

		String lastTimestampRaw = FUELINST.FUELINST_TIMESTAMP_JUST_TOO_OLD;
//		for(List<String> row : parsedBMRCSV)
		final int nRows = parsedBMRCSV.size();
		for(int r = 0; r < nRows; ++r)
			{
			final List<String> row = parsedBMRCSV.get(r);
			if(null == row) { return(new ValidBMRDataResultError("null row")); }
			if(row.size() < 5) { return(new ValidBMRDataResultError("short row")); }
			if(!"FUELINST".equals(row.get(0))) { return(new ValidBMRDataResultError("row first field not FUELINST")); }
			final String timestampRaw = row.get(3);
			if(14 != timestampRaw.length()) { return(new ValidBMRDataResultError("timestamp wrong length")); }

			// THIS IS POTENTIALLY REPAIRABLE!
			// Check for strictly monotonic (lexical) ordering.
			// Avoids an expensive time conversion...
			// Reject anything with timestamp older than newest so far read.
//WARNING: [FUELINST, 20230122, 12, 20230122055500, 5380, 0, 501, 5171, 6616, 0, 374, 0, 190, 651, 400, 1002, 153, 2017, 989, 259, 384, 1257]
//WARNING: [FUELINST, 20230122, 12, 20230122060000, 5779, 0, 500, 5172, 6614, 0, 374, 0, 165, 547, 400, 1003, 127, 2005, 985, 251, 344, 1257]
//WARNING: [FUELINST, 20230122, 13, 20230122061500, 6749, 0, 498, 5175, 6601, 0, 377, 0, 166, 125, 400, 1003, 51, 2010, 862, 227, 219, 1257]
//WARNING: [FUELINST, 20230122, 13, 20230122062000, 6951, 0, 500, 5175, 6539, 0, 376, 0, 167, 125, 400, 1003, 45, 2014, 862, 227, 219, 1257]
//WARNING: [FUELINST, 20230122, 13, 20230122060500, 6320, 0, 499, 5177, 6617, 0, 375, 0, 164, 249, 400, 1003, 102, 2001, 867, 236, 260, 1257]
//WARNING: [FUELINST, 20230122, 13, 20230122061000, 6575, 0, 499, 5175, 6656, 0, 377, 0, 165, 125, 400, 1004, 77, 2010, 862, 227, 219, 1257]
//WARNING: [FUELINST, 20230122, 13, 20230122062500, 7171, 0, 499, 5176, 6489, 0, 375, 0, 168, 125, 397, 1004, 68, 2013, 862, 227, 219, 1257]
//WARNING: [FUELINST, 20230122, 13, 20230122063000, 7055, 0, 500, 5179, 6458, 0, 374, 0, 187, 125, 376, 1004, 93, 2016, 862, 227, 219, 1257]
			if(lastTimestampRaw.compareTo(timestampRaw) > 0)
			    {
				final String errorMessage = "timestamps misordered (decreasing) at " + timestampRaw;
				if(attemptRepair)
					{
					// Note the error/repair.
					lastErrorRepaired = errorMessage;
					// Null out the later ('older') record.
					repairedBMRCSV.set(r, null);
					// Keep lastTimestampRaw as-is as high water mark.
					continue;
					}
				else
				    { return(new ValidBMRDataResultError(errorMessage)); }
				}

			// DHD20221117: after a lot of catching up, multiple records may get the same timestamp
			// because they seem to be stamped with when they are added,
			// not the time of day of the samples that they refer to.
			// All the records with timestamps 20221117131300 and 20221117131400 should be dropped.
//WARNING: [FUELINST, 20221117, 22, 20221117103500, 16266, 0, 0, 4228, 11863, 132, 496, 0, 205, 0, 0, 173, 0, 2005, 422, 0, 0, 1095]
//WARNING: [FUELINST, 20221117, 22, 20221117131300, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 69, 0, 0, 329, 0, 0, 1095]
//WARNING: [FUELINST, 20221117, 23, 20221117131300, 16297, 0, 0, 4231, 11865, 0, 496, 0, 209, 0, 0, 0, 0, 2011, 310, 0, 0, 1095]
//WARNING: [FUELINST, 20221117, 24, 20221117131300, 16471, 0, 0, 4251, 12685, 0, 435, 0, 205, 0, 0, 0, 0, 1957, 78, 0, 0, 1095]
//WARNING: [FUELINST, 20221117, 25, 20221117131300, 16670, 0, 0, 4234, 12699, 0, 428, 0, 205, 0, 0, 0, 0, 1974, 0, 0, 0, 1095]
//WARNING: [FUELINST, 20221117, 26, 20221117131300, 16642, 0, 0, 4238, 12599, 0, 430, 0, 144, 0, 0, 0, 0, 1972, 0, 0, 0, 1095]
//WARNING: [FUELINST, 20221117, 26, 20221117131400, 16511, 0, 0, 4239, 12640, 0, 429, 0, 135, 0, 0, 0, 0, 1974, 0, 0, 0, 1095]
//WARNING: [FUELINST, 20221117, 27, 20221117131400, 16331, 0, 0, 4234, 12636, 0, 430, 0, 155, 0, 0, 79, 0, 1973, 0, 0, 0, 1095]
//WARNING: [FUELINST, 20221117, 27, 20221117131500, 16060, 0, 0, 4227, 12641, 130, 429, 1, 152, 0, 0, 103, 0, 1969, 0, 0, 0, 1095]

			// THIS IS POTENTIALLY REPAIRABLE!
			if(lastTimestampRaw.compareTo(timestampRaw) == 0)
			    {
				final String errorMessage = "timestamps not monotonically increasing";
				if(attemptRepair)
					{
					// Note the error/repair.
					lastErrorRepaired = errorMessage;
					// Null out this record and the previous one that it shares a timestamp with.
					repairedBMRCSV.set(r, null);
					repairedBMRCSV.set(r-1, null);
					}
				else
					{ return(new ValidBMRDataResultError(errorMessage)); }
				}

			lastTimestampRaw = timestampRaw;
			}

		// Check that the last/newest record is not too new to be valid.
        final SimpleDateFormat timestampParser = FUELINSTUtils.getCSVTimestampParser();
        Date d;
		try { d = timestampParser.parse(lastTimestampRaw); }
		catch (final ParseException e) { return(new ValidBMRDataResultError("cannot parse timestamp")); }
        final long finalTimestamp = d.getTime();
        if(finalTimestamp > newestPossibleValidRecord) { return(new ValidBMRDataResultError("last record timestamp too new")); }


		// FIXME Validate timestamp range.


        // For a repaired result,
        // remove any nulls,
        // wrap to be immutable,
        // and return a result object with sample error and repaired data.
        if(null != lastErrorRepaired)
	        {
            while(repairedBMRCSV.remove(null)) { } // FIXME: O(n^2) cost.
            repairedBMRCSV.trimToSize();
        	return(new ValidBMRDataResultError(
        			"REPAIRED: " + lastErrorRepaired,
        			Collections.unmodifiableList(repairedBMRCSV)));
	        }

		// Did not find any problems.
		return(null);
	    }

    /**Parse bmreports-style CSV file/stream with HDR and FTR check rows (which are not returned); never null but may be empty.
     *
     * @param URL  URL to read from; never null
     * @param headerCheck  if non-null, the first/header row's second field is verified to have exactly this heading
     *
     * Each row's first field is a non-empty 'type' field.
     * <p>
     * Exactly the first row must have a 'HDR' type (first column value).
     * <p>
     * Exactly the last row must have a 'FTR' type (first column value).
     * This will stop reading once it has read a 'FTR' row.
     * This row's second value must be the number of data lines encountered.
     * <p>
     * The HDR and FTR rows are omitted from the returned List.
     * <p>
     * FUELINST rows are expected to be in increasing time order.
     * <p>
     * The outer and inner Lists implement RandomAccess.
     * <p>
     * This buffers its input for efficiency if not already a BufferedReader.
     *
     * @throws IOException  if there is an I/O problem or the data is malformed
     *
     * @return a non-null but possibly-empty in-order immutable List of rows,
     *    each of which is a non-null but possibly-empty in-order immutable List of fields
     */
    public static List<List<String>> parseBMRCSV(final URL url, final String headerCheck)
        throws IOException
        {
        // Set up URL connection to fetch the data.
        final URLConnection conn = url.openConnection();
        conn.setAllowUserInteraction(false);
        conn.setUseCaches(false); // Ensure that we get non-stale values each time.
        conn.setConnectTimeout(60000); // Set a long-ish connection timeout.
        conn.setReadTimeout(60000); // Set a long-ish read timeout.

        try(final InputStreamReader is = new InputStreamReader(conn.getInputStream()))
            { return(DataUtils.parseBMRCSV(is, headerCheck)); }
        }

    /**If true, attempt to minimise memory consumption when parsing and loading FUELINST data. */
    private static final boolean OPTIMISE_MEMORY_IN_FUELINST_PARSE = true;

    /**Parse bmreports-style CSV file/stream with HDR and FTR check rows (which are not returned); never null but may be empty.
     *
     * @param r  stream to read from, not closed by this routine; never null
     * @param headerCheck  if non-null, the first/header row's second field is verified to have exactly this heading
     *
     * Each row's first field is a non-empty 'type' field.
     * <p>
     * Exactly the first row must have a 'HDR' type (first column value).
     * <p>
     * Exactly the last row must have a 'FTR' type (first column value).
     * This will stop reading once it has read a 'FTR' row.
     * This row's second value must be the number of data lines encountered.
     * <p>
     * The HDR and FTR rows are omitted from the returned List.
     * <p>
     * FUELINST rows are expected to be in increasing time order.
     * <p>
     * The outer and inner Lists implement RandomAccess.
     * <p>
     * This buffers its input for efficiency if not already a BufferedReader.
     *
     * @throws IOException  if there is an I/O problem or the data is malformed
     *
     * @return a non-null but possibly-empty in-order immutable List of rows,
     *    each of which is a non-null but possibly-empty in-order List of fields
     */
    public static List<List<String>> parseBMRCSV(final Reader r, final String headerCheck)
        throws IOException
        {
        if(null == r) { throw new IllegalArgumentException(); }

        // Wrap a buffered reader around the input if not already so.
        final BufferedReader br = (r instanceof BufferedReader) ? (BufferedReader)r :
        	new BufferedReader(r, 8192);

        // Read first (header) line/row.
        final String header = br.readLine();
        if(null == header) { throw new EOFException(); }
        final String hdrArray[] = delimCSV.split(header);
        if((hdrArray.length < 1) || !"HDR".equals(hdrArray[0]))
            { throw new ProtocolException("missing header (HDR) row"); }
        if((null != headerCheck) && !headerCheck.equals(hdrArray[1]))
            { throw new IOException("wrong header (HDR) type/description found"); }

        // Initially-empty result...
        // Size it to initially accommodate ~288-row live FUELINST datum.
        final ArrayList<List<String>> result = new ArrayList<>(300);

        String row;
        while(null != (row = br.readLine()))
            {
            final String fields[] = delimCSV.split(row);
            if(fields.length < 1)
                { throw new IOException("unexpected empty row"); }
            final String type = fields[0];
            if(type.isEmpty())
                { throw new IOException("unexpected empty type"); }

            // Deal with FTR row.
            // Whatever happens we will be exiting the loop.
            if("FTR".equals(type))
                {
                if(fields.length < 2)
                    { throw new IOException("footer (FTR) data row count missing"); }
                final int rowCount;
                try { rowCount = Integer.parseInt(fields[1], 10); }
                catch(final NumberFormatException e) { throw new IOException("footer (FTR) data row count malformed", e); }
                if(rowCount != result.size())
                    { throw new IOException("footer (FTR) data row count wrong"); }
                // OK, all seems fine, so break out.
                break;
                }

//System.out.println("Data row: " + row);
//Data row: FUELINST,20221104,20,20221104093500,15219,0,0,4640,7999,0,901,1,123,0,0,406,0,2229,122,0,0,1257
//Data row: FUELINST,20221104,20,20221104094000,14981,0,0,4643,8140,0,901,0,122,0,0,406,0,2233,123,0,0,1257
//Data row: FUELINST,20221104,20,20221104094500,14705,0,0,4644,8281,0,901,0,123,0,0,406,0,2232,122,0,0,1257
//Data row: FUELINST,20221104,20,20221104095000,14429,0,0,4649,8379,0,901,0,123,0,0,406,0,2235,122,0,0,1257
//Data row: FUELINST,20221104,20,20221104095500,14289,0,0,4642,8513,0,899,1,122,0,0,406,0,2235,122,0,0,1257
//Data row: FUELINST,20221104,20,20221104100000,14269,0,0,4638,8646,0,883,0,125,0,0,301,0,2233,111,0,0,1257
//Data row: FUELINST,20221104,21,20221104100500,14217,0,0,4638,8788,0,849,0,146,0,0,129,0,2230,0,0,0,1257
//Data row: FUELINST,20221104,21,20221104101000,14209,0,0,4641,8936,0,848,0,141,0,0,129,0,2225,0,0,0,1257
//Data row: FUELINST,20221104,21,20221104101500,14133,0,0,4639,9047,0,848,0,136,0,0,130,0,2224,0,0,0,1257
//Data row: FUELINST,20221104,21,20221104102000,14025,0,0,4636,9078,0,848,0,132,0,0,129,0,2226,0,0,0,1257
//Data row: FUELINST,20221104,21,20221104102500,13998,0,0,4637,9195,0,848,1,136,0,0,130,0,2225,0,0,0,1257
//Data row: FUELINST,20221104,21,20221104103000,13964,0,0,4635,9332,0,848,0,134,0,0,129,0,2219,0,0,0,1257

            // Memory micro-optimisation.
            // Where possible, share duplicate values from the previous row,
            // or with a constant "0".
            // Costs maybe ~10% of parse execution time doing this extra work,
            // but may save more than that in avoided GC on small JVM instance.
            if(OPTIMISE_MEMORY_IN_FUELINST_PARSE && !result.isEmpty())
	            {
	            final List<String> prevRow = result.get(result.size() - 1);
	            if(fields.length == prevRow.size())
		            {
		            for(int i = fields.length; --i >= 0; )
			            {
		            	final String fi = fields[i];
			            // Deduplicate "0" (MW) values by using an implicitly intern()ed constant.
						if("0".equals(fi)) { fields[i] = "0"; continue; }
                        // Else if this matches the item from the previous row, reuse it.
			            final String pi = prevRow.get(i);
						if(fi.equals(pi)) { fields[i] = pi; }
			            }
		            }
	            }

            // Package up row data (and make it unmodifiable).
            result.add(Collections.unmodifiableList(Arrays.asList(fields)));
            }

        result.trimToSize(); // Free resources...
        return(Collections.unmodifiableList(result)); // Make outer list unmodifiable...
        }

    /**Load from file gzipped parsed FUELINST data in a form that parseBMRCSV() can read; never null but may be empty.
     *
     * @throws IOException  if file not present or unreadable/unparseable.
     */
    public static List<List<String>> loadBMRCSV(final File longStoreFile)
        throws IOException
        {
    	if(null == longStoreFile) { throw new IllegalArgumentException(); }
    	try(InputStream is = new FileInputStream(longStoreFile);
    			BufferedInputStream bis = new BufferedInputStream(is);
    			InputStream gis = new GZIPInputStream(bis);
    			Reader r = new InputStreamReader(gis, FUELINSTUtils.FUELINST_CHARSET))
    		{ return(parseBMRCSV(r, null)); }
        }

    /**Save/serialise, gzipped to file, CSV BMR FUELINST data in a form that parseBMRCSV() can read.
     * Generate (gzipped) ASCII CSV, with newlines to terminate rows.
     * <p>
     * May be run as an async task/thread, as no logging output is generated.
     * <p>
     * Write atomically, world-readable.
     *
     * @throws IllegalArgumentException  may be thrown if asked to serialise invalid
     *     (eg misordered or non-FUELINST) data
     * @throws IOException  in case of trouble writing to the filesystem
     */
    public static void saveBMRCSV(final List<List<String>> data, final File longStoreFile)
        throws IOException
	    {
    	if(null == longStoreFile) { throw new IllegalArgumentException(); }
    	final byte[] csvgz = saveBMRCSV(data, true);
    	FileUtils.replacePublishedFile(longStoreFile.getPath(), csvgz, true);
	    }

    /**Save/serialise parsed BMR FUELINST data in a form that parseBMRCSV() can read, optionally gzipped.
     * Generate ASCII CSV, with newlines to terminate rows.
     *
     * @param data  FUELINST CSV data rows (no header or footer); never null
     * @param gzip  if true then gzip the result (on the fly to reduce memory usage)
     *
     * @throws IllegalArgumentException  may be thrown if asked to serialise invalid
     *     (eg misordered or non-FUELINST) data
     * @throws IOException  in case of trouble serialising the data (should never happen)
     */
    public static byte[] saveBMRCSV(final List<List<String>> data, final boolean gzip)
        throws IOException
	    {
    	if(null == data) { throw new IllegalArgumentException(); }

    	// Write to an ASCII CSV byte[], optionally gzipped.
    	// Roughly size the working array for typical FUELINST data.
        final int rowCount = data.size();
        try (
			final ByteArrayOutputStream baos = new ByteArrayOutputStream(32 + (rowCount * (gzip ? 32 : 128)));
			final OutputStream os = gzip ?
				new BufferedOutputStream(new java.util.zip.GZIPOutputStream(baos)) : baos
            )

	        {
	        // Write header.
	        os.write("HDR\n".getBytes(FUELINSTUtils.FUELINST_CHARSET));

	        // Write body.
	        final StringBuilder rowBuilder = new StringBuilder(128);
	        for(final List<String> row : data)
		        {
	        	// Clear the new row under construction.
	        	rowBuilder.setLength(0);

		        final int fields = row.size();

		        // Do some simple validation.
		        // Abort if data not valid FUELINST format.
		        // TODO: more validation, eg of row time ordering, field format, etc.
		        if(fields < 4)
	                { throw new IllegalArgumentException("too few fields for FUELINST"); }
		        if(!row.isEmpty() && !"FUELINST".equals(row.get(0)))
	                { throw new IllegalArgumentException("not FUELINST records"); }

		        // Write row.
	        	for(int f = 0; f < fields; ++f)
		        	{
	        		rowBuilder.append(row.get(f));
	        		rowBuilder.append((f < fields-1) ? ',' : '\n');
//		        	os.write(row.get(f).getBytes(FUELINSTUtils.FUELINST_CHARSET));
//		        	os.write((f < fields-1) ? ',' : '\n'); // Horribly inefficient to write 1 byte direct to GZIPOutputStream!
		        	}

	        	// Write each row at once to help efficiency.
	        	os.write(rowBuilder.toString().getBytes(FUELINSTUtils.FUELINST_CHARSET));
		        }

	        // Write footer.
	        os.write(("FTR," + rowCount + "\n").getBytes(FUELINSTUtils.FUELINST_CHARSET));

	        // Force compressed data to be written through.
	        if(gzip) { os.close(); }

	        return(baos.toByteArray());
	        }
	    }

    /**Convert positional encoding of row values to Map form; never null but may be empty.
     * Returned value is immutable.
     *
     * @param template  CSV list of names for each position with empty positions ignored; never null
     * @param rowData  row data extracted from CSV, possibly by parseBMRCSV(); never null
     * @return  Map from names to values where name and value are present (non-empty); never null
     */
    public static Map<String,String> extractNamedFieldsByPositionFromRow(final String template, final List<String> rowData)
        {
        if((null == template) || (null == rowData)) { throw new IllegalArgumentException(); }

        final String[] names = delimCSV.split(template);
        final int maxResultSize = Math.min(names.length, rowData.size());
        final Map<String,String> result = new HashMap<>(maxResultSize);
        for(int i = maxResultSize; --i >= 0; )
            {
            final String name = names[i];
            if(name.isEmpty()) { continue; }
            final String value = rowData.get(i);
            if(value.isEmpty()) { continue; }
            result.put(name, value);
            }
        return(Collections.unmodifiableMap(result));
        }

    /**Convert positional encoding of List of row values to (immutable) Map form; never null but may be empty.
     * Returned value is immutable.
     *
     * @param template  CSV list of names for each position with empty positions ignored; never null
     * @param rowData  List of rows' data extracted from CSV, possibly by parseBMRCSV(); never null
     * @return  Map from names to values where name and value are present (non-empty); never null
     */
    public static List<Map<String,String>> extractNamedFieldsByPositionFromRows(final String template, final List<List<String>> rows)
        {
        if((null == template) || (null == rows)) { throw new IllegalArgumentException(); }

        final List<Map<String,String>> result = new ArrayList<>(rows.size());
        for(final List<String> rowData : rows)
            { result.add(extractNamedFieldsByPositionFromRow(template, rowData)); }
        return(Collections.unmodifiableList(result));
        }




    /**Brave new world of 2024.
     * See: https://bmrs.elexon.co.uk/api-documentation/endpoint/datasets/FUELINST

curl -X 'GET' \
  'https://data.elexon.co.uk/bmrs/api/v1/datasets/FUELINST?publishDateTimeFrom=2022-06-20T00%3A00%3A00Z&publishDateTimeTo=2022-06-26T00%3A00%3A00Z&format=csv' \
  -H 'accept: text/plain'


Dataset,PublishTime,StartTime,SettlementDate,SettlementPeriod,FuelType,Generation
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,BIOMASS,864
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,CCGT,6030
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,COAL,0
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,INTELEC,-796
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,INTEW,504
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,INTFR,-1030
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,INTIFA2,-1028
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,INTIRL,248
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,INTNED,-1074
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,INTNEM,-1021
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,INTNSL,106
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,NPSHYD,160
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,NUCLEAR,5056
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,OCGT,0
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,OIL,0
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,OTHER,120
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,PS,-9
FUELINST,2022-06-26T00:00:00Z,2022-06-25T23:55:00Z,2022-06-26,2,WIND,10586
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,BIOMASS,866
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,CCGT,6037
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,COAL,0
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,INTELEC,-792
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,INTEW,504
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,INTFR,-1031
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,INTIFA2,-1028
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,INTIRL,248
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,INTNED,-1075
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,INTNEM,-1021
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,INTNSL,199
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,NPSHYD,160
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,NUCLEAR,5059
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,OCGT,0
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,OIL,0
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,OTHER,134
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,PS,-9
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,WIND,10532
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,BIOMASS,870
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,CCGT,6087
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,COAL,0
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,INTELEC,-792
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,INTEW,504
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,INTFR,-1030
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,INTIFA2,-1028
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,INTIRL,248
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,INTNED,-1074
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,INTNEM,-1021
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,INTNSL,244
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,NPSHYD,160
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,NUCLEAR,5056
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,OCGT,0
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,OIL,0
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,OTHER,141
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,PS,-9
...
     */

    /**Convert from new (2024) format CSV FUELINST data to (immutable) older one-line-per-time-sample format; never null.
     * Uses the same template as used to extract these re-extract positional parameters.
     * <p>
     * Can optionally clamp negative values to zero
     * <p>
     * This allows us to use this compact form for storage internally,
     * and minimises disruption to the pre-2024 logic for the time being!
     *
     * @param template  CSV list of names for each position with empty positions ignored; never null
     * @param clampNonNegative  if true then clamp all values to be non-negative

     * New format:
     * <pre>
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,PS,-9
FUELINST,2022-06-25T23:55:00Z,2022-06-25T23:50:00Z,2022-06-26,2,WIND,10532
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,BIOMASS,870
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,CCGT,6087
FUELINST,2022-06-25T23:50:00Z,2022-06-25T23:45:00Z,2022-06-26,2,COAL,0
     * </pre>
     *
     * Old/compact format:
     * <pre>
FUELINST,20221104,20,20221104095000,14429,0,0,4649,8379,0,901,0,123,0,0,406,0,2235,122,0,0,1257
     * </pre>
     */
    public static final List<List<String>> convertToCompactForm(
    		final String template,
    		final boolean clampNonNegative
    		)
	    {
	    throw new RuntimeException("NOT IMPLEMENTED");
	    }


    /**FUELINST stream...
     * See: https://bmrs.elexon.co.uk/api-documentation/endpoint/datasets/FUELINST/stream
     * "This endpoint has an optimised JSON payload and is aimed at frequent requests for the data."
     * <pre>
curl -X 'GET' \
  'https://data.elexon.co.uk/bmrs/api/v1/datasets/FUELINST/stream?publishDateTimeFrom=2022-06-20T00%3A00%3A00Z&publishDateTimeTo=2022-06-26T00%3A00%3A00Z' \
  -H 'accept: text/plain'
     * </pre>
     * Sample response fragment (pretty-printed):
     * <pre>
  {
    "dataset": "FUELINST",
    "publishTime": "2022-06-26T00:00:00Z",
    "startTime": "2022-06-25T23:55:00Z",
    "settlementDate": "2022-06-26",
    "settlementPeriod": 2,
    "fuelType": "BIOMASS",
    "generation": 864
  },
  {
    "dataset": "FUELINST",
    "publishTime": "2022-06-26T00:00:00Z",
    "startTime": "2022-06-25T23:55:00Z",
    "settlementDate": "2022-06-26",
    "settlementPeriod": 2,
    "fuelType": "CCGT",
    "generation": 6030
  },
     */



    /**Record of MW generation by a named fuel in a timed slot/instant.
     *
     * @param time  interval/instant for this generation
     * @param fuelType  fuel name; non-empty, non-null
     * @param generation  generation in MW
     * @param settlementPeriod  half-hourly settlement period within the day, non-negative
     *
     * The settlementPeriod is canonical,
     * and is useful for reconstructing old CSV style records.
     *
     * <p>
     * TODO: break this out into its own top-level class
     */
    public record FuelMWByTime(long time, String fuelType, int generation, int settlementPeriod)
	    {
		public FuelMWByTime
			{
			if(time < 1) { throw new IllegalArgumentException(); }
			Objects.requireNonNull(fuelType);
			if("".equals(fuelType)) { throw new IllegalArgumentException(); }
			if(settlementPeriod < 1) { throw new IllegalArgumentException(); }
			}

        /**Populate from a JSON map/object.
         *
         * @param jo  populate using "startTime", "fuelType", "generation" and settlementPeriod fields; non-null
         * @param clampNonNegative  if true then clamp all values to be non-negative
         *
         * All required fields must be present and non-empty.
         * <p>
         * The time format "2024-02-12T17:50:00Z" ie ISO 8601 UTC down to at least minutes.
         * <p>
         * The fuelType format "INTIFA2" ie upper-case ASCII letters and digits.
         * <p>
         * The generation number "12234" ie an integer, possibly negative.
         * <p>
         * The settlement period "12" ie a small positive integer.
         * <p>
         * Sample record:
         * <pre>
{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"BIOMASS","generation":2249}
         * </pre>
         */
		public FuelMWByTime(final JSONObject jo, final boolean clampNonNegative)
			{
			this(
				java.time.Instant.parse(Objects.requireNonNull(jo).getString(TIME_IS_START ? "startTime" : "publishTime")).toEpochMilli(),
				Objects.requireNonNull(jo).getString("fuelType"),
				Math.max(Objects.requireNonNull(jo).getInt("generation"),
					clampNonNegative ? 0 : Integer.MIN_VALUE),
				Objects.requireNonNull(jo).getInt("settlementPeriod")
				);
			}

		/**If true, the time is the 'start' time, else it is the publish time.
		 * Publication time may lump several intervals together
		 * if there is a delay in Elexon's data processing,
		 * so start time is usually preferable.
		 */
		public static final boolean TIME_IS_START = true;

		/**Throws an exception if the supplied record is not suitable to parse as FUELINST stream.
		 * This may not reject all possible bad records.
		 * @param jo  putative JSON stream FUELINST record; should not be null
		 */
		public static void validateJSONRecord(final JSONObject jo)
			{
			Objects.requireNonNull(jo);
			if(!"FUELINST".equals(jo.get("dataset"))) { throw new IllegalArgumentException("not a FUELINST dataset"); }
			// TODO: add more tests
			}
	    }

    /**Convert from new (2024) format JSON stream FUELINST data to (immutable) by-time fuel-generation Map; never null.
     * Can optionally clamp negative values to zero
     *
     * @param rawJSONa  raw JSON array "[...]"; never null.
     * @param clampNonNegative  if true then clamp all values to be non-negative
     *
     * Will throw an exception if the input data is malformed.
     *
     * New format from open-ended query (no end date):
     * <pre>
curl -X 'GET' \
  'https://data.elexon.co.uk/bmrs/api/v1/datasets/FUELINST/stream?publishDateTimeFrom=2024-02-12T17%3A50%3A00Z' \
  -H 'accept: text/plain'
     * </pre>
     * results in:
     * <pre>
[{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"BIOMASS","generation":2249},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"CCGT","generation":15842},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"COAL","generation":478},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"INTELEC","generation":19},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"INTEW","generation":-83},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"INTFR","generation":267},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"INTIFA2","generation":-4},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"INTIRL","generation":-26},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"INTNED","generation":-170},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"INTNEM","generation":-353},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"INTNSL","generation":612},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"INTVKL","generation":-790},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"NPSHYD","generation":721},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"NUCLEAR","generation":3696},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"OCGT","generation":2},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"OIL","generation":0},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"OTHER","generation":1461},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"PS","generation":1457},{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"WIND","generation":12829}]
     * </pre>
     */
    public static final SortedMap<Long, Map<String, FuelMWByTime>> convertStreamJSONToRecord(
    		final String rawJSONa,
    		final boolean clampNonNegative
    		)
	    {
    	Objects.requireNonNull(rawJSONa);
    	return(convertStreamJSONToRecord(new JSONArray(rawJSONa), clampNonNegative));
	    }

    /**Convert from new (2024) format JSON stream FUELINST data to (immutable) by-time fuel-generation Map; never null.
     * Can optionally clamp negative values to zero
     *
     * @param rawJSONa  raw JSON array "[...]"; never null.
     * @param clampNonNegative  if true then clamp all values to be non-negative
     *
     * Will throw an exception if the input data is malformed.
     */
    public static final SortedMap<Long, Map<String, FuelMWByTime>> convertStreamJSONToRecord(
    		final JSONArray ja,
    		final boolean clampNonNegative
    		)
	    {
    	Objects.requireNonNull(ja);

    	// If empty then return empty immutable result
    	// else validate that first record looks somewhat sane.
    	final int recordCount = ja.length();
		if(0 == recordCount) { return(Collections.emptySortedMap()); }
    	final Object ja0 = ja.get(0);
    	if(!(ja0 instanceof JSONObject)) { throw new IllegalArgumentException("first array element not an object/map"); }
    	FuelMWByTime.validateJSONRecord(ja.getJSONObject(0));

        // Working result: copied to immutable form when done.
        final SortedMap<Long, Map<String, FuelMWByTime>> r = new TreeMap<>();

        for(int i = recordCount; --i >= 0; )
	        {
        	final JSONObject jo = ja.getJSONObject(i);
            final FuelMWByTime record = new FuelMWByTime(jo, true);

            if(!r.containsKey(record.time)) { r.put(record.time, new HashMap<>()); }
            final Map<String, FuelMWByTime> m = r.get(record.time);
            m.put(record.fuelType, record);
	        }

        final SortedMap<Long, Map<String, FuelMWByTime>> r2 = new TreeMap<>();
        for(final Entry<Long, Map<String, FuelMWByTime>> entry : r.entrySet())
	        { r2.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue())); }
	    return(Collections.unmodifiableSortedMap(r2));
	    }


    /**Parse JSON file/stream and return as if parsed from pre-2024 CSV; never null but may be empty.
     * Extracts up to 24h of data up to now from JSON streaming V1 API.
     * <p>
     * Appends a start date time to the URL,
     * and queries the new (2024) Elexon data service.
     * Parses the returned JSON, assembles into records, one per interval,
     * with all generation values for that interval,
     * in increasing time order.
     * <p>
     * The outer and inner Lists implement RandomAccess.
     * <p>
     * This buffers its input for efficiency if not already a BufferedReader.
     *
     * @throws IOException  if there is an I/O problem or the data is malformed
     *
     * @param urlPrefix  prefix of URL to read from; never null
     * @return a non-null but possibly-empty in-order immutable List of rows,
     *    each of which is a non-null but possibly-empty in-order immutable List of fields,
     *    as if returned by parseBMRCSV().
     * @throws URISyntaxException
     */
    public static List<List<String>> parseBMRJSON(final URL urlPrefix)
        throws IOException, URISyntaxException
        {
    	// Compute full URL to request latest 24h of FUELINST data.
    	final Instant dayAgo = Instant.now().minusSeconds(24 * 60 * 60).truncatedTo(ChronoUnit.SECONDS);
    	final String suffix = URLEncoder.encode(dayAgo.toString(), StandardCharsets.US_ASCII);
    	final URL fullURL = new URI(urlPrefix.toString() + suffix).toURL();
System.err.println("Full JSON URL: " + fullURL);

        // Set up URL connection to fetch the data.
        final URLConnection conn = fullURL.openConnection();
        conn.setAllowUserInteraction(false);
        conn.setUseCaches(false); // Ensure that we get non-stale values each time.
        conn.setConnectTimeout(60000); // Set a long-ish connection timeout.
        conn.setReadTimeout(60000); // Set a long-ish read timeout.
//        conn.setRequestProperty("accept", "text/plain"); // or maybe specify JSON...

        try(final InputStreamReader is = new InputStreamReader(conn.getInputStream()))
            { return(DataUtils.parseBMRJSON(is)); }
        }

	/**Parse JSON file/stream and return as if parsed from pre-2024 CSV; never null but may be empty.
	 * Extracts up to 24h of data up to now from JSON streaming V1
	 * API, as URL-argument version.
	 * <p>
	 * This buffers its input for efficiency if not already a BufferedReader.
	 * <p>
	 * Returns an immutable List (by increasing times: each entry is one time)
	 * of generation across all sources at each time,
	 * in the old CSV format.
	 */
	public static List<List<String>> parseBMRJSON(final Reader r) throws IOException
		{
		Objects.requireNonNull(r);

		// Wrap a buffered reader around the input if not already so.
		try(final BufferedReader br = (r instanceof BufferedReader) ?
				(BufferedReader) r : new BufferedReader(r, 8192))
			{
            // Parse and group the JSON records by time.
			final SortedMap<Long, Map<String, FuelMWByTime>> records =
				convertStreamJSONToRecord(new JSONArray(new JSONTokener(r)), true);
//System.err.println("Records after convertStreamJSONToRecord(): " + records.size());

			final ArrayList result = new ArrayList<>(records.size());

			for(final Map.Entry<Long, Map<String, FuelMWByTime>> entry : records.entrySet())
				{


				}


			throw new RuntimeException("NOT IMPLEMENTED");
//			return(Collections.unmodifiableList(result));
			}
		}




	/**Generate an immutable old (pre-2024) FUELINST CSV record from FuelMWByTime records; never null.
	 * The records must all be from the same time instant/interval.
	 * <p>
	 * Fuel types specified in the template but missing from the map
	 * will have a generation of zero inserted.
	 * <p>
	 * Values in the map not named in the template will be quietly unused.
	 * <p>
	 * The template will typically come from
	 * <code>rawProperties.get(FUELINST.FUELINST_MAIN_PROPNAME_ROW_FIELDNAMES)</code>
	 * where <code>FUELINST_MAIN_PROPNAME_ROW_FIELDNAMES = "intensity.csv.fueltype"</code>.
	 * As of 2023-08-03 this was defined:
	 * <pre>
intensity.csv.fueltype=type,date,settlementperiod,timestamp,CCGT,OIL,COAL,NUCLEAR,WIND,PS,NPSHYD,OCGT,OTHER,INTFR,INTIRL,INTNED,INTEW,BIOMASS,INTNEM,INTELEC,INTIFA2,INTNSL,INTVKL
	 * </pre>
	 * Result looks something like if expressed as CSV:
	 * <pre>
FUELINST,20221104,20,20221104095000,14429,0,0,4649,8379,0,901,0,123,0,0,406,0,2235,122,0,0,1257
	 * </pre>
	 *
	 * @param template  for where to insert values by fuel type; non-null and non-empty
	 * @param generation  generation MW by fuel time; non-null, non-empty,
	 *     time and settlement period must be the same across all entries,
	 *     fuel type of value must match key
	 */
	public static List<String> generateOldCSVRecord(final String template,
			                                        final Map<String, FuelMWByTime> generation)
    	{
		Objects.requireNonNull(template);
		Objects.requireNonNull(generation);
        if(generation.isEmpty()) { throw new IllegalArgumentException("generation map cannot be empty"); }
        final String[] names = delimCSV.split(template);
        final int templateFieldCount = names.length;
		if(templateFieldCount <= 4) { throw new IllegalArgumentException("template too short"); }

        // Extract model FuelMWByTime for times.
        // These times must be the same for all values.
        final FuelMWByTime times = generation.values().iterator().next();

        final ArrayList<String> result = new ArrayList<>(templateFieldCount);

        // Prefix of form (as CSV):
        //    FUELINST,20221104,20,20221104095000,
        // Type is FUELINST.
        result.add("FUELINST");
        // Date as UTCDAYFILENAME_FORMAT
        final SimpleDateFormat sDF1 = new SimpleDateFormat(FUELINSTUtils.UTCDAYFILENAME_FORMAT);
        sDF1.setTimeZone(FUELINSTUtils.GMT_TIME_ZONE); // All timestamps should be GMT/UTC.
        result.add(sDF1.format(new Date(times.time)));
        // Settlement period.
        result.add(Integer.toString(times.settlementPeriod()));
        // Datetime as CSVTIMESTAMP_FORMAT
        final SimpleDateFormat sDF2 = new SimpleDateFormat(FUELINSTUtils.CSVTIMESTAMP_FORMAT);
        sDF2.setTimeZone(FUELINSTUtils.GMT_TIME_ZONE); // All timestamps should be GMT/UTC.
        result.add(sDF2.format(new Date(times.time)));

        for(int i = 4; i < templateFieldCount; ++i)
	        {
        	final String fuelType = names[i];
	        final FuelMWByTime f = generation.get(fuelType);
	        if(null == f)
		        {
		        result.add("0");
		        continue;
		        }
	        // Do some validation for consistency.
	        if(!fuelType.equals(f.fuelType())) { throw new IllegalArgumentException("generation map mismatched fuelType key/value for " + fuelType); }
	        if(times.time != f.time()) { throw new IllegalArgumentException("generation map mismatched time for " + fuelType); }
	        if(times.settlementPeriod() != f.settlementPeriod()) { throw new IllegalArgumentException("generation map mismatched settlementPeriod for " + fuelType); }
	        // Add in the generation.
	        result.add(Integer.toString(f.generation()));
	        }

		return(Collections.unmodifiableList(result));
		}
	}

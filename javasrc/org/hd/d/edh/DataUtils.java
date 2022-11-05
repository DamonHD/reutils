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

package org.hd.d.edh;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;


/**Data utilities.
 * Handles the bmreports style of CSV (with an initial HDR row and a trailing FTR row),
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

        final ArrayList<Map<String,String>> result = new ArrayList<Map<String,String>>(1024); // Allow for reasonable number of messages...

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
            final Map<String,String> m = new HashMap<String, String>(mappingsRaw.length*2);
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
		catch (ParseException e) { e.printStackTrace(); return(null); }
        final long lastExistingTimestamp = dle.getTime();

		// Compute newest/last timestamp in new data.
        // If it is not newer than the newest existing record
        // then there is nothing to append.
        final int nRowsNew = newRecords.size();
        final String lastNewTimestampRaw = newRecords.get(nRowsNew-1).get(3);
        Date dln;
        try { dln = timestampParser.parse(lastNewTimestampRaw); }
		catch (ParseException e) { e.printStackTrace(); return(null); }
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
            new ArrayList<List<String>>(nRowsExisting + newRecordsToAppend.size());
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
		catch (ParseException e) { e.printStackTrace(); return(null); }
        final long lastTimestamp = dl.getTime();
        final long oldestAllowedTimestamp = lastTimestamp - (maxHoursSpan*3600*1000) + 1;

        // Find timestamp of existing first record.
		final String firstTimestampRaw = parsedBMRCSV.get(0).get(3);
        Date df;
		try { df = timestampParser.parse(firstTimestampRaw); }
		catch (ParseException e) { e.printStackTrace(); return(null); }
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
    		catch (ParseException e) { e.printStackTrace(); return(null); }
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

    /**Validate BMR FUELINST data on a number of key points.
     * 
     * @param parsedBMRCSV  parsed FUELINST records (minus HDR and FTR);
     *             never null
     * @param newestPossibleValidRecord  no record may be newer than (timestamped after) this;
     *             positive and can be set to maximum long value to avoid the check
     * @param maxHoursSpan  maximum span of hours between newest and oldest record;
     *             strictly positive
     * 
     * @return false if any problem found.
     */
	public static boolean isValidBMRData(
			final List<List<String>> parsedBMRCSV,
			final long newestPossibleValidRecord,
			final int maxHoursSpan)
	    {
		if(null == parsedBMRCSV) { return(false); }

// Sample data...
//FUELINST,20221104,20,20221104095000,14429,0,0,4649,8379,0,901,0,123,0,0,406,0,2235,122,0,0,1257
//FUELINST,20221104,20,20221104095500,14289,0,0,4642,8513,0,899,1,122,0,0,406,0,2235,122,0,0,1257
//FUELINST,20221104,20,20221104100000,14269,0,0,4638,8646,0,883,0,125,0,0,301,0,2233,111,0,0,1257
//FUELINST,20221104,21,20221104100500,14217,0,0,4638,8788,0,849,0,146,0,0,129,0,2230,0,0,0,1257
//FUELINST,20221104,21,20221104101000,14209,0,0,4641,8936,0,848,0,141,0,0,129,0,2225,0,0,0,1257
//FUELINST,20221104,21,20221104101500,14133,0,0,4639,9047,0,848,0,136,0,0,130,0,2224,0,0,0,1257

		String lastTimestampRaw = FUELINST.FUELINST_TIMESTAMP_JUST_TOO_OLD;
		for(List<String> row : parsedBMRCSV)
			{
			if(null == row) { return(false); }
			if(row.size() < 5) { return(false); }
			if(!"FUELINST".equals(row.get(0))) { return(false); }
			final String timestampRaw = row.get(3);
			if(14 != timestampRaw.length()) { return(false); }
			// Check for strictly monotonic (lexical) ordering.
			// Avoids an expensive time conversion...
			if(lastTimestampRaw.compareTo(timestampRaw) >= 0) { return(false); }
			lastTimestampRaw = timestampRaw;
			}

		// Check that the last/newest record is not too new to be valid.
        final SimpleDateFormat timestampParser = FUELINSTUtils.getCSVTimestampParser();
        Date d;
		try { d = timestampParser.parse(lastTimestampRaw); }
		catch (ParseException e) { return(false); }
        final long finalTimestamp = d.getTime();
        if(finalTimestamp > newestPossibleValidRecord) { return(false); }


		// FIXME Validate timestamp range.


		// Did not find any problems.
		return(true);
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
     *    each of which is a non-null but possibly-empty in-order List of fields
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

        final InputStreamReader is = new InputStreamReader(conn.getInputStream());
        try { return(DataUtils.parseBMRCSV(is, headerCheck)); }
        finally { is.close(); }
        }
    
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
        final BufferedReader br = (r instanceof BufferedReader) ? (BufferedReader)r : new BufferedReader(r, 8192);

        // Read first (header) line/row.
        final String header = br.readLine();
        if(null == header) { throw new EOFException(); }
        final String hdrArray[] = delimCSV.split(header);
        if((hdrArray.length < 1) || !"HDR".equals(hdrArray[0]))
            { throw new ProtocolException("missing header (HDR) row"); }
        if((null != headerCheck) && !headerCheck.equals(hdrArray[1]))
            { throw new IOException("wrong header (HDR) type/description found"); }

        // Initially-empty result...
        final ArrayList<List<String>> result = new ArrayList<List<String>>();

        String row;
        while(null != (row = br.readLine()))
            {
            final String fields[] = delimCSV.split(row);
            if(fields.length < 1)
                { throw new IOException("unexpected empty row"); }
            final String type = fields[0];
            if(fields[0].isEmpty())
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

            // Package up row data (and make it unmodifiable).
            result.add(Collections.unmodifiableList(Arrays.asList(fields)));
            }

        result.trimToSize(); // Free resources...
        return(Collections.unmodifiableList(result)); // Make outer list immutable...
        }

    /**Load from file parsed FUELINST data in a form that parseBMRCSV() can read.
     * 
     * @throws IOException  if file not present or unreadable/unparseable.
     */
    public static List<List<String>> loadBMRCSV(final File longStoreFile)
        throws IOException
        {
    	if(null == longStoreFile) { throw new IllegalArgumentException(); }
    	try(FileReader fr = new FileReader(longStoreFile, FUELINSTUtils.FUELINST_CHARSET);
   	        BufferedReader br = new BufferedReader(fr))
			{ return(parseBMRCSV(fr, null)); }
        }

    /**Save/serialise parsed BMR FUELINST data in a form that parseBMRCSV() can read.
     * Generate ASCII CSV, with newlines to terminate rows.
     * <p>
     * Write atomically, world-readable.
     * 
     * @throws IOException  may be thrown if asked to serialise invalid (eg misordered or non-FUELINST) data
     */
    public static void saveBMRCSV(final List<List<String>> data, final File longStoreFile)
        throws IOException
	    {
	    	if(null == longStoreFile) { throw new IllegalArgumentException(); }
	    	final byte [] out = saveBMRCSV(data);
	    	replacePublishedFile(longStoreFile.getPath(), out);
	    }

    /**Save/serialise parsed BMR FUELINST data in a form that parseBMRCSV() can read.
     * Generate ASCII CSV, with newlines to terminate rows.
     *
     * @throws IOException  may be thrown if asked to serialise invalid (eg misordered or non-FUELINST) data
     */
    public static byte[] saveBMRCSV(final List<List<String>> data)
        throws IOException
	    {
    	// Write to an ASCII CSV byte[].
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Write header.
        baos.writeBytes("HDR\n".getBytes(FUELINSTUtils.FUELINST_CHARSET));
        final int rowCount = data.size();

        // Write body.
        for(final List<String> row : data)
	        {
	        final int fields = row.size();

	        // Do some simple validation.
	        // Abort if data not valid FUELINST format.
	        // TODO: more validation, eg of row time ordering, field format, etc.
	        if(fields < 4)
                { throw new IOException("too few fields for FUELINST"); }
	        if(!row.isEmpty() && !"FUELINST".equals(row.get(0)))
                { throw new IOException("not FUELINST records"); }

	        // Write row.
        	for(int f = 0; f < fields; ++f)
	        	{
	        	baos.writeBytes(row.get(f).getBytes(FUELINSTUtils.FUELINST_CHARSET));
	        	baos.write((f < fields-1) ? ',' : '\n');
	        	}
	        }

        // Write footer.
        baos.writeBytes(("FTR," + rowCount + "\n").getBytes(FUELINSTUtils.FUELINST_CHARSET));
        return(baos.toByteArray());
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
        final Map<String,String> result = new HashMap<String,String>(maxResultSize);
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

        final List<Map<String,String>> result = new ArrayList<Map<String,String>>(rows.size());
        for(final List<String> rowData : rows)
            { result.add(extractNamedFieldsByPositionFromRow(template, rowData)); }
        return(Collections.unmodifiableList(result));
        }


    /**Prefix used on temporary files, eg while doing atomic replacements.
     * This used to be in GlobalParams but we may even need it
     * while loading GlobalParams.
     */
    public static final String F_tmpPrefix = ".tmp.";

    /**Private moderate pseudo-random-number source for replacePublishedFile(); not null. */
    private static final Random rnd = new Random();

    /**Replaces an existing published file with a new one (see 3-arg version).
     * Is verbose when it replaces the file.
     */
    public static boolean replacePublishedFile(final String name, final byte data[])
        throws IOException
        { return(replacePublishedFile(name, data, false)); }

    /**Replaces an existing published file with a new one.
     * This replaces (atomically if possible) the existing file (if any)
     * of the given name, ensuring the correct permissions for
     * a file to be published with a Web server (ie basically
     * global read permissions), provided the following
     * conditions are met:
     * <p>
     * <ul>
     * <li>The filename extension is acceptable (not checked yet).
     * <li>The data array is non-null and not zero-length.
     * <li>The content of the data array is different to the file.
     * <li>All the required permissions are available.
     * </ul>
     * <p>
     * If the file is successfully replaced, true is returned.
     * <p>
     * If the file does not need replacing, false is returned.
     * <p>
     * If an error occurs, eg in the input data or during file
     * operations, an IOException is thrown.
     * <p>
     * This routine enforces locking so that only one such
     * operation may be performed at any one time.  This does
     * not avoid the possibility of externally-generated races.
     * <p>
     * The final file, once replaced, will be globally readable,
     * and writable by us.
     * <p>
     * (If the final component of the file starts with ".",
     * then the file will be accessible only by us.)
     *
     * @param quiet     if true then only error messages will be output
     */
    public static boolean replacePublishedFile(final String name, final byte data[],
                                               final boolean quiet)
        throws IOException
        {
        if((name == null) || (name.length() == 0))
            { throw new IOException("inappropriate file name"); }
        if((data == null) || (data.length == 0))
            { throw new IOException("inappropriate file content"); }

        final File extant = new File(name);

        // Lock the critical external bits against read and write updates.
        rPF_rwlock.writeLock().lock();
        try
            {
            // Use a temporary file in the same directory (and thus the same filesystem)
            // to avoid unexpectedly truncating the file when copying/moving it.
            File tempFile;
            for( ; ; )
                {
                tempFile = new File(extant.getParent(),
                    F_tmpPrefix +
                    Long.toString((rnd.nextLong() >>> 1),
                        Character.MAX_RADIX) /* +
                    "." +
                    extant.getName() */ ); // Avoid making very long names...
                if(tempFile.exists())
                    {
                    System.err.println("WARNING: FileTools.replacePublishedFile(): "+
                        "temporary file " + tempFile.getPath() +
                        " exists, looping...");
                    continue;
                    }
                break;
                }

            // Get extant file's length.
            final long oldLength = extant.length();
            // Should we overwrite it?
            boolean overwrite = (oldLength < 1); // Missing or zero length.

            // If length has changed, we should overwrite.
            if(data.length != oldLength) { overwrite = true; }

            // Now, if we haven't already decided to overwrite the file,
            // check the content.
            if(!overwrite)
                {
                try
                    {
                    final InputStream is = new BufferedInputStream(
                        new FileInputStream(extant));
                    try
                        {
                        final int l = data.length;
                        for(int i = 0; i < l; ++i)
                            {
                            if(data[i] != is.read())
                                { overwrite = true; break; }
                            }
                        }
                    finally
                        { is.close(); }
                    }
                catch(final FileNotFoundException e) { overwrite = true; }
                }

            // OK, we don't want to overwrite, so return.
            if(!overwrite) { return(false); }


            // OVERWRITE OLD FILE WITH NEW...

            try {
                // Write new temp file...
                // (Allow any IOException to terminate the function.)
                OutputStream os = new FileOutputStream(tempFile);
                os.write(data);
                // os.flush(); // Possibly avoid unnecessary premature disc flush here.
                os.close();
                os = null; // Help GC.
                if(tempFile.length() != data.length)
                    { new IOException("temp file not written correctly"); }

                final boolean globalRead = !extant.getName().startsWith(".");

                // Ensure that the temp file has the correct read permissions.
                tempFile.setReadable(true, !globalRead);
                tempFile.setWritable(true, true);

                // Warn if target does not have write perms, and try to add them.
                // This should allow us to replace it with the new file.
                final boolean alreadyExists = extant.exists();
                if(alreadyExists && !extant.canWrite())
                    {
                    System.err.println("FileTools.replacePublishedFile(): "+
                        "WARNING: " + name + " not writable.");
                    extant.setWritable(true, true);
                    if(!extant.canWrite())
                        {
                        throw new IOException("can't make target writable");
                        }
                    }

                // (Atomically) move tempFile to extant file.
                // Note that renameTo() may not be atomic
                // and we may have to remove the target file first.
                if(!tempFile.renameTo(extant))
                    {
                    // If the target already exists,
                    // then be prepared to explicitly delete it.
                    if(!alreadyExists || !extant.delete() || !tempFile.renameTo(extant))
                        { throw new IOException("renameTo/update of "+name+" failed"); }
                    if(!quiet) { System.err.println("[WARNING: atomic replacement not possible for: " + name + ": used explicit delete.]"); }
                    }

                if(extant.length() != data.length)
                    { new IOException("update of "+name+" failed"); }
                extant.setReadable(true, !globalRead);
                extant.setWritable(true, true);
                if(!quiet) { System.err.println("["+(alreadyExists?"Updated":"Created")+" " + name + "]"); }
                return(true); // All seems OK.
                }
            finally // Tidy up...
                {
                tempFile.delete(); // Remove the temp file.
//                Thread.yield(); // That was probably expensive; give up the CPU...
                }
            }
        finally { rPF_rwlock.writeLock().unlock(); }

        // Can't get here...
        }

    /**Private lock for replacePublishedFile().
     * We use a read/write lock to improve available concurrency.
     * <p>
     * TODO: We could extend this to a lock per distinct directory or filesystem.
     */
    private static final ReentrantReadWriteLock rPF_rwlock = new ReentrantReadWriteLock();

    /**Given a file, serialises an object to it.
     * This atomically replaces the target file if possible.
     *
     * @param gzipped  if true, the file is written GZIP-compressed
     *     to (usually) save significant space
     * @param quiet  if true, only outputs errors
     *
     * @throws IOException  if something bad happens
     */
    public static void serialiseToFile(final Object o,
                                       final File filename, final boolean gzipped,
                                       final boolean quiet)
        throws IOException
        {
        ObjectOutputStream oos = null;
        try {
            // Serialise to a byte[].
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // We may gzip the stream to remove some of the redundancy.
            final java.util.zip.GZIPOutputStream gos = gzipped ?
                new java.util.zip.GZIPOutputStream(baos) : null;
            oos = new ObjectOutputStream(gzipped ?
                (OutputStream)gos : (OutputStream)baos);
            oos.writeObject(o);
            oos.flush();
            if(gos != null) { gos.finish(); }
            final byte data[] = baos.toByteArray();

            // Now save to file using pure Java APIs.
            // Write-locked against other file replacements.
            replacePublishedFile(filename.getPath(), data, quiet);
            }
        finally
            {
            if(oos != null) { oos.close(); } // Free up OS resources.
            }
        }

    /**Given a file, deserialises an object from it.
     * This buffers the input for efficiency.
     *
     * @param gzipped  if true, the file is assumed to be in GZIP
     *     format and the stream is decompressed on the fly
     * @throws IOException  if something bad happens
     */
    public static Object deserialiseFromFile(final File filename, final boolean gzipped)
        throws IOException
        {
        ObjectInputStream ois = null;

        // Lock out concurrent published-file updates.
        rPF_rwlock.readLock().lock();
        try {
            // Need to reload from disc.
            InputStream is = new BufferedInputStream(new FileInputStream(
                filename), 8192);
            if(gzipped) { is = new java.util.zip.GZIPInputStream(is, 8192); }
            ois = new ObjectInputStream(is);
            return(ois.readObject());
            }
        catch(final ClassNotFoundException e)
            { throw new IOException("ClassNotFoundException deserialising file ``"+filename+"'': " + e.getMessage()); }
        catch(final Exception e)
            {
            final IOException err = new IOException("unexpected exception deserialising file ``"+filename+"'': " + e.getMessage());
            err.initCause(e);
            throw err;
            }
        finally
            {
            // Potentially allow updates again.
            rPF_rwlock.readLock().unlock();

            // Ensure that we release OS file-handling resources.
            if(ois != null) { ois.close(); }
            }
        }
    }

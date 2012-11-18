package remoteIntensity;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.hd.d.edh.DataUtils;
import org.hd.d.edh.RemoteGenerationIntensity;
import org.hd.d.edh.Tuple;

/**IRL (Irish grid) remote-intensity fetcher.
<pre>
http://www.eirgrid.com/operations/systemperformancedata/download.jsp?download=Y&startdate=06/11/2012&enddate=06/11/2012&proc=data_pack.getco2intensityforadayiphone&templatename=CO2%20Intensity&columnnames=Time,g%20CO&#8322;/KWh&prevurl=http://www.eirgrid.com/operations/systemperformancedata/co2intensity/
</pre>
 */
public final class IRLgi implements RemoteGenerationIntensity
    {
    @Override public String gridName() { return("IRL"); }

    /**Remote URL to retrieve data from with POST; not null nor empty. */
    private static final String URL = "http://www.eirgrid.com/operations/systemperformancedata/download.jsp";

    /**Character encoding for the POST body; never null nor empty. */
    private static final String POST_ENCODING = "UTF-8";

    /**PWD-relative path of cache file; not null.
     * The cache file contains a tuple of serialised Date UTC time that the value was fetched computed,
     * followed by the Integer non-negative intensity (gCO2/kWh) or null to cache a -ve result if none was available.
     * <p>
     * The cache file is NOT compressed.
     */
    private static final File CACHE_PATH = new File(RGI_CACHE_DIR_BASE_PATH, "IRL.ser");

    /**Maximum time to cache IRL intensity result for (milliseconds); strictly positive.
     * Longer values will reduce amount that remote server is pestered,
     * and cost of less timely intensity figures.
     * <p>
     * Should probably be just less than recalculation interval at server.
     */
    private static int MAX_CACHE_MS = 290 * 1000; // Just under 5 minutes.

    /**Retrieve current / latest-recent generation intensity in gCO2/kWh; non-negative. */
    @Override public int getLatest() throws IOException
        {
        final long now = System.currentTimeMillis();

        // Attempt to retrieve from persistent cache, if any.
        if(CACHE_PATH.exists())
            {
            try
                {
                final Tuple.Pair<Date, Integer> lastStatus = (Tuple.Pair<Date, Integer>) DataUtils.deserialiseFromFile(CACHE_PATH, false);
                if((null != lastStatus) && ((now - lastStatus.first.getTime()) < MAX_CACHE_MS))
                    {
                    if(null == lastStatus.second) { throw new IOException("could not fetch/compute"); }
                    if(lastStatus.second >= 0) { return(lastStatus.second); }
                    System.err.println("Ignoring bad cached intensity for IRL: " + lastStatus.second);
                    // Fall through to disregard bad (-ve) persisted value.
                    }
                }
            catch(final Exception e)
                {
                System.err.println("Cannot retrieve cached intensity for IRL: " + e.getMessage());
                // Fall through to continue.
                }
            }

        // FETCH/COMPUTE

        // Get today's (UTC) date.
        final Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        cal.setTimeInMillis(now);
        final SimpleDateFormat sDF = new SimpleDateFormat("dd/MM/yyyy");
        sDF.setCalendar(cal);
        final String todayDDMMYYYY = sDF.format(cal.getTime());

        // Parameters and values to send...
        final String[][] params =
            {
                { "download", "Y" },
                { "startdate", todayDDMMYYYY },
                { "enddate", todayDDMMYYYY },
                { "proc", "data_pack.getco2intensityforadayiphone" }, // Would be more efficient just to request last hour or so.
                { "templatename", "CO2 Intensity" },
                { "columnnames", "Time,gCO2/KWh" },
            };
        final StringBuilder sb = new StringBuilder(256);
        for(int i = params.length; --i >= 0; )
            {
            // Send parameters down the wire...
            sb.append(URLEncoder.encode(params[i][0], POST_ENCODING));
            sb.append("=");
            sb.append(URLEncoder.encode(params[i][1], POST_ENCODING));
            if(0 != i) { sb.append("&"); }
            }

        // Set up URL connection to fetch the data.
        final URL url = new URL(URL + "?" + sb.toString());
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoInput(true);
        conn.setRequestMethod("GET");
        conn.setAllowUserInteraction(false);
        conn.setUseCaches(false); // Ensure non-stale values each time.
        conn.setConnectTimeout(60000); // Set a long-ish connection timeout.
        conn.setReadTimeout(60000); // Set a long-ish read timeout.

        try
            {
            // Read the response.
            // Use last non-negative parseable number in second column.
            int lastValue = -1;
            final BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            try
                {
                String row;
                while(null != (row = br.readLine()))
                    {
                    if(row.startsWith("Time")) { continue; }
                    final String cols[] = DataUtils.delimCSV.split(row);
                    if(cols.length < 2) { continue; }
                    try
                        {
                        final int v = Math.round(Float.parseFloat(cols[1].trim()));
                        if(v >= 0) { lastValue = v; }
                        }
                    catch(final NumberFormatException e) { /* Ignore non-numbers, eg "null". */ }
                    }
                }
            finally { br.close(); }

            if(lastValue < 0) { throw new IOException("no valid value found"); }

            // Persist/cache value.
            if(RGI_CACHE_DIR_BASE_PATH.exists())
                { DataUtils.serialiseToFile(new Tuple.Pair<Date, Integer>(new Date(), lastValue), CACHE_PATH, false, true); }
            else
                { System.err.println("No parent dir "+RGI_CACHE_DIR_BASE_PATH.getCanonicalPath()+" to cache intensity in "+CACHE_PATH); }

            return(lastValue);
            }
        catch(final IOException e)
            {
            // Negatively cache failure if parent dir exists (silently ignore if not)...
            if(RGI_CACHE_DIR_BASE_PATH.exists())
                { DataUtils.serialiseToFile(new Tuple.Pair<Date, Integer>(new Date(), null), CACHE_PATH, false, true); }
            // Rethrow...
            throw e;
            }
        }
    }

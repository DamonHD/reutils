package remoteIntensity;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.hd.d.edh.RemoteGenerationIntensity;

/**IRL (Irish grid) remote-intensity fetcher.
 * Does POST to <code>http://www.eirgrid.com/operations/systemperformancedata/co2intensity/</code>
 * <p>
 * Sample HTML to be emulated:
<pre>
&lt;form name="downloadForm" action="http://www.eirgrid.com/operations/systemperformancedata/co2intensity/" method="post"&gt;
&lt;input type="hidden" name="download" value="Y" /&gt;
&lt;input type="text" name="downloadstartdate" value="06/11/2012" /&gt;
&lt;input type="text" name="downloadenddate" value="06/11/2012" /&gt;
&lt;input type="hidden" name="proc" value="data_pack.getco2intensityforadayiphone" /&gt;
&lt;input type="hidden" name="templatename" value="CO2 Intensity" /&gt;
&lt;input type="hidden" name="columnnames" value="Time,g CO&#8322;/KWh" /&gt;
&lt;input type="hidden" name="prevurl" value="http://www.eirgrid.com/operations/systemperformancedata/co2intensity/" /&gt;
&lt;input type="submit" value="Download" class="submit" /&gt;
&lt;/form&gt;
</pre>
 */
public final class IRLgi implements RemoteGenerationIntensity
    {
    @Override public String gridName() { return("IRL"); }

    /**Remote URL to retrieve data from with POST; not null nor empty. */
    private static final String URL = "http://www.eirgrid.com/operations/systemperformancedata/co2intensity/";

    /**Character encoding for the POST body; never null nor empty. */
    private static final String POST_ENCODING = "UTF-8";

    /**Retrieve current / latest-recent generation intensity; non-negative. */
    @Override public int getLatest() throws IOException
        {
        // Set up URL connection to fetch the data.
        final URL url = new URL(URL);
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setAllowUserInteraction(false);
        conn.setUseCaches(false); // Ensure that we get non-stale values each time.
        conn.setConnectTimeout(60000); // Set a long-ish connection timeout.
        conn.setReadTimeout(60000); // Set a long-ish read timeout.

        // Parameters and values to send...
        final String todayDDMMYYYY = "06/11/2012";
        final String[][] params =
            {
                { "download", "Y" },
                { "downloadstartdate", todayDDMMYYYY },
                { "downloadenddate", todayDDMMYYYY },
                { "proc", "data_pack.getco2intensityforadayiphone" },
                { "templatename", "CO2 Intensity" },
                { "columnnames", "Time,g CO&#8322;/KWh" },
                { "download", "Y" },
            };
        final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream()));
        try
            {
            for(int i = params.length; --i >= 0; )
                {
                // Send parameters down the wire...
                bw.write(URLEncoder.encode(params[i][0], POST_ENCODING));
                    bw.write("=");
                    bw.write(URLEncoder.encode(params[i][1], POST_ENCODING));
                if(0 != i) { bw.write("&"); }
                }
            bw.write("\r\n");
            bw.flush();

            // Read the response...
            final InputStreamReader is = new InputStreamReader(conn.getInputStream());
            try { /* ... */ }
            finally { is.close(); }
            }
        finally { bw.close(); }




        throw new IOException("NOT IMPLEMENTED");
        }
    }

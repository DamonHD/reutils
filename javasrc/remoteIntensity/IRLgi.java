package remoteIntensity;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

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

    @Override public int getLatest() throws IOException
        {
        // Set up URL connection to fetch the data.
        final URL url = new URL(URL);
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setAllowUserInteraction(false);
        conn.setUseCaches(false); // Ensure that we get non-stale values each time.
        conn.setConnectTimeout(60000); // Set a long-ish connection timeout.
        conn.setReadTimeout(60000); // Set a long-ish read timeout.

        final InputStreamReader is = new InputStreamReader(conn.getInputStream());
        try { /* ... */ }
        finally { is.close(); }




        throw new IOException("NOT IMPLEMENTED");
        }
    }

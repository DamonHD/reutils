package remoteIntensity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.hd.d.edh.RemoteGenerationIntensity;

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

    /**Retrieve current / latest-recent generation intensity; non-negative. */
    @Override public int getLatest() throws IOException
        {
        // Parameters and values to send...
        final String todayDDMMYYYY = "05/11/2012"; // FIXME
        final String[][] params =
            {
                { "download", "Y" },
                { "startdate", todayDDMMYYYY },
                { "enddate", todayDDMMYYYY },
                { "proc", "data_pack.getco2intensityforadayiphone" },
                { "templatename", "CO2 Intensity" },
                //{ "columnnames", "Time,gCO2/KWh" },
                //{ "prevurl", URL },
                //{ "submit", "Download" },
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
        conn.setUseCaches(false); // Ensure that we get non-stale values each time.
        conn.setConnectTimeout(60000); // Set a long-ish connection timeout.
        conn.setReadTimeout(60000); // Set a long-ish read timeout.

        // Read the response...
        final BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        try
            {
            String row;
            while(null != (row = br.readLine()))
                {

                System.out.println(row);

                // TODO

                }


            // TODO



            }
        finally { br.close(); }




        throw new IOException("NOT IMPLEMENTED");
        }
    }

package remoteIntensity;

import java.io.IOException;

import org.hd.d.edh.RemoteGenerationIntensity;

/**IRL (Irish grid) remote-intensity fetcher; never succeeds. */
public final class IRLgi implements RemoteGenerationIntensity
    {
    @Override public String gridName() { return("IRL"); }

    @Override public int getLatest() throws IOException
        {
        throw new IOException("NOT IMPLEMENTED");
        }
    }

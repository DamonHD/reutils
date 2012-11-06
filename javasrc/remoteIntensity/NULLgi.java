package remoteIntensity;

import java.io.IOException;

import org.hd.d.edh.RemoteGenerationIntensity;

/**NULL remote-intensity fetcher; never succeeds. */
public final class NULLgi implements RemoteGenerationIntensity
    {
    @Override public String gridName() { return("NULL"); }
    @Override public int getLatest() throws IOException { throw new IOException(); }
    }

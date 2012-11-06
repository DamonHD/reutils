package org.hd.d.edh;

import java.io.IOException;

/**Get live generation intensity of remote grid.
 * Used for estimating intensity of inbound/imported energy across an interconnector.
 */
public interface LiveRemoteIntensity
    {
    /**Gets remote grid human-readable name/abbreviation; never null nor empty. */
    public String gridName();

    /**Get latest intensity in gCO2/MWh; non-negative.
     * Attempts to get recent intensity,
     * or throws an exception if no reasonably recent value available, eg within last hour or two.
     *
     * @return latest available recent/current generation intensity (gCO2/MWh)
     * @throws IOException if no sufficiently-recent or reliable value is available
     */
    public int getLatest() throws IOException;
    }

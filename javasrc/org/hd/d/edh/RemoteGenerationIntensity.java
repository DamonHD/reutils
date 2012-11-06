package org.hd.d.edh;

import java.io.IOException;

/**Get live generation intensity of remote grid.
 * Used for estimating intensity of inbound/imported energy across an interconnector.
 * <p>
 * Instances may not be internally thread-safe
 * but separate instances should be safe to run in separate threads,
 * eg to gather date on several remote grids concurrently.
 */
public interface RemoteGenerationIntensity
    {
    /**Gets remote grid human-readable name/abbreviation; never null nor empty. */
    public String gridName();

    /**Get latest intensity in gCO2/kWh; non-negative.
     * Attempts to get recent intensity,
     * or throws an exception if no reasonably recent value available, eg within last hour or two.
     * <p>
     * May take a while to run, so may often by run in a thread.
     *
     * @return latest available recent/current generation intensity (gCO2/MWh)
     * @throws IOException if no sufficiently-recent or reliable value is available
     */
    public int getLatest() throws IOException;
    }

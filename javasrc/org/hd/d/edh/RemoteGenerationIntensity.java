package org.hd.d.edh;

import java.io.File;
import java.io.IOException;

/**Get live generation intensity of remote grid.
 * Used for estimating intensity of inbound/imported energy across an interconnector.
 * <p>
 * Instances may not be internally thread-safe
 * but separate instances should be safe to run in separate threads,
 * eg to gather date on several remote grids concurrently.
 * <p>
 * Implementations may use persistence/cacheing to reduce the impact on remote servers.
 */
public interface RemoteGenerationIntensity
    {
    /**Remote grid human-readable name/abbreviation unique to this grid/implementation; never null nor empty.
     * Should be suitable/safe for use as part of a filename,
     * in particular avoiding whitespace and filesystem and shell meta-characters
     * such as '/', ':', '?', '*',
     * and should not differ from other instance/grid names only in case.
     */
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


    /**Base directory path relative to PWD for an RGI implementation to cache its state; non-null.
     * Within that directory any cache file name should be prefixed with the grid name
     * and optionally some other string for uniqueness.
     * <p>
     * Generally this path should not be automatically created,
     * and its presence indicates that the user wants cacheing.
     */
    static final File RGI_CACHE_DIR_BASE_PATH = new File(".cacheRGI");
    }

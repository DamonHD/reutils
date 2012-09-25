/*
Copyright (c) 2008-2012, Damon Hart-Davis
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

// We try to depend as little as possible on other classes in the
// system as we need to initialise early during bootstrapping.
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


/**This is a set of properties local to the JVM.
 * These properties are define parameters such as CO2-intensities
 * and parser mappings.
 * <p>
 * These properties are not Serializable.
 * <p>
 * These properties can be checked for being up-to-date
 * from time to time, and the in-memory values will be
 * refreshed if necessary.
 * <p>
 * Values set purely from system properties may be collected
 * once at class creation and not again.
 * <p>
 * All access to values is via static methods, and is
 * synchronized (on the class object) to ensure thread-safety.
 * <p>
 * This is necessarily a rag-tag of values used all over the system,
 * and will be loaded very early and should not depend on much of the
 * rest of the system if at all.
 */
public final class MainProperties
    {
    /**Load properties for the given InputStream.
     * In case of any problem reading the stream for properties
     * an IOException is thrown.
     * <p>
     * Also, if the property ``end'' is not defined with the
     * value OK, an EOFException is thrown to indicate that the
     * file was not read completely (eg if we catch it in the
     * middle of a save).  So make sure that files are defined
     * with this property at the end.
     *
     * @throws IOException  in case of general I/O problems
     * @throws EOFException  in case of missing <sample>end=OK</sample> value
     */
    public static Properties loadProperties(final InputStream is)
        throws IOException
        {
        final Properties props = new Properties();
        props.load(is);
        if(!"OK".equals(props.getProperty("end")))
            { throw new EOFException("missing end=OK at end of data: possibly corrupt or not fully saved?"); }
        return(props);
        }

    /**Minimum interval between rechecks of main properties file content (ms).
     * Since much of our data comes from the filesystem and many filesystems'
     * timestamp granularity is about one second, polling much faster than this
     * is pointless and may simply end up blocking the caller and wasting
     * CPU pointlessly.
     * <p>
     * (Arguably the only way that system properties can be changed is by
     * programmatic intervention want so we don't want to recheck such values
     * at all for fear of setting up a covert channel as well as just wasting
     * CPU time.)
     * <p>
     * We pick a prime-ish retry interval to try to avoid collisions with other
     * regular events.
     * <p>
     * A value of a few seconds to a few tens of seconds is probably appropriate.
     */
    private static final int MIN_RECHECK_MS = 33007;

    /**The last time that we checked/refreshed the data that we hold.
     * We update this whenever we do a check whether we changed any values;
     * this avoids possibly expensive and unnecessary rechecking.
     * This value is private to us.
     * <p>
     * Initially zero to force immediate read on first use of these properties.
     */
    private static long lastRecheck;

    /**This routine rechecks and refreshes the data items if necessary.
     * This is called internally by all getter methods to lazily ensure that
     * data items are up to date.
     * <p>
     * When this has finished it sets lastRecheck and possibly timestamp;
     * if this fails to complete the update neither (but especially lastRecheck)
     * may be set.
     */
    private static synchronized void _recheck()
        {
        final long now = System.currentTimeMillis();

        if((now - lastRecheck) <= MIN_RECHECK_MS)
            {
            // Not time for a recheck yet...
            return;
            }

        try
            {
            // Get location of main properties file, if extant.
            final File pf = new File(getMainPropertiesFilename());

            final long fileTimestamp = pf.lastModified();

            // Warn user if file not found,
            // and suggest what settings they might use.
            if(fileTimestamp <= 0)
                {
System.err.println("WARNING: main properties file not found.");
System.err.println("  Set name in system property: " + PNAME_MAIN_PROPERTIES_FILENAME);
System.err.println("  or put file at: `" + DEFAULT_MAIN_PROPERTIES_FILENAME + "' .");
                }

            // Warn if the timestamp has gone backwards
            // (or to zero because the file has disappeared).
            if(fileTimestamp < timestamp)
                {
                System.err.println("WARNING: timestamp on local properties file has gone backwards or file has gone: " + pf);
                }

            // If the file has changed and is newer than our in-memory copy,
            // read it in.
            //
            // If we have problems reading the info,
            // we abort without updating any timestamps so that can try again
            // immediately.
            // We may have updated some of the properties...
            if(fileTimestamp > timestamp)
                {
                Properties props; // The properties that we load.
                try {
                    // Read the properties file in buffered chunks for efficiency.
                    final InputStream is = new BufferedInputStream(
                        new FileInputStream(pf));
                    try { props = loadProperties(is); }
                    finally { is.close(); } // Release the handle ASAP...
                    }
                catch(final IOException e)
                    {
                    // If we encounter an I/O problem,
                    // log it to System.err,
                    // and return without updating our timestamps.
                    e.printStackTrace();
                    return;
                    }

                // Copy to (immutable) Map suitable to hand to callers.
                // Trim and intern() the keys and values.
                final Map<String,String> raw = new HashMap<String,String>(props.size());
                for(final Object k : props.keySet())
                    {
                    raw.put(((String) k).trim().intern(), ((String) props.get(k)).trim().trim()); /* Force error if not pure String. */
                    }
                rawProperties = Collections.unmodifiableMap(raw);
                assert(!rawProperties.isEmpty()); // Should at least contain end -> OK.

                // Temporaries to help in parsing for specialised fields.
//                int iTmp;
//                long lTmp;
//                String sTmp;

                // Get dir for read-only configuration files.
                confDir = props.getProperty(PNAME_CONF_DIR);

                // Get dir for data files.
                dataDir = props.getProperty(PNAME_DATA_DIR);

                // Add specialised handling of property values below here...
                }

            // Save the timestamp from the properties file
            // having successfully completed all that we wanted to.
            timestamp = fileTimestamp;

            // Update completed.
            lastRecheck = now;
            }
        catch(final SecurityException e)
            {
            if(!reportedSecurityException)
                {
                System.err.println("NOTE: no access to local properties file: " + e.getMessage());
                reportedSecurityException = true;
                }

            // Quietly absorb the security/permissions error,
            // and defer retrying for the normal length of time.
            lastRecheck = now;
            }
        }

    /**Set true if we've noted a SecurityException trying to read LocalProperties.
     * Marked volatile for lock-free thread-safe access.
     */
    private static volatile boolean reportedSecurityException;

    /**The last time our loaded values changed.
     * This can be used to cascade changes to parts of the system that depend
     * on these LocalProps.
     * <p>
     * Will never be newer than lastRecheck.
     * <p>
     * Initially zero, to indicate no (file-based) values loaded.
     * <p>
     * The timestamp is the latest timestamp of any of the files read to
     * make up the dataset.
     */
    private static long timestamp;

    /**Returns the timestamp of the current set of data values.
     * Forces a (re)load/refresh of data if necessary.
     * Zero indicates that no (file-based) values have yet been loaded.
     * <p>
     * The timestamp is the latest timestamp of any of the files read to
     * make up the dataset.
     */
    public static synchronized long getTimestamp()
        { _recheck(); return(timestamp); }

    /**Default suffix of main properties files, including the dot. */
    public static final String DEFAULT_PROPERTIES_SUFFIX = ".properties";
    /**Default filename of main properties file. */
    public static final String DEFAULT_MAIN_PROPERTIES_FILENAME =
        "main" + DEFAULT_PROPERTIES_SUFFIX;
    /**Name of props file for main config information property. */
    public static final String PNAME_MAIN_PROPERTIES_FILENAME = "org.hd.d.edh.mainPropertiesFilename";
    /**Cache of name of props file for main config information; null if no explicit system property value set. */
    private static final String mainPropertiesFilenameFromSystemProperties;
    /**Initialise localPropsFilenameFromSystemProperties. */
    static
        {
        String s = null;
        try { s = System.getProperty(PNAME_MAIN_PROPERTIES_FILENAME); }
        catch(final SecurityException e) { /* Treat lack of permission as if lack of value. */ }
        mainPropertiesFilenameFromSystemProperties = s;
        }
    /**Name of props file for main config information; never null.
     * It may be difficult for the system to run without this parameter
     * set correctly or if the corresponding file is not correctly set up.
     * <p>
     * This will specify a file in the current working directory if none
     * explicitly specified, and will deliver a warning if it cannot be found.
     * <p>
     * This might be bad news if the current working directory changes
     * or is inappropriate or unhelpful, so set the system property explicitly
     * if this is the case.
     * <p>
     * This does not check that the specified file actually exists.
     * <p>
     * We only use this internally; for example this may be inefficient to
     * continually recheck and we don't necessarily want to publicise the
     * actual location more than we must!
     */
    private static synchronized String getMainPropertiesFilename()
        {
        // Use explicit value if set.
        String result = mainPropertiesFilenameFromSystemProperties;
        // Else try to use canonicalised default name relative to pwd.
        if(result == null)
            {
            try {
                result = (new File(DEFAULT_MAIN_PROPERTIES_FILENAME)).getCanonicalPath();
                }
            catch(final IOException e)
                {
                System.err.println("WARNING: cannot canonicalise main properties filename " + DEFAULT_MAIN_PROPERTIES_FILENAME);
                }
            }
        // Else try to use relative name directly.
        if(result == null)
            { result = DEFAULT_MAIN_PROPERTIES_FILENAME; }
        return(result);
        }

    /**Local cache of name of raw (immutable) properties Map but may be empty. */
    private static Map<String,String> rawProperties = Collections.emptyMap();
    /**Get raw uninterpreted immutable copy of properties as a Map; never null but may be empty.
     * May be empty if no properties available to be correctly loaded.
     * <p>
     * Keys and values are trim()med of leading and trailing whitespace and intern()ed.
     */
    public static synchronized Map<String,String> getRawProperties()
        {
        // Force properties reload if necessary.
        _recheck();
        assert(rawProperties != null);
        return(rawProperties);
        }

    /**Default name of (relative) subdirectory for local data files; never null. */
    public static final String FS_DATA_ROOT = "data";
    /**Name of local properties parameter for name of subdirectory for (mainly read-only) data files. */
    public static final String PNAME_DATA_DIR = "edh.data.dir";
    /**Local cache of name of subdirectory for data files. */
    private static String dataDir;
    /**Get name of subdirectory for data files; never null.
     * If the PNAME_DATA_DIR local property is set then it is used,
     * else the CoreConsts.FS_DATA_ROOT value is used, canonicalised
     * if possible to give more meaningful error message at least.
     */
    public static synchronized String getDataDir()
        {
        // Force properties reload if necessary.
        _recheck();

        // Use local property if set, ...
        String result = dataDir;
        // ... else use default from CoreConsts.
        if(result == null) { result = FS_DATA_ROOT; }

        // If the directory name is not absolute,
        // make it relative to the directory the local properties are in.
        if(!(new File(result)).isAbsolute())
            {
            final File lpDir = new File(getMainPropertiesFilename()).getParentFile();
            result = (new File(lpDir, result)).getAbsolutePath();
            }

        // In any case, try to canonicalise the path before returning it.
        try { result = (new File(result)).getCanonicalPath(); }
        catch(final IOException e) { } // In case of error use non-canonical form...

        return(result);
        }

    /**Default name of (relative) subdirectory for local configuration files; never null. */
    public static final String FS_CONF_ROOT = "conf";
    /**Name of local properties parameter for name of subdirectory for read-only conf files. */
    public static final String PNAME_CONF_DIR = "edh.config.dir";
    /**Local cache of name of subdirectory for read-only conf files. */
    private static String confDir;
    /**Get name of subdirectory for read-only conf files; never null.
     * If the PNAME_CONF_DIR local property is set then it is used,
     * else the CoreConsts.FS_CONF_ROOT value is used, canonicalised
     * if possible to give more meaningful error message at least.
     */
    public static synchronized String getConfDir()
        {
        // Force properties reload if necessary.
        _recheck();

        // Use local property if set, ...
        String result = confDir;
        // ... else use default from CoreConsts.
        if(result == null) { result = FS_CONF_ROOT; }

        // If the directory name is not absolute,
        // make it relative to the directory the local properties are in.
        if(!(new File(result)).isAbsolute())
            {
            final File lpDir = new File(getMainPropertiesFilename()).getParentFile();
            result = (new File(lpDir, result)).getAbsolutePath();
            }

        // In any case, try to canonicalise the path before returning it.
        try { result = (new File(result)).getCanonicalPath(); }
        catch(final IOException e) { } // In case of error use non-canonical form...

        return(result);
        }

    }

/*
Copyright (c) 2008-2009, Damon Hart-Davis
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import winterwell.jtwitter.Twitter;



/**Twitter Utilities.
 * Handles some common interactions with Twitter.
 */
public final class TwitterUtils
    {
    /**Prevent creation of an instance. */
    private TwitterUtils() { }

    /**Maximum Twitter message length (Tweet) in (ASCII) characters. */
    public static final int MAX_TWEET_CHARS = 140;

    /**Property name prefix (needs traffic-light colour appended) for Twitter status messages; not null. */
    public static final String PNAME_PREFIX_TWITTER_TRAFFICLIGHT_STATUS_MESSAGES = "Twitter.trafficlight.status.";

    /**Property name prefix (needs traffic-light colour appended) for Twitter status messages when using historical data, ie predicting; not null. */
    public static final String PNAME_PREFIX_TWITTER_TRAFFICLIGHT_PREDICTION_MESSAGES = "Twitter.trafficlight.prediction.";

    /**Property name for Twitter user; not null. */
    public static final String PNAME_TWITTER_USERNAME = "Twitter.username";

    /**Property name for file containing Twitter password; not null. */
    public static final String PNAME_TWITTER_PASSWORD_FILENAME = "Twitter.passfile";

    /**Property name for alternate file containing Twitter password; not null. */
    public static final String PNAME_TWITTER_PASSWORD_FILENAME2 = "Twitter.passfile2";

    /**Property name for minimum gap between Tweets in minutes (non-negative); not null. */
    public static final String PNAME_TWITTER_MIN_GAP_MINS = "Twitter.minGapMins";

    /**Immutable class containing Twitter handle, user ID and read-only flag. */
    public static final class TwitterDetails
        {
        /**User name on Twitter; never null nor empty. */
        public final String username;
        /**Handle; never null. */
        public final Twitter handle;
        /**True if we know the handle to be read-only, eg because we have no password. */
        public final boolean readOnly;

        /**Create an instance. */
        public TwitterDetails(final String username, final Twitter handle, final boolean readOnly)
            {
            if((null == username) || username.isEmpty()) { throw new IllegalArgumentException(); }
            if(null == handle) { throw new IllegalArgumentException(); }
            this.username = username;
            this.handle = handle;
            this.readOnly = readOnly;
            }
        }

    /**Get the specified non-empty Twitter user name or null if none. */
    public static String getTwitterUsername()
        {
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        final String tUsername = rawProperties.get(PNAME_TWITTER_USERNAME);
        // Transform empty user name to null.
        if((null != tUsername) && tUsername.isEmpty()) { return(null); }
        return(tUsername);
        }

    /**Get Twitter handle for updates; null if nothing suitable set up.
     * May return a read-only handle for testing
     * if that is permitted by the argument.
     * <p>
     * A read/write handle is a valid return value if read-only is allowed.
     * <p>
     * We may test that we actually have authenticated (read/write) access
     * before claiming it as such,
     * so obtaining one of these may require network access and significant time.
     */
    public static TwitterDetails getTwitterHandle(final boolean allowReadOnly)
        {
        final String tUsername = getTwitterUsername();
        // We need at least a Twitter user ID to do anything; return null if we don't have one.
        if(null == tUsername) { return(null); }

        // Try first the primary password file, then the alternate if need be.
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        final String pass1 = getPasswordFromFile(rawProperties.get(PNAME_TWITTER_PASSWORD_FILENAME), allowReadOnly);
        final String pass = (pass1 != null) ? pass1 : getPasswordFromFile(rawProperties.get(PNAME_TWITTER_PASSWORD_FILENAME2), allowReadOnly);

        // If we have no password then we are definitely read-only.
        final boolean noWriteAccess = (pass == null);
        // If definitely read-only and that is not acceptable then return null.
        if(noWriteAccess && !allowReadOnly) { return(null); }

        return(new TwitterDetails(tUsername, new Twitter(tUsername, pass), noWriteAccess));
        }

    /**Extract a (non-empty) password from the specified file, or null if none or if the filename is bad.
     * This does not throw an exception if it cannot find or open the specified file
     * (or the file name is null or empty)
     * of it the file does not contain a password; for all these cases null is returned.
     * <p>
     * The password must be on the first line.
     *
     * @param passwordFilename  name of file containing password or null/empty if none
     * @param quiet  if true then keep quiet about file errors
     * @return non-null, non-empty password
     */
    private static String getPasswordFromFile(final String passwordFilename, final boolean quiet)
        {
        // Null/empty file name results in quiet return of null.
        if((null == passwordFilename) || passwordFilename.trim().isEmpty()) { return(null); }

        final File f = new File(passwordFilename);
        if(!f.canRead())
            {
            if(!quiet) { System.err.println("Cannot open pass file for reading: " + f); }
            return(null);
            }

        try
            {
            final BufferedReader r =  new BufferedReader(new FileReader(f));
            try
                {
                final String firstLine = r.readLine();
                if((null == firstLine) || firstLine.trim().isEmpty()) { return(null); }
                // Return non-null non-empty password.
                return(firstLine);
                }
            finally { r.close(); /* Release resources. */ }
            }
        // In case of error whinge but continue.
        catch(final Exception e)
            {
            if(!quiet) { e.printStackTrace(); }
            return(null);
            }
        }

    /**Attempt to update the displayed Twitter status if necessary.
     * Send a new Tweet only if we think the message/status changed since we last sent one,
     * and if it no longer matches what is actually at Twitter,
     * so as to eliminate spurious Tweets.
     * <p>
     * We should take pains to avoid unnecessary annoying/expensive updates.
     *
     * @param td  non-null, non-read-only Twitter handle
     * @param TwitterCacheFileName  if non-null is the location to cache twitter status messages;
     *     if the new status supplied is the same as the cached value then we won't send an update
     * @param statusMessage  short (max 140 chars) Twitter status message; never null
     */
    public static void setTwitterStatusIfChanged(final TwitterUtils.TwitterDetails td,
                                                 final File TwitterCacheFileName,
                                                 final String statusMessage)
        throws IOException
        {
        if((null == td) || td.readOnly) { throw new IllegalArgumentException(); }
        if(null == statusMessage) { throw new IllegalArgumentException(); }
        if(statusMessage.length() > MAX_TWEET_CHARS) { throw new IllegalArgumentException("message too long, 140 ASCII chars max"); }

        // Don't try to resend unless different from previous status string that we generated/cached...
        if((null != TwitterCacheFileName) && TwitterCacheFileName.canRead())
            {
            try
                {
                final String lastStatus = (String) DataUtils.deserialiseFromFile(TwitterCacheFileName, false);
                if(statusMessage.equals(lastStatus)) { return; }
                }
            catch(final Exception e) { e.printStackTrace(); /* Absorb errors for robustness, but whinge. */ }
            }

        // If there is a minimum interval between Tweets specified
        // then check when our cache of the last one sent was updated
        // and quietly veto this message (though log it) if the last update was too recent.
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        final String minIntervalS = rawProperties.get(PNAME_TWITTER_MIN_GAP_MINS);
        if((null != minIntervalS) && !minIntervalS.isEmpty())
            {
            try
                {
                final int minInterval = Integer.parseInt(minIntervalS, 10);
                if(minInterval < 0) { throw new NumberFormatException(PNAME_TWITTER_MIN_GAP_MINS + " must be non-negative"); }
                // Only restrict sending messages for a positive interval.
                if(minInterval > 0)
                    {
                    final long minIntervalmS = minInterval * 60 * 1000L;
                    if(TwitterCacheFileName.lastModified() + minIntervalmS > System.currentTimeMillis())
                        {
                        System.err.println("WARNING: sent previous Tweet too recently so skipping sending this one: " + statusMessage);
                        return;
                        }
                    }
                }
            // Complain about badly-formatted value, and continue as if not present.
            catch(final NumberFormatException e) { e.printStackTrace(); }
            }

        // Don't send a repeat/redundant message to Twitter... Save follower money and patience...
        // If this fails with an exception then we won't update our cached status message either...
        if(!statusMessage.equals(td.handle.getStatus(td.username)))
            { td.handle.setStatus(statusMessage); }

        // Now try to cache the status message (uncompressed, since it will be small) if we can.
        if(null != TwitterCacheFileName)
            {
            try { DataUtils.serialiseToFile(statusMessage, TwitterCacheFileName, false, true); }
            catch(final Exception e) { e.printStackTrace(); /* Absorb errors for robustness but whinge. */ }
            }
        }
    }

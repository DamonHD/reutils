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

import java.io.File;
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

    /**Property name for Twitter user. */
    public static final String PNAME_TWITTER_USERNAME = "Twitter.username";

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
        // Need at least a Twitter user ID to proceed.
        if(null == tUsername) { return(null); }

        if(!allowReadOnly) { return(null); } // FIXME: can't do r/w yet!

        return(new TwitterDetails(tUsername, new Twitter(tUsername, null), true));
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

    /**Attempt to update the displayed Twitter status as necessary.
     * Do so only if we think the status changed since we last sent it
     * and it has actually changed compared to what is at Twitter...
     * <p>
     * We must take great pains to avoid unnecessary annoying/expensive updates.
     *
     * @param TwitterCacheFileName  if non-null is the location to cache twitter status messages;
     *     if the new status supplied is the same as the cached value then we won't send an update
     * @param statusMessage  short (max 140 chars) Twitter status message; never null
     */
    public static void setTwitterStatusIfChanged(final TwitterUtils.TwitterDetails td,
                                                 final File TwitterCacheFileName,
                                                 final String statusMessage)
        throws IOException
        {
        if(null == statusMessage) { throw new IllegalArgumentException(); }
        if(statusMessage.length() > 140) { throw new IllegalArgumentException("message too long, 140 ASCII chars max"); }

        // Don't resend if not different from previous status string that we cached...
        if((null != TwitterCacheFileName) && TwitterCacheFileName.canRead())
            {
            try
                {
                final String lastStatus = (String) DataUtils.deserialiseFromFile(TwitterCacheFileName, false);
                if(statusMessage.equals(lastStatus)) { return; }
                }
            catch(final Exception e) { e.printStackTrace(); /* Absorb errors for robustness but whinge. */ }
            }

        // Don't send a repeat message to Twitter... Save money and patience...
        if(statusMessage.equals(td.handle.getStatus(td.username))) { return; }

        // Now send the new status...
        td.handle.setStatus(statusMessage);

        // Now try to cache the status message (uncompressed, since it will be small) if we can.
        if(null != TwitterCacheFileName)
            {
            try
                {
                DataUtils.serialiseToFile(statusMessage, TwitterCacheFileName, false, true);
                }
            catch(final Exception e) { e.printStackTrace(); /* Absorb errors for robustness but whinge. */ }
            }
        }
    }

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

    /**Get Twitter handle for updates; null if nothing suitable set up.
     * May return a read-only handle for testing
     * if that is permitted by the argument.
     * <p>
     * A read/write handle is a valid return value if read-only is allowed.
     */
    public static Twitter getTwitterHandle(final boolean allowReadOnly)
        {
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        final String tUsername = rawProperties.get(PNAME_TWITTER_USERNAME);
        // Need at least a Twitter user ID to proceed.
        if(null == tUsername) { return(null); }

        if(!allowReadOnly) { return(null); } // FIXME: can't do r/w yet!

        return(new Twitter(tUsername, null));
        }
    }

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

package localtest;

import junit.framework.TestCase;

import org.hd.d.edh.TwitterUtils;
import org.hd.d.edh.TwitterUtils.TwitterDetails;

import winterwell.jtwitter.OAuthSignpostClient;
import winterwell.jtwitter.Twitter;

/**Twitter tests.
 */
public final class TestTwitter extends TestCase
    {
    /**Test very basic access to our selected Twitter user.
     */
    public static void testBasics()
        throws Exception
        {
        final TwitterDetails td = TwitterUtils.getTwitterHandle(true);
        if(null != td)
            {
            // If we have a Twitter ID then we should be able to
            // print our user's status with no exception.
            System.out.println("Current status of "+td.username+": " + td.handle.getStatus(td.username));
            }

//        td.handle.setStatus("After the pip it will be: " + (new Date()));
//        TwitterUtils.setTwitterStatusIfChanged(td, null, "Pip: "+(new Date()));
        }

//    /**Test user-mediated extraction of auth token.
//     * Also useful for gathering new secrets manually...
//     */
//    public static void testOOBTokenAccess()
//        {
//        final OAuthSignpostClient client = new OAuthSignpostClient(OAuthSignpostClient.JTWITTER_OAUTH_KEY, OAuthSignpostClient.JTWITTER_OAUTH_SECRET, "oob");
//        final Twitter jtwit = new Twitter("EarthOrgUK", client);
//        // open the authorisation page in the user's browser
//        // This is a convenience method for directing the user to client.authorizeUrl()
//        client.authorizeDesktop();
//        // get the pin
//        final String v = OAuthSignpostClient.askUser("Please enter the verification PIN from Twitter");
//        client.setAuthorizationCode(v);
//        // Optional: store the authorisation token details
//        final String[] accessToken = client.getAccessToken();
//        for(final String s : accessToken) { System.out.println(s); }
//        // use the API!
//        jtwit.setStatus("Testing auth...");
//        }
    }

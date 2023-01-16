/*
Copyright (c) 2008-2021, Damon Hart-Davis
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
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import winterwell.jtwitter.OAuthSignpostClient;
import winterwell.jtwitter.Twitter;


/**Twitter Utilities.
 * Handles some common interactions with Twitter and other social media.
 */
public final class TwitterUtils
    {
    /**Prevent creation of an instance. */
    private TwitterUtils() { }

    /**Maximum Twitter message length (tweet) in (ASCII) characters.
     * This allows some elbow room for trailing automatic/variable content.
     */
    public static final int MAX_TWEET_CHARS = 274; // 134 before upgrade to JTwitter 3.8.5.

    /**Maximum Mastodon message length (post) in (ASCII) characters.
     * This allows some elbow room for trailing automatic/variable content.
     */
    public static final int MAX_TOOT_CHARS = 464;

    /**Property name prefix (needs traffic-light colour appended) for Twitter status messages; not null. */
    public static final String PNAME_PREFIX_TWITTER_TRAFFICLIGHT_STATUS_MESSAGES = "Twitter.trafficlight.status.";

    /**Property name prefix (needs traffic-light colour appended) for Twitter status messages when using historical data, ie predicting; not null. */
    public static final String PNAME_PREFIX_TWITTER_TRAFFICLIGHT_PREDICTION_MESSAGES = "Twitter.trafficlight.prediction.";

    /**Property name for Twitter user; not null. */
    public static final String PNAME_TWITTER_USERNAME = "Twitter.username";

    /**Property name for file containing Twitter OAuth tokens; not null. */
    public static final String PNAME_TWITTER_AUTHTOK_FILENAME = "Twitter.authtokenfile";

    /**Property name for alternate file containing Twitter OAuth tokens; not null. */
    public static final String PNAME_TWITTER_AUTHTOK_FILENAME2 = "Twitter.authtokenfile2";

    /**Property name for minimum gap between Tweets in minutes (non-negative); not null. */
    public static final String PNAME_SOCIAL_MEDIA_POST_MIN_GAP_MINS = "Twitter.minGapMins";

    /**Property name for Mastodon user; not null. */
    public static final String PNAME_MASTODON_USERNAME = "Mastodon.username";

    /**Property name for Mastodon host; not null. */
    public static final String PNAME_MASTODON_HOSTNAME = "Mastodon.hostname";

    /**Property name for Mastodon auth token file; not null. */
    public static final String PNAME_MASTODON_AUTH_TOKEN_FILE = "Mastodon.authtokenfile";

 
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
        final String[] authtokens1 = getAuthTokensFromFile(rawProperties.get(PNAME_TWITTER_AUTHTOK_FILENAME), allowReadOnly);
        final String[] authtokens = (authtokens1 != null) ? authtokens1 : getAuthTokensFromFile(rawProperties.get(PNAME_TWITTER_AUTHTOK_FILENAME2), allowReadOnly);

        // If we have no password then we are definitely read-only.
        final boolean noWriteAccess = (authtokens == null);
        // If definitely read-only and that is not acceptable then return null.
        if(noWriteAccess && !allowReadOnly) { return(null); }

        // Build new client...
        final OAuthSignpostClient client = new OAuthSignpostClient(OAuthSignpostClient.JTWITTER_OAUTH_KEY, OAuthSignpostClient.JTWITTER_OAUTH_SECRET, authtokens[0], authtokens[1]);

        return(new TwitterDetails(tUsername, new Twitter(tUsername, client), noWriteAccess));
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
            if(!quiet)
                {
                System.err.println("Cannot open pass file for reading: " + f);
                try { System.err.println("  Canonical path: " + f.getCanonicalPath()); } catch(final IOException e) { }
                }
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


    /**Extract a (non-empty) set of non-empty auth tokens from the specified file, or null if none or if the filename is bad.
     * This does not throw an exception if it cannot find or open the specified file
     * (or the file name is null or empty)
     * of it the file does not contain a password; for all these cases null is returned.
     * <p>
     * Each token must be on a separate line.
     * <p>
     * There must be at least two token else this will return null.
     *
     * @param tokensFilename  name of file containing auth tokens or null/empty if none
     * @param quiet  if true then keep quiet about file errors
     * @return non-null, non-empty password
     */
    private static String[] getAuthTokensFromFile(final String tokensFilename, final boolean quiet)
        {
        // Null/empty file name results in quiet return of null.
        if((null == tokensFilename) || tokensFilename.trim().isEmpty()) { return(null); }

        final File f = new File(tokensFilename);
        if(!f.canRead())
            {
            if(!quiet)
                {
                System.err.println("Cannot open pass file for reading: " + f);
                try { System.err.println("  Canonical path: " + f.getCanonicalPath()); } catch(final IOException e) { }
                }
            return(null);
            }

        try
            {
            final List<String> result = new ArrayList<String>();
            final BufferedReader r =  new BufferedReader(new FileReader(f));
            try
                {
                String line;
                while(null != (line = r.readLine()))
                    {
                    final String trimmed = line.trim();
                    if(trimmed.isEmpty()) { return(null); } // Give up with *any* blank token.
                    result.add(trimmed);
                    }
                if(result.size() < 2) { return(null); } // Give up if not (at least) two tokens.
                // Return non-null non-empty token(s).
                return(result.toArray(new String[result.size()]));
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

//    /**If true then resend tweet only when different to previous tweeted status.
//     * More robust than only sending when our message changes because Twitter can lose messages,
//     * but will result in any manual tweet followed up by retweet of previous status.
//     */
//    private static final boolean SEND_TWEET_IF_TWITTER_TEXT_DIFFERENT = true;

    /**If true then resend tweet only when different R/A/G status to previous tweeted status.
     * More robust than only sending when our message changes because Twitter can lose messages,
     * but will result in any manual tweet followed up by retweet of previous status.
     */
    private static final boolean SEND_TWEET_ONLY_IF_TWITTER_STATUS_DIFFERENT = true;

    /**Character used to separate (trailing) variable part from main part of message.
     * Generally whitespace would also be inserted to avoid confusion.
     */
    private static final char TWEET_TAIL_SEP = '|';
    
    /**Returns true if a social media grid intensity update should be posted.
     * This is based on whether the status has changed since the lost status post,
     * and when that last status post was.
     * 
     * This may log reasons to stdout/stderr if returning false, unless quiet is true.
     * 
     * @param socialMediaPostStatusCacheFileName  if non-null is the location to cache twitter status messages;
     *     if the new status supplied is the same as the cached value then we won't send an update
     * @param status  overall R/A/G status; never null
     * @param quiet  if true then suppress log messages on stdout/stderr
     */
    public static boolean canPostNewStatusMessage(
    		final File socialMediaPostStatusCacheFileName,
            final TrafficLight status,
    		final boolean quiet)
	    {
    	if(null == socialMediaPostStatusCacheFileName) { throw new IllegalArgumentException(); }
    	if(null == status) { throw new IllegalArgumentException(); }

        // Don't try to post a new status unless different from previous.
        final boolean twitterCacheFileExists = socialMediaPostStatusCacheFileName.canRead();

        if(twitterCacheFileExists)
            {
            try
                {
            	// Assume the that the cache contains one of "RED", "YELLOW" or "GREEN".
            	final TrafficLight lastStatus = TrafficLight.valueOf(Files.readString(
            			socialMediaPostStatusCacheFileName.toPath(), StandardCharsets.US_ASCII).trim());
                if(status.equals(lastStatus))
                    {
                    if(!quiet)
                    	{ System.out.println("INFO: social media post status unchanged ("+lastStatus+")"); }
                	return(false);
                	}
                }
            catch(final Exception e) { e.printStackTrace(); /* Absorb errors for robustness, but whinge. */ }
            }

        // If there is a minimum interval between social media posts specified
        // then check when the cache of the last one sent was updated.
        // Veto this update if the last update was too recent.
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        final String minIntervalS = rawProperties.get(PNAME_SOCIAL_MEDIA_POST_MIN_GAP_MINS);
        final boolean minIntervalSpecified = (null != minIntervalS) && !minIntervalS.isEmpty();
        if(!minIntervalSpecified)
	        {
        	// Complain if being accidentally antisocial: 0 can be specified if required.
	        System.err.println("ERROR: minimum social media posting gap (minutes) not specified: " + PNAME_SOCIAL_MEDIA_POST_MIN_GAP_MINS);
	        }
        if(twitterCacheFileExists && minIntervalSpecified)
            {
            try
                {
                final int minInterval = Integer.parseInt(minIntervalS, 10);
                if(minInterval < 0) { throw new NumberFormatException(PNAME_SOCIAL_MEDIA_POST_MIN_GAP_MINS + " must be non-negative"); }
                // Only restrict sending messages for a positive interval.
                if(minInterval > 0)
                    {
                    final long minIntervalmS = minInterval * 60 * 1000L;
                    final long lastSent = socialMediaPostStatusCacheFileName.lastModified();
                    if((lastSent + minIntervalmS) > System.currentTimeMillis())
                        {
                    	if(!quiet)
                            { System.out.println("INFO: sent previous social media post recently (<"+minIntervalS+"m, last "+(new Date(lastSent))); }
                        return(false);
                        }
                    }
                }
            // Complain about badly-formatted value, and continue as if not present.
            catch(final NumberFormatException e) { e.printStackTrace(); }
            }

        // OK to post new status message.
        return(true);
	    }

    /**Attempt to update Twitter status.
     * Can be run on a background thread.
     *
     * @param td  non-null, non-read-only Twitter handle
     * @param statusMessage  short (max 140 chars) Twitter status message; never null
     * @param status  overall R/A/G status, null if no attempt to regulate by this
     */
    public static void setTwitterStatus(final TwitterUtils.TwitterDetails td,
                                                 final String statusMessage)
        throws IOException
        {
        if((null == td) || td.readOnly) { throw new IllegalArgumentException(); }
        if(null == statusMessage) { throw new IllegalArgumentException(); }
        if(statusMessage.length() > MAX_TWEET_CHARS) { throw new IllegalArgumentException("message too long"); }

        // UTC timestamp to append to message to help make it unique.
        // Without this, in the olden days, Twitter sometimes blocked updates as duplicates.
        final String time = new java.text.SimpleDateFormat("HHmm").format(new java.util.Date());
	    // Append timestamp to status message.
        final String fullMessage = statusMessage + TWEET_TAIL_SEP + time + 'Z';

        // Send message...
        td.handle.setStatus(fullMessage);
        }

    /**Send a tweet and time it.
     * @param td  non-null, non-read-only Twitter handle
     * @param statusMessage  short (max 140 chars) Twitter status message; never null
     * @param status  overall R/A/G status, null if no attempt to regulate by this
     */
	public static Long timeSetTwitterStatus(final TwitterDetails td, final String statusMessage)
		throws IOException
	    {
		final long s = System.currentTimeMillis();
		setTwitterStatus(td, statusMessage);
		final long e = System.currentTimeMillis();
		return(e - s);
	    }

    /**Removes any trailing automatic/variable part from the tweet, leaving the core.
     * The 'trailing part' starts at the last occurrence of the TWEET_TAIL_SEP,
     * or the first occurrence of http:// because of Twitter link rewriting.
     *
     * @param tweet  full tweet, or null
     * @return  null if tweet message is null,
     *     else message stripped of trailing portion if present and trimmed of whitespace.
     */
    public static String removeTrailingPart(final String tweet)
        {
        // No tweet at all, return null.
        if(null == tweet) { return(null); }
        // Trim to last TWEET_TAIL_SEP, if any.
        final int lastSep = tweet.lastIndexOf(TWEET_TAIL_SEP);
        String cut = (-1 == lastSep) ? tweet : tweet.substring(0, lastSep);
        // Trim to first "http:".
        final int firstHttp = cut.indexOf("http:");
        cut = (-1 == firstHttp) ? cut : tweet.substring(0, firstHttp);
        // Trim residual whitespace.
        return(cut.trim());
        }

    /**Immutable class containing Mastodon details.
     * These details are enough (other than any security tokens) to make a post.
     */
    public static final class MastodonDetails
        {
        /**User name on Mastodon; never null nor empty. */
        public final String username;
        /**Mastodon host; never null nor empty. */
        public final String hostname;

        /**Create an instance. */
        public MastodonDetails(final String username, final String hostname)
            {
            if((null == username) || username.isEmpty()) { throw new IllegalArgumentException(); }
            if((null == hostname) || hostname.isEmpty()) { throw new IllegalArgumentException(); }
            this.username = username;
            this.hostname = hostname;
            }
        }

    /**Get Mastodon details for updates; null if nothing is available.
     * Note that this is not a live handle/connection.
     * Any authentication details to make a post (say)
     * will need to be looked up on the fly, separately.
     * 
     * This will return null and complain if such an auth token is not available
     * if username and hostname are present.
     */
    public static MastodonDetails getMastodonDetails()
	    {
		final String username = getMastodonUsername();
		if(null == username) { return(null); }
		final String hostname = getMastodonHostname();
		if(null == hostname) { return(null); }

		if(null == getMastodonAuthToken())
			{
			System.err.println("WARNING: getMastodonDetails(): Mastodon username and hostname available but not the auth token");
			return(null);
			}

		return(new MastodonDetails(username, hostname));
	    }

    /**Get the specified non-empty Mastodon user name or null if none. */
    public static String getMastodonUsername()
        {
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        final String username = rawProperties.get(PNAME_MASTODON_USERNAME);
        // Transform empty user name to null.
        if((null != username) && username.isEmpty()) { return(null); }
        return(username);
        }

    /**Get the specified non-empty Mastodon host name or null if none. */
    public static String getMastodonHostname()
        {
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        final String hostname = rawProperties.get(PNAME_MASTODON_HOSTNAME);
        // Transform empty user name to null.
        if((null != hostname) && hostname.isEmpty()) { return(null); }
        return(hostname);
        }

    /**Get the specified non-empty Mastodon status post auth token or null if none.
     * Note: this return value is to be treated with care, eg not logged or printed.
     * 
     * Any auth token file must be short and be pure (7-bit) ASCII.
     * Surrounding whitespace is trim()med.
     * 
     * @return auth token, or null if not available
     */
    public static String getMastodonAuthToken()
        {
        final Map<String, String> rawProperties = MainProperties.getRawProperties();
        final String authtokenfilename = rawProperties.get(PNAME_MASTODON_AUTH_TOKEN_FILE);
        // Transform empty user name to null.
        if((null != authtokenfilename) && authtokenfilename.isEmpty()) { return(null); }

        final Path path = Paths.get(authtokenfilename);
        if(!Files.exists(path)) { return(null); }

        try
            { return((new String(Files.readAllBytes(path), StandardCharsets.US_ASCII)).trim()); }
        catch (IOException e)
            { e.printStackTrace(); }

        // Failed.
        return(null);
        }

    /**Attempt to update Mastodon status.
     * Can be run on a background thread.
     *
     * @param td  non-null, non-read-only Twitter handle
     * @param statusMessage  short (max 140 chars) Twitter status message; never null
     */
    public static void setMastodonStatus(final MastodonDetails md,
                                         final String statusMessage)
        throws IOException
        {
        if(null == md) { throw new IllegalArgumentException(); }
        if(null == statusMessage) { throw new IllegalArgumentException(); }
        if(statusMessage.length() > MAX_TOOT_CHARS) { throw new IllegalArgumentException("message too long"); }

        // Fetch the auth tokens, or silently abort if not available...  
        final String authtoken = getMastodonAuthToken();
        if(null == authtoken) { return; }  

        // Send message...

        // Here is how to do it with curl...
        // (MAT is a file containing the access token.)
        // % curl https://mastodon.energy/api/v1/statuses -H "Authorization: Bearer `cat $MAT`" -F "status=$1"
        // See https://dev.to/bitsrfr/getting-started-with-the-mastodon-api-41jj
        
        // Use URL encoding to force into ASCII (7-bit) encoding.
        final String formEncodedBody = "status=" +
            URLEncoder.encode(statusMessage, StandardCharsets.US_ASCII);
        
        final int timeout_ms = 10000;

        final URL u = new URL("https", md.hostname, "/api/v1/statuses");
        
        final HttpsURLConnection uc = (HttpsURLConnection) u.openConnection();
        uc.setUseCaches(false);
        uc.setAllowUserInteraction(false);
        uc.setDoOutput(true);
        uc.setDoInput(true);
        uc.setConnectTimeout(timeout_ms);
        uc.setReadTimeout(timeout_ms);
        uc.setRequestMethod("POST");
        uc.setRequestProperty("Authorization", "Bearer " + authtoken);
        uc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        uc.setRequestProperty("Content-Length", String.valueOf(formEncodedBody.length()));
        final OutputStream output = uc.getOutputStream();
    	output.write(formEncodedBody.getBytes(StandardCharsets.US_ASCII));
    	output.close();
        final int responseCode = uc.getResponseCode();
        final String responseMessage = uc.getResponseMessage();

        uc.disconnect();
        if(200 != responseCode)
            {
        	throw new IOException("failed toot response code " +
                responseCode + ": " + responseMessage);
            }
        }

    /**Send a toot and time it.
     * @param md  non-null, Mastodon details
     * @param statusMessage  short (max 140 chars) Twitter status message; never null
     */
	public static long timeSetMastodonStatus(MastodonDetails md, String statusMessage)
		throws IOException
	    {
		final long s = System.currentTimeMillis();
		setMastodonStatus(md, statusMessage);
		final long e = System.currentTimeMillis();
		return(e - s);
	    }
    }

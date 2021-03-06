/*
Copyright (c) 2008-2013, Damon Hart-Davis
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

import java.io.IOException;

import junit.framework.TestCase;

import org.hd.d.edh.RemoteGenerationIntensity;

import remoteIntensity.IRLgi;
import remoteIntensity.NULLgi;

/**Test fetch/computation of remote intensity values. */
public final class TestRemoteIntensity extends TestCase
    {
    /**Test handling of NULL remote-intensity source. */
    public static void testNULL()
        {
        final RemoteGenerationIntensity rgiN = new NULLgi();
        assertNotNull(rgiN.gridName());
        try { rgiN.getLatest(); fail("expected IOException"); } catch(final IOException e) { /* expected */ }
        }

    /**Test handling of IRL remote-intensity source.
     * This should avoid hammering the real remote server, ie at least cacheing should be in place.
     */
    public static void testIRL() throws Exception
        {
        final RemoteGenerationIntensity rgiIRL = new IRLgi();
        assertNotNull(rgiIRL.gridName());
        assertEquals("IRL", rgiIRL.gridName());
        try { assertTrue("any intensity returned must be non-negative", 0 <= rgiIRL.getLatest()); } catch(final IOException e) { /* failure permitted */ }
        try { assertTrue("any intensity returned must be non-negative", 0 <= rgiIRL.getLatest()); } catch(final IOException e) { /* failure permitted */ }
        }
    }

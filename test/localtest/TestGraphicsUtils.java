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

import java.io.File;
import java.util.Random;

import junit.framework.TestCase;

import org.hd.d.edh.GraphicsUtils;
import org.hd.d.edh.TrafficLight;

/**Test stats routines. */
public final class TestGraphicsUtils extends TestCase
    {
    public static void testSimpleIntensityIconPNG() throws Exception
        {
        try { GraphicsUtils.writeSimpleIntensityIconPNG(null, 0, System.currentTimeMillis(), null, 0); fail("should have rejected bogus arguments"); } catch(final IllegalArgumentException e) { /* expected */ }

        final File fbase = File.createTempFile("icon", null, new File("out"));
        try
            {
            // Generate the icon and test it for basic sanity...
            final String suffix = GraphicsUtils.writeSimpleIntensityIconPNG(fbase.getPath(), GraphicsUtils.MIN_ICON_SIZE_PX, System.currentTimeMillis(), null, 555);
            assertNotNull(suffix);
            assertTrue(suffix.length() > 0);
            final File fIco = new File(fbase.getPath() + suffix);
            assertTrue(fIco.canRead());
            assertTrue(fIco.length() > 0);
            fIco.delete();

// TODO: test for content


            }
        finally { fbase.delete(); }
        }

    /**Simple test of writing RED icon to place we can look at it with browser/etc. */
    public static void testSimpleIntensityIconPNGWrite() throws Exception
        {
        final String fbase = "out/";
        for(final int size : new int[] { GraphicsUtils.MIN_ICON_SIZE_PX, 48, 64 } )
            {
            final String suffix = GraphicsUtils.writeSimpleIntensityIconPNG(fbase, size, System.currentTimeMillis(), TrafficLight.RED, rnd.nextInt(1100));
            assertNotNull(suffix);
            assertTrue(suffix.length() > 0);
            final File fIco = new File(fbase + suffix);
            System.out.println(fIco.getCanonicalPath());
            assertTrue(fIco.canRead());
            assertTrue(fIco.length() > 0);
            }
        }






    /**Private source of OK pseudo-random numbers. */
    private static Random rnd = new Random();
    }

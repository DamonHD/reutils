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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.hd.d.edh.StatUtils;
import org.hd.d.edh.Tuple;

/**Test stats routines. */
public final class TestStatUtils extends TestCase
    {
    /**Test implementation of Pearson correlation. */
    public static void testComputePearsonCorrelation()
        {
        assertTrue(Double.isNaN(StatUtils.ComputePearsonCorrelation(new double[1], new double[1])));
        assertEquals(1.0, StatUtils.ComputePearsonCorrelation(new double[]{0,1}, new double[]{0,1}));
        assertEquals(-1.0, StatUtils.ComputePearsonCorrelation(new double[]{0,1}, new double[]{1,0}));
        assertEquals("the two value sets need not be of same magnitude",
                1.0, StatUtils.ComputePearsonCorrelation(new double[]{0,1,2}, new double[]{0.0,0.1,0.2}));
        final double corrp1 = StatUtils.ComputePearsonCorrelation(new double[]{0,1,2}, new double[]{0.0,0.05,0.2});
        assertTrue((corrp1 > 0) && (corrp1 < 1));

        // Test List<Double> overload.
        assertEquals(1.0, StatUtils.ComputePearsonCorrelation(Arrays.asList(new Double[]{Double.valueOf(0),Double.valueOf(1)}), Arrays.asList(new Double[]{Double.valueOf(0),Double.valueOf(1)})));
        assertEquals(-1.0, StatUtils.ComputePearsonCorrelation(Arrays.asList(new Double[]{Double.valueOf(1),Double.valueOf(0)}), Arrays.asList(new Double[]{Double.valueOf(0),Double.valueOf(1)})));
        }

    /**Test implementation of combo fuel correlation computation. */
    public static void testComputeFuelCorrelations()
        {
        try { StatUtils.computeFuelCorrelations(null); fail("should reject null arg"); } catch(final IllegalArgumentException e) { /* expected */ }

        final Map<Long, Tuple.Pair<Map<String,Float>, Map<String,Integer>>> fuelinst1 = new HashMap<Long, Tuple.Pair<Map<String,Float>,Map<String,Integer>>>();
        try { StatUtils.computeFuelCorrelations(fuelinst1); fail("should reject empty collection"); } catch(final IllegalArgumentException e) { /* expected */ }

        fuelinst1.put(0L, new Tuple.Pair<Map<String,Float>,Map<String,Integer>>(Collections.<String,Float>emptyMap(), Collections.<String,Integer>emptyMap()));
        }
    }

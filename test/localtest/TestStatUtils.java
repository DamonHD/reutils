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

        assertTrue("All-identical data point pairs same as a single point", Double.isNaN(StatUtils.ComputePearsonCorrelation(new double[]{60000,60000}, new double[]{0.91,0.91})));
        assertEquals("Slightly changing values in same direction should yield +1", 1.0, StatUtils.ComputePearsonCorrelation(new double[]{40000,60000}, new double[]{0.90,0.91}), 1e-6);
        assertEquals("Slightly changing values, with dups, in same direction should yield +1", 1.0, StatUtils.ComputePearsonCorrelation(new double[]{40000,40000,60000}, new double[]{0.90,0.90,0.91}), 1e-6);

        assertTrue("Unchnaging values on one axis gives NaN", Double.isNaN(StatUtils.ComputePearsonCorrelation(new double[]{40000,40000,60000}, new double[]{0.90,0.90,0.90})));

        // Test List<Double> overload.
        assertEquals(1.0, StatUtils.ComputePearsonCorrelation(Arrays.asList(new Double[]{Double.valueOf(0),Double.valueOf(1)}), Arrays.asList(new Double[]{Double.valueOf(0),Double.valueOf(1)})));

        assertEquals(-1.0, StatUtils.ComputePearsonCorrelation(Arrays.asList(new Double[]{Double.valueOf(1),Double.valueOf(0)}), Arrays.asList(new Double[]{Double.valueOf(0),Double.valueOf(1)})));
        }

    /**Test implementation of combo fuel correlation computation. */
    public static void testComputeFuelCorrelations()
        {
        try { StatUtils.computeFuelCorrelations(null, 0); fail("should reject null arg"); } catch(final IllegalArgumentException e) { /* expected */ }

        final Map<Long, Tuple.Pair<Map<String,Float>, Map<String,Integer>>> fuelinst1 = new HashMap<Long, Tuple.Pair<Map<String,Float>,Map<String,Integer>>>();
        try { StatUtils.computeFuelCorrelations(fuelinst1, 0); fail("should reject empty collection"); } catch(final IllegalArgumentException e) { /* expected */ }

        fuelinst1.put(0L, new Tuple.Pair<Map<String,Float>,Map<String,Integer>>(Collections.<String,Float>emptyMap(), Collections.<String,Integer>emptyMap()));
        try { StatUtils.computeFuelCorrelations(fuelinst1, 1); fail("should reject effectively-empty collection"); } catch(final IllegalArgumentException e) { /* expected */ }

        final HashMap<String, Float> m1i = new HashMap<String,Float>();
        final HashMap<String, Integer> m1p = new HashMap<String,Integer>();
        m1i.put("COAL", 0.91f);
        m1p.put("COAL", 40000); // COAL-powered GB in summer!
        fuelinst1.put(1L, new Tuple.Pair<Map<String,Float>,Map<String,Integer>>(m1i, m1p));
        final Tuple.Triple<Map<String,Float>, Map<String,Float>, Float> r1 = StatUtils.computeFuelCorrelations(fuelinst1, 1);
        assertNotNull(r1);
        assertTrue("with just one real point demand/intensity correlation should be NaN", Float.isNaN(r1.third.floatValue()));

        fuelinst1.put(2L, new Tuple.Pair<Map<String,Float>,Map<String,Integer>>(m1i, m1p));
        final Tuple.Triple<Map<String,Float>, Map<String,Float>, Float> r2 = StatUtils.computeFuelCorrelations(fuelinst1, 1);
        assertNotNull(r2);
        assertTrue("with two identical real points demand/intensity correlation should be NaN", Float.isNaN(r2.third.floatValue()));

        final HashMap<String, Float> m3i = new HashMap<String,Float>(m1i);
        final HashMap<String, Integer> m3p = new HashMap<String,Integer>(m1p);
        m3i.put("CCGT", 0.36f);
        m3p.put("CCGT", 20000); // COAL- and CCGT- powered GB in winter!
        fuelinst1.put(3L, new Tuple.Pair<Map<String,Float>,Map<String,Integer>>(m3i, m3p));
        final Tuple.Triple<Map<String,Float>, Map<String,Float>, Float> r3 = StatUtils.computeFuelCorrelations(fuelinst1, 1);
        assertNotNull(r3);
        assertEquals("with two lower-intensity fuel covering extra demand correlation should be -1", -1f, r3.third.floatValue(), 1e-6);
        assertEquals(2, r3.first.size());
        assertEquals(2, r3.second.size());
        assertTrue(Float.isNaN(r3.first.get("COAL")));
        assertTrue(Float.isNaN(r3.first.get("CCGT")));
        assertTrue(Float.isNaN(r3.second.get("COAL")));
        assertTrue(Float.isNaN(r3.second.get("CCGT")));

        System.out.println(r3);
        }
    }

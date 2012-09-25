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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**Statistics utilities.
 */
public final class StatUtils
    {
    /**Prevent creation of an instance. */
    private StatUtils() { }


    /**Calculate Pearson's correlation' using List<Double> args. */
    public static double ComputePearsonCorrelation(final List<Double> values1, final List<Double> values2)
        {
        if(null == values1) { throw new IllegalArgumentException(); }
        if(null == values2) { throw new IllegalArgumentException(); }
        final double v1[] = new double[values1.size()];
        for(int i = v1.length; --i >= 0; ) { v1[i] = values1.get(i); }
        final double v2[] = new double[values2.size()];
        for(int i = v2.length; --i >= 0; ) { v2[i] = values2.get(i); }
        return(ComputePearsonCorrelation(v1, v2));
        }

    /**Calculate Pearson's correlation. */
    public static double ComputePearsonCorrelation(final double[] values1, final double[] values2)
        {
        if(null == values1) { throw new IllegalArgumentException(); }
        if(null == values2) { throw new IllegalArgumentException(); }
        final int length = values1.length;
        if(length != values2.length) { throw new IllegalArgumentException(); }
        if(length < 1) { throw new IllegalArgumentException(); }

        double sum_sq_x = 0;
        double sum_sq_y = 0;
        double sum_coproduct = 0;
        double mean_x = values1[0];
        double mean_y = values2[0];
        for(int i = 1; i++ < length; )
            {
            final double sweep = ((double)(i - 1)) / i;
            final double delta_x = values1[i - 1] - mean_x;
            final double delta_y = values2[i - 1] - mean_y;
            sum_sq_x += delta_x * delta_x * sweep;
            sum_sq_y += delta_y * delta_y * sweep;
            sum_coproduct += delta_x * delta_y * sweep;
            mean_x += delta_x / i;
            mean_y += delta_y / i;
            }
        final double pop_sd_x = Math.sqrt(sum_sq_x / length);
        final double pop_sd_y = Math.sqrt(sum_sq_y / length);
        final double cov_x_y = sum_coproduct / length;
        final double result = cov_x_y / (pop_sd_x * pop_sd_y);
        return(result);
        }

    /**Given map of (evenly-spaced) sample times to fuel MW and intensities, computes correlations between fuel, demand and intensity; never null.
     * @param fuelinst  map from timestamp to pairs of maps of fuel to (float) intensity (g/kWh) and to (int) MW generation; never null
     * @return tuple of correlation of map of fuel MW to demand, map of fuel MW to grid intensity, and intensity to demand; immutable, non-null, not containing nulls
     */
    public Tuple.Triple<Map<String,Float>, Map<String,Float>, Float> computeFuelCorrelations(final Map<Long, Tuple.Pair<Map<String,Float>, Map<String,Integer>>> fuelinst)
        {
        if(null == fuelinst) { throw new IllegalArgumentException(); }

        // Demand vs intensity pairs.
        final List<Tuple.Pair<Integer, Integer>> PairsDemandVsIntensity = new ArrayList<Tuple.Pair<Integer, Integer>>(fuelinst.size());

        // Fuel MW vs demand pairs.
        final Map<String, List<Tuple.Pair<Integer, Integer>>> PairsFuelVsDemand = new HashMap<String, List<Tuple.Pair<Integer, Integer>>>();

        // Fuel MW vs intensity pairs.
        final Map<String, List<Tuple.Pair<Integer, Integer>>> PairsFuelVsIntensity = new HashMap<String, List<Tuple.Pair<Integer, Integer>>>();

        // Iterate through the unique points in any order...
        for(final Long timestamp : fuelinst.keySet())
            {
            final Tuple.Pair<Map<String,Float>, Map<String,Integer>> datum = fuelinst.get(timestamp);
            if((null == datum) || (null == datum.first) || (null == datum.second)) { throw new IllegalArgumentException("null/missing data points"); }

            final float demand = FUELINSTUtils.computeWeightedIntensity(datum.first, datum.second, FUELINSTUtils.MIN_FUEL_TYPES_IN_MIX);
            }

        throw new RuntimeException("NOT IMPLEMENTED");
        }
    }

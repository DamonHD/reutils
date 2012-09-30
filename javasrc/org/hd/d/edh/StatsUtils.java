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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hd.d.edh.Tuple.Pair;


/**Statistics utilities.
 */
public final class StatsUtils
    {
    /**Prevent creation of an instance. */
    private StatsUtils() { }


    /**Calculate Pearson's correlation' using a Collection<Tuple.Pair<Number, Number>>, ie unordered set of numeric pairs. */
    public static <X extends Number, Y extends Number> double ComputePearsonCorrelation(final Collection<Tuple.Pair<X, Y>> pairs)
        {
        final int size = pairs.size();
        if(size < 1) { throw new IllegalArgumentException("must have at least one pair"); }
        final double[] values1 = new double[size];
        final double[] values2 = new double[size];
        final Iterator<Tuple.Pair<X, Y>> pit = pairs.iterator();
        for(int i = size; --i >= 0; )
            {
            final Tuple.Pair<X, Y> pair = pit.next();
            values1[i] = pair.first.doubleValue();
            values2[i] = pair.second.doubleValue();
            }
//        System.out.println("values1="+Arrays.toString(values1)+", values2="+Arrays.toString(values2));
        return(ComputePearsonCorrelation(values1, values2));
        }

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

    /**Calculate Pearson's correlation between a vector of unordered pairs. */
    public static double ComputePearsonCorrelation(final double[] values1, final double[] values2)
        {
        if(null == values1) { throw new IllegalArgumentException("null values1[]"); }
        if(null == values2) { throw new IllegalArgumentException("null values2[]"); }
        final int length = values1.length;
        if(length != values2.length) { throw new IllegalArgumentException("arguments must be equal length"); }
        if(length < 1) { throw new IllegalArgumentException("must have a least one pair"); }

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

    /**Given map of (preferably evenly-spaced) sample times to fuel intensities and MW, computes correlations between fuel, demand and intensity; never null.
     * @param fuelinst  map from timestamp to pairs of maps of fuel to (float) intensity (tCO2/MWh) and to (int) MW generation; never null
     * @param minFuelTypesInMix  minimum number of fuel types in mix at each sample else ignore; non-negative
     * @return tuple of map of fuel MW correlation to demand, map of fuel MW correlation to grid intensity, and correlation of intensity to demand; immutable, non-null, not containing nulls
     */
    public static Tuple.Triple<Map<String,Float>, Map<String,Float>, Float> computeFuelCorrelations(final Map<Long, Tuple.Pair<Map<String,Float>, Map<String,Integer>>> fuelinst, final int minFuelTypesInMix)
        {
        if(null == fuelinst) { throw new IllegalArgumentException(); }

        // Demand vs intensity pairs.
        final List<Tuple.Pair<Integer, Float>> PairsDemandVsIntensity = new ArrayList<Tuple.Pair<Integer, Float>>(fuelinst.size());

        // Fuel MW vs demand pairs.
        final Map<String, List<Tuple.Pair<Integer, Integer>>> PairsFuelVsDemand = new HashMap<String, List<Tuple.Pair<Integer, Integer>>>();

        // Fuel MW vs intensity pairs.
        final Map<String, List<Tuple.Pair<Integer, Float>>> PairsFuelVsIntensity = new HashMap<String, List<Tuple.Pair<Integer, Float>>>();

        // Iterate through the unique points in any order...
        for(final Long timestamp : fuelinst.keySet())
            {
            final Tuple.Pair<Map<String,Float>, Map<String,Integer>> datum = fuelinst.get(timestamp);
            if((null == datum) || (null == datum.first) || (null == datum.second)) { throw new IllegalArgumentException("null/missing data points"); }

            final float weightedIntensity = FUELINSTUtils.computeWeightedIntensity(datum.first, datum.second, minFuelTypesInMix);
            // Reject bad (-ve) records.
            if(weightedIntensity < 0)
                {
                System.err.println("Skipping non-positive weighed intensity record at " + timestamp + " = " + new Date(timestamp));
                continue;
                }

            int demand = 0;
            final List<String> goodFuels = new ArrayList<String>(datum.second.size());
            for(final String fuelName : datum.second.keySet())
                {
                final Integer fuelMW = datum.second.get(fuelName);
                if(null == fuelMW) { throw new IllegalArgumentException("bad (null) fuelMW value"); }
                if(fuelMW <= 0) { continue; } // Skip -ve generation (eg exporting interconnectors).
                demand += fuelMW;
                goodFuels.add(fuelName);
                }

            // Make demand/intensity entry.
            PairsDemandVsIntensity.add(new Tuple.Pair<Integer, Float>(demand, weightedIntensity));

            // Now make entries by fuel type.
            for(final String fuelName : goodFuels)
                {
                if(!PairsFuelVsDemand.containsKey(fuelName))
                    { PairsFuelVsDemand.put(fuelName, new ArrayList<Tuple.Pair<Integer, Integer>>(fuelinst.size())); }
                PairsFuelVsDemand.get(fuelName).add(new Tuple.Pair<Integer, Integer>(datum.second.get(fuelName), demand));

                if(!PairsFuelVsIntensity.containsKey(fuelName))
                    { PairsFuelVsIntensity.put(fuelName, new ArrayList<Tuple.Pair<Integer, Float>>(fuelinst.size())); }
                PairsFuelVsIntensity.get(fuelName).add(new Tuple.Pair<Integer, Float>(datum.second.get(fuelName), weightedIntensity));
                }
            }

        final float cdi = (float) ComputePearsonCorrelation(PairsDemandVsIntensity);

        final Map<String,Float> cfd = new HashMap<String,Float>();
        for(final String fuelName : PairsFuelVsDemand.keySet())
            {
            final List<Pair<Integer, Integer>> pairs = PairsFuelVsDemand.get(fuelName);
            if(pairs.size() < 1) { continue; }
            cfd.put(fuelName, (float) ComputePearsonCorrelation(pairs));
//            System.out.println("FuelVsDemand: "+fuelName+" " + pairs);
            }
        final Map<String,Float> cfi = new HashMap<String,Float>();
        for(final String fuelName : PairsFuelVsIntensity.keySet())
            {
            final List<Pair<Integer, Float>> pairs = PairsFuelVsIntensity.get(fuelName);
            if(pairs.size() < 1) { continue; }
            cfi.put(fuelName, (float) ComputePearsonCorrelation(pairs));
//            System.out.println("FuelVsIntensity: "+fuelName+" " + pairs);
            }

        return(new Tuple.Triple<Map<String,Float>, Map<String,Float>, Float>(Collections.unmodifiableMap(cfd), Collections.unmodifiableMap(cfi), cdi));
        }
    }

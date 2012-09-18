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

import org.hd.d.edh.StatUtils;

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
        }
    }

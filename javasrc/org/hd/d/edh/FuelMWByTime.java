/*
Copyright (c) 2024, Damon Hart-Davis
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

import java.util.Objects;

import org.json.JSONObject;

/**Record of MW generation by a named fuel in a timed slot/instant.
 *
 * @param time  interval/instant for this generation
 * @param fuelType  fuel name; non-empty, non-null
 * @param generation  generation in MW
 * @param settlementPeriod  half-hourly settlement period within the day, non-negative
 *
 * The settlementPeriod is canonical,
 * and is useful for reconstructing old CSV style records.
 */
public record FuelMWByTime(long time, String fuelType, int generation, int settlementPeriod)
   {
	public FuelMWByTime
		{
		if(time < 1) { throw new IllegalArgumentException(); }
		Objects.requireNonNull(fuelType);
		if("".equals(fuelType)) { throw new IllegalArgumentException(); }
		if(settlementPeriod < 1) { throw new IllegalArgumentException(); }
		}

   /**Populate from a JSON map/object.
    *
    * @param jo  populate using "startTime", "fuelType", "generation" and settlementPeriod fields; non-null
    * @param clampNonNegative  if true then clamp all values to be non-negative
    *
    * All required fields must be present and non-empty.
    * <p>
    * The time format "2024-02-12T17:50:00Z" ie ISO 8601 UTC down to at least minutes.
    * <p>
    * The fuelType format "INTIFA2" ie upper-case ASCII letters and digits.
    * <p>
    * The generation number "12234" ie an integer, possibly negative.
    * <p>
    * The settlement period "12" ie a small positive integer.
    * <p>
    * Sample record:
    * <pre>
{"dataset":"FUELINST","publishTime":"2024-02-12T17:50:00Z","startTime":"2024-02-12T17:45:00Z","settlementDate":"2024-02-12","settlementPeriod":36,"fuelType":"BIOMASS","generation":2249}
    * </pre>
    */
	public FuelMWByTime(final JSONObject jo, final boolean clampNonNegative)
		{
		this(
			java.time.Instant.parse(Objects.requireNonNull(jo).getString(TIME_IS_START ? "startTime" : "publishTime")).toEpochMilli(),
			Objects.requireNonNull(jo).getString("fuelType"),
			Math.max(Objects.requireNonNull(jo).getInt("generation"),
				clampNonNegative ? 0 : Integer.MIN_VALUE),
			Objects.requireNonNull(jo).getInt("settlementPeriod")
			);
		}

	/**If true, the time is the 'start' time, else it is the publish time.
	 * Publication time may lump several intervals together
	 * if there is a delay in Elexon's data processing,
	 * so start time is usually preferable.
	 */
	public static final boolean TIME_IS_START = true;

	/**Throws an exception if the supplied record is not suitable to parse as FUELINST stream.
	 * This may not reject all possible bad records.
	 *
	 * @param jo  putative JSON stream FUELINST record; should not be null
	 */
	public static void validateJSONRecord(final JSONObject jo)
		{
		Objects.requireNonNull(jo);
		if(!"FUELINST".equals(jo.get("dataset"))) { throw new IllegalArgumentException("not a FUELINST dataset"); }
		// TODO: add more tests
		}
   }

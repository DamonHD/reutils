/*
Copyright (c) 2008-2009, Damon Hart-Davis
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

import java.util.Arrays;

import org.hd.d.edh.FUELINST.TrafficLightsInterface;


/**Main (command-line) entry-point for the data handler.
 *
 */
public final class Main
    {
    /**Print a summary of command options to stderr. */
    public static void printOptions()
        {
        System.err.println("Commands/options");
        System.err.println("  -help");
        System.err.println("    This summary/help.");
        System.err.println("  trafficLights [-class full.generator.classname] {args}");
        System.err.println("  FUELINST outfile.html TIBCOfile.gz {TIBCOfile.gz*}");
        System.err.println("  FUELINST outfile.html <directory> [regex-filematch-pattern]");
        }

    /**Accepts command-line arguments.
     *
     * Accepts the following commands:
     * <ul>
     * <li>trafficLights [-class full.generator.classname] {args}<br />
     *         Using the default implementation,
     *         args consists of one optional filename.html
     *         from which other output file names (for flags, xhtml, caches, etc)
     *         are derived.
     *         <p>
     *         Shows red/yellow/green 'start your appliance now' indication
     *         based primarily on current UK grid carbon intensity.
     *         Can write HTML output for eou site if output filename is supplied,
     *         else writes a summary on System.out.
     *         Will also delete outfile.html.flag file if status is GREEN, else will create it,
     *         which is useful for automated remote 200/404 status check.
     *         <p>
     *         If a full class name is supplied
     *         then it is passed the remaining arguments
     *         and may interpret them as it wishes.
     *     </li>
     * <li>FUELINST outfile.html TIBCOfile.gz {TIBCOfile.gz*}</li>
     * <li>FUELINST outfile.html <directory> [regex-filematch-pattern]<br />
     *         Extracts fuel mix and carbon-intensity info,
     *         from one or more historical TIBCO GZipped format files
     *         either listed as explicit files
     *         or as a directory and an optional regex pattern to accept files by name,
     *         and then is parsed into MW by fuel type
     *         or converted to intensity in units of kgCO2/kWh
     *         with various analyses performed over the data.
     *      </li>
     * </ul>
     */
    public static void main(final String[] args)
        {
        final long startTime = System.currentTimeMillis();

        if((args.length < 1) || "-help".equals(args[0]))
            {
            printOptions();
            return; // Not an error.
            }

        // Command is first argument.
        final String command = args[0];

        try
            {
            if("FUELINST".equals(command))
                {
                FUELINSTHistorical.doHistoricalAnalysis(args);
                return; // Completed.
                }
            else if("trafficLights".equals(command))
                {;
                final TrafficLightsInterface impl;

                // Check if "-class" "name" optional args are present.
                final boolean classSpecified = (args.length >= 2) && "-class".equals(args[1]);

                // If first optional argument is "-class"
                // then attempt to create an instance of the specified class.
                if(classSpecified)
                    {
                    final String classname = args[2];
                    System.out.println("Class specified: " + classname);
                    impl = (TrafficLightsInterface) Class.forName(classname).newInstance();
                    }
                // Else use the default implementation.
                else
                    { impl = (new FUELINST.TrafficLightsDEFAULT()); }

                // Pass in trailing args (if any) to impl;
                // leading 'trafficLights' (and possible -class name) is omitted.
                impl.doTrafficLights(Arrays.copyOfRange(args, classSpecified ? 3 : 1, args.length));
                return; // Completed.
                }
            }
        catch(final Throwable e)
            {
            System.err.println("FAILED command: " + command);
            e.printStackTrace();
            System.exit(1);
            }

        // Unrecognised/unhandled command.
        System.err.println("Unrecognised or unhandled command: " + command);
        printOptions();
        System.exit(1);
        }
    }

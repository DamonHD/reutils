/**
 *
 */
package org.hd.d.edh;


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
        System.err.println("  trafficLights outfile.html");
        System.err.println("  FUELINST outfile.html TIBCOfile.gz {TIBCOfile.gz*}");
        System.err.println("  FUELINST outfile.html <directory> [regex-filematch-pattern]");
        }

    /**Accepts command-line arguments.
     *
     * Accepts following commands:
     * <ul>
     * <li>trafficLights [outfile.html]<br />
     *         Shows red/yellow/green 'start your appliance now' indication
     *         based primarily on current UK grid carbon intensity.
     *         Can write HTML output for eou site if output filename is supplied,
     *         else writes a summary on System.out.
     *         Will also delete outfile.html.flag file if status is GREEN, else will create it,
     *         which is useful for automated remote 200/404 status check.
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
                FUELINST.doHistoricalAnalysis(args);
                return; // Completed.
                }
            else if("trafficLights".equals(command))
                {
                FUELINST.doTrafficLights(args);
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

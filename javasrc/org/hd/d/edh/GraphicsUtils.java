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

package org.hd.d.edh;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.imageio.ImageIO;


/**Graphics utilities, eg generating Web-friendly output icons/buttons/etc.
 */
public final class GraphicsUtils
    {
    /**Prevent creation of an instance. */
    private GraphicsUtils() { }

    /**Minimum simple icon size (both dimensions) in pixels; strictly positive. */
    public static final int MIN_ICON_SIZE_PX = 32;

    /**Target icon border (around text) in pixels; strictly positive. */
    private static final int ICON_BORDER_PX = 1;

    /**Background colour for unknown traffic-light status; non-null. */
    public static final Color TL_UNKNOWN_ICON_BG = Color.WHITE;
    /**Background colour for yellow traffic-light status; non-null. */
    public static final Color TL_YELLOW_ICON_BG = new Color(0xffffcc);
    /**Background colour for green traffic-light status; non-null. */
    public static final Color TL_GREEN_ICON_BG = new Color(0xccffcc);
    /**Background colour for red traffic-light status; non-null. */
    public static final Color TL_RED_ICON_BG = new Color(0xffcccc);
    
    /**If true, aim to minimise PNG icon size, immediately or after (say) zopflipng postprocessing.
     * This may be using a palette, skipping anti-aliasing, and other techniques.
     */
    public static final boolean MINIMISE_PNG_ICON_SIZE = true;

    /**Write a simple PNG icon containing the current intensity and with the current traffic-light colour; never null.
     * This attempts to update the output atomically and leaving it globally readable at all times.
     *
     * The current intensity is usually expected to be (well) within the range [0,1000],
     * but this routine will attempt to behave gracefully for values &ge;1000.
     *
     * @param basename  base path including file name stub at which icon is to be written; never null
     * @param sizePX  icon output size (each side) in pixels; strictly positive and no less than MIN_ICON_SIZE_PX
     * @param timestamp  timestamp of underlying data, or 0 if none
     * @param status  traffic-light status; null for unknown
     * @param currentIntensity  current grid intensity in gCO2/kWh (kgCO2/MWh); non-negative
     * @return URL-friendly pure-printable-ASCII (no-'/') extension to add to basename for where PNG is written (does not vary with status/intensity arguments); never null nor empty.
     */
    public static String writeSimpleIntensityIconPNG(final String basename, final int sizePX, final long timestamp, final TrafficLight status, final int currentIntensity)
        throws IOException
        {
        if(null == basename) { throw new IllegalArgumentException(); }
        if(sizePX < MIN_ICON_SIZE_PX) { throw new IllegalArgumentException(); }
        if(currentIntensity < 0) { throw new IllegalArgumentException(); }

        // Basic text to show in the icon.
        final String basicIconText = String.valueOf(currentIntensity);

        // Get font set up...
        final Font fontTmp = Font.decode(null); // Use system default font...

        final BufferedImage buffer = new BufferedImage(sizePX, sizePX,
        		MINIMISE_PNG_ICON_SIZE ? BufferedImage.TYPE_BYTE_INDEXED : BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = buffer.createGraphics();
        try
            {
            Color bgColour = TL_UNKNOWN_ICON_BG;
            if(null != status)
                {
                switch(status)
                    {
                    case RED: bgColour = TL_RED_ICON_BG; break;
                    case GREEN: bgColour = TL_GREEN_ICON_BG; break;
                    case YELLOW: bgColour = TL_YELLOW_ICON_BG; break;
                    }
                }
            g.setColor(bgColour);
            g.fillRect(0, 0, sizePX, sizePX);

            // The text looks very rough without anti-aliasing.
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Size the main intensity number text to fit the icon (width).
            final FontRenderContext fc = g.getFontRenderContext();
            final Rectangle2D boundsTmp = fontTmp.getStringBounds(basicIconText, fc);
            final double wTmp = boundsTmp.getWidth();
            final double hTmp = boundsTmp.getHeight();
            final float sTmp = fontTmp.getSize2D();
            final int fitToWidth = sizePX - (2*ICON_BORDER_PX);
            final int fitToHeight = ((2*sizePX)/3) - (2*ICON_BORDER_PX); // Allow text to take 2/3rds of height.
            assert(fitToWidth > 0);
            assert(fitToHeight > 0);
//            System.out.println("initial width and height with font size "+sTmp+": " + wTmp + ", " + hTmp);
            final float fontMainScaleFactor = Math.min(fitToWidth / (float) wTmp, fitToHeight / (float) hTmp);
            final Font fontMain = fontTmp.deriveFont(sTmp * fontMainScaleFactor);
            final Rectangle2D boundsMain = fontMain.getStringBounds(basicIconText, fc);
            final double hMain = boundsMain.getHeight();
//            final double wMain = boundsMain.getWidth();
//            final float sMain = fontMain.getSize2D();
//            System.out.println("scaled ("+fontMainScaleFactor+") width and height with font size "+sMain+": " + wMain + ", " + hMain);
            g.setFont(fontMain);
            g.setColor(Color.BLACK);
            g.drawString(basicIconText, (int) (-boundsMain.getCenterX() + (sizePX/2.0)), (int) ((-boundsMain.getY() + (sizePX/2.0)) - (hMain/2.0))); // Centre vertically.

            if(!MINIMISE_PNG_ICON_SIZE || (sizePX > MIN_ICON_SIZE_PX))
	            {
	            // Show units at the bottom of the icon, ie under the number.
	            final String units = "gCO2/kWh";
	            final Rectangle2D boundsUTmp = fontTmp.getStringBounds(units, fc);
	            final double wUTmp = boundsUTmp.getWidth();
	            final double hUTmp = boundsUTmp.getHeight();
	            final int fitUToWidth = sizePX - (2*ICON_BORDER_PX);
	            final float fitUToHeight = (float) (((sizePX - hMain)/2.0) - 1); // Allow a 1-pixel extra border/gap; don't round prematurely.
	            final float fontUScaleFactor = Math.min(fitUToWidth / (float) wUTmp, fitUToHeight / (float) hUTmp);
	            final Font fontU = fontTmp.deriveFont(sTmp * fontUScaleFactor);
	            final Rectangle2D boundsU = fontU.getStringBounds(units, fc);
	            g.setFont(fontU);
	            g.setColor(Color.BLACK);
	            g.drawString(units, (int) (-boundsU.getCenterX() + (sizePX/2.0)), sizePX - ((int)(boundsU.getY() + boundsU.getHeight())) - ICON_BORDER_PX); // At bottom (with margin).
	
	            // If a timestamp is supplied, squeeze it into the display above the intensity (in grey).
	            if(0 != timestamp)
	                {
	                final SimpleDateFormat fmt = FUELINSTUtils.getHHMMTimestampParser();
	                final String ts = "@" + fmt.format(new Date(timestamp)) + "Z";
	                final Rectangle2D boundsTSTmp = fontTmp.getStringBounds(ts, fc);
	                final double wTSTmp = boundsTSTmp.getWidth();
	                final double hTSTmp = boundsTSTmp.getHeight();
	                final int fitTSToWidth = sizePX - (2*ICON_BORDER_PX);
	                final float fitTSToHeight = (float) (((sizePX - hMain)/2.0) - 1); // Allow a 1-pixel extra border/gap; don't round prematurely.
	                final float fontTSScaleFactor = Math.min(fitTSToWidth / (float) wTSTmp, fitTSToHeight / (float) hTSTmp);
	                final Font fontTS = fontTmp.deriveFont(sTmp * fontTSScaleFactor);
	                final Rectangle2D boundsTS = fontTS.getStringBounds(ts, fc);
	                g.setFont(fontTS);
	                g.setColor(Color.GRAY);
	                g.drawString(ts, (int) (-boundsTS.getCenterX() + (sizePX/2.0)), ((int)(-boundsTS.getY())) + ICON_BORDER_PX); // At top (with margin).
	                }
	            }
            }
        finally { g.dispose(); }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(buffer, "png", baos);

        final String suffix = "intico1-" + sizePX + ".png";
        DataUtils.replacePublishedFile(basename + suffix, baos.toByteArray());

        return(suffix);
        }
    }

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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;


/**Graphics utilities, eg generating Web-friendly output icons/buttons/etc.
 */
public final class GraphicsUtils
    {
    /**Prevent creation of an instance. */
    private GraphicsUtils() { }

    /**Minimum simple icon size (both dimensions) in pixels; strictly positive. */
    public static final int MIN_ICON_SIZE_PX = 32;

    /**Write a simple PNG icon containing the current intensity and with the current traffic-light colour; never null.
     * This attempts to update the output atomically and leaving it globally readable at all times.
     *
     * The current intensity is usually expected to be (well) within the range [0,1000],
     * but this routine will attempt to behave gracefully for values &ge;1000.
     *
     * @param basename  base path including file name stub at which icon is to be written; never null
     * @param sizePX  icon output size (each side) in pixels; strictly positive and no less than MIN_ICON_SIZE_PX
     * @param status  traffic-light status; null for unknown
     * @param currentIntensity  current grid intensity in gCO2/kWh (kgCO2/MWh); non-negative
     * @return URL-friendly pure-printable-ASCII (no-'/') extension to add to basename for where PNG is written (does not vary with status/intensity arguments); never null nor empty.
     */
    public static String writeSimpleIntensityIconPNG(final File basename, final int sizePX, final TrafficLight status, final int currentIntensity)
        throws IOException
        {
        if(null == basename) { throw new IllegalArgumentException(); }
        if(sizePX < MIN_ICON_SIZE_PX) { throw new IllegalArgumentException(); }
        if(currentIntensity < 0) { throw new IllegalArgumentException(); }

        final BufferedImage buffer = new BufferedImage(sizePX, sizePX, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = buffer.createGraphics();
        try
            {
            Color bgColour = Color.WHITE;
            if(null != status)
                {
                switch(status)
                    {
                    case RED: bgColour = new Color(0xffcccc); break;
                    case GREEN: bgColour = new Color(0xccffcc); break;
                    case YELLOW: bgColour = new Color(0xffffcc); break;
                    }
                }
            g.setColor(bgColour);
            g.fillRect(0,0,sizePX,sizePX);

            // TODO

            }
        finally { g.dispose(); }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(buffer, "png", baos);

        final String suffix = "intico1-" + sizePX + ".png";
        DataUtils.replacePublishedFile(basename.getPath() + suffix, baos.toByteArray());

        return(suffix);
        }
    }
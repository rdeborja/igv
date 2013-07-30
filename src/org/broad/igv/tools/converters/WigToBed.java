/*
 * Copyright (c) 2007-2013 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.tools.converters;

import org.broad.igv.tools.parsers.DataConsumer;
import org.broad.igv.tools.parsers.ToolsWiggleParser;
import org.broad.igv.track.TrackType;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Converts a "wig" file to a "bed" file by applying a threshold
 *
 * @author jrobinso
 * @date Jan 20, 2011
 */
public class WigToBed implements DataConsumer {

    PrintWriter bedWriter;
    double lowerThreshold = 0.1;
    double higherThreshold = 0.3;
    String chr = null;
    int featureStart = -1;
    int featureEnd = -1;
    String type;
    private float score;

    static String upperName = "upper";
    static String lowerName = "lower";


    public static void main(String[] args) throws IOException {
        String input =  args[0]; //"/Users/jrobinso/Sigma/566.wgs.bam.large_isize.wig";
        WigToBed wigToBed = new WigToBed(input, .17f, .55f);
        ToolsWiggleParser parser = new ToolsWiggleParser(input, wigToBed, null);
        parser.parse();
    }

    public static void run(String inputFile, float hetThreshold, float homThreshold) throws IOException {

        lowerName = String.valueOf((int) (hetThreshold * 10));
        upperName =  String.valueOf((int) (homThreshold * 10));

        int len = inputFile.length();
        String outputFile = inputFile.substring(0, len - 4) + ".bed";
        WigToBed wigToBed = new WigToBed(outputFile, hetThreshold, homThreshold);
        ToolsWiggleParser parser = new ToolsWiggleParser(inputFile, wigToBed, null);
        parser.parse();
    }

    public WigToBed(String bedFile, float lowerThreshold, float higherThreshold) {
        this.lowerThreshold = lowerThreshold;
        this.higherThreshold = higherThreshold;
        try {
            bedWriter = new PrintWriter(new BufferedWriter(new FileWriter(bedFile)));
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }


    public void addData(String chr, int start, int end, float[] data, String name) {

        if (featureStart >= 0) {
            // Feature in progress
            if (start > featureEnd || data[0] < lowerThreshold) {
                writeCurrentFeature();
                featureStart = -1;
                score = 0f;
            }
            featureEnd = end;
        }

        if (featureStart >= 0) {

            if (data[0] > higherThreshold) {
                type =  String.valueOf(higherThreshold);
                score = Math.max(score, data[0]);

            } else if (data[0] < lowerThreshold) {
                writeCurrentFeature();
                featureStart = -1;
                score = 0f;
            }

        } else {
            if (data[0] > lowerThreshold) {
                featureStart = start;
                featureEnd = end;
                this.chr = chr;
                score = Math.max(score, data[0]);
                type = data[0] > higherThreshold ? String.valueOf(higherThreshold) : String.valueOf(lowerThreshold);
            }

        }
    }

    private void writeCurrentFeature() {
        bedWriter.println(this.chr + "\t" + featureStart + "\t" + featureEnd + "\t" + type);
        featureStart = -1;
    }

    public void parsingComplete() {
        if (featureStart >= 0) writeCurrentFeature();
        bedWriter.close();
    }

    public void setTrackParameters(TrackType trackType, String trackLine, String[] trackNames) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setTrackParameters(TrackType trackType, String trackLine, String[] trackNames, boolean b) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * Set a tolerance for "sortedness" of the data.  A start position can be less than
     * the immediately previous start position by this amount.  This is needed for
     * chip-seq processing where the start position of an alignment can be artificially
     * extended post sorting.
     */
    public void setSortTolerance(int tolerance) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setAttribute(String key, String value) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setType(String type) {
        //To change body of implemented methods use File | Settings | File Templates.
    }


}

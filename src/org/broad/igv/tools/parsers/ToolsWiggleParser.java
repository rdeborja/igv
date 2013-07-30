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
package org.broad.igv.tools.parsers;

//~--- non-JDK imports --------------------------------------------------------

import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.data.WiggleDataset;
import org.broad.igv.data.WiggleParser;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.track.TrackProperties;
import org.broad.igv.track.TrackType;
import org.broad.igv.util.ParsingUtils;
import org.broad.igv.util.ResourceLocator;
import org.broad.tribble.readers.AsciiLineReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses a wiggle file, as described by UCSC
 * See http://genome.ucsc.edu/goldenPath/help/wiggle.html
 * The data read in is added to a {@link DataConsumer}
 */
public class ToolsWiggleParser extends WiggleParser{

    static private Logger log = Logger.getLogger(ToolsWiggleParser.class);
    private DataConsumer dataConsumer;

    // State variables.  This is a serial type parser,  these variables are used to hold temporary
    // state.
    String trackLine = null;
    String nextLine = null;

    public ToolsWiggleParser(String file, DataConsumer dataConsumer, Genome genome) {
        super(new ResourceLocator(file), genome);
        this.dataConsumer = dataConsumer;

        parseHeader();

        String[] trackNames = {resourceLocator.getTrackName()};
        // TODO -- total hack to get Manuel's file parsed quickly.  Revisit (obviously);
        if (resourceLocator.getPath().endsWith(".ewig") || resourceLocator.getPath().endsWith(".ewig.gz")
                || resourceLocator.getPath().endsWith("ewig.map")) {
            trackNames = new String[5];
            trackNames[4] = resourceLocator.getTrackName();
            trackNames[0] = "A";
            trackNames[1] = "C";
            trackNames[2] = "G";
            trackNames[3] = "T";
        }

        dataConsumer.setTrackParameters(TrackType.OTHER, trackLine, trackNames);


        // Parse track line, if any, to get the coordinate convention
        if (trackLine != null) {
            TrackProperties props = new TrackProperties();
            ParsingUtils.parseTrackLine(trackLine, props);
            TrackProperties.BaseCoord convention = props.getBaseCoord();
            if (convention == TrackProperties.BaseCoord.ZERO) {
                startBase = 0;
            }
        }

    }

    /**
     * @return the dataConsumer
     */
    public DataConsumer getDataConsumer() {
        return dataConsumer;
    }

    /**
     * Utility method.  Returns true if this looks like a wiggle locator.  The criteria is to scan
     * the first 100 lines looking for a valid "track" line.  According to UCSC documentation
     * track lines must contain a type attribute,  which must be equal to "wiggle_0".
     *
     * @param file
     * @return
     */
    public static boolean isWiggle(ResourceLocator file) {
        AsciiLineReader reader = null;
        try {
            reader = ParsingUtils.openAsciiReader(file);
            String nextLine = null;
            int lineNo = 0;
            while ((nextLine = reader.readLine()) != null && (nextLine.trim().length() > 0)) {
                if (nextLine.startsWith("track") && nextLine.contains("wiggle_0")) {
                    return true;
                }
                if (lineNo++ > 100) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return false;
    }

    private void parseHeader() {
        AsciiLineReader reader = null;

        // The DataConsumer interface takes an array of data per position, however wig
        // files contain a single data point.  Create an "array" once that can
        // be resused

        try {

            reader = ParsingUtils.openAsciiReader(resourceLocator);

            while ((nextLine = reader.readLine()) != null && (nextLine.trim().length() > 0)) {

                // Skip comment lines
                if (nextLine.startsWith("#") || nextLine.startsWith("data") || nextLine.startsWith(
                        "browser") || nextLine.trim().length() == 0) {
                    continue;
                }

                if (nextLine.startsWith("track")) {
                    trackLine = nextLine;

                } else {
                    return;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Parse Wiggle file into the DataConsumer
     * @return null
     * @throws IOException
     */
    @Override
    public WiggleDataset parse(){

        lastPosition = -1;
        unsortedChromosomes = new HashSet();

        if (resourceLocator.getPath().endsWith("ewig.map")) {
            startBase = 0;
            windowSpan = 1;
            type = Type.VARIABLE;
            String parent = new File(resourceLocator.getPath()).getParent();
            Map<String, String> fileMap = null;
            try {
                fileMap = parseEwigList(resourceLocator.getPath());
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
            for (Map.Entry<String, String> entry : fileMap.entrySet()) {
                chr = entry.getKey();
                File f = new File(parent, entry.getValue());
                parseFile(new ResourceLocator(f.getAbsolutePath()));
            }
        } else {
            parseFile(resourceLocator);
        }
        parsingComplete();
        return null;
    }

    private Map<String, String> parseEwigList(String path) throws IOException {
        BufferedReader reader = null;
        try {
            reader = ParsingUtils.openBufferedReader(path);
            String nextLine;
            LinkedHashMap<String, String> fileMap = new LinkedHashMap();
            while ((nextLine = reader.readLine()) != null) {
                String[] tokens = Globals.whitespacePattern.split(nextLine);
                fileMap.put(tokens[0], tokens[1]);
            }
            return fileMap;

        } finally {
            if (reader != null) reader.close();
        }
    }


    @Override
    protected void changedChromosome(WiggleDataset dataset, String lastChr) {
        //getDataConsumer().newChromosome(chr);
        //lastPosition = 0;
    }

    @Override
    protected void parsingComplete() {
        getDataConsumer().parsingComplete();
    }

    float[] buffer = new float[1];
    @Override
    public void addData(String chr, int start, int end, float value) {
        buffer[0] = value;
        addData(chr, start, end, buffer);
    }

    @Override
    public void addData(String chr, int start, int end, float[] values) {
        getDataConsumer().addData(chr, start, end, values, null);
    }
}

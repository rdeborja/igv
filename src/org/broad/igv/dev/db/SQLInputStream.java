/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.dev.db;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Wraps the results of a SQL query into an InputStream
 * <p/>
 * User: jacob
 * Date: 2012-Aug-22
 */
public class SQLInputStream extends InputStream {

    private ResultSet rs;

    /**
     * Whether we should encode all data as strings.
     * Fields get tab separated in that case, and lines
     * have a newline at end.
     */
    private boolean convertToString;

    private DataOutputStream dataOutputStream;
    private ByteArrayOutputStream byteOutputStream;
    private InputStream inputStream;
    private static final int bufferSize = 10000;

    private static final byte[] TAB_BYTES = "\t".getBytes();
    private static final byte[] NEWLINE_BYTES = "\n".getBytes();

    private int minColIndex;
    private int maxColIndex;

    public SQLInputStream(ResultSet rs, boolean convertToString) throws IOException {
        this(rs, convertToString, 1, Integer.MAX_VALUE);
    }

    public SQLInputStream(ResultSet rs, boolean convertToString, int minColIndex, int maxColIndex) throws IOException {
        this.rs = rs;
        this.convertToString = convertToString;
        this.minColIndex = minColIndex;
        this.maxColIndex = maxColIndex;

        byteOutputStream = new ByteArrayOutputStream(bufferSize);
        dataOutputStream = new DataOutputStream(byteOutputStream);
        fillRow();
    }

    private boolean fillRow() throws IOException {
        return fillRow(minColIndex, maxColIndex);
    }

    private boolean fillRow(int minColIndex, int maxColIndex) throws IOException {
        byteOutputStream.reset();
        try {
            if (rs.isAfterLast() || !rs.next()) {
                return false;
            }
            if (convertToString) {
                String[] tokens = DBManager.lineToArray(rs, minColIndex, maxColIndex, false);
                for (String tok : tokens) {
                    dataOutputStream.write(tok.getBytes());
                    dataOutputStream.write(TAB_BYTES);
                }
                dataOutputStream.write(NEWLINE_BYTES);
            } else {
                int maxCol = Math.min(rs.getMetaData().getColumnCount(), maxColIndex);
                for (int cc = minColIndex; cc <= maxCol; cc++) {
                    int type = rs.getMetaData().getColumnType(cc);
                    switch (type) {
                        case Types.BOOLEAN:
                        case Types.INTEGER:
                            int val = rs.getInt(cc);
                            dataOutputStream.writeInt(val);
                            break;
                        default:
                            String sval = rs.getString(cc);
                            dataOutputStream.writeUTF(sval);
                            break;
                    }

                }
            }

        } catch (SQLException e) {
            throw new IOException(e);
        }
        dataOutputStream.flush();
        setBuf(byteOutputStream.toByteArray());
        return true;
    }

    private synchronized void setBuf(byte[] buf) {
        inputStream = new ByteArrayInputStream(buf);
    }

    @Override
    public int read() throws IOException {
        int next = inputStream.read();
        if (next < 0) {
            if (fillRow()) {
                return inputStream.read();
            } else {
                return -1;
            }
        } else {
            return next;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int numRead = inputStream.read(b, off, len);
        if (inputStream.available() <= 0) {
            fillRow();
        }
        return numRead;
    }


    @Override
    public void close() throws IOException {
        try {
            dataOutputStream.close();
            super.close();
            rs.close();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public int available() throws IOException {
        return inputStream.available();
    }

    public boolean hasNext() {
        try {
            return !rs.isAfterLast();
        } catch (SQLException e) {
            return false;
        }
    }

}

/*
 *  Copyright 2012-2013 E.Hooijmeijer
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package nl.cad.tpsparse.tps.record;

import java.util.ArrayList;
import java.util.List;

import nl.cad.tpsparse.bin.RandomAccess;

import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

public class TableDefinitionRecord {

    private int driverVersion;
    private int recordLength;
    private int nrOfFields;
    private int nrOfMemos;
    private int nrOfIndexes;
    private List<FieldDefinitionRecord> fields = new ArrayList<FieldDefinitionRecord>();
    private List<MemoDefinitionRecord> memos = new ArrayList<MemoDefinitionRecord>();
    private List<IndexDefinitionRecord> indexes = new ArrayList<IndexDefinitionRecord>();

    public TableDefinitionRecord(RandomAccess rx) {
        this.driverVersion = rx.leShort();
        this.recordLength = rx.leShort();
        this.nrOfFields = rx.leShort();
        this.nrOfMemos = rx.leShort();
        this.nrOfIndexes = rx.leShort();
        //
        try {
            for (int t = 0; t < nrOfFields; t++) {
                fields.add(new FieldDefinitionRecord(rx));
            }
            for (int t = 0; t < nrOfMemos; t++) {
                memos.add(new MemoDefinitionRecord(rx));
            }
            for (int t = 0; t < nrOfIndexes; t++) {
                indexes.add(new IndexDefinitionRecord(rx));
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Bad Table Definition " + this, ex);
        }
    }

    public int getDriverVersion() {
        return driverVersion;
    }

    public List<FieldDefinitionRecord> getFields() {
        return fields;
    }

    public List<MemoDefinitionRecord> getMemos() {
        return memos;
    }

    public List<IndexDefinitionRecord> getIndexes() {
        return indexes;
    }

    public int getRecordLength() {
        return recordLength;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TableDefinition(" + driverVersion + "," + recordLength + "," + nrOfFields + "," + nrOfMemos + "," + nrOfIndexes + ",\n" + fields + ",\n"
                + memos + ",\n" + indexes + ")");
        return sb.toString();
    }

    public List<Object> parse(byte[] record) {
        RandomAccess rx = new RandomAccess(record);
        List<Object> values = new ArrayList<Object>(fields.size());
        for (int t = 0; t < fields.size(); t++) {
            int type = fields.get(t).getFieldType();
            int ofs = fields.get(t).getOffset();
            int len = fields.get(t).getLength();
            values.add(parseField(type, ofs, len, rx));
        }
        return values;
    }

    public Object parseField(int type, int ofs, int len, RandomAccess rx) {
        rx.jumpAbs(ofs);
        switch (type) {
        case 1:
            // byte
            assertEqual(1, len);
            return rx.leByte();
        case 2:
            // short
            assertEqual(2, len);
            return rx.leShort();
        case 3:
            // unsigned short
            assertEqual(2, len);
            return rx.leUShort();
        case 4:
            // Date, mask encoded.
            long date = rx.leULong();
            if (date != 0) {
                long years = (date & 0xFFFF0000) >> 16;
                long months = (date & 0x0000FF00) >> 8;
                long days = (date & 0x000000FF);
                return new LocalDate((int) years, (int) months, (int) days);
            } else {
                return null;
            }
        case 5:
            //
            // Time, mask encoded.
            // So far i've only had values with hours and minutes
            // but no seconds or milliseconds so I've no way of
            // knowing how to decode these.
            //
            int time = rx.leLong();
            int mins = (time & 0x00FF0000) >> 16;
            int hours = (time & 0x7F000000) >> 24;
            //
            return new LocalTime(hours, mins, 0, 0);
        case 6:
            // Long
            assertEqual(4, len);
            return rx.leLong();
        case 7:
            // Unsigned Long
            assertEqual(4, len);
            return rx.leULong();
        case 8:
            // Float
            assertEqual(4, len);
            return rx.leFloat();
        case 9:
            // Double
            assertEqual(8, len);
            return rx.leDouble();
        case 0x0A:
            // BCD encoded.
            // No sample data available.
            throw new IllegalArgumentException("BCD encoded data is not (yet) supported.");
        case 0x12:
            // Fixed Length String
            return rx.fixedLengthString(len);
        case 0x13:
            return rx.zeroTerminatedString();
        case 0x14:
            return rx.pascalString();
        case 0x16:
            // Group (an overlay on top of existing data, can be anything).
            return rx.readBytes(len);
        default:
            throw new IllegalArgumentException("Unsupported type " + type + " (" + len + ")");
        }
    }

    private void assertEqual(int ref, int value) {
        if (ref != value) {
            throw new IllegalArgumentException(ref + " != " + value);
        }
    }
}
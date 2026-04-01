package io.github.sijakubo.shapefilecreator.internal;

import io.github.sijakubo.shapefilecreator.ConversionResult;
import io.github.sijakubo.shapefilecreator.InvalidGeoJsonException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

public final class ShapefileWriter {

    private static final Charset DBF_CHARSET = StandardCharsets.ISO_8859_1;
    private static final int SHAPE_FILE_CODE = 9994;
    private static final int SHAPE_FILE_VERSION = 1000;

    private ShapefileWriter() {
    }

    public static ConversionResult write(
            ParsedFeatureCollection featureCollection,
            SchemaDefinition schemaDefinition,
            Path outputDirectory,
            String fileNameBase) {
        try {
            Files.createDirectories(outputDirectory);

            Path shpFile = outputDirectory.resolve(fileNameBase + ".shp");
            Path shxFile = outputDirectory.resolve(fileNameBase + ".shx");
            Path dbfFile = outputDirectory.resolve(fileNameBase + ".dbf");
            Path prjFile = outputDirectory.resolve(fileNameBase + ".prj");

            List<ShapeRecord> records = buildRecords(featureCollection.features(), schemaDefinition.shapeType());
            Envelope bounds = computeBounds(featureCollection.features());

            writeShp(shpFile, schemaDefinition.shapeType(), bounds, records);
            writeShx(shxFile, schemaDefinition.shapeType(), bounds, records);
            writeDbf(dbfFile, schemaDefinition.fields(), featureCollection.features());
            ProjectionFileWriter.write(prjFile, schemaDefinition.srid());

            return new ConversionResult(shpFile, shxFile, dbfFile, prjFile);
        } catch (IOException exception) {
            throw new InvalidGeoJsonException("Failed to write shapefile.", exception);
        }
    }

    private static List<ShapeRecord> buildRecords(List<ParsedFeature> features, ShapeType shapeType) throws IOException {
        List<ShapeRecord> records = new ArrayList<>();
        int offsetWords = 50;

        for (ParsedFeature feature : features) {
            byte[] content = serializeRecord(feature.geometry(), shapeType);
            int contentLengthWords = content.length / 2;
            records.add(new ShapeRecord(offsetWords, contentLengthWords, content));
            offsetWords += 4 + contentLengthWords;
        }

        return List.copyOf(records);
    }

    private static Envelope computeBounds(List<ParsedFeature> features) {
        Envelope envelope = new Envelope();
        for (ParsedFeature feature : features) {
            Geometry geometry = feature.geometry();
            if (geometry.isEmpty()) {
                throw new InvalidGeoJsonException("Empty geometries are not supported for shapefile export.");
            }
            envelope.expandToInclude(geometry.getEnvelopeInternal());
        }
        return envelope;
    }

    private static void writeShp(Path file, ShapeType shapeType, Envelope bounds, List<ShapeRecord> records) throws IOException {
        int fileLengthWords = 50;
        for (ShapeRecord record : records) {
            fileLengthWords += 4 + record.contentLengthWords();
        }

        try (var output = Files.newOutputStream(file)) {
            writeHeader(output, shapeType, bounds, fileLengthWords);
            int recordNumber = 1;
            for (ShapeRecord record : records) {
                writeIntBE(output, recordNumber++);
                writeIntBE(output, record.contentLengthWords());
                output.write(record.content());
            }
        }
    }

    private static void writeShx(Path file, ShapeType shapeType, Envelope bounds, List<ShapeRecord> records) throws IOException {
        int fileLengthWords = 50 + (records.size() * 4);

        try (var output = Files.newOutputStream(file)) {
            writeHeader(output, shapeType, bounds, fileLengthWords);
            for (ShapeRecord record : records) {
                writeIntBE(output, record.offsetWords());
                writeIntBE(output, record.contentLengthWords());
            }
        }
    }

    private static void writeDbf(Path file, List<DbfFieldDefinition> fields, List<ParsedFeature> features) throws IOException {
        int headerLength = 32 + (fields.size() * 32) + 1;
        int recordLength = 1;
        for (DbfFieldDefinition field : fields) {
            recordLength += field.length();
        }

        try (var output = Files.newOutputStream(file)) {
            LocalDate today = LocalDate.now();
            output.write(0x03);
            output.write(today.getYear() - 1900);
            output.write(today.getMonthValue());
            output.write(today.getDayOfMonth());
            writeIntLE(output, features.size());
            writeShortLE(output, headerLength);
            writeShortLE(output, recordLength);
            output.write(new byte[20]);

            for (DbfFieldDefinition field : fields) {
                writeFieldDescriptor(output, field);
            }

            output.write(0x0D);

            for (ParsedFeature feature : features) {
                output.write(0x20);
                for (DbfFieldDefinition field : fields) {
                    output.write(formatFieldValue(field, feature.properties().get(field.originalName())));
                }
            }

            output.write(0x1A);
        }
    }

    private static void writeFieldDescriptor(java.io.OutputStream output, DbfFieldDefinition field) throws IOException {
        byte[] nameBytes = field.fieldName().getBytes(StandardCharsets.US_ASCII);
        byte[] descriptor = new byte[32];
        System.arraycopy(nameBytes, 0, descriptor, 0, Math.min(nameBytes.length, 10));
        descriptor[11] = (byte) field.fieldType().code();
        descriptor[16] = (byte) field.length();
        descriptor[17] = (byte) field.decimalCount();
        output.write(descriptor);
    }

    private static byte[] formatFieldValue(DbfFieldDefinition field, Object value) {
        return switch (field.fieldType()) {
            case CHARACTER -> formatCharacter(field.length(), value);
            case LOGICAL -> formatLogical(value);
            case NUMERIC -> formatNumeric(field, value);
        };
    }

    private static byte[] formatCharacter(int length, Object value) {
        byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, (byte) ' ');
        if (value == null) {
            return bytes;
        }

        byte[] encoded = String.valueOf(value).getBytes(DBF_CHARSET);
        System.arraycopy(encoded, 0, bytes, 0, Math.min(length, encoded.length));
        return bytes;
    }

    private static byte[] formatLogical(Object value) {
        if (value == null) {
            return new byte[]{'?'};
        }
        return new byte[]{Boolean.TRUE.equals(value) ? (byte) 'T' : (byte) 'F'};
    }

    private static byte[] formatNumeric(DbfFieldDefinition field, Object value) {
        String text;
        if (value == null) {
            text = "";
        } else if (field.decimalCount() == 0) {
            text = String.valueOf(((Number) value).longValue());
        } else {
            text = BigDecimal.valueOf(((Number) value).doubleValue())
                    .setScale(field.decimalCount(), RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
            if (!text.contains(".")) {
                text = text + ".0";
            }
        }

        if (text.length() > field.length()) {
            throw new InvalidGeoJsonException("Value " + text + " exceeds DBF field width " + field.length() + ".");
        }

        return padLeft(text, field.length()).getBytes(StandardCharsets.US_ASCII);
    }

    private static String padLeft(String value, int length) {
        if (value.length() >= length) {
            return value;
        }
        return " ".repeat(length - value.length()) + value;
    }

    private static byte[] serializeRecord(Geometry geometry, ShapeType shapeType) throws IOException {
        if (geometry.isEmpty()) {
            throw new InvalidGeoJsonException("Empty geometries are not supported for shapefile export.");
        }

        return switch (shapeType) {
            case POINT -> serializePoint((Point) geometry);
            case MULTIPOINT -> serializeMultiPoint((MultiPoint) geometry);
            case POLYLINE -> serializePolyline(geometry);
            case POLYGON -> serializePolygon(geometry);
        };
    }

    private static byte[] serializePoint(Point point) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeIntLE(output, ShapeType.POINT.code());
        writeDoubleLE(output, point.getX());
        writeDoubleLE(output, point.getY());
        return output.toByteArray();
    }

    private static byte[] serializeMultiPoint(MultiPoint multiPoint) throws IOException {
        List<Coordinate> points = new ArrayList<>();
        for (int index = 0; index < multiPoint.getNumGeometries(); index++) {
            Point point = (Point) multiPoint.getGeometryN(index);
            points.add(point.getCoordinate());
        }
        return serializeMultiPointContent(points, multiPoint.getEnvelopeInternal());
    }

    private static byte[] serializePolyline(Geometry geometry) throws IOException {
        List<Coordinate[]> parts = new ArrayList<>();
        if (geometry instanceof LineString lineString) {
            parts.add(requireCoordinates(lineString.getCoordinates(), 2, "LineString"));
        } else if (geometry instanceof MultiLineString multiLineString) {
            for (int index = 0; index < multiLineString.getNumGeometries(); index++) {
                LineString lineString = (LineString) multiLineString.getGeometryN(index);
                parts.add(requireCoordinates(lineString.getCoordinates(), 2, "LineString"));
            }
        } else {
            throw new InvalidGeoJsonException("Expected line geometry but found " + geometry.getGeometryType() + ".");
        }
        return serializeMultiPartShape(ShapeType.POLYLINE, parts, geometry.getEnvelopeInternal());
    }

    private static byte[] serializePolygon(Geometry geometry) throws IOException {
        List<Coordinate[]> parts = new ArrayList<>();
        if (geometry instanceof Polygon polygon) {
            appendPolygonParts(parts, polygon);
        } else if (geometry instanceof MultiPolygon multiPolygon) {
            for (int index = 0; index < multiPolygon.getNumGeometries(); index++) {
                appendPolygonParts(parts, (Polygon) multiPolygon.getGeometryN(index));
            }
        } else {
            throw new InvalidGeoJsonException("Expected polygon geometry but found " + geometry.getGeometryType() + ".");
        }
        return serializeMultiPartShape(ShapeType.POLYGON, parts, geometry.getEnvelopeInternal());
    }

    private static void appendPolygonParts(List<Coordinate[]> parts, Polygon polygon) {
        parts.add(orientedRing((LinearRing) polygon.getExteriorRing(), false));
        for (int index = 0; index < polygon.getNumInteriorRing(); index++) {
            parts.add(orientedRing((LinearRing) polygon.getInteriorRingN(index), true));
        }
    }

    private static Coordinate[] orientedRing(LinearRing ring, boolean expectCounterClockwise) {
        Coordinate[] coordinates = requireCoordinates(ring.getCoordinates(), 4, "LinearRing");
        boolean isCounterClockwise = Orientation.isCCW(coordinates);
        if (isCounterClockwise == expectCounterClockwise) {
            return coordinates;
        }

        Coordinate[] reversed = new Coordinate[coordinates.length];
        for (int index = 0; index < coordinates.length; index++) {
            reversed[index] = coordinates[coordinates.length - 1 - index];
        }
        return reversed;
    }

    private static byte[] serializeMultiPointContent(List<Coordinate> points, Envelope envelope) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeIntLE(output, ShapeType.MULTIPOINT.code());
        writeBounds(output, envelope);
        writeIntLE(output, points.size());
        for (Coordinate point : points) {
            writeDoubleLE(output, point.getX());
            writeDoubleLE(output, point.getY());
        }
        return output.toByteArray();
    }

    private static byte[] serializeMultiPartShape(ShapeType shapeType, List<Coordinate[]> parts, Envelope envelope) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeIntLE(output, shapeType.code());
        writeBounds(output, envelope);
        writeIntLE(output, parts.size());

        int pointCount = 0;
        for (Coordinate[] part : parts) {
            pointCount += part.length;
        }
        writeIntLE(output, pointCount);

        int offset = 0;
        for (Coordinate[] part : parts) {
            writeIntLE(output, offset);
            offset += part.length;
        }

        for (Coordinate[] part : parts) {
            for (Coordinate coordinate : part) {
                writeDoubleLE(output, coordinate.getX());
                writeDoubleLE(output, coordinate.getY());
            }
        }

        return output.toByteArray();
    }

    private static Coordinate[] requireCoordinates(Coordinate[] coordinates, int minimumSize, String geometryType) {
        if (coordinates == null || coordinates.length < minimumSize) {
            throw new InvalidGeoJsonException(geometryType + " must contain at least " + minimumSize + " coordinates.");
        }
        return coordinates;
    }

    private static void writeHeader(java.io.OutputStream output, ShapeType shapeType, Envelope bounds, int fileLengthWords)
            throws IOException {
        writeIntBE(output, SHAPE_FILE_CODE);
        for (int index = 0; index < 5; index++) {
            writeIntBE(output, 0);
        }
        writeIntBE(output, fileLengthWords);
        writeIntLE(output, SHAPE_FILE_VERSION);
        writeIntLE(output, shapeType.code());
        writeBounds(output, bounds);
        writeDoubleLE(output, 0);
        writeDoubleLE(output, 0);
        writeDoubleLE(output, 0);
        writeDoubleLE(output, 0);
    }

    private static void writeBounds(java.io.OutputStream output, Envelope bounds) throws IOException {
        writeDoubleLE(output, bounds.getMinX());
        writeDoubleLE(output, bounds.getMinY());
        writeDoubleLE(output, bounds.getMaxX());
        writeDoubleLE(output, bounds.getMaxY());
    }

    private static void writeIntBE(java.io.OutputStream output, int value) throws IOException {
        output.write((value >>> 24) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }

    private static void writeIntLE(java.io.OutputStream output, int value) throws IOException {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 24) & 0xFF);
    }

    private static void writeShortLE(java.io.OutputStream output, int value) throws IOException {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
    }

    private static void writeDoubleLE(java.io.OutputStream output, double value) throws IOException {
        long bits = Double.doubleToLongBits(value);
        for (int shift = 0; shift < 64; shift += 8) {
            output.write((int) ((bits >>> shift) & 0xFF));
        }
    }

    private record ShapeRecord(int offsetWords, int contentLengthWords, byte[] content) {
    }
}

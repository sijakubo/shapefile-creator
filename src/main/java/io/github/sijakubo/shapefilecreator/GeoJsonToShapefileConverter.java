package io.github.sijakubo.shapefilecreator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sijakubo.shapefilecreator.internal.GeoJsonFeatureCollectionParser;
import io.github.sijakubo.shapefilecreator.internal.SchemaBuilder;
import io.github.sijakubo.shapefilecreator.internal.ShapefileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Entry point for converting GeoJSON FeatureCollections into ESRI shapefiles.
 */
public final class GeoJsonToShapefileConverter {

    private final ObjectMapper objectMapper;

    public GeoJsonToShapefileConverter() {
        this(new ObjectMapper());
    }

    public GeoJsonToShapefileConverter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public ConversionResult convert(String geoJsonFeatureCollection, Path outputDirectory, String fileNameBase) {
        Objects.requireNonNull(geoJsonFeatureCollection, "geoJsonFeatureCollection must not be null");

        try {
            JsonNode root = objectMapper.readTree(geoJsonFeatureCollection);
            return convert(root, outputDirectory, fileNameBase);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to parse GeoJSON payload.", exception);
        }
    }

    public ConversionResult convert(InputStream geoJsonFeatureCollectionStream, Path outputDirectory, String fileNameBase) {
        Objects.requireNonNull(geoJsonFeatureCollectionStream, "geoJsonFeatureCollectionStream must not be null");

        try {
            JsonNode root = objectMapper.readTree(geoJsonFeatureCollectionStream);
            return convert(root, outputDirectory, fileNameBase);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to parse GeoJSON stream.", exception);
        }
    }

    public ConversionResult convert(Path geoJsonFile, Path outputDirectory, String fileNameBase) {
        Objects.requireNonNull(geoJsonFile, "geoJsonFile must not be null");

        try (InputStream inputStream = Files.newInputStream(geoJsonFile)) {
            return convert(inputStream, outputDirectory, fileNameBase);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read GeoJSON file " + geoJsonFile + ".", exception);
        }
    }

    public byte[] convert(String geoJsonFeatureCollection, String fileNameBase) {
        Objects.requireNonNull(geoJsonFeatureCollection, "geoJsonFeatureCollection must not be null");

        try {
            JsonNode root = objectMapper.readTree(geoJsonFeatureCollection);
            return convertToZip(root, fileNameBase);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to parse GeoJSON payload.", exception);
        }
    }

    public byte[] convert(InputStream geoJsonFeatureCollectionStream, String fileNameBase) {
        Objects.requireNonNull(geoJsonFeatureCollectionStream, "geoJsonFeatureCollectionStream must not be null");

        try {
            JsonNode root = objectMapper.readTree(geoJsonFeatureCollectionStream);
            return convertToZip(root, fileNameBase);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to parse GeoJSON stream.", exception);
        }
    }

    public byte[] convert(Path geoJsonFile, String fileNameBase) {
        Objects.requireNonNull(geoJsonFile, "geoJsonFile must not be null");

        try (InputStream inputStream = Files.newInputStream(geoJsonFile)) {
            return convert(inputStream, fileNameBase);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read GeoJSON file " + geoJsonFile + ".", exception);
        }
    }

    private ConversionResult convert(JsonNode root, Path outputDirectory, String fileNameBase) {
        var featureCollection = GeoJsonFeatureCollectionParser.parse(root);
        var schema = SchemaBuilder.build(featureCollection);
        return ShapefileWriter.write(featureCollection, schema, outputDirectory, fileNameBase);
    }

    private byte[] convertToZip(JsonNode root, String fileNameBase) {
        Objects.requireNonNull(fileNameBase, "fileNameBase must not be null");

        try {
            Path tempDirectory = Files.createTempDirectory("shapefile-creator-");
            try {
                convert(root, tempDirectory, fileNameBase);
                return zipDirectory(tempDirectory);
            } finally {
                deleteRecursively(tempDirectory);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create ZIP archive for shapefile.", exception);
        }
    }

    private byte[] zipDirectory(Path directory) throws IOException {
        try (var outputStream = new java.io.ByteArrayOutputStream();
             var zipOutputStream = new ZipOutputStream(outputStream)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                for (Path file : files) {
                    ZipEntry entry = new ZipEntry(file.getFileName().toString());
                    zipOutputStream.putNextEntry(entry);
                    Files.copy(file, zipOutputStream);
                    zipOutputStream.closeEntry();
                }
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}

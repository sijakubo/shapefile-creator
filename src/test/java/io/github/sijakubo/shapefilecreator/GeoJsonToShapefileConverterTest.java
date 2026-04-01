package io.github.sijakubo.shapefilecreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoJsonToShapefileConverterTest {

    @TempDir
    Path tempDir;

    @Test
    void convertsFeatureCollectionIntoShapefile() throws IOException {
        String geoJson = """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": {
                        "type": "Point",
                        "coordinates": [13.404954, 52.520008]
                      },
                      "properties": {
                        "name": "Berlin",
                        "population": 3769000,
                        "capital": true
                      }
                    }
                  ]
                }
                """;


        GeoJsonToShapefileConverter converter = new GeoJsonToShapefileConverter();
        ConversionResult result = converter.convert(geoJson, tempDir, "cities");

        assertTrue(Files.exists(result.shpFile()));
        assertTrue(Files.exists(result.shxFile()));
        assertTrue(Files.exists(result.dbfFile()));
        assertEquals("cities.shp", result.shpFile().getFileName().toString());

        byte[] shpBytes = Files.readAllBytes(result.shpFile());
        assertEquals(9994, ByteBuffer.wrap(shpBytes, 0, 4).getInt());
        assertEquals(1000, ByteBuffer.wrap(shpBytes, 28, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        assertEquals(1, ByteBuffer.wrap(shpBytes, 32, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());

        byte[] shxBytes = Files.readAllBytes(result.shxFile());
        assertEquals(1, ByteBuffer.wrap(shxBytes, 32, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        assertEquals(50, ByteBuffer.wrap(shxBytes, 100, 4).getInt());
        assertEquals(10, ByteBuffer.wrap(shxBytes, 104, 4).getInt());
    }

    @Test
    void convertsFeatureCollectionIntoZipBytes() throws IOException {
        String geoJson = """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": {
                        "type": "Point",
                        "coordinates": [13.404954, 52.520008]
                      },
                      "properties": {
                        "name": "Berlin"
                      }
                    }
                  ]
                }
                """;

        GeoJsonToShapefileConverter converter = new GeoJsonToShapefileConverter();
        byte[] result = converter.convert(geoJson, "cities");

        assertTrue(result.length > 0);

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(result))) {
            var entry = zipInputStream.getNextEntry();
            while (entry != null) {
                entries.add(entry.getName());
                entry = zipInputStream.getNextEntry();
            }
        }

        assertTrue(entries.contains("cities.shp"));
        assertTrue(entries.contains("cities.shx"));
        assertTrue(entries.contains("cities.dbf"));
        assertTrue(entries.contains("cities.prj"));
    }
}

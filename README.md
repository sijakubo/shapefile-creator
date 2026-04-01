# shapefile-creator

Java library to convert GeoJSON `FeatureCollection` payloads into ESRI Shapefiles.

For a deeper walkthrough of the project and implementation, see this post:
[Building a Pure Java GeoJSON to Shapefile Library](https://sijakubo.github.io/info/posts/post-13/)

## Current scope

- Accepts GeoJSON as `String`, `InputStream`, or file `Path`
- Validates that all features use the same geometry type
- Infers DBF field types from GeoJSON properties
- Uses a pure Java writer without GeoTools
- Writes `.shp`, `.shx`, `.dbf`, and `.prj`
- Defaults to `EPSG:4326` when the GeoJSON does not declare a CRS
- Supports `Point`, `MultiPoint`, `LineString`, `MultiLineString`, `Polygon`, and `MultiPolygon`
- Supports `.prj` generation for `EPSG:4326` and `EPSG:3857`

## Usage

```java
import io.github.sijakubo.shapefilecreator.GeoJsonToShapefileConverter;
import java.nio.file.Path;

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
converter.convert(geoJson, Path.of("out"), "cities");
```

This creates:

- `out/cities.shp`
- `out/cities.shx`
- `out/cities.dbf`
- `out/cities.prj`

You can also create a ZIP archive in memory:

```java
GeoJsonToShapefileConverter converter = new GeoJsonToShapefileConverter();
byte[] zipBytes = converter.convert(geoJson, "cities");
```

## Build

The repository includes a Gradle build and a Maven `pom.xml`.

Validated locally with:

```bash
GRADLE_OPTS='-Dorg.gradle.native=false' ./gradlew test
```

## Maven Central

The project is prepared for publishing under the namespace `de.sijakubo`.

Before the first release, configure:

- a Sonatype Central user token in `~/.m2/settings.xml` under server id `central`
- a GPG key for artifact signing

Typical release command:

```bash
mvn clean deploy
```

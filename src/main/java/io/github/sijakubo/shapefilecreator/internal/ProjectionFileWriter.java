package io.github.sijakubo.shapefilecreator.internal;

import io.github.sijakubo.shapefilecreator.InvalidGeoJsonException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectionFileWriter {

    private ProjectionFileWriter() {
    }

    public static void write(Path prjFile, Integer srid) throws IOException {
        Files.writeString(prjFile, wktFor(srid), StandardCharsets.US_ASCII);
    }

    private static String wktFor(Integer srid) {
        if (srid == null || srid == 4326) {
            return "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],"
                    + "PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
        }
        if (srid == 3857) {
            return "PROJCS[\"WGS 84 / Pseudo-Mercator\",GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\","
                    + "SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],"
                    + "UNIT[\"degree\",0.0174532925199433]],PROJECTION[\"Mercator_1SP\"],"
                    + "PARAMETER[\"central_meridian\",0],PARAMETER[\"scale_factor\",1],"
                    + "PARAMETER[\"false_easting\",0],PARAMETER[\"false_northing\",0],"
                    + "UNIT[\"metre\",1],AXIS[\"Easting\",EAST],AXIS[\"Northing\",NORTH]]";
        }

        throw new InvalidGeoJsonException("Unsupported CRS EPSG:" + srid + ". Supported without GeoTools: EPSG:4326 and EPSG:3857.");
    }
}

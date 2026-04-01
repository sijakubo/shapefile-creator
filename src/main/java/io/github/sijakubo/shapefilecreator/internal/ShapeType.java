package io.github.sijakubo.shapefilecreator.internal;

import io.github.sijakubo.shapefilecreator.InvalidGeoJsonException;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

public enum ShapeType {
    POINT(1),
    POLYLINE(3),
    POLYGON(5),
    MULTIPOINT(8);

    private final int code;

    ShapeType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ShapeType fromGeometryClass(Class<?> geometryClass) {
        if (Point.class.equals(geometryClass)) {
            return POINT;
        }
        if (MultiPoint.class.equals(geometryClass)) {
            return MULTIPOINT;
        }
        if (LineString.class.equals(geometryClass) || MultiLineString.class.equals(geometryClass)) {
            return POLYLINE;
        }
        if (Polygon.class.equals(geometryClass) || MultiPolygon.class.equals(geometryClass)) {
            return POLYGON;
        }

        throw new InvalidGeoJsonException("Unsupported geometry type for shapefile export: " + geometryClass.getSimpleName() + ".");
    }
}

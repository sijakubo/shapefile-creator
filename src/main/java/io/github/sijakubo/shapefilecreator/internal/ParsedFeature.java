package io.github.sijakubo.shapefilecreator.internal;

import java.util.Map;
import org.locationtech.jts.geom.Geometry;

public record ParsedFeature(
        Geometry geometry,
        Map<String, Object> properties) {
}

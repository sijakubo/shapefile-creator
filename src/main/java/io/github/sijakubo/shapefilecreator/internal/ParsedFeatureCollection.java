package io.github.sijakubo.shapefilecreator.internal;

import java.util.List;
import org.locationtech.jts.geom.Geometry;

public record ParsedFeatureCollection(
        Class<? extends Geometry> geometryClass,
        Integer srid,
        List<ParsedFeature> features) {
}

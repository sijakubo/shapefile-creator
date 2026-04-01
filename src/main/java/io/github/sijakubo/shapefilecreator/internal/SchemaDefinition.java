package io.github.sijakubo.shapefilecreator.internal;

import java.util.List;

public record SchemaDefinition(
        ShapeType shapeType,
        Integer srid,
        List<DbfFieldDefinition> fields) {
}

package io.github.sijakubo.shapefilecreator.internal;

public record DbfFieldDefinition(
        String originalName,
        String fieldName,
        DbfFieldType fieldType,
        int length,
        int decimalCount) {
}

package io.github.sijakubo.shapefilecreator.internal;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.nio.charset.StandardCharsets;

public final class SchemaBuilder {

    private static final int DBF_FIELD_NAME_MAX_LENGTH = 10;
    private static final int DBF_CHARACTER_MAX_LENGTH = 254;
    private static final int DBF_NUMERIC_MAX_LENGTH = 20;

    private SchemaBuilder() {
    }

    public static SchemaDefinition build(ParsedFeatureCollection featureCollection) {
        Objects.requireNonNull(featureCollection, "featureCollection must not be null");

        Map<String, Class<?>> propertyTypes = inferPropertyTypes(featureCollection.features());
        Map<String, String> sanitizedNames = sanitizeNames(propertyTypes.keySet());
        List<DbfFieldDefinition> fields = new java.util.ArrayList<>();
        for (String propertyName : propertyTypes.keySet()) {
            fields.add(buildFieldDefinition(propertyName, sanitizedNames.get(propertyName), propertyTypes.get(propertyName), featureCollection.features()));
        }

        return new SchemaDefinition(
                ShapeType.fromGeometryClass(featureCollection.geometryClass()),
                featureCollection.srid(),
                List.copyOf(fields));
    }

    private static Map<String, Class<?>> inferPropertyTypes(List<ParsedFeature> features) {
        Map<String, Class<?>> types = new LinkedHashMap<>();

        for (ParsedFeature feature : features) {
            for (Map.Entry<String, Object> entry : feature.properties().entrySet()) {
                Class<?> candidate = normalizeType(entry.getValue());
                types.merge(entry.getKey(), candidate, SchemaBuilder::mergeTypes);
            }
        }

        return types;
    }

    private static Class<?> normalizeType(Object value) {
        if (value == null) {
            return String.class;
        }
        if (value instanceof Boolean) {
            return Boolean.class;
        }
        if (value instanceof Integer || value instanceof Long) {
            return Long.class;
        }
        if (value instanceof Float || value instanceof Double) {
            return Double.class;
        }
        return String.class;
    }

    private static Class<?> mergeTypes(Class<?> left, Class<?> right) {
        if (left.equals(right)) {
            return left;
        }
        if ((left.equals(Long.class) && right.equals(Double.class))
                || (left.equals(Double.class) && right.equals(Long.class))) {
            return Double.class;
        }
        return String.class;
    }

    private static Map<String, String> sanitizeNames(Set<String> propertyNames) {
        Map<String, String> sanitizedNames = new LinkedHashMap<>();
        Map<String, Integer> collisions = new LinkedHashMap<>();

        for (String originalName : propertyNames) {
            String sanitized = sanitize(originalName);
            int suffix = collisions.getOrDefault(sanitized, 0);
            collisions.put(sanitized, suffix + 1);

            String finalName = sanitized;
            if (suffix > 0) {
                String suffixText = String.valueOf(suffix);
                int maxBaseLength = DBF_FIELD_NAME_MAX_LENGTH - suffixText.length();
                finalName = sanitized.substring(0, Math.max(1, maxBaseLength)) + suffixText;
            }
            sanitizedNames.put(originalName, finalName);
        }

        return sanitizedNames;
    }

    private static DbfFieldDefinition buildFieldDefinition(
            String originalName,
            String fieldName,
            Class<?> javaType,
            List<ParsedFeature> features) {
        if (Boolean.class.equals(javaType)) {
            return new DbfFieldDefinition(originalName, fieldName, DbfFieldType.LOGICAL, 1, 0);
        }
        if (Long.class.equals(javaType)) {
            return new DbfFieldDefinition(
                    originalName,
                    fieldName,
                    DbfFieldType.NUMERIC,
                    inferLongLength(originalName, features),
                    0);
        }
        if (Double.class.equals(javaType)) {
            return inferDoubleField(originalName, fieldName, features);
        }

        return new DbfFieldDefinition(
                originalName,
                fieldName,
                DbfFieldType.CHARACTER,
                inferCharacterLength(originalName, features),
                0);
    }

    private static int inferLongLength(String propertyName, List<ParsedFeature> features) {
        int maxLength = 1;
        for (ParsedFeature feature : features) {
            Object value = feature.properties().get(propertyName);
            if (value instanceof Number number) {
                maxLength = Math.max(maxLength, String.valueOf(number.longValue()).length());
            }
        }
        return Math.min(DBF_NUMERIC_MAX_LENGTH, maxLength);
    }

    private static DbfFieldDefinition inferDoubleField(String originalName, String fieldName, List<ParsedFeature> features) {
        int maxLength = 3;
        int maxScale = 0;

        for (ParsedFeature feature : features) {
            Object value = feature.properties().get(originalName);
            if (value instanceof Number number) {
                BigDecimal decimal = BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros();
                String plain = decimal.toPlainString();
                maxLength = Math.max(maxLength, plain.length());
                maxScale = Math.max(maxScale, Math.max(0, decimal.scale()));
            }
        }

        maxScale = Math.min(10, maxScale);
        if (maxLength > DBF_NUMERIC_MAX_LENGTH) {
            return new DbfFieldDefinition(
                    originalName,
                    fieldName,
                    DbfFieldType.CHARACTER,
                    inferCharacterLength(originalName, features),
                    0);
        }

        return new DbfFieldDefinition(
                originalName,
                fieldName,
                DbfFieldType.NUMERIC,
                Math.min(DBF_NUMERIC_MAX_LENGTH, Math.max(maxLength, maxScale > 0 ? maxScale + 2 : 1)),
                maxScale);
    }

    private static int inferCharacterLength(String propertyName, List<ParsedFeature> features) {
        int maxLength = 1;
        for (ParsedFeature feature : features) {
            Object value = feature.properties().get(propertyName);
            if (value != null) {
                int byteLength = String.valueOf(value).getBytes(StandardCharsets.ISO_8859_1).length;
                maxLength = Math.max(maxLength, byteLength);
            }
        }
        return Math.min(DBF_CHARACTER_MAX_LENGTH, maxLength);
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "field";
        }

        String compact = name.replaceAll("[^A-Za-z0-9_]", "_")
                .replaceAll("_+", "_")
                .toLowerCase(Locale.ROOT);

        if (compact.isBlank()) {
            compact = "field";
        }

        if (Character.isDigit(compact.charAt(0))) {
            compact = "f" + compact;
        }

        if (compact.length() > DBF_FIELD_NAME_MAX_LENGTH) {
            compact = compact.substring(0, DBF_FIELD_NAME_MAX_LENGTH);
        }

        return compact;
    }
}

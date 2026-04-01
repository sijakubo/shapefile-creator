package io.github.sijakubo.shapefilecreator.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.sijakubo.shapefilecreator.InvalidGeoJsonException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;

public final class GeoJsonFeatureCollectionParser {

    private GeoJsonFeatureCollectionParser() {
    }

    public static ParsedFeatureCollection parse(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new InvalidGeoJsonException("GeoJSON root must be an object.");
        }

        String type = text(root.get("type"));
        if (!"FeatureCollection".equals(type)) {
            throw new InvalidGeoJsonException("GeoJSON root type must be FeatureCollection.");
        }

        JsonNode featuresNode = root.get("features");
        if (featuresNode == null || !featuresNode.isArray()) {
            throw new InvalidGeoJsonException("FeatureCollection must contain an array field named features.");
        }

        List<ParsedFeature> features = new ArrayList<>();
        Class<? extends Geometry> geometryClass = null;
        Integer srid = readSrid(root);
        GeoJsonReader reader = new GeoJsonReader();

        for (JsonNode featureNode : featuresNode) {
            if (!"Feature".equals(text(featureNode.get("type")))) {
                throw new InvalidGeoJsonException("Each entry in features must be of type Feature.");
            }

            JsonNode geometryNode = featureNode.get("geometry");
            if (geometryNode == null || geometryNode.isNull()) {
                throw new InvalidGeoJsonException("Features without geometry are not supported for shapefile export.");
            }

            Geometry geometry = parseGeometry(geometryNode, reader);
            if (geometryClass == null) {
                geometryClass = geometry.getClass();
                if (srid == null) {
                    srid = readSrid(geometryNode);
                }
            } else if (!geometryClass.equals(geometry.getClass())) {
                throw new InvalidGeoJsonException(
                        "A shapefile can only contain one geometry type. Found both "
                                + geometryClass.getSimpleName() + " and " + geometry.getClass().getSimpleName() + ".");
            }

            Map<String, Object> properties = readProperties(featureNode.get("properties"));
            features.add(new ParsedFeature(geometry, properties));
        }

        if (features.isEmpty()) {
            throw new InvalidGeoJsonException("FeatureCollection must contain at least one feature.");
        }

        return new ParsedFeatureCollection(geometryClass, srid != null ? srid : 4326, List.copyOf(features));
    }

    private static Geometry parseGeometry(JsonNode geometryNode, GeoJsonReader reader) {
        try {
            Geometry geometry = reader.read(geometryNode.toString());
            if (geometry == null) {
                throw new InvalidGeoJsonException("Failed to parse GeoJSON geometry.");
            }
            return geometry;
        } catch (ParseException exception) {
            throw new InvalidGeoJsonException("Failed to parse GeoJSON geometry.", exception);
        }
    }

    private static Map<String, Object> readProperties(JsonNode propertiesNode) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (propertiesNode == null || propertiesNode.isNull()) {
            return properties;
        }
        if (!propertiesNode.isObject()) {
            throw new InvalidGeoJsonException("Feature properties must be a JSON object.");
        }

        propertiesNode.fields().forEachRemaining(entry -> properties.put(entry.getKey(), coerceValue(entry.getValue())));
        return properties;
    }

    private static Object coerceValue(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isBoolean()) {
            return valueNode.booleanValue();
        }
        if (valueNode.isIntegralNumber()) {
            return valueNode.longValue();
        }
        if (valueNode.isFloatingPointNumber()) {
            return valueNode.doubleValue();
        }
        if (valueNode.isTextual()) {
            return valueNode.textValue();
        }

        // DBF does not support nested objects or arrays directly. They are stored as JSON strings.
        return valueNode.toString();
    }

    private static Integer readSrid(JsonNode geometryNode) {
        JsonNode crsNode = geometryNode.get("crs");
        if (crsNode == null || !crsNode.isObject()) {
            return null;
        }

        JsonNode propertiesNode = crsNode.get("properties");
        if (propertiesNode == null || !propertiesNode.isObject()) {
            return null;
        }

        String name = text(propertiesNode.get("name"));
        if (name == null) {
            return null;
        }

        String normalized = name.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("EPSG:")) {
            try {
                return Integer.parseInt(normalized.substring("EPSG:".length()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String text(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}

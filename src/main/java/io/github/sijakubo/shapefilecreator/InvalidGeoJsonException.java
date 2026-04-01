package io.github.sijakubo.shapefilecreator;

public final class InvalidGeoJsonException extends RuntimeException {

    public InvalidGeoJsonException(String message) {
        super(message);
    }

    public InvalidGeoJsonException(String message, Throwable cause) {
        super(message, cause);
    }
}

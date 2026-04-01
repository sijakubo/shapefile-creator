package io.github.sijakubo.shapefilecreator.internal;

public enum DbfFieldType {
    CHARACTER('C'),
    NUMERIC('N'),
    LOGICAL('L');

    private final char code;

    DbfFieldType(char code) {
        this.code = code;
    }

    public char code() {
        return code;
    }
}

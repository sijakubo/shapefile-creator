package io.github.sijakubo.shapefilecreator;

import java.nio.file.Path;

public record ConversionResult(
        Path shpFile,
        Path shxFile,
        Path dbfFile,
        Path prjFile) {
}

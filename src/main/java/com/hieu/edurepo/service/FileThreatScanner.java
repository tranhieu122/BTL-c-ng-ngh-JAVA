package com.hieu.edurepo.service;

import java.io.IOException;
import java.nio.file.Path;

/** Extension point for an antivirus/content scanner; implementations must fail closed. */
@FunctionalInterface
public interface FileThreatScanner {
    boolean isSafe(Path stagedFile, String originalFileName) throws IOException;
}

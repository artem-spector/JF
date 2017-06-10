package com.jflop.server.util;

import java.io.File;

/**
 * TODO: Document!
 *
 * @author artem on 24/05/2017.
 */
public class FileUtil {

    public static boolean deleteRecursively(File file) {
        if (!file.exists()) return false;
        if (file.isFile()) return file.delete();
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
            return file.delete();
        }
        throw new IllegalArgumentException("Cannot recognize file " + file.getAbsolutePath());
    }
}

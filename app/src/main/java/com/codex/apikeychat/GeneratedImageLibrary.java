package com.codex.apikeychat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class GeneratedImageLibrary {
    private GeneratedImageLibrary() {
    }

    static List<File> listImages(File dir) {
        if (dir == null) {
            return new ArrayList<>();
        }
        File[] files = dir.listFiles(file -> file != null && file.isFile() && isImageFile(file));
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }
        ArrayList<File> imageFiles = new ArrayList<>(Arrays.asList(files));
        Collections.sort(imageFiles, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        return imageFiles;
    }

    static boolean deleteImage(File file, File libraryDir) {
        if (file == null || libraryDir == null || !file.isFile() || !isImageFile(file)) {
            return false;
        }
        try {
            String filePath = file.getCanonicalPath();
            String dirPath = libraryDir.getCanonicalPath();
            if (!filePath.startsWith(dirPath + File.separator)) {
                return false;
            }
            return file.delete();
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isImageFile(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }
}

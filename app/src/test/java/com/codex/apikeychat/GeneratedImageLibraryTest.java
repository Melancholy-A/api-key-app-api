package com.codex.apikeychat;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeneratedImageLibraryTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void listsOnlyImagesNewestFirst() throws Exception {
        File dir = folder.newFolder("generated_images");
        File oldPng = touch(dir, "old.png", 1000L);
        touch(dir, "notes.txt", 3000L);
        File newJpg = touch(dir, "new.jpg", 2000L);

        List<File> files = GeneratedImageLibrary.listImages(dir);

        assertEquals(2, files.size());
        assertEquals(newJpg.getName(), files.get(0).getName());
        assertEquals(oldPng.getName(), files.get(1).getName());
    }

    @Test
    public void deletesOnlyInsideLibraryDirectory() throws Exception {
        File dir = folder.newFolder("generated_images");
        File image = touch(dir, "image.jpeg", 1000L);
        File outside = folder.newFile("outside.png");

        assertTrue(GeneratedImageLibrary.deleteImage(image, dir));
        assertFalse(image.exists());
        assertFalse(GeneratedImageLibrary.deleteImage(outside, dir));
        assertTrue(outside.exists());
    }

    private static File touch(File dir, String name, long modified) throws Exception {
        File file = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[]{1, 2, 3});
        }
        assertTrue(file.setLastModified(modified));
        return file;
    }
}

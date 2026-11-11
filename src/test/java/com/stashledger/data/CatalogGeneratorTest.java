package com.stashledger.data;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class CatalogGeneratorTest
{
    @Test
    public void rejectsAnIncompleteUpstreamStashCatalog() throws Exception
    {
        Path directory = Files.createTempDirectory("stash-catalog-invalid-");
        try
        {
            Path stash = directory.resolve("STASHUnit.java");
            Path clue = directory.resolve("EmoteClue.java");
            Path watson = directory.resolve("WatsonStashUnit.java");
            Path output = directory.resolve("Generated.java");
            Files.writeString(stash, "public enum STASHUnit {}", StandardCharsets.UTF_8);
            Files.writeString(clue, "public class EmoteClue {}", StandardCharsets.UTF_8);
            Files.writeString(watson, "public enum StashUnit {}", StandardCharsets.UTF_8);

            Process process = new ProcessBuilder("python3", "scripts/generate-catalog.py",
                "--stash-source", stash.toString(), "--clue-source", clue.toString(),
                "--watson-source", watson.toString(),
                "--output", output.toString())
                .redirectErrorStream(true)
                .start();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            process.getInputStream().transferTo(stdout);

            assertNotEquals(0, process.waitFor());
            assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("expected 119 unique STASH units"));
        }
        finally
        {
            deleteRecursively(directory.toFile());
        }
    }

    private static void deleteRecursively(File file) throws IOException
    {
        if (file.isDirectory())
        {
            File[] children = file.listFiles();
            if (children != null)
            {
                for (File child : children)
                {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(file.toPath());
    }
}

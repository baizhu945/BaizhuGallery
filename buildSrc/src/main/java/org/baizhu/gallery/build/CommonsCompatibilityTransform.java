package org.baizhu.gallery.build;

import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Source: FossifyOrg/Commons tag 7.1.3 (GPL-3.0),
 * commons/src/main/kotlin/org/fossify/commons/activities/BaseSimpleActivity.kt, onCreate.
 * Only the false fake-app nag's startsWith prefix needs compatibility with this independent app.
 * No permission/SAF checks, app identity, other warnings, or executable instructions are changed.
 * The pinned Maven AAR remains the source; no patched binary is checked in.
 */
public abstract class CommonsCompatibilityTransform implements TransformAction<TransformParameters.None> {
    private static final String CLASS = "org/fossify/commons/activities/BaseSimpleActivity.class";

    @InputArtifact
    @PathSensitive(PathSensitivity.NONE)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        File input = getInputArtifact().get().getAsFile();
        File output = outputs.file("baizhu-compatible-commons-7.1.3.aar");
        try {
            Files.write(output.toPath(), rewriteZip(Files.readAllBytes(input.toPath()), "classes.jar", true));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot apply pinned Commons 7.1.3 compatibility transform", e);
        }
    }

    static byte[] rewriteZip(byte[] bytes, String target, boolean aar) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int hits = 0;
        Set<String> names = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes));
             ZipOutputStream out = new ZipOutputStream(result)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (!names.add(name)) throw new IOException("Duplicate ZIP entry: " + name);
                byte[] content = in.readAllBytes();
                if (name.equals(target)) {
                    hits++;
                    content = aar ? rewriteZip(content, CLASS, false) : ConstantPoolRewriter.rewrite(content);
                }
                // Fixed timestamp and stable input order make the generated archives deterministic.
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(0L);
                out.putNextEntry(copy);
                out.write(content);
                out.closeEntry();
            }
        }
        if (hits != 1) throw new IOException("Expected exactly one " + target + ", found " + hits);
        return result.toByteArray();
    }
}

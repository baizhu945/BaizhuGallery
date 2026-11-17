package org.baizhu.gallery.build;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** JDK-only self-test: run with javac/java, or :buildSrc:compatibilitySelfTest. */
public final class ConstantPoolRewriterSelfTest {
    public static void main(String[] args) throws Exception {
        byte[] original = fixture(ConstantPoolRewriter.ORIGINAL, 1);
        byte[] expected = fixture(ConstantPoolRewriter.REPLACEMENT, 1);
        if (!Arrays.equals(expected, ConstantPoolRewriter.rewrite(original))) {
            throw new AssertionError("Bytes other than the sole UTF8 entry changed");
        }
        rejects(fixture("org.fossify", 1));
        rejects(fixture(ConstantPoolRewriter.ORIGINAL, 2));
        rejects(expected);
        System.out.println("PASS: all constant-pool tags preserved byte-for-byte; exactly one UTF8 changed; zero/two matches rejected");
    }

    private static void rejects(byte[] bytes) throws Exception {
        try {
            ConstantPoolRewriter.rewrite(bytes);
            throw new AssertionError("Expected fail-closed rejection");
        } catch (IOException expected) {
            if (!expected.getMessage().contains("exactly one")) throw expected;
        }
    }

    private static byte[] fixture(String text, int copies) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        out.writeInt(0xCAFEBABE);
        out.writeShort(0);
        out.writeShort(61);
        out.writeShort(20 + copies);
        for (int i = 0; i < copies; i++) {
            out.writeByte(1);
            out.writeUTF(text);
        }
        // Every non-UTF8 JVM constant-pool tag, including the two-slot entries.
        int[] tags = {3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 16, 17, 18, 19, 20};
        int[] sizes = {4, 4, 8, 8, 2, 2, 4, 4, 4, 4, 3, 2, 4, 4, 2, 2};
        for (int i = 0; i < tags.length; i++) {
            out.writeByte(tags[i]);
            for (int j = 0; j < sizes[i]; j++) out.writeByte(0x80 + j);
        }
        // One unrelated UTF8 entry plus arbitrary post-pool bytes must survive exactly.
        out.writeByte(1);
        out.writeUTF("org.fossify.unrelated\u0000");
        out.write(new byte[] {0, 33, 0, 7, 0, 8, 1, 2, -1});
        return buffer.toByteArray();
    }
}

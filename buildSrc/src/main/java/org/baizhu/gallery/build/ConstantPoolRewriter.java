package org.baizhu.gallery.build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Replaces one exact UTF8 pool entry; all other class bytes remain untouched. */
public final class ConstantPoolRewriter {
    public static final String ORIGINAL = "org.fossify.";
    public static final String REPLACEMENT = "org.fossifymigration.baizhu.gallery";

    private ConstantPoolRewriter() {}

    public static byte[] rewrite(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(bytes.length + 64);
        DataOutputStream out = new DataOutputStream(buffer);
        int magic = in.readInt();
        if (magic != 0xCAFEBABE) throw new IOException("Not a Java class");
        out.writeInt(magic);
        out.writeShort(in.readUnsignedShort());
        out.writeShort(in.readUnsignedShort());
        int count = in.readUnsignedShort();
        out.writeShort(count);
        int hits = 0;
        for (int index = 1; index < count; index++) {
            int tag = in.readUnsignedByte();
            out.writeByte(tag);
            switch (tag) {
                case 1:
                    int length = in.readUnsignedShort();
                    byte[] text = new byte[length];
                    in.readFully(text);
                    if (Arrays.equals(text, ORIGINAL.getBytes(StandardCharsets.UTF_8))) {
                        text = REPLACEMENT.getBytes(StandardCharsets.UTF_8);
                        hits++;
                    }
                    out.writeShort(text.length);
                    out.write(text);
                    break;
                case 3: case 4: copy(in, out, 4); break;
                case 5: case 6:
                    if (++index >= count) throw new IOException("Invalid wide constant");
                    copy(in, out, 8);
                    break;
                case 7: case 8: case 16: case 19: case 20: copy(in, out, 2); break;
                case 9: case 10: case 11: case 12: case 17: case 18: copy(in, out, 4); break;
                case 15: copy(in, out, 3); break;
                default: throw new IOException("Unknown constant-pool tag " + tag);
            }
        }
        if (hits != 1) throw new IOException("Commons 7.1.3 compatibility requires exactly one '"
                + ORIGINAL + "' UTF8 entry, found " + hits + "; review upstream before upgrading");
        in.transferTo(out);
        return buffer.toByteArray();
    }

    private static void copy(DataInputStream in, DataOutputStream out, int size) throws IOException {
        byte[] bytes = new byte[size];
        in.readFully(bytes);
        out.write(bytes);
    }
}

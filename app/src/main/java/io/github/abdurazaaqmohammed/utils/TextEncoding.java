package io.github.abdurazaaqmohammed.utils;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

/**
 * Encoding and line-ending detection for plain text files, written for novel
 * editing where files routinely arrive as UTF-8, GBK/GB18030 or UTF-16 and
 * must be written back in the same shape they were opened with.
 */
public final class TextEncoding {

    public static final String UTF8 = "UTF-8";
    public static final String GB18030 = "GB18030";

    private TextEncoding() {
    }

    /** One decoded document plus the metadata needed to save it back as-is. */
    public static final class Decoded {
        public final String text;
        public final String charset;
        public final boolean bom;
        public final String lineSeparator;

        Decoded(String text, String charset, boolean bom, String lineSeparator) {
            this.text = text;
            this.charset = charset;
            this.bom = bom;
            this.lineSeparator = lineSeparator;
        }
    }

    public static Decoded decode(byte[] data) {
        return decode(data, null);
    }

    /**
     * Decodes {@code data}. A forced charset skips detection (used by the
     * "reload with encoding" action); otherwise a BOM wins, then strict UTF-8
     * validation, then GB18030, which covers GBK and most Chinese downloads.
     */
    public static Decoded decode(byte[] data, String forcedCharset) {
        if (data == null) data = new byte[0];
        int offset = 0;
        String charset = forcedCharset;
        boolean bom = false;

        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            if (charset == null) charset = UTF8;
            if (UTF8.equalsIgnoreCase(charset)) {
                offset = 3;
                bom = true;
            }
        } else if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            if (charset == null) {
                charset = "UTF-16LE";
                offset = 2;
                bom = true;
            }
        } else if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            if (charset == null) {
                charset = "UTF-16BE";
                offset = 2;
                bom = true;
            }
        }

        String text;
        if (charset == null) {
            text = strictDecode(data, UTF8);
            if (text != null) {
                charset = UTF8;
            } else {
                charset = GB18030;
                text = new String(data, Charset.forName(GB18030));
            }
        } else {
            text = decodeBody(data, offset, charset);
        }
        if (text == null) text = new String(data, offset, data.length - offset, Charset.forName(UTF8));

        String lineSeparator = detectLineSeparator(text);
        return new Decoded(normalizeNewlines(text), charset, bom, lineSeparator);
    }

    public static byte[] encode(String text, String charsetName, boolean bom, String lineSeparator) {
        if (text == null) text = "";
        String normalized = normalizeNewlines(text);
        String target = lineSeparator == null || lineSeparator.isEmpty() ? "\n" : lineSeparator;
        if (!"\n".equals(target)) normalized = normalized.replace("\n", target);

        String name = charsetName == null || charsetName.isEmpty() ? UTF8 : charsetName;
        Charset charset = Charset.forName(name);
        byte[] body = normalized.getBytes(charset);
        if (!bom) return body;

        String upper = name.toUpperCase(Locale.ROOT);
        if (UTF8.equals(upper)) {
            byte[] out = new byte[body.length + 3];
            out[0] = (byte) 0xEF;
            out[1] = (byte) 0xBB;
            out[2] = (byte) 0xBF;
            System.arraycopy(body, 0, out, 3, body.length);
            return out;
        }
        if ("UTF-16LE".equals(upper)) {
            byte[] out = new byte[body.length + 2];
            out[0] = (byte) 0xFF;
            out[1] = (byte) 0xFE;
            System.arraycopy(body, 0, out, 2, body.length);
            return out;
        }
        if ("UTF-16BE".equals(upper)) {
            byte[] out = new byte[body.length + 2];
            out[0] = (byte) 0xFE;
            out[1] = (byte) 0xFF;
            System.arraycopy(body, 0, out, 2, body.length);
            return out;
        }
        return body;
    }

    public static String detectLineSeparator(String text) {
        if (text == null) return "\n";
        if (text.contains("\r\n")) return "\r\n";
        if (text.contains("\r")) return "\r";
        return "\n";
    }

    public static String normalizeNewlines(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String decodeBody(byte[] data, int offset, String charsetName) {
        try {
            Charset charset = Charset.forName(charsetName);
            if (UTF8.equalsIgnoreCase(charsetName)) {
                String strict = strictDecode(data, UTF8, offset);
                if (strict != null) return strict;
            }
            return new String(data, offset, data.length - offset, charset);
        } catch (Exception e) {
            return null;
        }
    }

    private static String strictDecode(byte[] data, String charsetName) {
        return strictDecode(data, charsetName, 0);
    }

    private static String strictDecode(byte[] data, String charsetName, int offset) {
        try {
            CharsetDecoder decoder = Charset.forName(charsetName).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(data, offset, data.length - offset)).toString();
        } catch (CharacterCodingException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}

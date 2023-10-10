
package com.amazonaws.services.glue.readers.csv;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.format.InputAccessor;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.core.io.ContentReference;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.MergedStream;
import com.fasterxml.jackson.core.io.UTF32Reader;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.dataformat.csv.impl.CsvIOContext;
import com.fasterxml.jackson.dataformat.csv.impl.UTF8Reader;
import java.io.ByteArrayInputStream;
import java.io.CharConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;


public final class GlueCsvParserBootstrapper {
    static final byte UTF8_BOM_1 = -17;
    static final byte UTF8_BOM_2 = -69;
    static final byte UTF8_BOM_3 = -65;
    protected final IOContext _context;
    protected final ObjectCodec _codec;
    protected final InputStream _in;
    protected final byte[] _inputBuffer;
    private int _inputPtr;
    private int _inputEnd;
    protected int _inputProcessed;
    protected boolean _bigEndian = true;
    protected int _bytesPerChar = 0;

    public GlueCsvParserBootstrapper(IOContext ctxt, ObjectCodec codec, InputStream in) {
        this._context = ctxt;
        this._codec = codec;
        this._in = in;
        this._inputBuffer = ctxt.allocReadIOBuffer();
        this._inputEnd = this._inputPtr = 0;
        this._inputProcessed = 0;
    }

    public GlueCsvParserBootstrapper(IOContext ctxt, ObjectCodec codec, byte[] inputBuffer, int inputStart, int inputLen) {
        this._context = ctxt;
        this._codec = codec;
        this._in = null;
        this._inputBuffer = inputBuffer;
        this._inputPtr = inputStart;
        this._inputEnd = inputStart + inputLen;
        this._inputProcessed = -inputStart;
    }

    public GlueCsvParser constructParser(int baseFeatures, int csvFeatures) throws IOException {
        boolean foundEncoding = false;
        int quad;
        if (this.ensureLoaded(4)) {
            quad = this._inputBuffer[this._inputPtr] << 24 | (this._inputBuffer[this._inputPtr + 1] & 255) << 16 | (this._inputBuffer[this._inputPtr + 2] & 255) << 8 | this._inputBuffer[this._inputPtr + 3] & 255;
            if (this.handleBOM(quad)) {
                foundEncoding = true;
            } else if (this.checkUTF32(quad)) {
                foundEncoding = true;
            } else if (this.checkUTF16(quad >>> 16)) {
                foundEncoding = true;
            }
        } else if (this.ensureLoaded(2)) {
            quad = (this._inputBuffer[this._inputPtr] & 255) << 8 | this._inputBuffer[this._inputPtr + 1] & 255;
            if (this.checkUTF16(quad)) {
                foundEncoding = true;
            }
        }

        JsonEncoding enc;
        if (foundEncoding && this._bytesPerChar != 1) {
            if (this._bytesPerChar == 2) {
                enc = this._bigEndian ? JsonEncoding.UTF16_BE : JsonEncoding.UTF16_LE;
            } else {
                if (this._bytesPerChar != 4) {
                    throw new RuntimeException("Internal error");
                }

                enc = this._bigEndian ? JsonEncoding.UTF32_BE : JsonEncoding.UTF32_LE;
            }
        } else {
            enc = JsonEncoding.UTF8;
        }

        this._context.setEncoding(enc);

        if (this._context instanceof CsvIOContext) {
            return new GlueCsvParser((CsvIOContext)this._context, baseFeatures, csvFeatures, this._codec, this._createReader(enc));
        }
        /** start of patch **/
        try {
            Class<?> clazz = this._context.getClass();
            Field f1 = clazz.getDeclaredField("_bufferRecycler");
            Field f2 = clazz.getDeclaredField("_contentReference");
            Field f3 = clazz.getDeclaredField("_managedResource");
            f1.setAccessible(true);
            f2.setAccessible(true);
            f3.setAccessible(true);
            BufferRecycler br = (BufferRecycler) f1.get(this._context);
            ContentReference sourceRef = (ContentReference)f2.get(this._context);
            boolean managedResource = (Boolean) f3.get(this._context);
            CsvIOContext context = new CsvIOContext(br, sourceRef, managedResource);
            return new GlueCsvParser(context, baseFeatures, csvFeatures, this._codec, this._createReader(enc));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        /** end of patch **/
    }

    private Reader _createReader(JsonEncoding enc) throws IOException {
        switch (enc) {
            case UTF32_BE:
            case UTF32_LE:
                return new UTF32Reader(this._context, this._in, this._inputBuffer, this._inputPtr, this._inputEnd, enc.isBigEndian());
            case UTF16_BE:
            case UTF16_LE:
                InputStream in = this._in;
                if (in == null) {
                    in = new ByteArrayInputStream(this._inputBuffer, this._inputPtr, this._inputEnd);
                } else if (this._inputPtr < this._inputEnd) {
                    in = new MergedStream(this._context, (InputStream)in, this._inputBuffer, this._inputPtr, this._inputEnd);
                }

                return new InputStreamReader((InputStream)in, enc.getJavaName());
            case UTF8:
                return new UTF8Reader(this._in == null ? null : this._context, this._in, this._context.isResourceManaged(), this._inputBuffer, this._inputPtr, this._inputEnd - this._inputPtr);
            default:
                throw new RuntimeException();
        }
    }

    public static MatchStrength hasCSVFormat(InputAccessor acc, char quoteChar, char separatorChar) throws IOException {
        if (!acc.hasMoreBytes()) {
            return MatchStrength.INCONCLUSIVE;
        } else {
            byte b = acc.nextByte();
            if (b == -17) {
                if (!acc.hasMoreBytes()) {
                    return MatchStrength.INCONCLUSIVE;
                }

                if (acc.nextByte() != -69) {
                    return MatchStrength.NO_MATCH;
                }

                if (!acc.hasMoreBytes()) {
                    return MatchStrength.INCONCLUSIVE;
                }

                if (acc.nextByte() != -65) {
                    return MatchStrength.NO_MATCH;
                }

                if (!acc.hasMoreBytes()) {
                    return MatchStrength.INCONCLUSIVE;
                }

                b = acc.nextByte();
            }

            int ch = skipSpace(acc, b);
            if (ch < 0) {
                return MatchStrength.INCONCLUSIVE;
            } else {
                return ch != quoteChar && ch != separatorChar ? MatchStrength.INCONCLUSIVE : MatchStrength.WEAK_MATCH;
            }
        }
    }

    private static final int skipSpace(InputAccessor acc, byte b) throws IOException {
        while(true) {
            int ch = b & 255;
            if (ch != 32 && ch != 13 && ch != 10 && ch != 9) {
                return ch;
            }

            if (!acc.hasMoreBytes()) {
                return -1;
            }

            b = acc.nextByte();
            ch = b & 255;
        }
    }

    private boolean handleBOM(int quad) throws IOException {
        switch (quad) {
            case -131072:
                this._inputPtr += 4;
                this._bytesPerChar = 4;
                this._bigEndian = false;
                return true;
            case 65279:
                this._bigEndian = true;
                this._inputPtr += 4;
                this._bytesPerChar = 4;
                return true;
            case 65534:
                this.reportWeirdUCS4("2143");
            case -16842752:
                this.reportWeirdUCS4("3412");
            default:
                int msw = quad >>> 16;
                if (msw == 65279) {
                    this._inputPtr += 2;
                    this._bytesPerChar = 2;
                    this._bigEndian = true;
                    return true;
                } else if (msw == 65534) {
                    this._inputPtr += 2;
                    this._bytesPerChar = 2;
                    this._bigEndian = false;
                    return true;
                } else if (quad >>> 8 == 15711167) {
                    this._inputPtr += 3;
                    this._bytesPerChar = 1;
                    this._bigEndian = true;
                    return true;
                } else {
                    return false;
                }
        }
    }

    private boolean checkUTF32(int quad) throws IOException {
        if (quad >> 8 == 0) {
            this._bigEndian = true;
        } else if ((quad & 16777215) == 0) {
            this._bigEndian = false;
        } else if ((quad & -16711681) == 0) {
            this.reportWeirdUCS4("3412");
        } else {
            if ((quad & -65281) != 0) {
                return false;
            }

            this.reportWeirdUCS4("2143");
        }

        this._bytesPerChar = 4;
        return true;
    }

    private boolean checkUTF16(int i16) {
        if ((i16 & '\uff00') == 0) {
            this._bigEndian = true;
        } else {
            if ((i16 & 255) != 0) {
                return false;
            }

            this._bigEndian = false;
        }

        this._bytesPerChar = 2;
        return true;
    }

    private void reportWeirdUCS4(String type) throws IOException {
        throw new CharConversionException("Unsupported UCS-4 endianness (" + type + ") detected");
    }

    protected boolean ensureLoaded(int minimum) throws IOException {
        int count;
        for(int gotten = this._inputEnd - this._inputPtr; gotten < minimum; gotten += count) {
            if (this._in == null) {
                count = -1;
            } else {
                count = this._in.read(this._inputBuffer, this._inputEnd, this._inputBuffer.length - this._inputEnd);
            }

            if (count < 1) {
                return false;
            }

            this._inputEnd += count;
        }

        return true;
    }
}

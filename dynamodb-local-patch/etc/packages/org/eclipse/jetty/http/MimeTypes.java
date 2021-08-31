//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.eclipse.jetty.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import org.eclipse.jetty.util.ArrayTrie;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.Trie;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class MimeTypes {
    private static final Logger LOG = Log.getLogger(MimeTypes.class);
    private static final Trie<ByteBuffer> TYPES = new ArrayTrie(512);
    private static final Map<String, String> __dftMimeMap = new HashMap();
    private static final Map<String, String> __inferredEncodings = new HashMap();
    private static final Map<String, String> __assumedEncodings = new HashMap();
    public static final Trie<MimeTypes.Type> CACHE = new ArrayTrie(512);
    private final Map<String, String> _mimeMap = new HashMap();

    public MimeTypes() {
    }

    public synchronized Map<String, String> getMimeMap() {
        return this._mimeMap;
    }

    public void setMimeMap(Map<String, String> mimeMap) {
        this._mimeMap.clear();
        if (mimeMap != null) {
            Iterator var2 = mimeMap.entrySet().iterator();

            while(var2.hasNext()) {
                Entry<String, String> ext = (Entry)var2.next();
                this._mimeMap.put(StringUtil.asciiToLowerCase((String)ext.getKey()), normalizeMimeType((String)ext.getValue()));
            }
        }

    }

    public static String getDefaultMimeByExtension(String filename) {
        String type = null;
        if (filename != null) {
            int i = -1;

            while(type == null) {
                i = filename.indexOf(".", i + 1);
                if (i < 0 || i >= filename.length()) {
                    break;
                }

                String ext = StringUtil.asciiToLowerCase(filename.substring(i + 1));
                if (type == null) {
                    type = (String)__dftMimeMap.get(ext);
                }
            }
        }

        if (type == null) {
            type = (String)__dftMimeMap.get("*");
        }

        return type;
    }

    public String getMimeByExtension(String filename) {
        String type = null;
        if (filename != null) {
            int i = -1;

            while(type == null) {
                i = filename.indexOf(".", i + 1);
                if (i < 0 || i >= filename.length()) {
                    break;
                }

                String ext = StringUtil.asciiToLowerCase(filename.substring(i + 1));
                if (this._mimeMap != null) {
                    type = (String)this._mimeMap.get(ext);
                }

                if (type == null) {
                    type = (String)__dftMimeMap.get(ext);
                }
            }
        }

        if (type == null) {
            if (this._mimeMap != null) {
                type = (String)this._mimeMap.get("*");
            }

            if (type == null) {
                type = (String)__dftMimeMap.get("*");
            }
        }

        return type;
    }

    public void addMimeMapping(String extension, String type) {
        this._mimeMap.put(StringUtil.asciiToLowerCase(extension), normalizeMimeType(type));
    }

    public static Set<String> getKnownMimeTypes() {
        return new HashSet(__dftMimeMap.values());
    }

    private static String normalizeMimeType(String type) {
        MimeTypes.Type t = (MimeTypes.Type)CACHE.get(type);
        return t != null ? t.asString() : StringUtil.asciiToLowerCase(type);
    }

    public static String getCharsetFromContentType(String value) {
        if (value == null) {
            return null;
        } else {
            int end = value.length();
            int state = 0;
            int start = 0;
            boolean quote = false;

            int i;
            for(i = 0; i < end; ++i) {
                char b = value.charAt(i);
                if (quote && state != 10) {
                    if ('"' == b) {
                        quote = false;
                    }
                } else if (';' == b && state <= 8) {
                    state = 1;
                } else {
                    switch(state) {
                    case 0:
                        if ('"' == b) {
                            quote = true;
                        }
                        break;
                    case 1:
                        if ('c' == b) {
                            state = 2;
                        } else if (' ' != b) {
                            state = 0;
                        }
                        break;
                    case 2:
                        if ('h' == b) {
                            state = 3;
                        } else {
                            state = 0;
                        }
                        break;
                    case 3:
                        if ('a' == b) {
                            state = 4;
                        } else {
                            state = 0;
                        }
                        break;
                    case 4:
                        if ('r' == b) {
                            state = 5;
                        } else {
                            state = 0;
                        }
                        break;
                    case 5:
                        if ('s' == b) {
                            state = 6;
                        } else {
                            state = 0;
                        }
                        break;
                    case 6:
                        if ('e' == b) {
                            state = 7;
                        } else {
                            state = 0;
                        }
                        break;
                    case 7:
                        if ('t' == b) {
                            state = 8;
                        } else {
                            state = 0;
                        }
                        break;
                    case 8:
                        if ('=' == b) {
                            state = 9;
                        } else if (' ' != b) {
                            state = 0;
                        }
                        break;
                    case 9:
                        if (' ' != b) {
                            if ('"' == b) {
                                quote = true;
                                start = i + 1;
                                state = 10;
                            } else {
                                start = i;
                                state = 10;
                            }
                        }
                        break;
                    case 10:
                        if (!quote && (';' == b || ' ' == b) || quote && '"' == b) {
                            return StringUtil.normalizeCharset(value, start, i - start);
                        }
                    }
                }
            }

            if (state == 10) {
                return StringUtil.normalizeCharset(value, start, i - start);
            } else {
                return null;
            }
        }
    }

    public static Map<String, String> getInferredEncodings() {
        return __inferredEncodings;
    }

    public static Map<String, String> getAssumedEncodings() {
        return __assumedEncodings;
    }

    /** @deprecated */
    @Deprecated
    public static String inferCharsetFromContentType(String contentType) {
        return getCharsetAssumedFromContentType(contentType);
    }

    public static String getCharsetInferredFromContentType(String contentType) {
        return (String)__inferredEncodings.get(contentType);
    }

    public static String getCharsetAssumedFromContentType(String contentType) {
        return (String)__assumedEncodings.get(contentType);
    }

    public static String getContentTypeWithoutCharset(String value) {
        int end = value.length();
        int state = 0;
        int start = 0;
        boolean quote = false;
        int i = 0;

        StringBuilder builder;
        for(builder = null; i < end; ++i) {
            char b = value.charAt(i);
            if ('"' == b) {
                if (quote) {
                    quote = false;
                } else {
                    quote = true;
                }

                switch(state) {
                case 9:
                    builder = new StringBuilder();
                    builder.append(value, 0, start + 1);
                    state = 10;
                case 10:
                    break;
                case 11:
                    builder.append(b);
                    break;
                default:
                    start = i;
                    state = 0;
                }
            } else if (quote) {
                if (builder != null && state != 10) {
                    builder.append(b);
                }
            } else {
                switch(state) {
                case 0:
                    if (';' == b) {
                        state = 1;
                    } else if (' ' != b) {
                        start = i;
                    }
                    break;
                case 1:
                    if ('c' == b) {
                        state = 2;
                    } else if (' ' != b) {
                        state = 0;
                    }
                    break;
                case 2:
                    if ('h' == b) {
                        state = 3;
                    } else {
                        state = 0;
                    }
                    break;
                case 3:
                    if ('a' == b) {
                        state = 4;
                    } else {
                        state = 0;
                    }
                    break;
                case 4:
                    if ('r' == b) {
                        state = 5;
                    } else {
                        state = 0;
                    }
                    break;
                case 5:
                    if ('s' == b) {
                        state = 6;
                    } else {
                        state = 0;
                    }
                    break;
                case 6:
                    if ('e' == b) {
                        state = 7;
                    } else {
                        state = 0;
                    }
                    break;
                case 7:
                    if ('t' == b) {
                        state = 8;
                    } else {
                        state = 0;
                    }
                    break;
                case 8:
                    if ('=' == b) {
                        state = 9;
                    } else if (' ' != b) {
                        state = 0;
                    }
                    break;
                case 9:
                    if (' ' != b) {
                        builder = new StringBuilder();
                        builder.append(value, 0, start + 1);
                        state = 10;
                    }
                    break;
                case 10:
                    if (';' == b) {
                        builder.append(b);
                        state = 11;
                    }
                    break;
                case 11:
                    if (' ' != b) {
                        builder.append(b);
                    }
                }
            }
        }

        if (builder == null) {
            return value;
        } else {
            return builder.toString();
        }
    }

    static {
        MimeTypes.Type[] var0 = MimeTypes.Type.values();
        int var1 = var0.length;

        for(int var2 = 0; var2 < var1; ++var2) {
            MimeTypes.Type type = var0[var2];
            CACHE.put(type.toString(), type);
            TYPES.put(type.toString(), type.asBuffer());
            int charset = type.toString().indexOf(";charset=");
            if (charset > 0) {
                String alt = type.toString().replace(";charset=", "; charset=");
                CACHE.put(alt, type);
                TYPES.put(alt, type.asBuffer());
            }

            if (type.isCharsetAssumed()) {
                __assumedEncodings.put(type.asString(), type.getCharsetString());
            }
        }

        String resourceName = "org/eclipse/jetty/http/mime.properties";

        InputStream stream;
        InputStreamReader reader;
        final Properties props = new Properties();
        try {
            stream = MimeTypes.class.getClassLoader().getResourceAsStream(resourceName);

            try {
                if (stream == null) {
                    LOG.warn("Missing mime-type resource: {}", new Object[]{resourceName});
                } else {
                    try {
                        reader = new InputStreamReader(stream, StandardCharsets.UTF_8);

                        try {
                            // props = new Properties();
                            props.load(reader);
                            props.stringPropertyNames().stream().filter((x) -> {
                                return x != null;
                            }).forEach((x) -> {
                                __dftMimeMap.put(StringUtil.asciiToLowerCase(x), normalizeMimeType(props.getProperty(x)));
                            });
                            if (__dftMimeMap.size() == 0) {
                                LOG.warn("Empty mime types at {}", new Object[]{resourceName});
                            } else if (__dftMimeMap.size() < props.keySet().size()) {
                                LOG.warn("Duplicate or null mime-type extension in resource: {}", new Object[]{resourceName});
                            }
                        } catch (Throwable var12) {
                            try {
                                reader.close();
                            } catch (Throwable var11) {
                                var12.addSuppressed(var11);
                            }

                            throw var12;
                        }

                        reader.close();
                    } catch (IOException var13) {
                        LOG.warn(var13.toString(), new Object[0]);
                        LOG.debug(var13);
                    }
                }
            } catch (Throwable var16) {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Throwable var10) {
                        var16.addSuppressed(var10);
                    }
                }

                throw var16;
            }

            if (stream != null) {
                stream.close();
            }
        } catch (IOException var17) {
            LOG.warn(var17.toString(), new Object[0]);
            LOG.debug(var17);
        }

        resourceName = "org/eclipse/jetty/http/encoding.properties";

        try {
            stream = MimeTypes.class.getClassLoader().getResourceAsStream(resourceName);

            try {
                if (stream == null) {
                    LOG.warn("Missing encoding resource: {}", new Object[]{resourceName});
                } else {
                    try {
                        reader = new InputStreamReader(stream, StandardCharsets.UTF_8);

                        try {
                            // props = new Properties();
                            props.load(reader);
                            props.stringPropertyNames().stream().filter((t) -> {
                                return t != null;
                            }).forEach((t) -> {
                                String charset = props.getProperty(t);
                                if (charset.startsWith("-")) {
                                    __assumedEncodings.put(t, charset.substring(1));
                                } else {
                                    __inferredEncodings.put(t, props.getProperty(t));
                                }

                            });
                            if (__inferredEncodings.size() == 0) {
                                LOG.warn("Empty encodings at {}", new Object[]{resourceName});
                            } else if (__inferredEncodings.size() + __assumedEncodings.size() < props.keySet().size()) {
                                LOG.warn("Null or duplicate encodings in resource: {}", new Object[]{resourceName});
                            }
                        } catch (Throwable var8) {
                            try {
                                reader.close();
                            } catch (Throwable var7) {
                                var8.addSuppressed(var7);
                            }

                            throw var8;
                        }

                        reader.close();
                    } catch (IOException var9) {
                        LOG.warn(var9.toString(), new Object[0]);
                        LOG.debug(var9);
                    }
                }
            } catch (Throwable var14) {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Throwable var6) {
                        var14.addSuppressed(var6);
                    }
                }

                throw var14;
            }

            if (stream != null) {
                stream.close();
            }
        } catch (IOException var15) {
            LOG.warn(var15.toString(), new Object[0]);
            LOG.debug(var15);
        }

    }

    public static enum Type {
        FORM_ENCODED("application/x-www-form-urlencoded"),
        MESSAGE_HTTP("message/http"),
        MULTIPART_BYTERANGES("multipart/byteranges"),
        MULTIPART_FORM_DATA("multipart/form-data"),
        TEXT_HTML("text/html"),
        TEXT_PLAIN("text/plain"),
        TEXT_XML("text/xml"),
        TEXT_JSON("text/json", StandardCharsets.UTF_8),
        APPLICATION_JSON("application/json", StandardCharsets.UTF_8),
        TEXT_HTML_8859_1("text/html;charset=iso-8859-1", TEXT_HTML),
        TEXT_HTML_UTF_8("text/html;charset=utf-8", TEXT_HTML),
        TEXT_PLAIN_8859_1("text/plain;charset=iso-8859-1", TEXT_PLAIN),
        TEXT_PLAIN_UTF_8("text/plain;charset=utf-8", TEXT_PLAIN),
        TEXT_XML_8859_1("text/xml;charset=iso-8859-1", TEXT_XML),
        TEXT_XML_UTF_8("text/xml;charset=utf-8", TEXT_XML),
        TEXT_JSON_8859_1("text/json;charset=iso-8859-1", TEXT_JSON),
        TEXT_JSON_UTF_8("text/json;charset=utf-8", TEXT_JSON),
        APPLICATION_JSON_8859_1("application/json;charset=iso-8859-1", APPLICATION_JSON),
        APPLICATION_JSON_UTF_8("application/json;charset=utf-8", APPLICATION_JSON);

        private final String _string;
        private final MimeTypes.Type _base;
        private final ByteBuffer _buffer;
        private final Charset _charset;
        private final String _charsetString;
        private final boolean _assumedCharset;
        private final HttpField _field;

        private Type(String s) {
            this._string = s;
            this._buffer = BufferUtil.toBuffer(s);
            this._base = this;
            this._charset = null;
            this._charsetString = null;
            this._assumedCharset = false;
            this._field = new PreEncodedHttpField(HttpHeader.CONTENT_TYPE, this._string);
        }

        // FIXME: patched String.indexOf()
        private Type(String s, MimeTypes.Type base) {
            this._string = s;
            this._buffer = BufferUtil.toBuffer(s);
            this._base = base;

            java.util.List<String> parts = java.util.Arrays.asList(s.split(";charset="));
            String charset = "";

            if (parts.size() == 2) {
                charset = parts.get(1);
            } else {
                System.out.println("Unable to extract charset type from string " + s);
            }

            this._charset = Charset.forName(charset);
            this._charsetString = this._charset.toString().toLowerCase(Locale.ENGLISH);
            this._assumedCharset = false;
            this._field = new PreEncodedHttpField(HttpHeader.CONTENT_TYPE, this._string);
        }

        private Type(String s, Charset cs) {
            this._string = s;
            this._base = this;
            this._buffer = BufferUtil.toBuffer(s);
            this._charset = cs;
            this._charsetString = this._charset == null ? null : this._charset.toString().toLowerCase(Locale.ENGLISH);
            this._assumedCharset = true;
            this._field = new PreEncodedHttpField(HttpHeader.CONTENT_TYPE, this._string);
        }

        public ByteBuffer asBuffer() {
            return this._buffer.asReadOnlyBuffer();
        }

        public Charset getCharset() {
            return this._charset;
        }

        public String getCharsetString() {
            return this._charsetString;
        }

        public boolean is(String s) {
            return this._string.equalsIgnoreCase(s);
        }

        public String asString() {
            return this._string;
        }

        public String toString() {
            return this._string;
        }

        public boolean isCharsetAssumed() {
            return this._assumedCharset;
        }

        public HttpField getContentTypeField() {
            return this._field;
        }

        public MimeTypes.Type getBaseType() {
            return this._base;
        }
    }
}


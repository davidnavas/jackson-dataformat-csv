package com.fasterxml.jackson.dataformat.csv;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import org.codehaus.jackson.*;
import org.codehaus.jackson.impl.JsonGeneratorBase;
import org.codehaus.jackson.impl.JsonWriteContext;
import org.codehaus.jackson.io.IOContext;
import org.codehaus.jackson.io.SerializedString;

import com.fasterxml.jackson.dataformat.csv.impl.CsvWriter;

public class CsvGenerator extends JsonGeneratorBase
{
    /**
     * Enumeration that defines all togglable features for CSV writers
     */
    public enum Feature {
        /**
         * Feature that determines whether the first line of output
         * should consist of column names or not; if not (false), all lines
         * including the first one are data.
         *<p>
         * Default value is false.
         */
        WRITE_HEADER(false)
        ;

        protected final boolean _defaultState;
        protected final int _mask;
        
        /**
         * Method that calculates bit set (flags) of all features that
         * are enabled by default.
         */
        public static int collectDefaults()
        {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }
        
        private Feature(boolean defaultState) {
            _defaultState = defaultState;
            _mask = (1 << ordinal());
        }
        
        public boolean enabledByDefault() { return _defaultState; }
        public int getMask() { return _mask; }
    };

    protected final static long MIN_INT_AS_LONG = (long) Integer.MIN_VALUE;
    protected final static long MAX_INT_AS_LONG = (long) Integer.MAX_VALUE;
    
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    final protected IOContext _ioContext;

    /**
     * Bit flag composed of bits that indicate which
     * {@link org.codehaus.jackson.smile.SmileGenerator.Feature}s
     * are enabled.
     */
    protected int _csvFeatures;

    /**
     * Definition of columns being written, if available.
     */
    protected CsvSchema _schema;

    protected final CsvWriter _writer;
    
    /*
    /**********************************************************
    /* Output state
    /**********************************************************
     */

    /**
     * Index of column that we will be getting next, based on
     * field name call that was made.
     */
    protected int _nextColumnByName = -1;
    
    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */
    
    public CsvGenerator(IOContext ctxt, int jsonFeatures, int csvFeatures,
            ObjectCodec codec, Writer out,
            char columnSeparator, char quoteChar, char[] linefeed,
            CsvSchema schema)
    {
        super(jsonFeatures, codec);
        _ioContext = ctxt;
        _csvFeatures = csvFeatures;
        _writer = new CsvWriter(ctxt, out, columnSeparator, quoteChar, linefeed);
        _schema = schema;
    }

    /**
     * Method that {@link CsvFactory} calls immediately after constructing
     * the generator instance.
     */
    public void init()
        throws IOException, JsonGenerationException    
    {
        if (isEnabled(Feature.WRITE_HEADER)) {
            // could perhaps generate header on-the-fly in future, but for now
            if (_schema == null) {
                throw new JsonGenerationException("No Schema assigned, but Feature WRITE_HEADER set to true");
            }
            
        }
    }
    
    /*
    /**********************************************************
    /* Overridden methods, configuration
    /**********************************************************
     */

    /**
     * No way (or need) to indent anything, so let's block any attempts.
     * (should we throw an exception instead?)
     */
    @Override
    public CsvGenerator useDefaultPrettyPrinter()
    {
        return this;
    }

    /**
     * No way (or need) to indent anything, so let's block any attempts.
     * (should we throw an exception instead?)
     */
    @Override
    public CsvGenerator setPrettyPrinter(PrettyPrinter pp) {
        return this;
    }

    @Override
    public Object getOutputTarget() {
        return _writer.getOutputTarget();
    }

    @Override
    public void setSchema(FormatSchema schema)
    {
        if (!(schema instanceof CsvSchema)) {
            super.setSchema(schema);
            return;
        }
        _schema = (CsvSchema) schema;
    }

    /*
    /**********************************************************************
    /* Overridden methods; writing field names
    /**********************************************************************
     */
    
    /* And then methods overridden to make final, streamline some
     * aspects...
     */

    @Override
    public final void writeFieldName(String name) throws IOException, JsonGenerationException
    {
        if (_writeContext.writeFieldName(name) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        _writeFieldName(name);
    }
    
    @Override
    public final void writeFieldName(SerializedString name)
        throws IOException, JsonGenerationException
    {
        // Object is a value, need to verify it's allowed
        if (_writeContext.writeFieldName(name.getValue()) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        _writeFieldName(name.getValue());
    }

    @Override
    public final void writeFieldName(SerializableString name)
        throws IOException, JsonGenerationException
    {
        // Object is a value, need to verify it's allowed
        if (_writeContext.writeFieldName(name.getValue()) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        _writeFieldName(name.getValue());
    }

    @Override
    public final void writeStringField(String fieldName, String value)
        throws IOException, JsonGenerationException
    {
        if (_writeContext.writeFieldName(fieldName) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        _writeFieldName(fieldName);
        writeString(value);
    }

    private final void _writeFieldName(String name)
        throws IOException, JsonGenerationException
    {
        // just find the matching index -- must have schema for that
        if (_schema == null) {
            _reportError("Unrecognized column '"+name+"', can not resolve without CsvSchema");
        }
        CsvSchema.Column col = _schema.column(name);
        if (col == null) {
            _reportError("Unrecognized column '"+name+"': known columns: "+_schema.toString());
            
        }
        // and all we do is just note index to use for following value write
        _nextColumnByName = col.getIndex();
    }
    
    /*
    /**********************************************************
    /* Extended API, configuration
    /**********************************************************
     */

    public CsvGenerator enable(Feature f) {
        _csvFeatures |= f.getMask();
        return this;
    }

    public CsvGenerator disable(Feature f) {
        _csvFeatures &= ~f.getMask();
        return this;
    }

    public final boolean isEnabled(Feature f) {
        return (_csvFeatures & f.getMask()) != 0;
    }

    public CsvGenerator configure(Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    /*
    /**********************************************************
    /* Public API: low-level I/O
    /**********************************************************
     */

    @Override
    public final void flush() throws IOException
    {
        _writer.flush(isEnabled(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM));
    }
    
    @Override
    public void close() throws IOException
    {
        super.close();

        // Let's mark row as closed, if we had any...
        finishRow();
        _writer.close(_ioContext.isResourceManaged() || isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));
    }

    /*
    /**********************************************************
    /* Public API: structural output
    /**********************************************************
     */

    @Override
    public final void writeStartArray() throws IOException, JsonGenerationException
    {
        _verifyValueWrite("start an array");
        _writeContext = _writeContext.createChildArrayContext();
        // and that's about it, really
    }

    @Override
    public final void writeEndArray() throws IOException, JsonGenerationException
    {
        if (!_writeContext.inArray()) {
            _reportError("Current context not an ARRAY but "+_writeContext.getTypeDesc());
        }
        _writeContext = _writeContext.getParent();
        // not 100% fool-proof, but chances are row should be done now
        finishRow();
    }

    @Override
    public final void writeStartObject() throws IOException, JsonGenerationException
    {
        _verifyValueWrite("start an object");
        _writeContext = _writeContext.createChildObjectContext();
        // nothing else to do ...
    }

    @Override
    public final void writeEndObject() throws IOException, JsonGenerationException
    {
        if (!_writeContext.inObject()) {
            _reportError("Current context not an object but "+_writeContext.getTypeDesc());
        }
        _writeContext = _writeContext.getParent();
        // not 100% fool-proof, but chances are row should be done now
        finishRow();
    }
    
    /*
    /**********************************************************
    /* Output method implementations, textual
    /**********************************************************
     */

    @Override
    public void writeString(String text) throws IOException,JsonGenerationException
    {
        if (text == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write String value");
        _writer.write(_columnIndex(), text);
    }

    @Override
    public void writeString(char[] text, int offset, int len) throws IOException, JsonGenerationException
    {
        _verifyValueWrite("write String value");
        _writer.write(_columnIndex(), text, offset, len);
    }

    @Override
    public final void writeString(SerializableString sstr)
        throws IOException, JsonGenerationException
    {
        _verifyValueWrite("write String value");
        _writer.write(_columnIndex(), sstr.getValue());
    }

    @Override
    public void writeRawUTF8String(byte[] text, int offset, int len)
        throws IOException, JsonGenerationException
    {
        _reportUnsupportedOperation();
    }

    @Override
    public final void writeUTF8String(byte[] text, int offset, int len)
        throws IOException, JsonGenerationException
    {
        writeString(new String(text, offset, len, "UTF-8"));
    }

    /*
    /**********************************************************
    /* Output method implementations, unprocessed ("raw")
    /**********************************************************
     */

    @Override
    public void writeRaw(String text) throws IOException, JsonGenerationException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(String text, int offset, int len) throws IOException, JsonGenerationException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(char[] text, int offset, int len) throws IOException, JsonGenerationException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(char c) throws IOException, JsonGenerationException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(String text) throws IOException, JsonGenerationException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(String text, int offset, int len) throws IOException, JsonGenerationException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(char[] text, int offset, int len) throws IOException, JsonGenerationException {
        _reportUnsupportedOperation();
    }

    /*
    /**********************************************************
    /* Output method implementations, base64-encoded binary
    /**********************************************************
     */

    @Override
    public void writeBinary(Base64Variant b64variant, byte[] data, int offset, int len) throws IOException, JsonGenerationException
    {
        if (data == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write Binary value");
        // ok, better just Base64 encode as a String...
        if (offset > 0 || (offset+len) != data.length) {
            data = Arrays.copyOfRange(data, offset, offset+len);
        }
        String encoded = b64variant.encode(data);
        _writer.write(_columnIndex(), encoded);
    }

    /*
    /**********************************************************
    /* Output method implementations, primitive
    /**********************************************************
     */

    @Override
    public void writeBoolean(boolean state) throws IOException, JsonGenerationException
    {
        _verifyValueWrite("write boolean value");
        _writer.write(_columnIndex(), state);
    }

    @Override
    public void writeNull() throws IOException, JsonGenerationException
    {
        _verifyValueWrite("write null value");
        // !!! TODO: empty String vs String null?
        _writer.write(_columnIndex(), "");
    }

    @Override
    public void writeNumber(int i) throws IOException, JsonGenerationException
    {
        _verifyValueWrite("write number");
        _writer.write(_columnIndex(), i);
    }

    @Override
    public void writeNumber(long l) throws IOException, JsonGenerationException
    {
        // First: maybe 32 bits is enough?
        if (l <= MAX_INT_AS_LONG && l >= MIN_INT_AS_LONG) {
            writeNumber((int) l);
            return;
        }
        _verifyValueWrite("write number");
        _writer.write(_columnIndex(), l);
    }

    @Override
    public void writeNumber(BigInteger v) throws IOException, JsonGenerationException
    {
        if (v == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write number");
        _writer.write(_columnIndex(), v.toString());
    }
    
    @Override
    public void writeNumber(double d) throws IOException, JsonGenerationException
    {
        _verifyValueWrite("write number");
        _writer.write(_columnIndex(), d);
    }    

    @Override
    public void writeNumber(float f) throws IOException, JsonGenerationException
    {
        _verifyValueWrite("write number");
        _writer.write(_columnIndex(), (double) f);
    }

    @Override
    public void writeNumber(BigDecimal dec) throws IOException, JsonGenerationException
    {
        if (dec == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write number");
        _writer.write(_columnIndex(), dec.toString());
    }

    @Override
    public void writeNumber(String encodedValue) throws IOException,JsonGenerationException, UnsupportedOperationException
    {
        if (encodedValue == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write number");
        _writer.write(_columnIndex(), encodedValue);
    }

    /*
    /**********************************************************
    /* Implementations for methods from base class
    /**********************************************************
     */
    
    @Override
    protected final void _verifyValueWrite(String typeMsg)
        throws IOException, JsonGenerationException
    {
        int status = _writeContext.writeValue();
        if (status == JsonWriteContext.STATUS_EXPECT_NAME) {
            _reportError("Can not "+typeMsg+", expecting field name");
        }
    }

    @Override
    protected void _releaseBuffers()
    {
        _writer._releaseBuffers();
    }

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    protected final int _columnIndex()
    {
        int ix = _nextColumnByName;
        if (ix < 0) { // if we had one, remove now
            ix = _writer.nextColumnIndex();
        }
        return ix;
    }

    /**
     * Method called when the current row is complete; typically
     * will flush possibly buffered column values, append linefeed
     * and reset state appropriately.
     */
    protected void finishRow()  throws IOException, JsonGenerationException
    {
        _writer.endRow();
        _nextColumnByName = -1;
    }
}

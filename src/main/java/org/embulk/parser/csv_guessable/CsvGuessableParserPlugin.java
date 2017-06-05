package org.embulk.parser.csv_guessable;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.opencsv.CSVReader; // TODO: use embulk's parser

import org.embulk.config.Config;
import org.embulk.config.ConfigDefault;
import org.embulk.config.ConfigException;
import org.embulk.config.ConfigSource;
import org.embulk.config.Task;
import org.embulk.config.TaskSource;
import org.embulk.spi.Column;
import org.embulk.spi.ColumnConfig;
import org.embulk.spi.ColumnVisitor;
import org.embulk.spi.DataException;
import org.embulk.spi.Exec;
import org.embulk.spi.FileInput;
import org.embulk.spi.PageBuilder;
import org.embulk.spi.PageOutput;
import org.embulk.spi.ParserPlugin;
import org.embulk.spi.Schema;
import org.embulk.spi.SchemaConfig;
import org.embulk.spi.json.JsonParseException;
import org.embulk.spi.json.JsonParser;
import org.embulk.spi.time.TimestampParseException;
import org.embulk.spi.time.TimestampParser;
import org.embulk.spi.type.Types;
import org.embulk.spi.unit.LocalFile;
import org.embulk.spi.util.LineDecoder;
import org.embulk.spi.util.Timestamps;
import org.embulk.standards.CsvParserPlugin;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class CsvGuessableParserPlugin
        extends CsvParserPlugin
{
    private static final ImmutableSet<String> TRUE_STRINGS =
        ImmutableSet.of(
                "true", "True", "TRUE",
                "yes", "Yes", "YES",
                "t", "T", "y", "Y",
                "on", "On", "ON",
                "1");

    private final Logger log;

    public CsvGuessableParserPlugin()
    {
        log = Exec.getLogger(CsvGuessableParserPlugin.class);
    }

    public interface PluginTask
            extends Task, LineDecoder.DecoderTask, TimestampParser.Task
    {
        @Config("columns")
        @ConfigDefault("null")
        Optional<SchemaConfig> getSchemaConfig();

        @Config("header_line")
        @ConfigDefault("null")
        Optional<Boolean> getHeaderLine();

        @Config("skip_header_lines")
        @ConfigDefault("0")
        int getSkipHeaderLines();
        void setSkipHeaderLines(int n);

        @Config("delimiter")
        @ConfigDefault("\",\"")
        String getDelimiter();

        @Config("quote")
        @ConfigDefault("\"\\\"\"")
        Optional<QuoteCharacter> getQuoteChar();

        @Config("escape")
        @ConfigDefault("\"\\\\\"")
        Optional<EscapeCharacter> getEscapeChar();

        // Null value handling: if the CsvParser found 'non-quoted empty string's,
        // it replaces them to string that users specified like "\N", "NULL".
        @Config("null_string")
        @ConfigDefault("null")
        Optional<String> getNullString();

        @Config("trim_if_not_quoted")
        @ConfigDefault("false")
        boolean getTrimIfNotQuoted();

        @Config("max_quoted_size_limit")
        @ConfigDefault("131072") //128kB
        long getMaxQuotedSizeLimit();

        @Config("comment_line_marker")
        @ConfigDefault("null")
        Optional<String> getCommentLineMarker();

        @Config("allow_optional_columns")
        @ConfigDefault("false")
        boolean getAllowOptionalColumns();

        @Config("allow_extra_columns")
        @ConfigDefault("false")
        boolean getAllowExtraColumns();

        @Config("stop_on_invalid_record")
        @ConfigDefault("false")
        boolean getStopOnInvalidRecord();

        @Config("schema_file")
        @ConfigDefault("null")
        public Optional<LocalFile> getSchemaFile();

        @Config("schema_line")
        @ConfigDefault("1")
        public int getSchemaLine();
    }

    @Override
    public void transaction(ConfigSource config, ParserPlugin.Control control)
    {
        PluginTask task = config.loadConfig(PluginTask.class);
        SchemaConfig schemaConfig = null;

        if (task.getSchemaFile().isPresent()) { /* embulk-parser-csv_guessable */
            if (task.getSchemaConfig().isPresent()) {
                // TODO: use 'columns' as hints for guess
                throw new ConfigException("embulk-parsre-csv_gussable will use 'columnes' as hints for guess. Please delete 'columnes' now.");
            }
            else { /* guess from header */
                int schemaLine = task.getSchemaLine();
                task.setSkipHeaderLines(schemaLine); // TODO: use 'skip_header_line'

                String header = readHeader(task.getSchemaFile().get().getPath(), schemaLine);
                log.debug(header);
                ArrayList<ColumnConfig> columns = newColumns(header, config);
                log.debug(columns.toString());
                schemaConfig = new SchemaConfig(columns);
            }
        }
        else if (task.getSchemaConfig().isPresent()) { /* original CsvParserPlugin */
            // backward compatibility
            if (task.getHeaderLine().isPresent()) {
                if (task.getSkipHeaderLines() > 0) {
                    throw new ConfigException("'header_line' option is invalid if 'skip_header_lines' is set.");
                }
                if (task.getHeaderLine().get()) {
                    task.setSkipHeaderLines(1);
                }
                else {
                    task.setSkipHeaderLines(0);
                }
            }
            schemaConfig = task.getSchemaConfig().get();
        }
        else {
            throw new ConfigException("Field 'columns' or 'schema_file' is required but not set");
        }

        control.run(task.dump(), schemaConfig.toSchema());
    }

    @Override
    public void run(TaskSource taskSource, final Schema schema,
            FileInput input, PageOutput output)
    {
        PluginTask task = taskSource.loadTask(PluginTask.class);
        TimestampParser[] timestampParsers = null;
        if (task.getSchemaConfig().isPresent()) {
          timestampParsers = Timestamps.newTimestampColumnParsers(task, task.getSchemaConfig().get());
        }
        final JsonParser jsonParser = new JsonParser();
        final CsvTokenizer tokenizer = new CsvTokenizer(new LineDecoder(input, task), task);
        final boolean allowOptionalColumns = task.getAllowOptionalColumns();
        final boolean allowExtraColumns = task.getAllowExtraColumns();
        final boolean stopOnInvalidRecord = task.getStopOnInvalidRecord();
        int skipHeaderLines = task.getSkipHeaderLines();

        try (final PageBuilder pageBuilder = new PageBuilder(Exec.getBufferAllocator(), schema, output)) {
            while (tokenizer.nextFile()) {
                // skip the header lines for each file
                for (; skipHeaderLines > 0; skipHeaderLines--) {
                    if (!tokenizer.skipHeaderLine()) {
                        break;
                    }
                }

                if (!tokenizer.nextRecord()) {
                    // empty file
                    continue;
                }

                while (true) {
                    boolean hasNextRecord;

                    try {
                        schema.visitColumns(new ColumnVisitor() {
                            @Override
                            public void booleanColumn(Column column)
                            {
                                String v = nextColumn();
                                if (v == null) {
                                    pageBuilder.setNull(column);
                                }
                                else {
                                    pageBuilder.setBoolean(column, TRUE_STRINGS.contains(v));
                                }
                            }

                            @Override
                            public void longColumn(Column column)
                            {
                                String v = nextColumn();
                                if (v == null) {
                                    pageBuilder.setNull(column);
                                }
                                else {
                                    try {
                                        pageBuilder.setLong(column, Long.parseLong(v));
                                    }
                                    catch (NumberFormatException e) {
                                        // TODO support default value
                                        throw new CsvRecordValidateException(e);
                                    }
                                }
                            }

                            @Override
                            public void doubleColumn(Column column)
                            {
                                String v = nextColumn();
                                if (v == null) {
                                    pageBuilder.setNull(column);
                                }
                                else {
                                    try {
                                        pageBuilder.setDouble(column, Double.parseDouble(v));
                                    }
                                    catch (NumberFormatException e) {
                                        // TODO support default value
                                        throw new CsvRecordValidateException(e);
                                    }
                                }
                            }

                            @Override
                            public void stringColumn(Column column)
                            {
                                String v = nextColumn();
                                if (v == null) {
                                    pageBuilder.setNull(column);
                                }
                                else {
                                    pageBuilder.setString(column, v);
                                }
                            }

                            @Override
                            public void timestampColumn(Column column)
                            {
                                String v = nextColumn();
                                if (v == null) {
                                    pageBuilder.setNull(column);
                                }
                                else {
                                    try {
//                                        pageBuilder.setTimestamp(column, timestampParsers[column.getIndex()].parse(v));
                                    }
                                    catch (TimestampParseException e) {
                                        // TODO support default value
                                        throw new CsvRecordValidateException(e);
                                    }
                                }
                            }

                            @Override
                            public void jsonColumn(Column column)
                            {
                                String v = nextColumn();
                                if (v == null) {
                                    pageBuilder.setNull(column);
                                }
                                else {
                                    try {
                                        pageBuilder.setJson(column, jsonParser.parse(v));
                                    }
                                    catch (JsonParseException e) {
                                        // TODO support default value
                                        throw new CsvRecordValidateException(e);
                                    }
                                }
                            }

                            private String nextColumn()
                            {
                                if (allowOptionalColumns && !tokenizer.hasNextColumn()) {
                                    //TODO warning
                                    return null;
                                }
                                return tokenizer.nextColumnOrNull();
                            }
                        });

                        try {
                            hasNextRecord = tokenizer.nextRecord();
                        }
                        catch (CsvTokenizer.TooManyColumnsException ex) {
                            if (allowExtraColumns) {
                                String tooManyColumnsLine = tokenizer.skipCurrentLine();
                                // TODO warning
                                hasNextRecord = tokenizer.nextRecord();
                            }
                            else {
                                // this line will be skipped at the following catch section
                                throw ex;
                            }
                        }
                        pageBuilder.addRecord();
                    }
                    catch (CsvTokenizer.InvalidFormatException | CsvTokenizer.InvalidValueException | CsvRecordValidateException e) {
                        String skippedLine = tokenizer.skipCurrentLine();
                        long lineNumber = tokenizer.getCurrentLineNumber();
                        if (stopOnInvalidRecord) {
                            throw new DataException(String.format("Invalid record at line %d: %s", lineNumber, skippedLine), e);
                        }
                        log.warn(String.format("Skipped line %d (%s): %s", lineNumber, e.getMessage(), skippedLine));
                        //exec.notice().skippedLine(skippedLine);

                        hasNextRecord = tokenizer.nextRecord();
                    }

                    if (!hasNextRecord) {
                        break;
                    }
                }
            }

            pageBuilder.finish();
        }
    }

    static class CsvRecordValidateException
            extends DataException
    {
        CsvRecordValidateException(Throwable cause)
        {
            super(cause);
        }
    }

    private String readHeader(Path path, int schemaLine)
    {
        if (schemaLine <= 0) {
            throw new ConfigException("'schemaLine' must be set '> 0'");
        }

        String line = null;
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            for (int i = 1; i <= schemaLine; ++i) {
                line = br.readLine();
                if (line == null) {
                    throw new ConfigException("not found 'schema_line' in 'schema_file'");
                }
            }
        } catch (IOException e) {
            throw new ConfigException(e);
        }
        return line;
    }

    private ArrayList<ColumnConfig> newColumns(String header, ConfigSource config)
    {
        ArrayList columns = new ArrayList<ArrayList>();
        PluginTask task = config.loadConfig(PluginTask.class);

        try (CSVReader reader = new CSVReader(new StringReader(header))) {
            String[] csv = reader.readNext();
            for (String column : csv) {
                columns.add(new ColumnConfig(column, Types.STRING, config));
            }
        } catch (IOException e) {
            throw new ConfigException(e);
        }

        return columns;
    }
}

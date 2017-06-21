package org.embulk.parser.csv_guessable;

import com.google.common.collect.ImmutableList;
import org.embulk.EmbulkTestRuntime;
import org.embulk.config.ConfigException;
import org.embulk.config.ConfigLoader;
import org.embulk.config.ConfigSource;
import org.embulk.config.TaskSource;
import org.embulk.spi.Column;
import org.embulk.spi.Exec;
import org.embulk.spi.FileInput;
import org.embulk.spi.ParserPlugin;
import org.embulk.spi.Schema;
import org.embulk.spi.TestPageBuilderReader.MockPageOutput;
import org.embulk.spi.type.Types;
import org.embulk.spi.util.InputStreamFileInput;
import org.embulk.spi.util.Pages;
//import org.embulk.standards.TestCsvParserPlugin;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.embulk.parser.csv_guessable.CsvGuessableParserPlugin.PluginTask;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestCsvGuessableParserPlugin
//        extends TestCsvParserPlugin
{
    @Rule
    public EmbulkTestRuntime runtime = new EmbulkTestRuntime();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private CsvGuessableParserPlugin plugin;
    private MockPageOutput output;
    private Schema schema;

    @Before
    public void createResouce()
    {
        plugin = new CsvGuessableParserPlugin();
        output = new MockPageOutput();
    }

    @Test(expected = ConfigException.class)
    public void checkColumnsRequired()
    {
        String configYaml = "" +
                "type: csv_guessable";
        ConfigSource config = getConfigFromYaml(configYaml);
        PluginTask task = config.loadConfig(PluginTask.class);

        if (!task.getSchemaConfig().isPresent() && !task.getSchemaFile().isPresent()) {
            throw new ConfigException("Field 'columns' or 'schema_line' is required but not set");
        }
    }

    @Test
    public void defaultValue()
    {
        String configYaml = "" +
                "type: csv_guessable";
        ConfigSource config = getConfigFromYaml(configYaml);
        PluginTask task = config.loadConfig(PluginTask.class);

        assertNull(task.getSchemaConfig().orNull());
        assertNull(task.getSchemaFile().orNull());
        assertEquals(1, task.getSchemaLine());
    }

    @Test
    public void originalCsvParserPlugin()
    {
        String configYaml = "" +
                "type: csv_guessable\n" +
                "columns:\n" +
                "  - {name: id, type: long}\n" +
                "  - {name: title, type: string}\n" +
                "  - {name: status, type: string}";
        ConfigSource config = getConfigFromYaml(configYaml);
        PluginTask task = config.loadConfig(PluginTask.class);

        // TODO: impl or extends
    }

    @Test
    public void guessableCsv()
            throws Exception
    {
        String configYaml = "" +
                "type: csv_guessable\n" +
                "schema_file: src/test/resources/org/embulk/parser/csv_guessable/data/test.csv\n" + // TODO: FIX PATH
                "schema_line: 1";
        ConfigSource config = getConfigFromYaml(configYaml);
        transaction(config, fileInput("data/test.csv"));
        List<Object[]> records = Pages.toObjects(schema, output.pages);
        assertEquals(2, records.size());

        Object[] record;
        {
            record = records.get(0);
            assertEquals("100", record[0]);
            assertEquals("test-title", record[1]);
            assertEquals("ok", record[2]);
        }
        {
            record = records.get(1);
            assertEquals("191", record[0]);
            assertEquals("title2", record[1]);
            assertEquals("ng", record[2]);
        }
    }

    @Test
    public void specifyType()
            throws Exception
    {
        String configYaml = "" +
                "type: csv_guessable\n" +
                "schema_file: src/test/resources/org/embulk/parser/csv_guessable/data/test.csv\n" +
                "columns:\n" +
                "  - {name: 'id', type: long}\n" +
                "  - {name: 'title', type: string}\n" +
                "  - {name: 'status', type: string}";

        ConfigSource config = getConfigFromYaml(configYaml);
        transaction(config, fileInput("data/test.csv"));
        List<Object[]> records = Pages.toObjects(schema, output.pages);

        Column column;
        {
            column = schema.getColumn(0);
            assertEquals(Types.LONG, column.getType());
        }
        {
            column = schema.getColumn(1);
            assertEquals(Types.STRING, column.getType());
        }
        {
            column = schema.getColumn(2);
            assertEquals(Types.STRING, column.getType());
        }

        assertEquals(2, records.size());

        Object[] record;
        {
            record = records.get(0);
            assertEquals(100L, record[0]);
            assertEquals("test-title", record[1]);
            assertEquals("ok", record[2]);
        }
        {
            record = records.get(1);
            assertEquals(191L, record[0]);
            assertEquals("title2", record[1]);
            assertEquals("ng", record[2]);
        }
    }

    @Test
    public void renameColumn()
            throws Exception
    {
        String configYaml = "" +
                "type: csv_guessable\n" +
                "schema_file: src/test/resources/org/embulk/parser/csv_guessable/data/test.csv\n" +
                "columns:\n" +
                "  - {value_name: 'id', name: 'number', type: string}\n" +
                "  - {value_name: 'title', name: 'description', type: string}\n" +
                "  - {value_name: 'status', name: 'ok?', type: string}";
        ConfigSource config = getConfigFromYaml(configYaml);
        transaction(config, fileInput("data/test.csv"));

        Column column;
        {
            column = schema.getColumn(0);
            assertEquals("number", column.getName());
            assertEquals(Types.STRING, column.getType());
        }
        {
            column = schema.getColumn(1);
            assertEquals("description", column.getName());
            assertEquals(Types.STRING, column.getType());
        }
        {
            column = schema.getColumn(2);
            assertEquals("ok?", column.getName());
            assertEquals(Types.STRING, column.getType());
        }
    }

    @Test
    public void renameColumnAndSpecifyType()
            throws Exception
    {
        String configYaml = "" +
                "type: csv_guessable\n" +
                "schema_file: src/test/resources/org/embulk/parser/csv_guessable/data/test.csv\n" + // TODO: FIX PATH
                "columns:\n" +
                "  - {value_name: 'id', name: 'number', type: long}\n" +
                "  - {value_name: 'title', name: 'description', type: string}\n" +
                "  - {value_name: 'status', name: 'ok?', type: string}";
        ConfigSource config = getConfigFromYaml(configYaml);
        transaction(config, fileInput("data/test.csv"));

        Column column;
        {
            column = schema.getColumn(0);
            assertEquals("number", column.getName());
            assertEquals(Types.LONG, column.getType());
        }
        {
            column = schema.getColumn(1);
            assertEquals("description", column.getName());
            assertEquals(Types.STRING, column.getType());
        }
        {
            column = schema.getColumn(2);
            assertEquals("ok?", column.getName());
            assertEquals(Types.STRING, column.getType());
        }
    }

    private ConfigSource getConfigFromYaml(String yaml)
    {
        ConfigLoader loader = new ConfigLoader(Exec.getModelManager());
        return loader.fromYamlString(yaml);
    }

    private FileInput fileInput(String path)
            throws Exception
    {
        File file = new File(this.getClass().getResource(path).getPath());
        FileInputStream in = new FileInputStream(file);
        return new InputStreamFileInput(runtime.getBufferAllocator(), provider(in));
    }

    private void transaction(ConfigSource config, final FileInput input)
    {
        plugin.transaction(config, new ParserPlugin.Control()
        {
            @Override
            public void run(TaskSource taskSource, Schema schema)
            {
                TestCsvGuessableParserPlugin.this.schema = schema;
                plugin.run(taskSource, schema, input, output);
            }
        });
    }

    private InputStreamFileInput.IteratorProvider provider(InputStream... inputStreams)
            throws IOException
    {
        return new InputStreamFileInput.IteratorProvider(
                ImmutableList.copyOf(inputStreams));
    }
}

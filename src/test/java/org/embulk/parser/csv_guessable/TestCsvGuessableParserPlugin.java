package org.embulk.parser.csv_guessable;

import com.google.common.collect.ImmutableList;
import org.embulk.EmbulkTestRuntime;
import org.embulk.config.ConfigException;
import org.embulk.config.ConfigLoader;
import org.embulk.config.ConfigSource;
import org.embulk.config.TaskSource;
import org.embulk.spi.Exec;
import org.embulk.spi.FileInput;
import org.embulk.spi.ParserPlugin;
import org.embulk.spi.Schema;
import org.embulk.spi.TestPageBuilderReader.MockPageOutput;
import org.embulk.spi.util.InputStreamFileInput;
import org.embulk.standards.TestCsvParserPlugin;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.embulk.parser.csv_guessable.CsvGuessableParserPlugin.PluginTask;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestCsvGuessableParserPlugin
        extends TestCsvParserPlugin
{
    @Rule
    public EmbulkTestRuntime runtime = new EmbulkTestRuntime();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private CsvGuessableParserPlugin plugin;
    private MockPageOutput output;

    @Before
    public void createResouce()
    {
        plugin = new CsvGuessableParserPlugin();
        output = new MockPageOutput();
    }

    private ConfigSource getConfigFromYaml(String yaml)
    {
        ConfigLoader loader = new ConfigLoader(Exec.getModelManager());
        return loader.fromYamlString(yaml);
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
        PluginTask task = config.loadConfig(PluginTask.class);
//        transaction(config, fileInput(new File(this.getClass().getResource("data/test.csv").getPath())));

        // TODO: impl
    }

    @Test
    public void replaceColumnsMetadata()
            throws Exception
    {
        String configYaml = "" +
                "type: csv_guessable\n" +
                "schema_file: src/test/resources/org/embulk/parser/csv_guessable/data/test.csv\n" + // TODO: FIX PATH
                "schema_line: 1\n" +
                "columns:\n" +
                "- {value_name: '#', name: 'number', type: long}\n" +
                "- {value_name: 'title', name: 'description', type: string}\n" +
                "- {value_name: 'status', name: 'ok?', type: string}";
        ConfigSource config = getConfigFromYaml(configYaml);

        // TODO: impl
    }

    private void transaction(ConfigSource config, final FileInput input)
    {
        plugin.transaction(config, new ParserPlugin.Control()
        {
            @Override
            public void run(TaskSource taskSource, Schema schema)
            {
                plugin.run(taskSource, schema, input, output);
            }
        });
    }

    private FileInput fileInput(File file)
            throws Exception
    {
        FileInputStream in = new FileInputStream(file);
        return new InputStreamFileInput(runtime.getBufferAllocator(), provider(in));
    }

    private InputStreamFileInput.IteratorProvider provider(InputStream... inputStreams)
            throws IOException
    {
        return new InputStreamFileInput.IteratorProvider(
                ImmutableList.copyOf(inputStreams));
    }
}

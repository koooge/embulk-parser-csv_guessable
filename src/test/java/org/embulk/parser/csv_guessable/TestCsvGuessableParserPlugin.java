package org.embulk.parser.csv_guessable;

import org.embulk.config.ConfigException;
import org.embulk.config.ConfigLoader;
import org.embulk.config.ConfigSource;
import org.embulk.EmbulkTestRuntime;
import org.embulk.spi.Exec;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

import static org.embulk.parser.csv_guessable.CsvGuessableParserPlugin.PluginTask;

public class TestCsvGuessableParserPlugin
{
    @Rule
    public EmbulkTestRuntime runtime = new EmbulkTestRuntime();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private ConfigSource getConfigFromYaml(String yaml)
    {
        ConfigLoader loader = new ConfigLoader(Exec.getModelManager());
        return loader.fromYamlString(yaml);
    }

    /*
    @Test
    public void throwExceptionWithoutRequisite()
    {
        String configYaml = "" +
                "type: csv_with_header";

        ConfigSource config = getConfigFromYaml(configYaml);

        exception.expect(ConfigException.class);
        exception.expectMessage("Field either 'columns' or 'schema_file' is required but not set");
    }
    */

    @Test
    public void defaultValue()
    {
        String configYaml = "" +
                "type: csv_with_header\n" +
                "schema_line: 2";
        ConfigSource config = getConfigFromYaml(configYaml);
        PluginTask task = config.loadConfig(PluginTask.class);

        assertFalse(task.getSchemaConfig().isPresent());
        assertFalse(task.getSchemaFile().isPresent());
    }

    /*
    @Test
    public void originalCsvParserPlugin()
    {
        String configYaml = "" +
                "type: csv_with_header\n" +
                "columns:\n" +
                "  - {name: id, type: long}\n" +
                "  - {name: title, type: string}\n" +
                "  - {name: status, type: string}";
        ConfigSource config = getConfigFromYaml(configYaml);
        PluginTask task = config.loadConfig(PluginTask.class);
    }

    @Test
    public void csvGuessable()
    {
    }

    @Test
    public void replaceColumnsName()
    {
    }
    */
}

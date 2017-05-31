Embulk::JavaPlugin.register_parser(
  "csv_guessable", "org.embulk.parser.csv_guessable.CsvGuessableParserPlugin",
  File.expand_path('../../../../classpath', __FILE__))

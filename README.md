# Csv Guessable parser plugin for Embulk
**embulk-parser-csv_gussable** guesses and parses csv which has schema in header.

Also it can behave as original csv parser without **embulk-parser-csv_guessable** specified configs.

## Overview

* **Plugin type**: parser
* **Guess supported**: no

## Configuration

- **schema_file**: filename which has schema.(string, default: `null`)
- **schema_line**: schema line in header. (integer default: `"1"`)
- **columns**: Columns hint for guess (hash, default: `null`)
- any other csv configs: see [www.embulk.org](http://www.embulk.org/docs/built-in.html#csv-parser-plugin)

## Example
data/test.csv

```csv
id, title, description
1, awesome-title, awesome-description
2, shoddy-title, shoddy-description
```

config.yml

```yaml
in:
  type: any file input plugin type
  parser:
    type: csv_guessable
    schema_file: data/test.csv
    schema_line: 1
```

<!--
(If guess supported) you don't have to write `parser:` section in the configuration file. After writing `in:` section, you can let embulk guess `parser:` section using this command:
-->

```
$ embulk gem install embulk-parser-csv_guessable
<!--
$ embulk guess -g csv_guessable config.yml -o guessed.yml
-->
```

## Build

```
$ ./gradlew gem  # -t to watch change of files and rebuild continuously
```

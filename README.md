# Csv Guessable parser plugin for Embulk
**embulk-parser-csv_guessable** (runtime)guesses and parses csv which has schema in header.
This plugin is useful in case of target csv schema changes frequently.

It behaves as original csv parser when **embulk-parser-csv_guessable** conifgs(`schema_file` and `schema_line`) is not defined.

## Overview

* **Plugin type**: parser
* **Guess supported**: no

## Configuration

- **schema_file**: filename which has schema.(string, default: `null`)
- **schema_line**: schema line in header. (integer default: `"1"`)
- **(TODO)columns**: Columns attributes for parse. `embulk-parser-csv_guessable` use this config only when `"schema_file"` is set. If `"schema_file"` isn't set, this is same as original csv parser's `"columns"`. (hash, default: `null`)
- any other csv configs: see [www.embulk.org](http://www.embulk.org/docs/built-in.html#csv-parser-plugin)

## Example
test.csv

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

(To explain)
In case original csv parser 
config.yml
```yaml
in:
  type: any file input plugin type
  parser:
    type: csv
    skip_header_lines: 1
    column:
    - {name: id, type: string}
    - {name: title, type: string}
    - {name: description, type: string}
```

<!--
(If guess supported) you don't have to write `parser:` section in the configuration file. After writing `in:` section, you can let embulk guess `parser:` section using this command:
-->

```
$ embulk gem install embulk-parser-csv_guessable
```
<!--
$ embulk guess -g csv_guessable config.yml -o guessed.yml
-->

## Build

```
$ ./gradlew gem  # -t to watch change of files and rebuild continuously
```

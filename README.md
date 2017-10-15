[![Gem Version](https://badge.fury.io/rb/embulk-parser-csv_guessable.svg)](https://badge.fury.io/rb/embulk-parser-csv_guessable)

# Guessable csv parser plugin for Embulk
**embulk-parser-csv_guessable** (runtime)guesses and parses csv which has schema in header.

Csv file sometimes has a schema in the header.
**embulk-parser-csv_guessable** parses such a csv by using their header as column name.
This plugin is useful in case of target csv schema changes frequently.

It behaves as original csv parser when **embulk-parser-csv_guessable** conifgs(`schema_file` and `schema_line`) is not defined.

## Overview

* **Plugin type**: parser
* **Guess supported**: no

## Configuration

- **schema_file**: filename which has schema.(string, default: `null`)
- **schema_line**: schema line in header. (integer default: `1`)
- **columns**: Columns attributes for parse. `embulk-parser-csv_guessable` use this config only when `schema_file` is set. If `"schema_file"` isn't set, this is same as the original csv parser's `columns`. (hash, default: `null`)
    - **value_name**: Name of the column in the header. rename to `name`
    - **name**: Name of the column
    - **type**: Type of the column
    - **format**: Format of the timestamp if type is timestamp
    - **date**: Set date part if the format doesn't include date part
- any other csv configs: see [www.embulk.org](http://www.embulk.org/docs/built-in.html#csv-parser-plugin)

## Example
test.csv (There is a schema at the first line.)

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
    schema_file: test.csv
    schema_line: 1
```

(For explain)
In case original csv parser 
config.yml
```yaml
in:
  type: any file input plugin type
  parser:
    type: csv
    skip_header_lines: 1
    columns:
    - {name: id, type: string}
    - {name: title, type: string}
    - {name: description, type: string}
```

## Example2
rename column name and set type Example

```yaml
in:
  type: any file input plugin type
  parser:
    type: csv_guessable
    schema_file test.csv
    schema_line: 1
    columns:
    - {value_name: 'id', name: 'number', type: long}
    - {value_name: 'title', name: 'description', type: string}
    - {value_name: 'status', name: 'ok?', type: string}
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

## Sample

```
$ cd samples/sample2
$ embulk run -L ../../ config_rename.yml -l debug
```

## Build

```
$ ./gradlew gem  # -t to watch change of files and rebuild continuously
```

## Test

```
$ ./gradlew test
```

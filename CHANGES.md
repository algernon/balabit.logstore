# balabit.logstore 0.2.0-SNAPSHOT (git master)

No user visible changes yet.

# balabit.logstore 0.1.2 (2013-01-09)

## Miscellaneous changes

### Split the library and the CLI apart

The library and the CLI part of balabit.logstore has been split, to
reduce the number of dependencies the library pulls in. The library
itself retains the `com.balabit/logstore` name, while the CLI will be
available as `com.balabit/logstore.cli`.

# balabit.logstore 0.1.1 (2013-01-01)

## Breaking changes

### Java API reworked

The Java API was reworked significantly: instead of just exposing two
functions to parse a LogStore, and then let one do whatever one wants
with the Clojure map returned, we do some more advanced hand-holding
now.

The API now consists of a `LogStore` class, which encapsulates the
parsing itself, and a `LogStoreMap` class that makes it more
straightforward to work with the parsed data. See the
[documentation][1] and the [example application][2] for more
information.

 [1]: http://algernon.github.com/balabit.logstore/#balabit.logstore.java
 [2]: https://github.com/algernon/balabit.logstore/blob/master/src/java/LGSCat.java

### Message representation changed

The `:meta` property of serialized messages is no more, those values
are merged directly into the message map now, under similar (but
uppercased) names.

### Key names in serialized messages are expanded

syslog-ng (and therefore LogStore too) uses dot-notation to store
hierarchical data structures in a flat representation. The library now
expands the dot notation into proper structure, by reversing the dot
notation. If any part of the hierarchy would be an empty string, it is
replaced with an underscore.

Therefore, `:.SDATA.timeQuality.isSynced` becomes `:_ {:SDATA
{:timeQuality {:isSynced "..."}}}`.
 
## Significant changes

### Improved regexp matching in the CLI search tool

The CLI search tool (`lgstool search`) gained the ability to do regexp
matching on different fields than `:MESSAGE`, and an alias for the
`(re-match)` function was introduced, under the name of `(?=)`. This
makes the new syntax look like this: `lgstool search messages.store
'(?= :SOURCE #".*net.*")'`.

### Support date-based searching in the CLI search tool

The CLI search tool (`logstore search`) gained the ability to filter
messages based on their timestamps (if any). It can match messages
either before, exactly on, or after a given timestamp.

Usage: `lgstool search messages.store '(after "2012-12-27")'`

### File and chunk checksums are parsed and converted to string

The various checksums within LogStore files (the file MAC and chunk
HMACs in particular) are now converted to a hexadecimal string
representation, instead of being returned as-is as byte buffers.

### Beginnings of data verification

The library now does some data verification during parsing, and throws
more useful exceptions when a parsing error happens. Though this is
still a work in progress, it already is useful.

### XFRM info records are now partially supported

The third type of records - xfrm info ones - are now partially
supported: the library recognises them, and extracts the encrypted
master key as-is. It does not do anything more than that, though.

Nevertheless, this allows the library to fully parse even an encrypted
LogStore, without throwing exceptions.

## Bugfixes

- Some of the CLI search predicates weren't handling the non-matching
  cases correctly, this has been corrected.

- Timestamp records are now parsed properly, instead of only working
  by accident.

# balabit.logstore 0.1.0 (2012-12-15)

## Breaking changes

### Complete rearchitecture

The library was rewritten from the grounds up, to be more idiomatic
and easier to use. With this release, it exposes only a few functions,
and lazily parses the LogStore into a map.

Basic support for accessing this functionality - and the map - from
Java is also provided with this release.

# balabit.logstore 0.0.1 (2012-05-11)

Initial release.

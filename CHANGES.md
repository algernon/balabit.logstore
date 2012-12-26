# balabit.logstore 0.1.1-SNAPSHOT (git master)

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

## Significant changes

### Improved regexp matching in the CLI search tool

The CLI search tool (`lgstool search`) gained the ability to do regexp
matching on different fields than `:MESSAGE`, and an alias for the
`(re-match)` function was introduced, under the name of `(?=)`. This
makes the new syntax look like this: `lgstool search messages.store
'(?= :SOURCE #".*net.*")'`.

### Beginnings of data verification

The library now does some data verification during parsing, and throws
more useful exceptions when a parsing error happens. Though this is
still a work in progress, it already is useful.

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

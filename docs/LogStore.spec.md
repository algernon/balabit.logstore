# The LogStore file format specification

## Overview

The LogStore file format is built up from two basic blocks: a [file header](#file-header), and different kinds of [records](#records) that follow it. Each record is independent of all the others (except for whole-file checksums, more on that later): they're independently compressed and encrypted (optionally, of course), and can be read on their own, without knowing anything about any other record.

There have been multiple versions of the format, this document is a specification for versions 3 and 4 of the format.

## File header

| offset | size | type     | name            | description
| ------ | ---- | ------   | -----           | -----------
|      0 |    4 | string   | magic           | Magic identification string, **LST** followed by a number (either **1**, **2**, **3** or **4**)
|      4 |    4 | uint32_t | size            | Full size of the header, including this field, but not including the magic.
|      8 |    4 | uint32_t | flags           | Currently unused, and set to zero.
|     12 |    4 | uint32_t | last_chunk      | ID of the last used chunk record.
|     16 |    4 | uint32_t | last_record     | ID of the last used record.
|     20 |    8 | uint32_t | last_chunk_tail | Offset pointing to the end of the last closed chunk, from the start of the file.
|     28 |  108 | bytes    |                 | Padding, reserved for future use.
|    136 |    4 | uint32_t | hash_algo_len   | Length of the name of the hashing algorithm used within the store.
|    140 |    ? | string   | hash_algo       | The hash algorithm used within the store, as a string of *hash_algo_len* bytes, not NULL-terminated.
|        |    4 | uint32_t | crypto_algo_len | Length of the name of the crypto algorithm used within the store.
|        |    ? | string   | crypto_algo     | The encryption algorithm used within the store, as a string of *crypto_algo_len* bytes, not NULL-terminated.
|        |    4 | uint32_t | file_mac_len    | Length of the whole-file MAC, in bytes.
|        |    ? | bytes    | file_mac        | The file MAC, output of the hashing algorithm, as a sequence of bytes of *file_mac_len* length.
|        |    4 | uint32_t | x509_len        | Length of the X509 certificate used for encryption, in bytes.
|        |    ? | bytes    | x509            | The X509 certificate used for encryption, a sequence of bytes of *x509_len* length

The record and chunk IDs (referenced by *last_chunk* and *last_record*) are sequential IDs, they're not stored the within the file itself, but are trivially discoverable: the first record in the file is record \#1, and so on and so forth.

## Records

Immediately following the file header starts the first record. All records, regardless of their type have a common header:

| offset | size | type     | name            | description
| ------ | ---- | ------   | -----           | -----------
|      0 |    4 | uint32_t | size            | The size of the record, including this header
|      4 |    1 | uint8_t  | type            | The type of the header. Valid values are: **1** for [**XFRM_INFO**](#xfrm_info-records), **2** for [**CHUNK**](#chunk-records) and **3** for [**TIMESTAMP**](#timestamp-records)
|      5 |    1 | uint8_t  | flags           | Bitwise OR-ed flags. Valid values are dependent on the record type.

### XFRM_INFO records

**XFRM_INFO** records are used to store information needed to decrypt **CHUNK** records. They have no defined flags, and no extra header fields either. They only have a data area:

| offset | size | type     | name            | description
| ------ | ---- | ------   | -----           | -----------
|      6 |    ? | bytes    | master_key      | The master key needed to decrypt **CHUNK** records, encrypted with the public part of the X509 certificate (see the [file header](#file-header)). The length of this is the record *size* minus the header size.

### TIMESTAMP records

**TIMESTAMP** records are used to store a cryptographically secure timestamp about a preceeding **CHUNK** record. Like [**XFRM__INFO**](#xfrm_info-records) records, they have no flags defined. However, these records have extra headers before their data part:

| offset | size | type     | name            | description
| ------ | ---- | ------   | -----           | -----------
|      6 |    4 | uint32_t | chunk_id        | The ID of the [**CHUNK**](#chunk) the timestamp is for.
|     10 |    4 | uint32_t | timestamp_len   | The length of the timestamp, in bytes.
|     14 |    ? | bytes    | timestamp       | The timestamp itself, as a sequence of *timestamp_len* bytes.
|        |    ? | bytes    | padding         | Each **TIMESTAMP** record is exactly 4096 bytes long, if the timestamp is shorter than that, then padding is used to fill the space.

As of this writing, I have no information how the *timestamp* itself should be further processed.

### CHUNK records

**CHUNK** records contain the log messages themselves, these are the core of the LogStore format. They have a couple of flags defined (see [later](#chunk-flags)), along with a larger number of header fields:

| offset | size | type     | name            | description
| ------ | ---- | ------   | -----           | -----------
|      6 |    8 | time_t   | start_time      | Starting time of the chunk, as a 64-bit *time_t* value.
|     14 |    4 | uint32_t | start_time_usec | The micro-second part of the chunk starting time.
|     18 |    8 | time_t   | end_time        | Ending time of the chunk, as a 64-bit *time_t* value.
|     26 |    4 | uint32_t | end_time_usec   | The micro-second part of the chunk ending time.
|     30 |    4 | uint32_t | first_msgid     | ID of the first message within the chunk.
|     34 |    4 | uint32_t | last_msgid      | ID of the last message within the chunk.
|     38 |    4 | uint32_t | chunk_id        | ID of this chunk itself.
|     42 |    8 | uint64_t | xfrm_offset     | Offset of the [**XFRM_INFO**](#xfrm_info-records) record needed to decrypt this chunk, from the beginning of the file.
|     50 |    4 | uint32_t | tail_offset     | Offset of the chunk's tail, from the beginning of the record.
|     54 |    ? | bytes    | data            | The chunk data. The exact format of this depends on the *flags*, see [later](#chunk-data-storage-formats).

Following the *data*, at *tail_offset*, starts the chunk tail, which has the following fields:

| offset | size | type     | name            | description
| ------ | ---- | ------   | -----           | -----------
|      0 |    4 | uint32_t | tail_flags      | Additional flags.
|      4 |    4 | uint32_t | file_mac_len    | Length of the file MAC stored with the chunk.
|      8 |    ? | bytes    | file_mac        | The file MAC, as a series of bytes of *file_mac_len* length.
|        |    4 | uint32_t | chunk_hmac_len  | The length of the chunk HMAC.
|        |    ? | bytes    | chunk_hmac      | The chunk HMAC, as a series of bytes of *chunk_hmac_len* length.

#### Chunk flags

**CHUNK** records have flags in two places, the header and the tail. These flags are:

* **COMPRESSED**: The first bit of the header flags. If set, then the chunk is compressed. Compression happens after encryption.
* **ENCRYPTED**: Second bit of the header flags. If set, then the chunk is encrypted, and the *xfrm_offset* field must not be empty.
* **BROKEN**: Third bit of the header flags. If set, the chunk is broken, and should be skipped.
* **SERIALIZED**: Fourth bit of the header flags. If set, the chunk is stored in a serialized format. See the [data storage format](#chunk-data-storage-formats) section below.
* **HMAC** and **HASH**: First and second bits of the tail flags. They must both be either set, or both be unset. If set, then the chunk has MAC verification data.

#### Chunk data storage formats

There are only two types of chunk data storage formats: [*serialized*](#serialized-message-format) and *unserialized*.

The second format is simple: it is simply all the messages from *first_msgid* until *last_msgid* concatenated, using the following format for each and every one of them:

| offset | size | type     | name            | description
| ------ | ---- | ------   | -----           | -----------
|      0 |    4 | uint32_t | message_length  | The length of the message, in bytes.
|      4 |    ? | string   | message         | The message itself, a plain text string of *message_length* size.

The format of the string is unspecified, apart from being a string.

## Serialized message format

The *serialized* message format is much more complicated than the unserialized, length-prefixed string array described above. This too, starts with a header, followed by specially encoded data, whose [decoding process](#the-name-value-table) will be documented later below:

| offset | size | type     | name            | description
| ------ | ---- | ------   | -----           | -----------
|      0 |    4 | uint32_t | length          | The length of the header, in bytes.
|      4 |    1 | uint8_t  | version         | The version of the serialized format. This specification documents version **22** and **23** only.
|      5 |    4 | uint32_t | flags           | Currently unused.
|      9 |    2 | uint16_t | priority        | The priority: facility & severity encoded into one value: the first half is the facility number, the lower half is the severity.
|     11 |    2 | uint16_t | socket_family   | The address family of the socket, if any. Address family can be **2** (IPv4), **10** (IPv6) or **0** (neither).
|   (13) |    8 | uint64_t | rcptid          | The receipt ID of the message, only appears from the **v23** format onwards, former ones do not have this field.
|        |    ? | bytes    | socket_address  | The socket address, pretty much a dump of a C *sockaddr* struct. Not present if *socket_family* is zero, otherwise the length depends on the *socket_family*. For IPv4, this is 32 bits, for IPv6, 128.
|        |    8 | time_t   | timestamp       | Timestamp of the message, as a 64-bit *time_t*.
|        |    4 | uint32_t | timestamp_usec  | The micro-second precision of the timestamp.
|        |    4 | uint32_t | timestamp_offs  | Time-zone offset of the timestamp (in milliseconds)
|        |    ? |          | tags...         | The tags belonging to the message, their format is [described later](#tags).
|        |    ? |          | nvtable...      | The core of the message: serialized name-value pairs. This format is also [described later](#the-name-value-table).

Facilities and severities map to the corresponding UNIX facility and severity levels, namely:

* Facilities: *kern*, *user*, *mail*, *daemon*, *auth*, *syslog*, *lpr*, *news*, *uucp*, *cron*, *authpriv*, *ftp*, *ntp*, *log-audit*, *log-alert*, *clock*, and *local0* through *local7*
* Severities: *emergency*, *alert*, *critical*, *error*, *warning*, *notice*, *informational*, *debug*

### Tags

Tags are stored as a series of length-prefixed strings (see below), where an empty string with zero length marks the end of the list.

| offset | size | type     | name            | description
| ------ | ---- | ------   | -----           | -----------
|      0 |    4 | uint32_t | tag_length      | The length of the tag, in bytes.
|      4 |    ? | string   | tag             | The tag itself, a plain text string of *tag_length* size.

### The name-value table

Like all other bigger structures in the LogStore, the name-value table starts with a header too:

| offset | size | type     | name            | description
| ------ | ---- | ------   | -----           | -----------
|      0 |    1 | uint8_t  |                 | Used only by syslog-ng, internally.
|      1 |    1 | uint8_t  |                 | Used only by syslog-ng, internally.
|      2 |    1 | uint8_t  | num-sdata       | The number of SDATA fields in the serialized message.
|      3 |    1 | uint8_t  |                 | Used only by syslog-ng, internally.

Following the header, we have the SDATA handles, a array of 16-bit integers, of *num-sdata* size:

| offset | size | type     | name            | description
| ------ | ---- | ------   | -----           | -----------
|      0 |    2 | uint16_t | handle          | The ID of the handle.

Past the SDATA handles, we have the payload itself, which, again, starts with a header:

| offset | size | type     | name               | description
| ------ | ---- | ------   | -----              | -----------
|      0 |    4 | string   | magic              | The magic string **NVT2**, that identifies the payload.
|      4 |    1 | uint8_t  | flag               | Used only by syslog-ng, internally.
|      5 |    2 | uint16_t | size               | The full size of the table, shifted left two bits.
|      7 |    2 | uint16_t | used               | The used area of the table, shifted left two bits.
|      9 |    2 | uint16_t | num-dyn-entries    | The number of dynamic entries in the table.
|     11 |    1 | uint8_t  | num-static-entries | The number of static entries in the table.

Right after this payload header, we have the offsets of the static entries, followed by the offsets of the dynamic ones, where the offsets are encoded as follows:

| offset | size | type     | name               | description
| ------ | ---- | ------   | -----              | -----------
|      0 |    2 | uint16_t | static-offset      | The offset of a static entry, shifted left two bits.

| offset | size | type     | name               | description
| ------ | ---- | ------   | -----              | -----------
|      0 |    4 | uint32_t | dynamic-offset     | The offset of a dynamic entry.

In case of static entries, offsets can be zero, which means that the particular static entry is not present in the table.

The values of both static and dynamic entries are encoded in the same manner, the difference is, that for static pairs, the name of the name-value pair is not encoded within the LogStore, but the index of the static entry corresponds to a pre-defined key. These keys are: *HOST*, *HOST_FROM*, *MESSAGE*, *PROGRAM*, *PID*, *MSGID*, *SOURCE*, *LEGACY_MSGHDR*.

The structure both static and dynamic entries are stored in, looks as follows:

| offset | size | type     | name               | description
| ------ | ---- | ------   | -----              | -----------
|      0 |    1 | uint8_t  | is_indirect        | A flag that tells whether the entry is a direct or an indirect entry.
|      1 |    1 | uint8_t  | name_len           | The length - in bytes - of the field name of the entry, if any. For static entries, this is zero.
|      2 |    2 | uint16_t |                    | Used only by syslog-ng, internally.

Here, direct and indirect entries start to differ. Indirect entries are basically pointers to other direct entries within the same table, they can't point to another indirect entry. Their format is this:

| offset | size | type     | name               | description
| ------ | ---- | ------   | -----              | -----------
|      4 |    2 | uint16_t | handle             | The handle of the value, shifted right by two bits.
|      6 |    2 | uint16_t | offset             | Offset of the direct entry, shifted right by two bits.
|      8 |    2 | uint16_t | length             | The length of the value itself.
|     10 |    1 | uint8_t  | type               | Unused at this time.

A direct entry on the other hand has a format as follows:

| offset | size | type     | name               | description
| ------ | ---- | ------   | -----              | -----------
|      4 |    2 | uint16   | value_len          | Length of the value part of the pair.
|      6 |    ? | string   | name               | The name part of the pair, a string of *name_len* bytes (see the common header part above). The string is NULL terminated, and the trailing NULL character is not included in the length.
|        |    ? | string   | value              | The value part of the pair, a string of *value_len* bytes. The string is NULL terminated, and the trailing NULL character is not included in the length.

An important thing of note is that the offsets used within the name-value pair table all count from the end of the table, pointing backwards.

(ns balabit.logstore.codec.chunk.serialization

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (java.nio ByteBuffer))
  (:use [balabit.blobbity]
        [balabit.logstore.utils]
        [balabit.logstore.exceptions]
        [balabit.logstore.codec.verify]
        [balabit.logstore.codec.common]))

;; ### Decoding messages
;;
;; Deserializing messages is a tough job, the format is fairly
;; compact, and optimised for easy run-time access. As a consequence,
;; there are a lot of transformations the library will need to apply
;; to get useful results back.
;;

;; #### Deserialization
;;
;; There are two types of messages that can be stored within a chunk:
;; `:serialized` and `:unserialized`.

;; Unserialized messages are simply 32-bit length prefixed strings. To
;; be compatible with serialzied messages (which are key-value based),
;; when decoding an unserialized message, we forcibly associate the
;; message text to a `:MESSAGE` field, and return a map.
;;
(defmethod decode-frame :chunk/message.unserialized
  [#^ByteBuffer buffer _]
  (decode-blob buffer [:MESSAGE [:prefixed :string :uint32]]))

;; A serialized message contains a lot more information than an
;; already formatted message text.
;;
;; It starts with a [:header][chunk/sr-msghdr], followed by a
;; [:socket-address][chunk/sr-sockaddr], two
;; [timestamps][chunk/sr-timestamp] (`:stamp` and `:recv-stamp`, which
;; are the timestamps of the message itself, and the timestamp of its
;; receipt, respectively).
;;
;; After the timestamps, we have the [:tags][chunk/sr-tags], followed
;; by the [name-value pairs][chunk/nvtable].
;;
;; With these extracted, all we need is to transform the result into
;; something usable by the end user. We do this by post-processing the
;; socket address, so that the address and the family are of
;; appropriate type (`InetAddress` and a keyword) and resolving both
;; timestamps into Java objects. Apart from this, fields that are only
;; necessary for decoding (such as `:length`, `:vesion`, `:priority`
;; and `:flags` are removed.
;;
;; The returned map therefore contains a combination of data extracted
;; from the header (including the `:priority` and `:facility` of the
;; message), timestamps, `:tags` assembled inside a `:meta` map, along
;; with the deserialized name-value pairs.
;;
;; [chunk/sr-msghdr]: #chunk/sr-msghdr
;; [chunk/sr-sockaddr]: #chunk/sr-sockaddr
;; [chunk/sr-timestamp]: #chunk/sr-timestamp
;; [chunk/sr-tags]: #chunk/sr-tags
;; [chunk/nvtable]: #chunk/nvtable
;;
(defmethod decode-frame :chunk/message.serialized
  [#^ByteBuffer buffer _]

  (let [hdr (decode-frame buffer :chunk.serialized/message-header)
        sockaddr (decode-frame buffer :chunk.serialized/sockaddr
                               (-> hdr :socket :family))
        stamps (decode-blob buffer [:stamp :chunk.serialized/timestamp
                                    :recv-stamp :chunk.serialized/timestamp])
        tags (decode-frame buffer :chunk.serialized/tags)
        nvt (decode-frame buffer :chunk.serialized/nvtable)

        facility (quot (:priority hdr) 8)
        severity (rem (:priority hdr) 8)]

    (let [meta-data (-> hdr
                        (update-in [:socket] (partial merge sockaddr))
                        (update-in [:socket :family] (partial get {2 :inet4, 10 :inet6}))
                        (update-in [:socket :address] resolve-address)
                        (merge stamps)
                        (update-in [:stamp] resolve-timestamp)
                        (update-in [:recv-stamp] resolve-timestamp)
                        (dissoc :length :version :priority :flags)
                        (assoc :facility (facility-map facility)
                               :severity (severity-map severity)
                               :tags tags))]
      (assoc nvt :meta meta-data))))

;; #### <a name="chunk/sr-msghdr">The message header</a>
;;
;; Each serialized message has its own little header, starting with a
;; 32-bit `:length`, which is followed by a 8-bit `:version`.
;;
;; Depending on the version, a different set of other fields
;; follow. This library supports version 22 and above only, though.
;;
;; For version 22, the header contains a 32-bit integer for various
;; `:flags` (which, at this time, are not used by the library), a
;; 16-bit `:priority`, which is a combination of `:facility` and
;; `:severity` (and is not decoded by this function, it is left up to
;; the caller). Finally, it also contains a `:socket-address`, of
;; which only its `:family` is interesting for this function, the rest
;; of it will be decoded by
;; [:chunk.serialized/sockaddr][chunk/sr-sockaddr].
;;
;; Version 23 also contains a 64-bit `:rcptid` field in front of the
;; fields above.
;;
(defmethod decode-frame :chunk.serialized/message-header
  [#^ByteBuffer buffer _]

  (let [header (decode-blob buffer [:length :uint32
                                    :version :byte])
        v22-fields [:flags :uint32
                    :priority :uint16
                    :socket [:struct [:family :uint16]]]]
    (if (= (:version header) 23)
      (merge header (decode-blob buffer (concat [:rcptid :uint64] v22-fields)))
      (merge header (decode-blob buffer v22-fields)))))

;; #### <a name="chunk/sr-timestamp">Timestamps</a>
;;
;; Timestamps consist of a 64-bit second counter (`:sec`), a 32-bit
;; micro-second counter (`:usec`), and a 32-bit zone offset (`:zone-offset`).
;;
(defmethod decode-frame :chunk.serialized/timestamp
  [#^ByteBuffer buffer _]

  (decode-blob buffer [:sec :uint64
                       :usec :uint32
                       :zone-offset :uint32]))

;; #### <a name="chunk/sr-sockaddr">Socket addresses</a>
;;
;; Socket addresses can be IPv4 or IPv6 addresses, and in both cases,
;; are stored along with a 16-bit port number.
;;
;; In case the family is IPv4 (2), the decoder uses 32 bits for the
;; address, if it is IPv6 (10), it uses 128. If the family is zero, or
;; any other unknown value, it returns nil.
(defmethod decode-frame :chunk.serialized/sockaddr
  [#^ByteBuffer buffer _ family]

  (cond
   (= family 2) (decode-blob buffer [:address [:slice 4]
                                     :port :uint16])

   (= family 10) (decode-blob buffer [:address [:slice 16]
                                      :port :uint16])
   (zero? family) nil))

;; #### <a name="chunk/sr-tags">Tags</a>
;;
;; Tags are stored as a list of 32-bit length prefixed strings. An
;; empty string of zero length marks the end of the tag list.
;;
;; Tags are converted into keywords.
;;
(defmethod decode-frame :chunk.serialized/tags
  [#^ByteBuffer buffer _]

  (loop [tags []]
    (let [tag (decode-frame buffer :prefixed :string :uint32)]
      (if (empty? tag)
        tags
        (recur (conj tags (keyword tag)))))))

;; #### <a name="chunk/nvtable">Name-value pairs</a>
;;
;; The name-value table is the toughest part to decode, as it was
;; architected for compactness and performance in C. In the end, we
;; want a list of key-value pairs, everything else this method does,
;; is done to that end.
;;
;; The structure of the table is as follows:
;;
;; We have a [header][nvt/header], followed by
;; [structured-data][nvt/sdata], which is followed by a payload, which
;; itself is made up of a [payload-header][nvt/payload-header], and
;; two sets of offsets: one for [static properties][nvt/static-prop],
;; and one for [dynamic ones][nvt/dyn-prop] (we'll see in a moment
;; what the difference between the two is).
;;
;; The bulk of the table is stored past the offsets, and combination
;; of static and dynamic properties from that will be the result of
;; the function.
;;
;; [nvt/header]: #nvt/header
;; [nvt/sdata]: #nvt/sdata
;; [nvt/payload-header]: #nvt/payload-header
;; [nvt/static-prop]: #nvt/static-prop
;; [nvt/dynamic-prop]: #nvt/dynamic-prop
;;
(defmethod decode-frame :chunk.serialized/nvtable
  [#^ByteBuffer buffer _]

  (let [nvt-header (decode-frame buffer :nvtable/header)
        sdata (decode-frame buffer :nvtable/sdata (:num-sdata nvt-header))
        payload-header (decode-frame buffer :nvtable/payload.header)
        static-offsets (decode-frame buffer :nvtable/payload.offsets.static
                                     (:num-static-entries payload-header))
        dynamic-offsets (decode-frame buffer :nvtable/payload.offsets.dynamic
                                      (:num-dyn-entries payload-header))

        pairs (.order
               (decode-frame buffer :slice (bit-shift-left (:used payload-header) 2))
               java.nio.ByteOrder/LITTLE_ENDIAN)

        static-pairs (decode-frame pairs :nvtable/payload.pairs.static static-offsets)
        dyn-pairs (decode-frame pairs :nvtable/payload.pairs.dynamic dynamic-offsets)]
    (merge static-pairs dyn-pairs)))

;; <a name="nvt/header">The name-value table header</a>
;;
;; The header of the name-value table is a mere four bytes:
;; `:initial-parse`, `:num-matches`, `:num-sdata`, and `:alloc-sdata`,
;; all of them 8-bit. For this library, only `:num-sdata` is
;; interesting, [see below][nvt/sdata].
(defmethod decode-frame :nvtable/header
  [#^ByteBuffer buffer _]

  (decode-blob buffer [:initial-parse :byte
                       :num-matches :byte
                       :num-sdata :byte
                       :alloc-sdata :byte]))

;; <a name="nvt/sdata">Structured data</a>
;;
;; Structured data offsets are stored as a list of 16-bit integers,
;; where each integer is an offset into the main table data.
(defmethod decode-frame :nvtable/sdata
  [#^ByteBuffer buffer _ num-sdata]

  ; FIXME: Need to resolve these too!
  (doall (take num-sdata (decode-blob-array buffer :uint16))))

;; <a name="nvt/payload-header">The payload header</a>
;;
;; The payload itself is a header, followed by table entries. The
;; header itself is made up from a 4-byte magic string, a 8-bit flag,
;; then a 16-bit `:size` and a 16-bit `:used` property. The `:used`
;; property is the amount of data saved to disk, but needs to be
;; shifted left 2 bits (all table entries are put on word boundaries).
;;
;; After these properties, the header has the number of dynamic
;; entries stored, as a 16-bit integer, and finally the number of
;; static entries, as a 8-bit byte.
(defmethod decode-frame :nvtable/payload.header
  [#^ByteBuffer buffer _]

  (decode-blob buffer [:magic [:string 4]
                       :serialized-flag :ubyte
                       :size :uint16
                       :used :uint16
                       :num-dyn-entries :uint16
                       :num-static-entries :ubyte]))

;; <a name="nvt/static-prop">Static properties</a>
;;
;; Static properties are those that have built-in names (these are
;; `:HOST`, `:HOST_FROM`, `:LEGACY_MSGHDR`, `:MESSAGE`, `:MSGID`,
;; `:PID`, `:PROGRAM`, `:SOURCE`), so they do not have the property
;; name stored.
;;

;; Static properties have their offsets stored as 16-bit integers,
;; where each offset needs to be shifted left two bits.
(defmethod decode-frame :nvtable/payload.offsets.static
  [#^ByteBuffer buffer _ num-static]

  (doall (map #(bit-shift-left % 2)
              (take num-static (decode-blob-array buffer :uint16)))))

;; Decoding all the static property pairs is done by decoding each
;; property, and zipmapping the results to the predefined
;; keys. Returns a map of key-value pairs.
(defmethod decode-frame :nvtable/payload.pairs.static
  [#^ByteBuffer buffer _ offsets]

  (into {} (mapcat (fn [[key offset]]
         (decode-frame buffer :nvpair/static key offset))
                   (zipmap [:HOST :HOST_FROM :MESSAGE :PROGRAM
                            :PID :MSGID :SOURCE :LEGACY_MSGHDR] offsets))))

;; Since a static property does not have a name, the caller must
;; supply one (as can be seen above). Decoding one also needs an
;; offset, where the offset is counted backwards from the end of the
;; buffer.
;;
;; Because the buffer in this case is not used anywhere but only in
;; situations where the library knows where to seek, it is safe to
;; mutate the position, and not restore it.
;;
;; The result will be a map of a single key-value pair, where the key
;; is caller-supplied.
(defmethod decode-frame :nvpair/static
  [#^ByteBuffer buffer _ key offset]

  (.position buffer (- (.limit buffer) offset))
  (when-not (zero? offset)
    {key (:value (decode-frame buffer :nvtable/nventry))}))


;; <a name="nvt/dyn-prop">Dynamic properties</a>
;;
;; Dynamic properties differ from static ones by having a name, and no
;; pre-defined handle.

;; Dynamic properties have their offsets stored as 32-bit integers,
;; where the first 16 bits are the ID, the other 16 bits are the
;; offset. This function does not split them up, however.
(defmethod decode-frame :nvtable/payload.offsets.dynamic
  [#^ByteBuffer buffer _ num-dynamic]

  (doall (take num-dynamic (decode-blob-array buffer :uint32))))

;; Decoding all the dynamic properties is as simple as figuring out
;; the offsets, and decoding the elements one by one. Returns a map of
;; key-value pairs.
(defmethod decode-frame :nvtable/payload.pairs.dynamic
  [#^ByteBuffer buffer _ offsets]

  (let [real-offsets (map #(bit-shift-left (bit-and % 0xffff) 2) offsets)]
    (mapcat #(decode-frame buffer :nvpair/dynamic %)
            real-offsets)))

;; A dynamic property has a name, so all the caller needs to supply is
;; the offset to read the entry from. Similar to the case of static
;; properties, seeking in this buffer without restoring the state is
;; fine, as every function that reads from the nvpair buffer does seek
;; to the appropriate place anyway.
;;
;; Returns a map of a single key-value pair.
(defmethod decode-frame :nvpair/dynamic
  [#^ByteBuffer buffer _ offset]

  (.position buffer (- (.limit buffer) offset))
  (let [v (decode-frame buffer :nvtable/nventry)]
    {(keyword (:name v)) (:value v)}))

;; <a name="nventry">Name-value entry pairs</a>
;;
;; Behind every static and dynamic property, there's a name-value
;; entry, a structure common between both.
;;
;; It consists of a header, followed by either a direct entry, or an
;; indirect one, that is a kind of glorified alias for another, direct
;; entry.
;;
;; Returns a map of a single key-value pair.
(defmethod decode-frame :nvtable/nventry
  [#^ByteBuffer buffer _]

  (let [common-header (decode-frame buffer :nventry/common-header)]
    (if (bit-test (:indirect common-header) 1)
      (decode-frame buffer :nventry/indirect common-header)
      (decode-frame buffer :nventry/direct common-header))))

;; The header common to both direct and indirect entries consists of a
;; 8-bit flag, whose first bit signals whether the entry is indirect
;; or not. Following the flag, is the `:name-length` property, which,
;; as the name implies, is the length of the property's name. In case
;; of static entries, this will be zero.
;;
;; There's also an `:alloc-len` property, but that is unused by the
;; library.
(defmethod decode-frame :nventry/common-header
  [#^ByteBuffer buffer _]

  (decode-blob buffer [:indirect :ubyte
                       :name-len :ubyte
                       :alloc-len :uint16]))

;; A direct entry has another property: the `:value-len`, which tells
;; us how long the value is.
;;
;; The function returns a map of a single key-value pair.
(defmethod decode-frame :nventry/direct
  [#^ByteBuffer buffer _ common-header]

  (let [header (assoc common-header
                 :value-len (decode-frame buffer :uint16))]
    (decode-frame buffer :nventry/pair
                  (:name-len header) (:value-len header))))

;; An indirect buffer has only meta-data: the 16-bit `:handle` of the
;; property (unused by the library), the 16-bit `:offset` (which, like
;; all other offsets within the table, needs to be shifted left two
;; bits), the 16-bit `:length` of the value, and a 8-bit `:type` (both
;; of which are unused by the library at this time).
;;
;; Based on this information, the decoding function can find the
;; referenced direct property, read and return it.
(defmethod decode-frame :nventry/indirect
  [#^ByteBuffer buffer _ common-header]

  (let [header (merge common-header
                      (decode-blob buffer [:handle :uint16
                                           :offset :uint16
                                           :length :uint16
                                           :type :ubyte]))]
    (.position buffer (- (.limit buffer)
                         (bit-shift-left (:offset header) 2)))
    (decode-frame buffer :nventry/direct common-header)))

;; On the lowest level, a name-value pair is a NULL-terminated string,
;; followed by a value. The caller must supply the length of both.
(defmethod decode-frame :nventry/pair
  [#^ByteBuffer buffer _ name-len value-len]

  (decode-blob buffer [:name [:string name-len]
                       :skip 1
                       :value [:string value-len]]))

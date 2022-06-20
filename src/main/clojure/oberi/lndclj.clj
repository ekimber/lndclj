(ns oberi.lndclj
  (:gen-class)
  (:require [protojure.grpc.client.providers.http2 :as http2]
            [clj-commons.byte-streams :refer [to-byte-array]])
  (:import (java.io File)))

(def connection {:host     "https://192.168.0.15:10019"
                 :cert     "/local/work/lnd-cljs/tls.cert"
                 :macaroon "/local/work/lnd-cljs/admin.macaroon"})

(defn hexify "Convert byte sequence to hex string" [coll]
  (let [hex [\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \A \B \C \D \E \F]]
    (letfn [(hexify-byte [b]
              (let [v (bit-and b 0xFF)]
                [(hex (bit-shift-right v 4)) (hex (bit-and v 0x0F))]))]
      (apply str (mapcat hexify-byte coll)))))

(defn macaroon-header [^String macaroon-path]
  (-> (File. macaroon-path)
       to-byte-array
       hexify))

(defn connect [connection]
  (http2/connect {:uri (:host connection)
                  :metadata {"macaroon" (macaroon-header (:macaroon connection))}}))

(ns oberi.lndclj
  (:require [clj-commons.byte-streams :refer [to-byte-array]]
            [protojure.internal.grpc.client.providers.http2.core :as core]
            [protojure.grpc.codec.compression :refer [builtin-codecs]]
            [lambdaisland.uri :as lambdaisland]
            [clojure.tools.logging :as log]
            [clojure.string :refer [lower-case]]
            [promesa.core :as p]
            [promesa.exec :as p.exec])
  (:import (java.io File FileInputStream BufferedInputStream)
           (org.eclipse.jetty.http2.client HTTP2Client)
           (java.net InetSocketAddress)
           (org.eclipse.jetty.http2.api.server ServerSessionListener$Adapter)
           (org.eclipse.jetty.util.ssl SslContextFactory$Client)
           (java.security.cert CertificateFactory)
           (java.security KeyStore)
           (org.eclipse.jetty.util Promise)))

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

(defn read-cert [^String cert-file]
  (let [fis (FileInputStream. cert-file)
        bis (BufferedInputStream. fis)
        cf (CertificateFactory/getInstance "X.509")]
    (.generateCertificate cf bis)))

(defn create-keystore [cert-file]
  (let [cert (read-cert cert-file)
        keystore (KeyStore/getInstance (KeyStore/getDefaultType))]
    (.load keystore nil (char-array ""))
    (.setCertificateEntry keystore "default" cert)
    keystore))

(defn- jetty-promise
  "converts a jetty promise to promesa"
  [f]
  (p/create
    (fn [resolve reject]
      (let [p (reify Promise
                (succeeded [_ result]
                  (resolve result))
                (failed [_ error]
                  (reject error)))]
        (f p)))))

(def ^:const default-input-buffer (* 1024 1024))

(defn jetty-connect [{:keys [host port input-buffer-size idle-timeout ssl cert-file] :or {host "localhost" input-buffer-size default-input-buffer port 80 ssl false} :as params}]
  (let [client (HTTP2Client.)
        address (InetSocketAddress. ^String host (int port))
        listener (ServerSessionListener$Adapter.)
        ssl-context-factory (when ssl (SslContextFactory$Client.))]
    (when ssl (.addBean client ssl-context-factory))
    (when (and ssl cert-file)
      (.setTrustStore ssl-context-factory (create-keystore cert-file)))
    (log/debug "Connecting with parameters: " params)
    (.setInputBufferSize client input-buffer-size)
    (.setInitialStreamRecvWindow client input-buffer-size)
    (when idle-timeout
      (.setIdleTimeout client idle-timeout))
    (.start client)
    (-> (jetty-promise
          (fn [p]
            (.connect client (when ssl ssl-context-factory) address listener p)))
        (p/then (fn [session]
                  (let [context {:client client :session session}]
                    (log/debug "Session established:" context)
                    context)))
        (p/catch (fn [e]
                   (p/create
                     (fn [resolve reject]
                       (.stop client)                       ;; run (.stop) in a different thread, because p/catch will be called from .connect -> reject
                       (reject e))
                     p.exec/default-executor))))))

(defn connect
  "
Connects the client to a [GRPC-HTTP2](https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md) compatible server

#### Parameters
A map with the following entries:

| Value                 | Type          | Default | Description                                                               |
|-----------------------|---------------|-------------------------------------------------------------------------------------|
| **uri**               | _String_      | n/a     | The URI of the GRPC server                                                |
| **codecs**            | _map_         | [[protojure.grpc.codec.core/builtin-codecs]] | Optional custom codecs               |
| **content-coding**    | _String_      | nil     | The encoding to use on request data                                       |
| **max-frame-size**    | _UInt32_      | 16KB    | The maximum HTTP2 DATA frame size                                         |
| **input-buffer-size** | _UInt32_      | 1MB     | The input-buffer size                                                     |
| **cert-file**         | _String_      | n/a     | Optional X.509 server certificate file path                               |
| **metadata**          | _map_ or _fn_ | n/a     | Optional [string string] tuples as a map, or a 0-arity fn that returns    |
|                       |               |         | same that will be submitted as attributes to the request, such as via     |
|                       |               |         | HTTP headers for GRPC-HTTP2                                               |

#### Return value
A promise that, on success, evaluates to an instance of [[api/Provider]].
_(api/disconnect)_ should be used to release any resources when the connection is no longer required.
  "
  [{:keys [uri codecs content-coding max-frame-size input-buffer-size metadata idle-timeout ssl cert-file]
    :or {codecs builtin-codecs max-frame-size 16384 input-buffer-size default-input-buffer} :as params}]
  (log/debug "Connecting with GRPC-HTTP2:" params)
  (let [{:keys [host port scheme]} (lambdaisland/uri uri)
        https? (= "https" (lower-case scheme))
        parsed-port (cond
                      port (Integer/parseInt port)
                      https? 443
                      :else 80)]
    (-> (jetty-connect {:host host :port parsed-port :input-buffer-size input-buffer-size :idle-timeout idle-timeout
                        :ssl (or ssl https?) :cert-file cert-file})
        (p/then #(core/->Http2Provider % uri codecs content-coding max-frame-size input-buffer-size metadata)))))

(defn connect-lnd
  "Create LND client connection with the default jetty parameters"
  [{:keys [uri cert macaroon]}]
  (connect {:uri uri
            :metadata {"macaroon" (macaroon-header macaroon)}
            :cert-file cert}))

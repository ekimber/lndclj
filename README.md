# lndclj

Clojure LND gRPC client.
Protocol buffer support from the [protojure](https://protojure.readthedocs.io/en/latest/) library. 

## Usage

Version numbers match the LND release versions (major/minor).

Building the library requires `protoc-gen-clojure` to be installed in the `bin` directory. In the project root, run:

    sudo curl -L https://github.com/protojure/protoc-plugin/releases/download/v2.0.0/protoc-gen-clojure --output ./bin/protoc-gen-clojure
    sudo chmod +x ./bin/protoc-gen-clojure

Run the project's tests (there aren't any):

    $ ./gradlew test

## Examples

    (require '[oberi.lndclj :refer [connect-lnd])
    (require '[walletrpc.WalletKit.client :refer [ListUnspent]])
    (require '[walletrpc :refer [new-ListUnspentRequest]])

    (def connection {:uri      "https://localhost:10009"
                     :cert     "<path to tls.cert>"
                     :macaroon "<path to admin.macaroon>"})
    
    (def cli @(connect-lnd connection))
    @(ListUnspent cli (new-ListUnspentRequest {:min-confs 3 :max-confs 99999}))

## License

Copyright © 2022 Edward Kimber

Distributed under LGPL 3.0

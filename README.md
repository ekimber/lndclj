# lndclj

Clojure LND gRPC client.
Protocol buffer support from the [protojure](https://protojure.readthedocs.io/en/latest/) library. 

## Usage

Version numbers match the LND release versions (major/minor).

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

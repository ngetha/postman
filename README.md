# postman

postman is a mobile money gateway for B2C payments. 

It currently wraps over an M-Pesa PayBill (with your paybill credentials) and exposes a simple http interface for you to work with.

Written in Clojure initially by Ngetha & Kibet

## Installation

Grab the sources from git and compile with lein

## Usage

Running the server

    $ java -Dconfig=/path/to/postman.clj -jar postman-0.1.0-standalone.jar 
    
Making requests
1. Request a Payment

```
    >>
    POST /postman/send
    amt=5000
    via=m-pesa
    to=+254700008009
    tid=A15363727763G
    
    <<
    {"status" : 202, "status-msg" : "Accepted"}
    
    >>
    GET /postman/status?tid=A15363727763G
    {"status":200,"status-msg":"OK","tid":"A74847383","resp":{"status-msg":"Sent Successfully","status":"1","ext-data":{"ext-mpesa-code":"XFDG3674","ext-trans-id":474883822}}}
    
```

## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2015 Postman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

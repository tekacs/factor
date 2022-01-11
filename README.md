## factor

### example

The project comes with an example client and server, which you can start and which can be extended to test and run the functionality of a full-stack application out of the box.

#### server

Booting the server is: `clj -M:server:example-server`.

You can also skip the `example-` alias and just jack in using the `server` alias.

To get the application system started, just run `(-main)` in `factor.server.example`.

#### client

To get a running shadow-cljs client, you can run `npm install && clj -M:client:example-client`.

Once the shadow-cljs client is running, you can connect to it from your editor on `localhost:8230`.

To see how the system is started and constructed, look in `factor.client.example`.
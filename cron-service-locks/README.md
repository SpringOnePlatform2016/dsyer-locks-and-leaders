A basic ping-a-web-hook-on-a-cron service using explicit locks to
ensure only one instance does the ping. The hooks are stored in a
relational database, and in this sample the same database is used to
manage the locks, via Spring Integration. It will work with any
relational database that supports transaction isolation (see the
Spring Integration user guide for more detail).

The default profile just uses in-memory (java.util.concurrent) locks,
so it only works for a single node. There is a "cloud" profile that
you can run to enable the JDBC locks (e.g. this would be activated
automatically in Cloud Foundry). The database is not explicitly
configured, except for the schema to run on startup. To run on Cloud
Foundry just bind to a MySQL service called "mysql". To run locally
with multiple nodes set `spring.datasource.url` to a local h2 server
(or use `DATABASE_PLATFORM=mysql` if using MySQL) and activate
the "cloud" profile.

There is a `docker-compose.yml` to run MySQL locally in the parent
directory. Or you can run h2 really easily with `spring cloud h2`
(using the spring cloud CLI launcher plugin).


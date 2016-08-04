A basic ping-a-web-hook-on-a-cron service using optimistic locks to
ensure only one instance does the ping. The hooks are stored in a
relational database, and in this sample the same database is used to
manage the locks, via Spring Integration and Spring Data.

The database is not explicitly configured, except for the schema to
run on startup. To run on Cloud Foundry just bind to a MySQL service
called "mysql". To run locally with multiple nodes set
`spring.datasource.url` to a local h2 server (or use
`DATABASE_PLATFORM=mysql` if using MySQL).

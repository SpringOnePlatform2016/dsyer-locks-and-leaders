A basic ping-a-web-hook-on-a-cron service using explicit leader
election to ensure only one instance does the ping. The hooks are
stored in a relational database, and in this sample the same database
is used to manage the leader, via Spring Integration.

# Android background synchronization 0.15.0

## Purpose

Helpify and MyDealer can periodically refresh authenticated server data even
when their dashboard is not open.

The implementation uses Android WorkManager 2.7.1 with unique work names for
each application.

## User controls

Background synchronization is disabled by default. A signed-in user can enable
it from the notification settings and choose one of these intervals:

- 15 minutes;
- 30 minutes;
- 60 minutes;
- 180 minutes.

The events section displays the most recent background run, result and number
of detected changes. It also provides a one-time `Synchronize now` action.

## Execution constraints

Periodic and one-time work require:

- an active network connection;
- a battery level that Android does not consider low.

WorkManager controls the exact execution time. The selected interval is a
minimum recurrence target rather than an exact alarm.

## Background workflow

A worker:

1. restores the current authenticated session;
2. requests the product-specific live dashboard;
3. updates the offline dashboard cache;
4. compares server cards with the previous snapshot;
5. records new items and status changes;
6. sends enabled system notifications;
7. stores the run status for the dashboard.

A missing or expired session does not expose cached credentials and does not
produce repeated retry traffic.

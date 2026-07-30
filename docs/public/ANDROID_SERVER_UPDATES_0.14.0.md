# Android server change notifications 0.14.0

## Change detection

After each successful authenticated server refresh, the application compares
the current live dashboard with the previous role-specific snapshot.

The application records:

- newly appearing server cards;
- changes to a card badge or workflow status.

The first successful synchronization creates the baseline and does not produce
a notification flood.

## In-app notification centre

The `События` section contains up to sixty entries per role. Each entry has a
timestamp, event type, title, message and read state.

Users can:

- open an event;
- mark all events as read;
- show only unread events;
- clear the event list;
- change notification preferences.

## System notifications

Android system notifications summarize new items and status changes. Separate
preferences control:

- all system notifications;
- new item notifications;
- status change notifications.

Notifications are produced after an application refresh. This release does not
add a background polling service or remote push provider.

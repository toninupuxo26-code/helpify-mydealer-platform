# Android deep links and quick navigation 0.16.0

## Notification navigation

Server-change notifications now open the `События` dashboard section and
activate the unread-only filter. Notifications produced by both foreground
refreshes and WorkManager use the same navigation contract.

## Dynamic launcher shortcuts

On supported Android launchers, long-pressing the application icon exposes:

- unread events;
- favourite cards;
- server data with immediate refresh;
- one-time background synchronization.

## Deep links

Helpify accepts:

- `helpsiffyy://dashboard?section=events`;
- `https://helpsiffyy.app/app?section=server`;
- `https://helpsiffyy.app/app?section=favorites`.

MyDealer accepts:

- `mydealers://dashboard?section=events`;
- `https://mydealers.app/app?section=server`;
- `https://mydealers.app/app?section=recent`.

Supported query parameters include:

- `section`;
- `q` or `query`;
- `favorites`;
- `unread`;
- `sync`;
- `refresh`.

The HTTPS links use standard browsable intent filters. Verified App Links
would additionally require domain-hosted Digital Asset Links files.

## Session handling

The dashboard still checks the saved authenticated session before showing
server data. A deep link opened without a valid session returns the user to
authentication.

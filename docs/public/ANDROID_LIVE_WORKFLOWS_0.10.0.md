# Android live workflows 0.10.0

## Purpose

The Android dashboards now combine two types of content:

- guided local scenarios for product demonstrations;
- current server data loaded through the authenticated Laravel APIs.

## Helpify

The Helpify client reads `/api/work/tasks` and presents current tasks, offers,
budgets and statuses.

Available quick actions depend on the signed-in role and task state:

- customer task creation;
- contractor offer submission;
- customer selection of the lowest-priced offer;
- task completion;
- participant chat message.

## MyDealer

The MyDealer client reads:

- `/api/market/products`;
- `/api/market/cart`;
- `/api/market/orders`.

Available quick actions include:

- vendor product creation;
- vendor product publication;
- buyer add-to-cart;
- buyer checkout;
- vendor order confirmation and completion;
- buyer order message.

## Failure handling

The guided scenario catalogue remains available when the server cannot be
reached. Live sections show the API error and can be refreshed independently.
An HTTP 401 response clears the local session and returns to authentication.

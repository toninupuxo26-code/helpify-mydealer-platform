# Android interactive scenarios 0.9.0

## Helpify

The Helpify Android client includes separate customer and contractor datasets.

Customer scenarios cover:

- urgent and scheduled task creation;
- offer comparison and price clarification;
- assigned task lifecycle;
- rescheduling;
- task chat;
- completion and rating;
- history and support.

Contractor scenarios cover:

- nearby task discovery;
- offer creation and update;
- assigned work lifecycle;
- route and contact access;
- task chat;
- calendar;
- activity statistics;
- professional profile and reviews.

## MyDealer

The buyer dataset includes catalogue products, collections, favourites, cart,
checkout, active orders, order chat, purchase history, reviews and profile
settings.

The vendor dataset includes product creation, moderation, catalogue management,
stock control, order processing, buyer communication, sales analytics, reviews,
vendor profile and operating settings.

## Scenario progress

Each scenario contains a stable identifier and a sequence of steps. Progress is
stored locally in SharedPreferences and survives application restarts. Users can
advance, reset one scenario or reset all scenarios.

# Development guide

## Workflow

1. Create a branch from `main`.
2. Implement one focused change.
3. Update tests and documentation.
4. Run repository Doctors.
5. Open a pull request.
6. Merge only after successful review.

## Backend

The backend applications use PHP 7.4 and Laravel 8. Business rules belong in application services and controllers, while persistence is managed through migrations and database access layers.

## Web

Web clients use HTML5, CSS3 and JavaScript. Protected actions call the REST API and include the Bearer token issued by the authentication service.

## Android

The Android workspace contains:

- `core` — shared API and session code;
- `helpify` — Helpify mobile client;
- `mydealer` — MyDealer mobile client.

The Android baseline uses Kotlin 1.5.31, Android Gradle Plugin 7.0.4 and SDK 31.

## Data

Local and staging environments use test fixtures. Production data must not be copied into the repository.

## Security

Every change must preserve role checks, validation, safe error handling and secret isolation.

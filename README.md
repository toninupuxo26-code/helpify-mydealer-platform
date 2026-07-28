# Helpify & MyDealer Platform

Monorepository for the Helpify service marketplace and the MyDealer product marketplace.

## Products

### Helpify

Helpify connects customers with local service professionals. Customers publish tasks, receive offers, select a contractor, communicate in the task chat and complete the work cycle.

### MyDealer

MyDealer connects buyers with selected vendors. Buyers browse the catalogue, manage a cart, place orders and communicate with vendors. Vendors manage products, moderation states and order processing.

## Repository structure

```text
implementation/
  helpify/
    backend/
    landing/
    web-app/
  mydealer/
    backend/
    landing/
    web-app/
  android-native/
    core/
    helpify/
    mydealer/

infrastructure/
docs/
patches/
scripts/
```

## Technology stack

- PHP 7.4 and Laravel 8
- MySQL 8
- Redis 6
- Nginx
- Docker Compose
- JavaScript, HTML5 and CSS3
- Kotlin 1.5.31
- Android Gradle Plugin 7.0.4
- Android SDK 31

## Environments

- `local` — developer workstation
- `staging` — integration and acceptance testing
- `production` — public services

## Local verification

```bash
./scripts/repository_doctor.sh
./scripts/web_doctor.sh all
./scripts/backend_doctor.sh all
./scripts/android_doctor.sh source
```

## Public services

- Helpify: `https://helpsiffyy.app`
- MyDealer: `https://mydealers.app`

## Documentation

The complete project documentation is stored under `docs/`.

## Versioning

The project follows semantic versioning. Release tags use the format `vMAJOR.MINOR.PATCH`.

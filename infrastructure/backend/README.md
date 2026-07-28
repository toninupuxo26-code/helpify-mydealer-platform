# Backend runtime baseline

This directory defines the first server-side runtime for both products.

- PHP `7.4.33` in isolated containers;
- Laravel `8.83.27` application baseline;
- MySQL `8.0.28` with separate logical databases and users;
- Redis `6.2.6`;
- loopback-only application ports `18081` and `18082`;
- same-origin public routes `/api/*` through the existing Nginx virtual hosts.

Runtime secrets are generated on the VPS and stored outside Git at
`/etc/helpify-mydealer/backend.env` with mode `0600`.

Deployment and verification are performed through patch `3010` and
`scripts/backend_doctor.sh`.

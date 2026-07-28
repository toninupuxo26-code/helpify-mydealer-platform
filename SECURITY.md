# Security Policy

## Reporting

Do not publish security vulnerabilities in public issues.

Report vulnerabilities privately to the project owner and include:

- affected component;
- impact;
- reproduction steps;
- expected and actual result;
- suggested mitigation, when available.

## Protected data

Do not commit:

- passwords or access tokens;
- private keys or certificates;
- production `.env` files;
- personal data exports;
- database backups;
- store signing materials;
- Cloudflare, Apple or Google credentials.

## Baseline controls

The project applies:

- server-side authorization for every protected operation;
- input validation and output encoding;
- password hashing;
- short-lived or revocable access tokens;
- HTTPS;
- rate limiting;
- audit logging;
- dependency review;
- backup and recovery procedures;
- OWASP-aligned web, API and mobile controls.

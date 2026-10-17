# Backend changes required for account-free (guest) mode

The `cutejarvis-backend` was not part of the project files, so it could not be edited here. The app now sends:

    Authorization: Device <64 hex chars>      (random 256-bit ID generated on the phone, never logged)

Public `GET /api/health` stays unauthenticated, as before.

## Replace the Clerk JWT middleware with a device-ID middleware
1. Read the header; require the `Device ` scheme and the regex `^[0-9a-f]{64}$`, otherwise respond 401.
2. Compute `deviceKey = SHA-256(id)` (hex). **Store only the hash**, never the raw ID.
3. Upsert a user row keyed by `deviceKey` and set `req.userId` to it. Every existing route (`/chat`, `/memories`,
   `/skills`, `/providers`, `/settings`, `/actions/:id/result`) keeps working unchanged because it already scopes data by `userId`.
4. Keep all queries scoped by `userId` (no route may return another device's rows). Database access stays server-side only;
   never expose the DB, service keys or provider API keys to the client.
5. Add rate limiting per `deviceKey` and per IP (especially `/chat` and `/providers/test`), since anyone can mint a device ID.
   Consider a daily cap on new device IDs per IP.
6. Provider API keys remain encrypted at rest and are never returned (only `keyLast4`).
7. Remove the Clerk JWKS/secret env vars and the `@clerk/*` packages once the middleware is swapped (or keep Clerk as an optional second scheme).

## Honest limits of guest mode
- The device ID is the only credential: uninstalling / clearing app data creates a new identity, and the old server-side
  memories and provider keys become unreachable (there is no recovery without an account).
- Data does not sync between phones.

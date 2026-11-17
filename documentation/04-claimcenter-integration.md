# Guidewire ClaimCenter integration (prepared, not connected)

ClaimCenter remains the system of record. SmartAssign runs standalone on the synthetic dataset; nothing in this project
connects to a real ClaimCenter, and no credentials are included.

* Boundary: `integration/ClaimCenterGateway` (fetch changed claims, fetch handlers, optional assignment write-back).
* Default implementation `NotConfiguredClaimCenterGateway` makes no external calls and reports `NOT_CONNECTED`.
* `GET /api/integration/claimcenter/status` returns the status, the indicative field mapping and limitations.
* The dashboard shows `claimCenter: NOT_CONNECTED`; it never implies a live connection.

The field mapping in `ClaimCenterFieldMapping.java` is **indicative**: ClaimCenter entity names vary by version and by
customer extensions (`*_Ext`), so every row must be confirmed with the customer's Guidewire team. Synchronisation should
be incremental (changed-since), idempotent on `claim_number`, and write-back should only happen after supervisor
approval, through approved APIs, respecting ClaimCenter's own assignment rules.

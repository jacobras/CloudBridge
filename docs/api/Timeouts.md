# Timeouts

These are the non-configurable defaults set by CloudBridge.

| Timeout   | Limit      | Description                                       |
|-----------|------------|---------------------------------------------------|
| `connect` | 20 seconds | Connection should happen within this time.        |
| `socket`  | 20 seconds | Longest wait time for new bytes during a request. |
| `request` | 2 minutes  | Maximum total duration of a request.              |
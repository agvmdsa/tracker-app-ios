"""In-process pub/sub for pushing new fixes to connected SSE clients (GET /fixes/stream).

In-memory only — works because this API runs as a single process. If it's ever scaled to
multiple worker processes/instances, a subscriber connected to one process would miss fixes
published from another; that would need a real broker (Redis pub/sub, Postgres LISTEN/NOTIFY)
instead of this.
"""

import asyncio
from typing import Any

# Bounded per subscriber: a slow/stalled client shouldn't let the queue (and the fixes waiting
# in it) grow forever. Dropping the fix for that one subscriber when it's full is the same
# drop-and-move-on policy the ESP32 firmware's BoundedQueue uses, for the same reason.
_QUEUE_MAXSIZE = 100


class FixBroadcaster:
    def __init__(self) -> None:
        self._subscribers: set[asyncio.Queue[Any]] = set()

    def subscribe(self) -> "asyncio.Queue[Any]":
        queue: asyncio.Queue[Any] = asyncio.Queue(maxsize=_QUEUE_MAXSIZE)
        self._subscribers.add(queue)
        return queue

    def unsubscribe(self, queue: "asyncio.Queue[Any]") -> None:
        self._subscribers.discard(queue)

    def publish(self, fix: dict[str, Any]) -> None:
        for queue in self._subscribers:
            try:
                queue.put_nowait(fix)
            except asyncio.QueueFull:
                pass


broadcaster = FixBroadcaster()

"""In-process pub/sub for pushing new fixes to connected SSE clients (GET /fixes/stream).

In-memory only — works because this API runs as a single process. If it's ever scaled to
multiple worker processes/instances, a subscriber connected to one process would miss fixes
published from another; that would need a real broker (Redis pub/sub, Postgres LISTEN/NOTIFY)
instead of this.

Thread-safety: `POST /fixes` is a sync endpoint, so FastAPI runs it on a threadpool worker, not on
the event loop. `asyncio.Queue` isn't thread-safe — calling `put_nowait` from that worker can race
the loop and doesn't reliably wake a client awaiting `queue.get()`. So `publish` hands each put to
the subscriber's own loop with `call_soon_threadsafe` instead of touching the queue directly.
"""

import asyncio
import threading
from typing import Any

# Bounded per subscriber: a slow/stalled client shouldn't let the queue (and the fixes waiting
# in it) grow forever. Dropping the fix for that one subscriber when it's full is the same
# drop-and-move-on policy the ESP32 firmware's BoundedQueue uses, for the same reason.
_QUEUE_MAXSIZE = 100


def _put_or_drop(queue: "asyncio.Queue[Any]", item: Any) -> None:
    try:
        queue.put_nowait(item)
    except asyncio.QueueFull:
        pass


class FixBroadcaster:
    def __init__(self) -> None:
        # queue -> the event loop that owns it (the one its SSE generator awaits on).
        self._subscribers: dict[asyncio.Queue[Any], asyncio.AbstractEventLoop] = {}
        # Guards _subscribers: publish iterates it from a worker thread while subscribe/
        # unsubscribe mutate it from the event loop.
        self._lock = threading.Lock()

    def subscribe(self) -> "asyncio.Queue[Any]":
        """Must be called from the event loop (i.e. inside an async endpoint/generator)."""
        queue: asyncio.Queue[Any] = asyncio.Queue(maxsize=_QUEUE_MAXSIZE)
        with self._lock:
            self._subscribers[queue] = asyncio.get_running_loop()
        return queue

    def unsubscribe(self, queue: "asyncio.Queue[Any]") -> None:
        with self._lock:
            self._subscribers.pop(queue, None)

    def publish(self, fix: dict[str, Any]) -> None:
        """Safe to call from any thread, including the threadpool sync endpoints run on."""
        with self._lock:
            subscribers = list(self._subscribers.items())
        for queue, loop in subscribers:
            try:
                loop.call_soon_threadsafe(_put_or_drop, queue, fix)
            except RuntimeError:
                # Loop already closed (server shutting down) — nothing left to deliver to.
                pass


broadcaster = FixBroadcaster()

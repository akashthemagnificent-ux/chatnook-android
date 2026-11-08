#!/usr/bin/env python3
"""Act as a second client in Supabase Realtime, for verifying typing and presence.

The emulator can only be one user, so this joins a channel exactly the way the app
does (Phoenix protocol, vsn 1.0.0) and pings or tracks as somebody else.

    python3 tools/realtime_client.py typing   <channel_id> <name>
    python3 tools/realtime_client.py presence <guild_id>

Both keep the socket open until Ctrl-C.
"""

import asyncio
import json
import sys

import websockets

import e2e_backend as e2e  # noqa: E402  (loads local.properties)


def endpoint() -> str:
    ref = e2e.URL.split("//", 1)[1].split(".", 1)[0]
    return f"wss://{ref}.supabase.co/realtime/v1/websocket?apikey={e2e.ANON}&vsn=1.0.0"


async def join(ws, topic: str, token: str, key: str) -> None:
    await ws.send(json.dumps({
        "topic": f"realtime:{topic}",
        "event": "phx_join",
        "payload": {
            "config": {
                "broadcast": {"self": False, "ack": False},
                "presence": {"key": key},
                "postgres_changes": [],
            },
            "access_token": token,
        },
        "ref": "1",
        "join_ref": "1",
    }))


async def keepalive(ws) -> None:
    while True:
        await asyncio.sleep(20)
        await ws.send(json.dumps({"topic": "phoenix", "event": "heartbeat", "payload": {}, "ref": "9"}))


async def main() -> int:
    mode, room = sys.argv[1], sys.argv[2]
    token, user_id = e2e.signup()
    async with websockets.connect(endpoint(), ping_interval=None) as ws:
        await join(ws, f"{mode}-{room}", token, user_id)
        reply = json.loads(await ws.recv())
        print("join:", reply.get("payload", {}).get("status") or reply.get("event"), flush=True)
        asyncio.create_task(keepalive(ws))

        if mode == "typing":
            await asyncio.sleep(1)
            payload = {"user_id": user_id, "name": sys.argv[3]}
            for _ in range(3):
                await ws.send(json.dumps({
                    "topic": f"realtime:typing-{room}",
                    "event": "broadcast",
                    "payload": {"type": "broadcast", "event": "typing", "payload": payload},
                    "ref": "3",
                }))
                print("sent typing ping", flush=True)
                await asyncio.sleep(2)
            await asyncio.sleep(3)
            return 0

        await ws.send(json.dumps({
            "topic": f"realtime:presence-{room}",
            "event": "presence",
            "payload": {"type": "presence", "event": "track", "payload": {"user_id": user_id}},
            "ref": "4",
        }))
        print("tracked presence", flush=True)
        await asyncio.sleep(20)
        return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))

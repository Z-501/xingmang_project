import argparse
import asyncio
import csv
import json
import platform
import statistics
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode

import requests
import websockets


CSV_COLUMNS = [
    "mode",
    "runId",
    "videoId",
    "requestedConnections",
    "connectedConnections",
    "connectionSuccessRate",
    "scenarioValid",
    "connectionSetupMs",
    "messages",
    "actualSendDurationMs",
    "actualMessagesPerSecond",
    "expectedDeliveries",
    "receivedDeliveries",
    "deliveryRatePct",
    "fullyDeliveredMessages",
    "perDeliveryAvgMs",
    "perDeliveryP50Ms",
    "perDeliveryP95Ms",
    "perDeliveryP99Ms",
    "allClientsAvgMs",
    "allClientsP50Ms",
    "allClientsP95Ms",
    "allClientsP99Ms",
    "persistedCount",
    "expectedPersistedCount",
    "persistenceComplete",
    "persistenceExact",
    "duplicatePersistedRows",
    "persistenceDrainMs",
    "serverMessageCount",
    "handlerTotalP95Ms",
    "broadcastP95Ms",
    "redisCacheP95Ms",
    "persistStageP95Ms",
    "cleanupDeletedMysqlRows",
    "cleanupDeletedRedisMembers",
]


def redact(text, token):
    if not text:
        return text
    return str(text).replace(token, "<ACCESS_TOKEN>")


def percentile(values, pct):
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * pct / 100.0
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    weight = position - lower
    return ordered[lower] * (1.0 - weight) + ordered[upper] * weight


def summary(values):
    return {
        "avg": statistics.fmean(values) if values else None,
        "p50": percentile(values, 50),
        "p95": percentile(values, 95),
        "p99": percentile(values, 99),
    }


def api_payload(response, token):
    try:
        payload = response.json()
    except ValueError as exc:
        raise RuntimeError(f"HTTP {response.status_code}: non-JSON response") from exc
    if response.status_code >= 400 or payload.get("code") != 200:
        raise RuntimeError(redact(f"HTTP {response.status_code}: {payload.get('msg', payload)}", token))
    return payload.get("data") or {}


def api_post(session, base_url, path, token, body=None, timeout=30):
    response = session.post(
        f"{base_url}{path}",
        headers={"accessToken": token},
        json=body,
        timeout=timeout,
    )
    return api_payload(response, token)


def api_get(session, base_url, path, token, params=None, timeout=30):
    response = session.get(
        f"{base_url}{path}",
        headers={"accessToken": token},
        params=params,
        timeout=timeout,
    )
    return api_payload(response, token)


def api_delete(session, base_url, path, token, params=None, timeout=30):
    response = session.delete(
        f"{base_url}{path}",
        headers={"accessToken": token},
        params=params,
        timeout=timeout,
    )
    return api_payload(response, token)


def build_ws_url(ws_url, token, video_id, mode):
    params = urlencode({
        "accessToken": token,
        "videoId": str(video_id),
        "benchmarkPersistMode": mode,
    })
    separator = "&" if "?" in ws_url else "?"
    return f"{ws_url}{separator}{params}"


async def open_connection(index, url, token):
    try:
        ws = await websockets.connect(url, max_queue=None)
        return index, ws, None
    except Exception as exc:
        return index, None, redact(str(exc), token)


async def receiver_loop(index, websocket, send_times, received, stop_event, token, expected_run_id):
    try:
        async for raw in websocket:
            now = time.perf_counter_ns()
            try:
                data = json.loads(raw)
                content = data.get("content", "")
                if not content.startswith("BM:"):
                    continue
                parts = content.split(":", 2)
                if len(parts) != 3:
                    continue
                if parts[1] != expected_run_id:
                    continue
                sequence = int(parts[2])
                send_time = send_times.get(sequence)
                if send_time is None:
                    continue
                received.setdefault(sequence, {})[index] = (now - send_time) / 1_000_000.0
            except Exception:
                continue
            if stop_event.is_set():
                break
    except Exception as exc:
        if not stop_event.is_set():
            print(f"WARNING receiver {index} failed: {redact(str(exc), token)}")


async def send_messages(websocket, run_id, count, interval_ms, send_times):
    first_send_start_ns = None
    last_send_time = None
    for sequence in range(1, count + 1):
        content = f"BM:{run_id}:{sequence:06d}"
        payload = {
            "content": content,
            "danmuTime": float(sequence),
            "color": "#FFFFFF",
            "mode": 1,
            "fontSize": 2,
        }
        send_start_ns = time.perf_counter_ns()
        if first_send_start_ns is None:
            first_send_start_ns = send_start_ns
        send_times[sequence] = send_start_ns
        await websocket.send(json.dumps(payload, separators=(",", ":")))
        last_send_time = time.perf_counter_ns()
        if interval_ms > 0 and sequence < count:
            await asyncio.sleep(interval_ms / 1000.0)
    return first_send_start_ns, last_send_time


async def wait_delivery(received, expected_connections, messages, timeout_seconds):
    deadline = time.perf_counter() + timeout_seconds
    while time.perf_counter() < deadline:
        if all(len(received.get(seq, {})) >= expected_connections for seq in range(1, messages + 1)):
            return
        await asyncio.sleep(0.05)


async def close_connections(websockets_list):
    for ws in websockets_list:
        try:
            await ws.close()
        except Exception:
            pass


async def run_websocket_phase(args, run_id, message_count, collect_stats):
    url = build_ws_url(args.ws_url, args.access_token, args.video_id, args.mode)
    setup_start = time.perf_counter()
    opened = await asyncio.gather(
        *(open_connection(i, url, args.access_token) for i in range(args.connections))
    )
    connection_setup_ms = (time.perf_counter() - setup_start) * 1000.0
    sockets = [ws for _, ws, error in opened if ws is not None]
    errors = [error for _, ws, error in opened if error]

    connected = len(sockets)
    received = {}
    send_times = {}
    stop_event = asyncio.Event()
    receiver_tasks = [
        asyncio.create_task(receiver_loop(i, ws, send_times, received, stop_event, args.access_token, run_id))
        for i, ws in enumerate(sockets)
    ]

    last_send_time = None
    first_send_start_ns = None
    try:
        if connected > 0 and message_count > 0:
            first_send_start_ns, last_send_time = await send_messages(
                sockets[0],
                run_id,
                message_count,
                args.interval_ms,
                send_times,
            )
            await wait_delivery(received, connected, message_count, args.delivery_timeout)
    finally:
        stop_event.set()
        await close_connections(sockets)
        await asyncio.gather(*receiver_tasks, return_exceptions=True)

    if not collect_stats:
        return {
            "runId": run_id,
            "connectedConnections": connected,
            "connectionErrors": errors,
        }

    return build_delivery_result(
        run_id,
        args.connections,
        connected,
        connection_setup_ms,
        errors,
        received,
        message_count,
        last_send_time,
        first_send_start_ns,
    )


async def run_formal_phase(args, session, base_url, run_id):
    url = build_ws_url(args.ws_url, args.access_token, args.video_id, args.mode)
    setup_start = time.perf_counter()
    opened = await asyncio.gather(
        *(open_connection(i, url, args.access_token) for i in range(args.connections))
    )
    connection_setup_ms = (time.perf_counter() - setup_start) * 1000.0
    sockets = [ws for _, ws, error in opened if ws is not None]
    errors = [error for _, ws, error in opened if error]

    connected = len(sockets)
    received = {}
    send_times = {}
    stop_event = asyncio.Event()
    receiver_tasks = [
        asyncio.create_task(receiver_loop(i, ws, send_times, received, stop_event, args.access_token, run_id))
        for i, ws in enumerate(sockets)
    ]

    last_send_time = None
    first_send_start_ns = None
    persistence_task = None
    try:
        if connected > 0 and args.messages > 0:
            first_send_start_ns, last_send_time = await send_messages(
                sockets[0],
                run_id,
                args.messages,
                args.interval_ms,
                send_times,
            )
            persistence_task = asyncio.to_thread(
                wait_persistence,
                session,
                base_url,
                args.access_token,
                args.video_id,
                run_id,
                args.messages,
                last_send_time,
                args.persistence_timeout,
            )
            delivery_task = asyncio.create_task(
                wait_delivery(received, connected, args.messages, args.delivery_timeout)
            )
            persistence, _ = await asyncio.gather(persistence_task, delivery_task)
        else:
            persistence = build_persistence_result(0, args.messages, None, None)
    finally:
        stop_event.set()
        await close_connections(sockets)
        await asyncio.gather(*receiver_tasks, return_exceptions=True)

    delivery = build_delivery_result(
        run_id,
        args.connections,
        connected,
        connection_setup_ms,
        errors,
        received,
        args.messages,
        last_send_time,
        first_send_start_ns,
    )
    return delivery, persistence


def build_delivery_result(run_id, requested, connected, setup_ms, errors, received, messages, last_send_time,
                          first_send_start_ns):
    per_delivery = []
    all_clients = []
    fully_delivered = 0
    for sequence in range(1, messages + 1):
        latencies = list(received.get(sequence, {}).values())
        per_delivery.extend(latencies)
        if connected > 0 and len(latencies) >= connected:
            fully_delivered += 1
            all_clients.append(max(latencies))

    expected = connected * messages
    received_count = len(per_delivery)
    actual_send_duration_ms = None
    actual_messages_per_second = None
    if first_send_start_ns is not None and last_send_time is not None:
        actual_send_duration_ms = (last_send_time - first_send_start_ns) / 1_000_000.0
        if actual_send_duration_ms > 0:
            actual_messages_per_second = messages / (actual_send_duration_ms / 1000.0)

    return {
        "runId": run_id,
        "requestedConnections": requested,
        "connectedConnections": connected,
        "connectionSuccessRate": connected * 100.0 / requested if requested else 0.0,
        "scenarioValid": connected == requested,
        "connectionSetupMs": setup_ms,
        "connectionErrors": errors,
        "messages": messages,
        "actualSendDurationMs": actual_send_duration_ms,
        "actualMessagesPerSecond": actual_messages_per_second,
        "expectedDeliveries": expected,
        "receivedDeliveries": received_count,
        "deliveryRatePct": received_count * 100.0 / expected if expected else 0.0,
        "fullyDeliveredMessages": fully_delivered,
        "perDeliveryLatency": summary(per_delivery),
        "allClientsLatency": summary(all_clients),
        "lastMessageSendTimeNs": last_send_time,
    }


def cleanup_run(session, base_url, token, video_id, run_id):
    try:
        return api_delete(
            session,
            base_url,
            "/benchmark/danmu/data",
            token,
            {"videoId": video_id, "runId": run_id},
        )
    except Exception as exc:
        return {"error": redact(str(exc), token)}


def wait_persistence(session, base_url, token, video_id, run_id, expected, last_send_time_ns, timeout_seconds):
    deadline = time.perf_counter() + timeout_seconds
    persisted = 0
    complete_time_ns = None
    while time.perf_counter() < deadline:
        data = api_get(
            session,
            base_url,
            "/benchmark/danmu/persisted-count",
            token,
            {"videoId": video_id, "runId": run_id},
        )
        persisted = int(data.get("persistedCount", 0))
        if persisted >= expected:
            complete_time_ns = time.perf_counter_ns()
            break
        time.sleep(0.1)

    return build_persistence_result(persisted, expected, complete_time_ns, last_send_time_ns)


def build_persistence_result(persisted, expected, complete_time_ns, last_send_time_ns):
    drain_ms = None
    if complete_time_ns is not None and last_send_time_ns is not None:
        drain_ms = (complete_time_ns - last_send_time_ns) / 1_000_000.0

    return {
        "persistedCount": persisted,
        "expectedPersistedCount": expected,
        "persistenceComplete": persisted >= expected,
        "persistenceExact": persisted == expected,
        "duplicatePersistedRows": max(0, persisted - expected),
        "persistenceDrainMs": drain_ms,
    }


def flatten_result(args, run_id, delivery, server_metrics, persistence, cleanup):
    per_delivery = delivery["perDeliveryLatency"]
    all_clients = delivery["allClientsLatency"]
    return {
        "mode": args.mode,
        "runId": run_id,
        "videoId": args.video_id,
        "requestedConnections": args.connections,
        "connectedConnections": delivery["connectedConnections"],
        "connectionSuccessRate": delivery["connectionSuccessRate"],
        "scenarioValid": delivery["scenarioValid"],
        "connectionSetupMs": delivery["connectionSetupMs"],
        "messages": args.messages,
        "actualSendDurationMs": delivery["actualSendDurationMs"],
        "actualMessagesPerSecond": delivery["actualMessagesPerSecond"],
        "expectedDeliveries": delivery["expectedDeliveries"],
        "receivedDeliveries": delivery["receivedDeliveries"],
        "deliveryRatePct": delivery["deliveryRatePct"],
        "fullyDeliveredMessages": delivery["fullyDeliveredMessages"],
        "perDeliveryAvgMs": per_delivery["avg"],
        "perDeliveryP50Ms": per_delivery["p50"],
        "perDeliveryP95Ms": per_delivery["p95"],
        "perDeliveryP99Ms": per_delivery["p99"],
        "allClientsAvgMs": all_clients["avg"],
        "allClientsP50Ms": all_clients["p50"],
        "allClientsP95Ms": all_clients["p95"],
        "allClientsP99Ms": all_clients["p99"],
        "persistedCount": persistence["persistedCount"],
        "expectedPersistedCount": persistence["expectedPersistedCount"],
        "persistenceComplete": persistence["persistenceComplete"],
        "persistenceExact": persistence["persistenceExact"],
        "duplicatePersistedRows": persistence["duplicatePersistedRows"],
        "persistenceDrainMs": persistence["persistenceDrainMs"],
        "serverMessageCount": server_metrics.get("messageCount"),
        "handlerTotalP95Ms": nested(server_metrics, "handlerTotal", "p95"),
        "broadcastP95Ms": nested(server_metrics, "broadcast", "p95"),
        "redisCacheP95Ms": nested(server_metrics, "redisCache", "p95"),
        "persistStageP95Ms": nested(server_metrics, "persistStage", "p95"),
        "cleanupDeletedMysqlRows": cleanup.get("deletedMysqlRows"),
        "cleanupDeletedRedisMembers": cleanup.get("deletedRedisMembers"),
    }


def nested(data, key, child):
    value = data.get(key) or {}
    return value.get(child)


def write_results(row, detail):
    results_dir = Path(__file__).resolve().parent / "results"
    results_dir.mkdir(parents=True, exist_ok=True)

    csv_path = resolve_csv_path(results_dir)
    need_header = not csv_path.exists() or csv_path.stat().st_size == 0
    with csv_path.open("a", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=CSV_COLUMNS)
        if need_header:
            writer.writeheader()
        writer.writerow(row)

    json_path = results_dir / (
        f"danmu_benchmark_detail_{row['mode']}_{row['requestedConnections']}_{row['runId']}.json"
    )
    with json_path.open("w", encoding="utf-8") as file:
        json.dump(detail, file, ensure_ascii=False, indent=2)

    print(f"csv={csv_path}")
    print(f"json={json_path}")


def resolve_csv_path(results_dir):
    csv_path = results_dir / "danmu_benchmark_results.csv"
    if not csv_path.exists() or csv_path.stat().st_size == 0:
        return csv_path
    if csv_header_matches(csv_path):
        return csv_path

    v2_path = results_dir / "danmu_benchmark_results_v2.csv"
    if not v2_path.exists() or v2_path.stat().st_size == 0 or csv_header_matches(v2_path):
        return v2_path

    raise RuntimeError("Existing danmu benchmark CSV headers do not match current schema")


def csv_header_matches(path):
    with path.open("r", newline="", encoding="utf-8") as file:
        reader = csv.reader(file)
        try:
            header = next(reader)
        except StopIteration:
            return True
    return header == CSV_COLUMNS


async def run(args):
    base_url = args.base_url.rstrip("/")
    session = requests.Session()
    formal_run_id = uuid.uuid4().hex[:8]
    warmup_run_id = uuid.uuid4().hex[:8]

    warmup = None
    if args.warmup_messages > 0:
        warmup = await run_websocket_phase(args, warmup_run_id, args.warmup_messages, False)
        wait_persistence(session, base_url, args.access_token, args.video_id, warmup_run_id,
                         args.warmup_messages, None, args.persistence_timeout)
        cleanup_run(session, base_url, args.access_token, args.video_id, warmup_run_id)

    server_metrics = {}
    cleanup = {}
    metrics_started = False
    try:
        api_post(session, base_url, "/benchmark/danmu/metrics/start", args.access_token,
                 {"runId": formal_run_id, "mode": args.mode})
        metrics_started = True
        delivery, persistence = await run_formal_phase(args, session, base_url, formal_run_id)
        server_metrics = api_post(session, base_url, "/benchmark/danmu/metrics/stop", args.access_token,
                                  {"runId": formal_run_id})
        metrics_started = False
    finally:
        if metrics_started:
            try:
                server_metrics = api_post(session, base_url, "/benchmark/danmu/metrics/stop", args.access_token,
                                          {"runId": formal_run_id})
            except Exception as exc:
                print(f"WARNING metrics stop failed: {redact(str(exc), args.access_token)}")
        cleanup = cleanup_run(session, base_url, args.access_token, args.video_id, formal_run_id)

    row = flatten_result(args, formal_run_id, delivery, server_metrics, persistence, cleanup)
    detail = {
        "environment": {
            "createdAt": datetime.now(timezone.utc).isoformat(),
            "python": platform.python_version(),
            "platform": platform.platform(),
            "baseUrl": base_url,
            "wsUrl": redact(args.ws_url, args.access_token),
            "percentileMethod": "linear interpolation",
        },
        "scenario": {
            "mode": args.mode,
            "connections": args.connections,
            "messages": args.messages,
            "actualSendDurationMs": row["actualSendDurationMs"],
            "actualMessagesPerSecond": row["actualMessagesPerSecond"],
            "intervalMs": args.interval_ms,
            "videoId": args.video_id,
        },
        "warmup": warmup,
        "connectionMetrics": {
            "requestedConnections": args.connections,
            "connectedConnections": delivery["connectedConnections"],
            "connectionSuccessRate": row["connectionSuccessRate"],
            "scenarioValid": row["scenarioValid"],
            "connectionSetupMs": delivery["connectionSetupMs"],
            "connectionErrors": delivery["connectionErrors"],
        },
        "deliveryMetrics": delivery["perDeliveryLatency"],
        "allClientsMetrics": delivery["allClientsLatency"],
        "serverMetrics": server_metrics,
        "persistenceMetrics": persistence,
        "cleanupResult": cleanup,
        "row": row,
    }

    write_results(row, detail)
    print(json.dumps(row, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description="Benchmark danmu async MQ persistence vs sync DB persistence.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8082")
    parser.add_argument("--ws-url", default="ws://127.0.0.1:8082/ws/danmu")
    parser.add_argument("--access-token", required=True)
    parser.add_argument("--video-id", type=int, required=True)
    parser.add_argument("--mode", choices=["async", "sync"], required=True)
    parser.add_argument("--connections", type=int, default=100)
    parser.add_argument("--messages", type=int, default=100)
    parser.add_argument("--warmup-messages", type=int, default=10)
    parser.add_argument("--interval-ms", type=int, default=20)
    parser.add_argument("--delivery-timeout", type=float, default=10.0)
    parser.add_argument("--persistence-timeout", type=float, default=15.0)
    args = parser.parse_args()

    if args.connections <= 0 or args.messages <= 0 or args.warmup_messages < 0:
        raise SystemExit("connections/messages must be positive and warmup-messages cannot be negative")

    try:
        asyncio.run(run(args))
    except Exception as exc:
        raise SystemExit(redact(str(exc), args.access_token))


if __name__ == "__main__":
    main()

import argparse
import csv
import json
import platform
import statistics
import time
from datetime import datetime, timezone
from pathlib import Path

import requests


CSV_COLUMNS = [
    "mode",
    "round",
    "success",
    "fileSizeBytes",
    "directUrlGenerationMs",
    "transferMs",
    "endToEndMs",
    "throughputMiBPerSec",
    "heapUsedStartMb",
    "heapUsedEndMb",
    "heapPeakMb",
    "heapPeakDeltaMb",
    "processCpuTimeMs",
    "processCpuUtilizationPct",
    "gcCountDelta",
    "gcTimeDeltaMs",
    "appFileIngressBytes",
    "appFileEgressBytes",
    "objectName",
    "failureReason",
]


def api_post(session, base_url, path, token, json_body=None, timeout=30):
    response = session.post(
        f"{base_url}{path}",
        headers={"accessToken": token},
        json=json_body,
        timeout=timeout,
    )
    return parse_api_response(response)


def api_delete(session, base_url, object_name, token, timeout=30):
    response = session.delete(
        f"{base_url}/benchmark/upload/object",
        headers={"accessToken": token},
        params={"objectName": object_name},
        timeout=timeout,
    )
    return parse_api_response(response)


def parse_api_response(response):
    try:
        payload = response.json()
    except ValueError as exc:
        raise RuntimeError(f"HTTP {response.status_code}: non-JSON response") from exc

    if response.status_code >= 400 or payload.get("code") != 200:
        raise RuntimeError(f"HTTP {response.status_code}: {payload.get('msg', payload)}")
    return payload.get("data") or {}


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


def throughput_mib_per_sec(file_size_bytes, transfer_ms):
    if not transfer_ms or transfer_ms <= 0:
        return None
    return file_size_bytes / 1024 / 1024 / (transfer_ms / 1000.0)


def merge_metrics(row, metrics):
    for key in [
        "heapUsedStartMb",
        "heapUsedEndMb",
        "heapPeakMb",
        "heapPeakDeltaMb",
        "processCpuTimeMs",
        "processCpuUtilizationPct",
        "gcCountDelta",
        "gcTimeDeltaMs",
        "appFileIngressBytes",
        "appFileEgressBytes",
    ]:
        row[key] = metrics.get(key)


def stop_metrics(session, base_url, token, session_id, timeout):
    if not session_id:
        return {}
    return api_post(
        session,
        base_url,
        "/benchmark/upload/metrics/stop",
        token,
        {"sessionId": session_id},
        timeout=timeout,
    )


def cleanup_object(session, base_url, token, object_name):
    if not object_name:
        return
    try:
        api_delete(session, base_url, object_name, token)
    except Exception as exc:
        print(f"WARNING cleanup failed for {object_name}: {exc}")


def run_relay(session, base_url, token, file_path, round_number, timeout):
    file_size = file_path.stat().st_size
    session_id = None
    object_name = ""
    row = base_row("relay", round_number, file_size)

    try:
        start_data = api_post(session, base_url, "/benchmark/upload/metrics/start", token, timeout=timeout)
        session_id = start_data["sessionId"]

        headers = {
            "accessToken": token,
            "Content-Type": "application/octet-stream",
            "Content-Length": str(file_size),
        }
        transfer_start = time.perf_counter()
        with file_path.open("rb") as file:
            response = session.put(
                f"{base_url}/benchmark/upload/relay",
                headers=headers,
                data=file,
                timeout=timeout,
            )
        transfer_ms = (time.perf_counter() - transfer_start) * 1000.0
        data = parse_api_response(response)
        object_name = data.get("objectName", "")

        metrics = stop_metrics(session, base_url, token, session_id, timeout)
        session_id = None

        row.update(
            success=True,
            transferMs=transfer_ms,
            endToEndMs=transfer_ms,
            throughputMiBPerSec=throughput_mib_per_sec(file_size, transfer_ms),
            objectName=object_name,
        )
        merge_metrics(row, metrics)
    except Exception as exc:
        row["failureReason"] = str(exc)
        try:
            metrics = stop_metrics(session, base_url, token, session_id, timeout)
            merge_metrics(row, metrics)
        except Exception as stop_exc:
            row["failureReason"] = f"{row['failureReason']}; metrics_stop_failed={stop_exc}"
    finally:
        cleanup_object(session, base_url, token, object_name)

    return row


def run_direct(session, base_url, token, file_path, round_number, timeout):
    file_size = file_path.stat().st_size
    session_id = None
    object_name = ""
    row = base_row("direct", round_number, file_size)

    try:
        start_data = api_post(session, base_url, "/benchmark/upload/metrics/start", token, timeout=timeout)
        session_id = start_data["sessionId"]

        url_start = time.perf_counter()
        direct_data = api_post(session, base_url, "/benchmark/upload/direct-url", token, timeout=timeout)
        direct_url_generation_ms = (time.perf_counter() - url_start) * 1000.0
        object_name = direct_data.get("objectName", "")
        upload_url = direct_data["uploadUrl"]

        transfer_start = time.perf_counter()
        with file_path.open("rb") as file:
            response = requests.put(
                upload_url,
                headers={
                    "Content-Type": "application/octet-stream",
                    "Content-Length": str(file_size),
                },
                data=file,
                timeout=timeout,
            )
        transfer_ms = (time.perf_counter() - transfer_start) * 1000.0
        if response.status_code >= 400:
            raise RuntimeError(f"MinIO PUT failed: HTTP {response.status_code}")

        metrics = stop_metrics(session, base_url, token, session_id, timeout)
        session_id = None

        ingress = metrics.get("appFileIngressBytes")
        egress = metrics.get("appFileEgressBytes")
        if ingress != 0 or egress != 0:
            print(f"WARNING direct mode app file bytes expected 0/0, got {ingress}/{egress}")

        row.update(
            success=True,
            directUrlGenerationMs=direct_url_generation_ms,
            transferMs=transfer_ms,
            endToEndMs=direct_url_generation_ms + transfer_ms,
            throughputMiBPerSec=throughput_mib_per_sec(file_size, transfer_ms),
            objectName=object_name,
        )
        merge_metrics(row, metrics)
    except Exception as exc:
        row["failureReason"] = str(exc)
        try:
            metrics = stop_metrics(session, base_url, token, session_id, timeout)
            merge_metrics(row, metrics)
        except Exception as stop_exc:
            row["failureReason"] = f"{row['failureReason']}; metrics_stop_failed={stop_exc}"
    finally:
        cleanup_object(session, base_url, token, object_name)

    return row


def base_row(mode, round_number, file_size):
    return {
        "mode": mode,
        "round": round_number,
        "success": False,
        "fileSizeBytes": file_size,
        "directUrlGenerationMs": None,
        "transferMs": None,
        "endToEndMs": None,
        "throughputMiBPerSec": None,
        "heapUsedStartMb": None,
        "heapUsedEndMb": None,
        "heapPeakMb": None,
        "heapPeakDeltaMb": None,
        "processCpuTimeMs": None,
        "processCpuUtilizationPct": None,
        "gcCountDelta": None,
        "gcTimeDeltaMs": None,
        "appFileIngressBytes": None,
        "appFileEgressBytes": None,
        "objectName": "",
        "failureReason": "",
    }


def summarize(rows):
    result = {}
    for mode in ["relay", "direct"]:
        mode_rows = [row for row in rows if row["mode"] == mode]
        success_rows = [row for row in mode_rows if row["success"]]
        result[mode] = {
            "rounds": len(mode_rows),
            "successRounds": len(success_rows),
            "transferAvgMs": avg(success_rows, "transferMs"),
            "transferP50Ms": percentile(extract(success_rows, "transferMs"), 50),
            "transferP95Ms": percentile(extract(success_rows, "transferMs"), 95),
            "endToEndAvgMs": avg(success_rows, "endToEndMs"),
            "endToEndP50Ms": percentile(extract(success_rows, "endToEndMs"), 50),
            "endToEndP95Ms": percentile(extract(success_rows, "endToEndMs"), 95),
            "throughputAvgMiBPerSec": avg(success_rows, "throughputMiBPerSec"),
            "heapPeakDeltaAvgMb": avg(success_rows, "heapPeakDeltaMb"),
            "heapPeakDeltaP95Mb": percentile(extract(success_rows, "heapPeakDeltaMb"), 95),
            "processCpuUtilizationAvgPct": avg(success_rows, "processCpuUtilizationPct"),
            "processCpuUtilizationP95Pct": percentile(extract(success_rows, "processCpuUtilizationPct"), 95),
            "gcTimeAvgMs": avg(success_rows, "gcTimeDeltaMs"),
            "appFileIngressAvgBytes": avg(success_rows, "appFileIngressBytes"),
            "appFileEgressAvgBytes": avg(success_rows, "appFileEgressBytes"),
        }
    return result


def extract(rows, key):
    return [row[key] for row in rows if row.get(key) is not None]


def avg(rows, key):
    values = extract(rows, key)
    return statistics.fmean(values) if values else None


def write_csv(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=CSV_COLUMNS)
        writer.writeheader()
        writer.writerows(rows)


def write_json(path, args, file_path, rows, summary):
    detail = {
        "environment": {
            "createdAt": datetime.now(timezone.utc).isoformat(),
            "baseUrl": args.base_url,
            "python": platform.python_version(),
            "platform": platform.platform(),
            "percentileMethod": "linear interpolation",
        },
        "file": {
            "path": str(file_path.resolve()),
            "fileSizeBytes": file_path.stat().st_size,
        },
        "rawRounds": rows,
        "summary": summary,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as file:
        json.dump(detail, file, ensure_ascii=False, indent=2)


def main():
    parser = argparse.ArgumentParser(description="Benchmark Spring relay upload vs MinIO presigned direct upload.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8082")
    parser.add_argument("--access-token", required=True)
    parser.add_argument("--file", required=True)
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--warmup", type=int, default=1)
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()

    if args.rounds <= 0:
        raise SystemExit("--rounds must be greater than 0")
    if args.warmup < 0:
        raise SystemExit("--warmup cannot be negative")

    file_path = Path(args.file)
    if not file_path.exists() or not file_path.is_file():
        raise SystemExit(f"file not found: {file_path}")

    base_url = args.base_url.rstrip("/")
    session = requests.Session()

    for index in range(1, args.warmup + 1):
        print(f"warmup {index}/{args.warmup}: relay")
        run_relay(session, base_url, args.access_token, file_path, index, args.timeout)
        print(f"warmup {index}/{args.warmup}: direct")
        run_direct(session, base_url, args.access_token, file_path, index, args.timeout)

    rows = []
    for index in range(1, args.rounds + 1):
        print(f"round {index}/{args.rounds}: relay")
        relay_row = run_relay(session, base_url, args.access_token, file_path, index, args.timeout)
        rows.append(relay_row)
        print(f"  relay success={relay_row['success']} transferMs={relay_row['transferMs']}")

        print(f"round {index}/{args.rounds}: direct")
        direct_row = run_direct(session, base_url, args.access_token, file_path, index, args.timeout)
        rows.append(direct_row)
        print(f"  direct success={direct_row['success']} transferMs={direct_row['transferMs']}")

    summary = summarize(rows)
    results_dir = Path(__file__).resolve().parent / "results"
    write_csv(results_dir / "upload_benchmark_results.csv", rows)
    write_json(results_dir / "upload_benchmark_detail.json", args, file_path, rows, summary)

    print(f"csv={results_dir / 'upload_benchmark_results.csv'}")
    print(f"json={results_dir / 'upload_benchmark_detail.json'}")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

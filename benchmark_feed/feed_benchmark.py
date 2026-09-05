#!/usr/bin/env python3
import argparse
import csv
import json
import math
import statistics
import time
from pathlib import Path

import pymysql
import redis
import requests


TIMELINE_PREFIX = "moments:timeline:"


def parse_args():
    p = argparse.ArgumentParser(description="XingMang Feed fan-out benchmark")
    p.add_argument("--base-url", default="http://127.0.0.1:8082")
    p.add_argument("--access-token", required=True)
    p.add_argument("--author-id", type=int, required=True)

    p.add_argument("--mysql-host", default="127.0.0.1")
    p.add_argument("--mysql-port", type=int, default=3306)
    p.add_argument("--mysql-user", default="root")
    p.add_argument("--mysql-password", default="")
    p.add_argument("--mysql-db", default="xingmang_db")

    p.add_argument("--redis-host", default="127.0.0.1")
    p.add_argument("--redis-port", type=int, default=6379)
    p.add_argument("--redis-password", default=None)

    p.add_argument("--followers", type=int, required=True,
                   help="Use the first N followers of author_id for this benchmark.")
    p.add_argument("--rounds", type=int, default=20)
    p.add_argument("--warmup", type=int, default=3)
    p.add_argument("--poll-interval-ms", type=int, default=100)
    p.add_argument("--fanout-timeout-s", type=float, default=30.0)
    p.add_argument("--verify-batch-size", type=int, default=1000)
    p.add_argument("--output", default="results/feed_benchmark_results.csv")
    p.add_argument("--detail-output", default="results/feed_benchmark_detail.json")
    return p.parse_args()


def mysql_conn(a):
    return pymysql.connect(
        host=a.mysql_host,
        port=a.mysql_port,
        user=a.mysql_user,
        password=a.mysql_password,
        database=a.mysql_db,
        charset="utf8mb4",
        autocommit=True,
    )


def percentile(values, p):
    if not values:
        return None
    xs = sorted(values)
    if len(xs) == 1:
        return xs[0]
    rank = (len(xs) - 1) * p
    lo = math.floor(rank)
    hi = math.ceil(rank)
    if lo == hi:
        return xs[lo]
    return xs[lo] + (xs[hi] - xs[lo]) * (rank - lo)


def load_follower_ids(conn, author_id, limit):
    with conn.cursor() as cur:
        cur.execute(
            "SELECT user_id FROM t_user_following "
            "WHERE following_id=%s ORDER BY user_id LIMIT %s",
            (author_id, limit),
        )
        rows = cur.fetchall()
    ids = [int(r[0]) for r in rows]
    if len(ids) < limit:
        raise RuntimeError(
            f"author_id={author_id} has only {len(ids)} followers, but --followers={limit}. "
            f"Run prepare_followers.py first or choose a smaller follower count."
        )
    return ids


def latest_moment_id(conn, author_id):
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id FROM t_user_moments WHERE user_id=%s ORDER BY id DESC LIMIT 1",
            (author_id,),
        )
        row = cur.fetchone()
    return int(row[0]) if row else None


def wait_for_new_moment_id(conn, author_id, previous_id, timeout_s=3.0):
    deadline = time.perf_counter() + timeout_s
    while time.perf_counter() < deadline:
        mid = latest_moment_id(conn, author_id)
        if mid is not None and mid != previous_id:
            return mid
        time.sleep(0.005)
    raise TimeoutError("Published request returned but new t_user_moments row was not observed.")


def count_visible(r, follower_ids, moment_id, batch_size):
    visible = 0
    for start in range(0, len(follower_ids), batch_size):
        part = follower_ids[start:start + batch_size]
        pipe = r.pipeline(transaction=False)
        for uid in part:
            pipe.zscore(f"{TIMELINE_PREFIX}{uid}", str(moment_id))
        res = pipe.execute()
        visible += sum(v is not None for v in res)
    return visible


def wait_fanout(r, follower_ids, moment_id, poll_interval_s, timeout_s, batch_size):
    start = time.perf_counter()
    deadline = start + timeout_s
    checks = 0
    last_visible = 0
    while True:
        checks += 1
        last_visible = count_visible(r, follower_ids, moment_id, batch_size)
        if last_visible == len(follower_ids):
            return (time.perf_counter() - start) * 1000.0, last_visible, checks
        if time.perf_counter() >= deadline:
            return None, last_visible, checks
        time.sleep(poll_interval_s)


def publish(session, a):
    url = a.base_url.rstrip("/") + "/user-moments"
    headers = {"accessToken": a.access_token, "Content-Type": "application/json"}
    payload = {"type": "0", "contentId": int(time.time_ns() % 2_000_000_000)}
    t0 = time.perf_counter()
    resp = session.post(url, headers=headers, json=payload, timeout=10)
    elapsed_ms = (time.perf_counter() - t0) * 1000.0
    if resp.status_code // 100 != 2:
        raise RuntimeError(f"publish failed: status={resp.status_code}, body={resp.text[:500]}")
    try:
        body = resp.json()
        if isinstance(body, dict) and body.get("code") not in (None, 0, 200):
            raise RuntimeError(f"publish returned business error: {body}")
    except ValueError:
        pass
    return elapsed_ms


def summarize(rows):
    publish_values = [r["publish_rt_ms"] for r in rows]
    fanout_values = [
        r["fanout_after_response_ms"]
        for r in rows
        if r["fanout_after_response_ms"] is not None
    ]
    return {
        "rounds": len(rows),
        "publish_p50_ms": percentile(publish_values, 0.50),
        "publish_p95_ms": percentile(publish_values, 0.95),
        "publish_p99_ms": percentile(publish_values, 0.99),
        "publish_avg_ms": statistics.mean(publish_values),
        "fanout_p50_ms": percentile(fanout_values, 0.50) if fanout_values else None,
        "fanout_p95_ms": percentile(fanout_values, 0.95) if fanout_values else None,
        "fanout_p99_ms": percentile(fanout_values, 0.99) if fanout_values else None,
        "fanout_avg_ms": statistics.mean(fanout_values) if fanout_values else None,
        "fanout_success_rounds": len(fanout_values),
    }


def main():
    a = parse_args()
    db = mysql_conn(a)
    r = redis.Redis(
        host=a.redis_host,
        port=a.redis_port,
        password=a.redis_password,
        decode_responses=True,
    )
    r.ping()

    follower_ids = load_follower_ids(db, a.author_id, a.followers)
    print(f"[setup] followers selected: {len(follower_ids)}")

    session = requests.Session()

    print(f"[warmup] rounds={a.warmup}")
    for i in range(a.warmup):
        prev = latest_moment_id(db, a.author_id)
        rt = publish(session, a)
        mid = wait_for_new_moment_id(db, a.author_id, prev)
        fanout_ms, visible, _ = wait_fanout(
            r,
            follower_ids,
            mid,
            a.poll_interval_ms / 1000.0,
            a.fanout_timeout_s,
            a.verify_batch_size,
        )
        print(f"  warmup {i + 1}: publish={rt:.2f} ms fanout={fanout_ms} visible={visible}")

    rows = []
    print(f"[benchmark] rounds={a.rounds}, followers={a.followers}")
    for i in range(a.rounds):
        prev = latest_moment_id(db, a.author_id)

        request_start = time.perf_counter()
        publish_rt = publish(session, a)

        moment_id = wait_for_new_moment_id(db, a.author_id, prev)
        fanout_after_response_ms, visible, checks = wait_fanout(
            r,
            follower_ids,
            moment_id,
            a.poll_interval_ms / 1000.0,
            a.fanout_timeout_s,
            a.verify_batch_size,
        )
        total_from_request_ms = (
            (time.perf_counter() - request_start) * 1000.0
            if fanout_after_response_ms is not None else None
        )

        row = {
            "round": i + 1,
            "followers": a.followers,
            "moment_id": moment_id,
            "publish_rt_ms": round(publish_rt, 3),
            "fanout_after_response_ms": (
                round(fanout_after_response_ms, 3)
                if fanout_after_response_ms is not None else None
            ),
            "total_request_to_fanout_ms": (
                round(total_from_request_ms, 3)
                if total_from_request_ms is not None else None
            ),
            "visible_followers": visible,
            "verify_checks": checks,
        }
        rows.append(row)
        fanout_text = (
            "TIMEOUT"
            if fanout_after_response_ms is None
            else f"{fanout_after_response_ms:.2f} ms"
        )
        print(
            f"  {i + 1:02d}/{a.rounds}: publish={publish_rt:.2f} ms, "
            f"fanout_after_response={fanout_text}, visible={visible}/{a.followers}"
        )

    summary = summarize(rows)
    summary.update({
        "followers": a.followers,
        "author_id": a.author_id,
        "base_url": a.base_url,
        "poll_interval_ms": a.poll_interval_ms,
        "fanout_timeout_s": a.fanout_timeout_s,
    })

    out = Path(a.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    exists = out.exists()
    with out.open("a", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=list(summary.keys()))
        if not exists:
            writer.writeheader()
        writer.writerow(summary)

    detail = Path(a.detail_output)
    detail.parent.mkdir(parents=True, exist_ok=True)
    detail.write_text(
        json.dumps({"summary": summary, "rounds": rows}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print("\n[summary]")
    for key, value in summary.items():
        print(f"  {key}: {value}")
    print(f"[saved] summary CSV: {out}")
    print(f"[saved] detail JSON: {detail}")

    db.close()


if __name__ == "__main__":
    main()

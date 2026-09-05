#!/usr/bin/env python3
import argparse
import json
from pathlib import Path

import pymysql
import redis


def parse_args():
    p = argparse.ArgumentParser(description="Cleanup synthetic XingMang Feed benchmark followers.")
    p.add_argument("--manifest", default="results/feed_benchmark_manifest.json")
    p.add_argument("--mysql-host", default="127.0.0.1")
    p.add_argument("--mysql-port", type=int, default=3306)
    p.add_argument("--mysql-user", default="root")
    p.add_argument("--mysql-password", default="")
    p.add_argument("--mysql-db", default="xingmang_db")
    p.add_argument("--redis-host", default="127.0.0.1")
    p.add_argument("--redis-port", type=int, default=6379)
    p.add_argument("--redis-password", default=None)
    p.add_argument("--batch-size", type=int, default=1000)
    return p.parse_args()


def main():
    a = parse_args()
    manifest_path = Path(a.manifest)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    ids = [int(x) for x in manifest["created_user_ids"]]

    conn = pymysql.connect(
        host=a.mysql_host,
        port=a.mysql_port,
        user=a.mysql_user,
        password=a.mysql_password,
        database=a.mysql_db,
        charset="utf8mb4",
        autocommit=False,
    )
    r = redis.Redis(
        host=a.redis_host,
        port=a.redis_port,
        password=a.redis_password,
        decode_responses=True,
    )

    try:
        with conn.cursor() as cur:
            for start in range(0, len(ids), a.batch_size):
                part = ids[start:start + a.batch_size]
                placeholders = ",".join(["%s"] * len(part))

                cur.execute(
                    f"DELETE FROM t_user_following WHERE user_id IN ({placeholders})",
                    part,
                )
                cur.execute(
                    f"DELETE FROM t_user_info WHERE user_id IN ({placeholders})",
                    part,
                )
                cur.execute(
                    f"DELETE FROM t_user WHERE id IN ({placeholders})",
                    part,
                )
                conn.commit()

                pipe = r.pipeline(transaction=False)
                for uid in part:
                    pipe.delete(f"moments:timeline:{uid}")
                pipe.execute()

                print(f"[cleanup] {min(start + a.batch_size, len(ids))}/{len(ids)}")

        print("[done] synthetic followers and their Redis timelines removed.")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()

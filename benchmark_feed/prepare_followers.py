#!/usr/bin/env python3
import argparse
import json
import time
import uuid
from pathlib import Path

import pymysql


BCRYPT_PLACEHOLDER = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5YtU1HnH5v0nY9Jm6H5nR3B8vP0zL8K"


def parse_args():
    p = argparse.ArgumentParser(description="Prepare synthetic followers for XingMang Feed benchmark.")
    p.add_argument("--mysql-host", default="127.0.0.1")
    p.add_argument("--mysql-port", type=int, default=3306)
    p.add_argument("--mysql-user", default="root")
    p.add_argument("--mysql-password", default="")
    p.add_argument("--mysql-db", default="xingmang_db")
    p.add_argument("--author-id", type=int, required=True)
    p.add_argument("--followers", type=int, required=True)
    p.add_argument("--batch-size", type=int, default=1000)
    p.add_argument("--manifest", default="results/feed_benchmark_manifest.json")
    return p.parse_args()


def connect(a):
    return pymysql.connect(
        host=a.mysql_host,
        port=a.mysql_port,
        user=a.mysql_user,
        password=a.mysql_password,
        database=a.mysql_db,
        charset="utf8mb4",
        autocommit=False,
    )


def ensure_author(cur, author_id):
    cur.execute("SELECT id FROM t_user WHERE id=%s", (author_id,))
    if cur.fetchone() is None:
        raise RuntimeError(f"author_id={author_id} does not exist in t_user")


def main():
    a = parse_args()
    run_id = time.strftime("%Y%m%d_%H%M%S") + "_" + uuid.uuid4().hex[:8]
    email_prefix = f"bench_feed_{run_id}_"
    phone_prefix = str(int(time.time()))[-6:]

    conn = connect(a)
    created_user_ids = []
    try:
        with conn.cursor() as cur:
            ensure_author(cur, a.author_id)

            cur.execute("SHOW COLUMNS FROM t_user_following LIKE 'group_id'")
            group_col = cur.fetchone()
            if group_col and str(group_col[2]).upper() == "NO":
                raise RuntimeError(
                    "t_user_following.group_id is NOT NULL in your local schema. "
                    "This script deliberately avoids inventing group data. "
                    "Use existing follower data or a disposable benchmark schema adapted to your constraints."
                )

            for start in range(0, a.followers, a.batch_size):
                end = min(start + a.batch_size, a.followers)
                rows = []
                for i in range(start, end):
                    phone = f"19{phone_prefix}{i:05d}"[-11:]
                    email = f"{email_prefix}{i}@example.local"
                    rows.append((phone, email, BCRYPT_PLACEHOLDER))
                cur.executemany(
                    "INSERT INTO t_user(phone,email,password,create_time,update_time) "
                    "VALUES(%s,%s,%s,NOW(),NOW())",
                    rows,
                )
                conn.commit()
                print(f"[prepare] users inserted: {end}/{a.followers}")

            cur.execute(
                "SELECT id,email FROM t_user WHERE email LIKE %s ORDER BY id",
                (email_prefix + "%",),
            )
            selected = cur.fetchall()
            created_user_ids = [int(row[0]) for row in selected]
            if len(created_user_ids) != a.followers:
                raise RuntimeError(
                    f"Expected {a.followers} synthetic users but found {len(created_user_ids)}"
                )

            info_rows = [
                (uid, f"bench_fan_{idx}", "benchmark", "0")
                for idx, uid in enumerate(created_user_ids)
            ]
            for start in range(0, len(info_rows), a.batch_size):
                part = info_rows[start:start + a.batch_size]
                cur.executemany(
                    "INSERT INTO t_user_info(user_id,nick,sign,gender,create_time,update_time) "
                    "VALUES(%s,%s,%s,%s,NOW(),NOW())",
                    part,
                )
                conn.commit()
                print(
                    f"[prepare] user_info inserted: "
                    f"{min(start + a.batch_size, len(info_rows))}/{len(info_rows)}"
                )

            follow_rows = [(uid, a.author_id, None) for uid in created_user_ids]
            for start in range(0, len(follow_rows), a.batch_size):
                part = follow_rows[start:start + a.batch_size]
                cur.executemany(
                    "INSERT INTO t_user_following(user_id,following_id,group_id,create_time) "
                    "VALUES(%s,%s,%s,NOW())",
                    part,
                )
                conn.commit()
                print(
                    f"[prepare] following relations inserted: "
                    f"{min(start + a.batch_size, len(follow_rows))}/{len(follow_rows)}"
                )

        manifest = {
            "run_id": run_id,
            "author_id": a.author_id,
            "followers": len(created_user_ids),
            "email_prefix": email_prefix,
            "created_user_ids": created_user_ids,
        }
        path = Path(a.manifest)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"[done] manifest: {path}")
        print(f"[done] synthetic followers: {len(created_user_ids)}")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()

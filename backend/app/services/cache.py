import json
import sqlite3
from datetime import datetime, timedelta, timezone
from pathlib import Path


class CompsCache:
    def __init__(self, db_path: Path, ttl_hours: int) -> None:
        self.db_path = db_path
        self.ttl_hours = ttl_hours
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _init_db(self) -> None:
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS comps_cache (
                    keywords TEXT PRIMARY KEY,
                    payload TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """
            )

    def get(self, keywords: str) -> dict | None:
        key = keywords.strip().lower()
        with sqlite3.connect(self.db_path) as conn:
            row = conn.execute(
                "SELECT payload, created_at FROM comps_cache WHERE keywords = ?",
                (key,),
            ).fetchone()

        if not row:
            return None

        payload, created_at = row
        created = datetime.fromisoformat(created_at)
        if datetime.now(timezone.utc) - created > timedelta(hours=self.ttl_hours):
            return None

        return json.loads(payload)

    def set(self, keywords: str, payload: dict) -> None:
        key = keywords.strip().lower()
        now_iso = datetime.now(timezone.utc).isoformat()
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                """
                INSERT INTO comps_cache(keywords, payload, created_at)
                VALUES(?, ?, ?)
                ON CONFLICT(keywords)
                DO UPDATE SET payload = excluded.payload, created_at = excluded.created_at
                """,
                (key, json.dumps(payload), now_iso),
            )
            conn.commit()

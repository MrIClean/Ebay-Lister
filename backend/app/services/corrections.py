import sqlite3
from pathlib import Path


class CorrectionStore:
    def __init__(self, db_path: Path) -> None:
        self.db_path = db_path
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _init_db(self) -> None:
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS corrections (
                    predicted TEXT PRIMARY KEY,
                    corrected TEXT NOT NULL,
                    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """
            )

    def get(self, predicted: str) -> str | None:
        key = predicted.strip().lower()
        with sqlite3.connect(self.db_path) as conn:
            row = conn.execute(
                "SELECT corrected FROM corrections WHERE predicted = ?",
                (key,),
            ).fetchone()
        return row[0] if row else None

    def save(self, predicted: str, corrected: str) -> None:
        key = predicted.strip().lower()
        value = corrected.strip()
        if not key or not value:
            return
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                """
                INSERT INTO corrections(predicted, corrected)
                VALUES(?, ?)
                ON CONFLICT(predicted)
                DO UPDATE SET corrected = excluded.corrected, updated_at = CURRENT_TIMESTAMP
                """,
                (key, value),
            )
            conn.commit()

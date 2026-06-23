from __future__ import annotations

import sqlite3
import threading
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path


TOKYO_TZ = timezone(timedelta(hours=9))


@dataclass
class UsageDecision:
    allowed: bool
    subject_key: str
    period_ym: str
    used_scans: int
    quota_scans: int
    remaining_scans: int
    plan: str
    duplicate_request: bool


class UsageStore:
    def __init__(self, db_path: str, free_quota: int, pro_quota: int) -> None:
        if free_quota <= 0:
            raise ValueError("free_quota must be > 0")
        if pro_quota <= 0:
            raise ValueError("pro_quota must be > 0")

        self.free_quota = free_quota
        self.pro_quota = pro_quota
        self._db_path = str(Path(db_path))
        self._lock = threading.Lock()
        self._conn = sqlite3.connect(self._db_path, check_same_thread=False, isolation_level=None)
        self._conn.row_factory = sqlite3.Row
        self._initialize_schema()

    def _initialize_schema(self) -> None:
        with self._lock:
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS user_entitlements (
                    subject_key TEXT PRIMARY KEY,
                    plan TEXT NOT NULL,
                    pro_expires_at TEXT NULL,
                    updated_at TEXT NOT NULL
                )
                """
            )
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS monthly_scan_usage (
                    subject_key TEXT NOT NULL,
                    period_ym TEXT NOT NULL,
                    used_scans INTEGER NOT NULL,
                    quota_scans INTEGER NOT NULL,
                    plan_snapshot TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY(subject_key, period_ym)
                )
                """
            )
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS processed_scan_requests (
                    subject_key TEXT NOT NULL,
                    request_id TEXT NOT NULL,
                    period_ym TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY(subject_key, request_id)
                )
                """
            )

    def set_plan(self, subject_key: str, plan: str, pro_expires_at: str | None = None) -> None:
        normalized_plan = plan.strip().lower()
        if normalized_plan not in {"free", "pro"}:
            raise ValueError("plan must be one of: free, pro")

        now_iso = self._now_utc().isoformat()
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO user_entitlements(subject_key, plan, pro_expires_at, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(subject_key) DO UPDATE SET
                    plan = excluded.plan,
                    pro_expires_at = excluded.pro_expires_at,
                    updated_at = excluded.updated_at
                """,
                (subject_key, normalized_plan, pro_expires_at, now_iso),
            )

    def consume_scan(self, subject_key: str, request_id: str | None = None) -> UsageDecision:
        now_utc = self._now_utc()
        now_tokyo = now_utc.astimezone(TOKYO_TZ)
        period_ym = f"{now_tokyo.year:04d}-{now_tokyo.month:02d}"
        now_iso = now_utc.isoformat()

        with self._lock:
            self._conn.execute("BEGIN IMMEDIATE")
            try:
                plan = self._resolve_plan_locked(subject_key=subject_key, now_utc=now_utc)
                quota = self.pro_quota if plan == "pro" else self.free_quota

                usage = self._upsert_and_fetch_usage_locked(
                    subject_key=subject_key,
                    period_ym=period_ym,
                    quota=quota,
                    plan=plan,
                    updated_at=now_iso,
                )

                if request_id:
                    duplicate = self._conn.execute(
                        """
                        SELECT 1
                        FROM processed_scan_requests
                        WHERE subject_key = ? AND request_id = ?
                        """,
                        (subject_key, request_id),
                    ).fetchone()
                    if duplicate:
                        self._conn.execute("COMMIT")
                        return self._to_decision(
                            allowed=True,
                            duplicate_request=True,
                            subject_key=subject_key,
                            period_ym=period_ym,
                            used_scans=int(usage["used_scans"]),
                            quota_scans=int(usage["quota_scans"]),
                            plan=str(usage["plan_snapshot"]),
                        )

                used_scans = int(usage["used_scans"])
                quota_scans = int(usage["quota_scans"])
                if used_scans >= quota_scans:
                    self._conn.execute("COMMIT")
                    return self._to_decision(
                        allowed=False,
                        duplicate_request=False,
                        subject_key=subject_key,
                        period_ym=period_ym,
                        used_scans=used_scans,
                        quota_scans=quota_scans,
                        plan=str(usage["plan_snapshot"]),
                    )

                self._conn.execute(
                    """
                    UPDATE monthly_scan_usage
                    SET used_scans = used_scans + 1,
                        updated_at = ?
                    WHERE subject_key = ? AND period_ym = ?
                    """,
                    (now_iso, subject_key, period_ym),
                )

                if request_id:
                    self._conn.execute(
                        """
                        INSERT INTO processed_scan_requests(subject_key, request_id, period_ym, created_at)
                        VALUES (?, ?, ?, ?)
                        """,
                        (subject_key, request_id, period_ym, now_iso),
                    )

                usage_after = self._conn.execute(
                    """
                    SELECT used_scans, quota_scans, plan_snapshot
                    FROM monthly_scan_usage
                    WHERE subject_key = ? AND period_ym = ?
                    """,
                    (subject_key, period_ym),
                ).fetchone()

                self._conn.execute("COMMIT")
                return self._to_decision(
                    allowed=True,
                    duplicate_request=False,
                    subject_key=subject_key,
                    period_ym=period_ym,
                    used_scans=int(usage_after["used_scans"]),
                    quota_scans=int(usage_after["quota_scans"]),
                    plan=str(usage_after["plan_snapshot"]),
                )
            except Exception:
                self._conn.execute("ROLLBACK")
                raise

    def developer_bypass_decision(self, subject_key: str) -> UsageDecision:
        now_tokyo = self._now_utc().astimezone(TOKYO_TZ)
        period_ym = f"{now_tokyo.year:04d}-{now_tokyo.month:02d}"
        return self._to_decision(
            allowed=True,
            duplicate_request=False,
            subject_key=subject_key,
            period_ym=period_ym,
            used_scans=0,
            quota_scans=-1,
            plan="dev_unlimited",
        )
    def _upsert_and_fetch_usage_locked(
        self,
        *,
        subject_key: str,
        period_ym: str,
        quota: int,
        plan: str,
        updated_at: str,
    ) -> sqlite3.Row:
        self._conn.execute(
            """
            INSERT INTO monthly_scan_usage(subject_key, period_ym, used_scans, quota_scans, plan_snapshot, updated_at)
            VALUES (?, ?, 0, ?, ?, ?)
            ON CONFLICT(subject_key, period_ym) DO UPDATE SET
                quota_scans = excluded.quota_scans,
                plan_snapshot = excluded.plan_snapshot,
                updated_at = excluded.updated_at
            """,
            (subject_key, period_ym, quota, plan, updated_at),
        )
        usage = self._conn.execute(
            """
            SELECT used_scans, quota_scans, plan_snapshot
            FROM monthly_scan_usage
            WHERE subject_key = ? AND period_ym = ?
            """,
            (subject_key, period_ym),
        ).fetchone()
        if usage is None:
            raise RuntimeError("monthly_scan_usage row missing after upsert")
        return usage

    def _resolve_plan_locked(self, *, subject_key: str, now_utc: datetime) -> str:
        row = self._conn.execute(
            """
            SELECT plan, pro_expires_at
            FROM user_entitlements
            WHERE subject_key = ?
            """,
            (subject_key,),
        ).fetchone()
        if row is None:
            return "free"

        plan = str(row["plan"]).strip().lower()
        if plan not in {"free", "pro"}:
            return "free"

        if plan == "pro":
            expires_at_raw = row["pro_expires_at"]
            if expires_at_raw:
                try:
                    expires_at = datetime.fromisoformat(str(expires_at_raw))
                    if expires_at.tzinfo is None:
                        expires_at = expires_at.replace(tzinfo=TOKYO_TZ)
                    if expires_at.astimezone(timezone.utc) <= now_utc.astimezone(timezone.utc):
                        plan = "free"
                except ValueError:
                    plan = "free"

        return plan

    @staticmethod
    def _to_decision(
        *,
        allowed: bool,
        duplicate_request: bool,
        subject_key: str,
        period_ym: str,
        used_scans: int,
        quota_scans: int,
        plan: str,
    ) -> UsageDecision:
        remaining_scans = max(0, quota_scans - used_scans)
        return UsageDecision(
            allowed=allowed,
            subject_key=subject_key,
            period_ym=period_ym,
            used_scans=used_scans,
            quota_scans=quota_scans,
            remaining_scans=remaining_scans,
            plan=plan,
            duplicate_request=duplicate_request,
        )

    @staticmethod
    def _now_utc() -> datetime:
        return datetime.now(timezone.utc)

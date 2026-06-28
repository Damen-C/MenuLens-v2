from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any
from urllib.parse import quote

import google.auth
from google.auth.transport.requests import AuthorizedSession


ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
ACTIVE_SUBSCRIPTION_STATES = {
    "SUBSCRIPTION_STATE_ACTIVE",
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
}


class PlayBillingVerificationError(Exception):
    pass


@dataclass(frozen=True)
class PlaySubscriptionVerification:
    product_id: str | None
    subscription_state: str
    active: bool
    expires_at: str | None


class PlayBillingVerifier:
    def __init__(
        self,
        *,
        package_name: str,
        pro_product_ids: set[str],
        session: Any | None = None,
    ) -> None:
        self.package_name = package_name.strip()
        self.pro_product_ids = {value.strip() for value in pro_product_ids if value.strip()}
        if not self.package_name:
            raise PlayBillingVerificationError("PLAY_PACKAGE_NAME is required")
        if not self.pro_product_ids:
            raise PlayBillingVerificationError("PLAY_PRO_PRODUCT_IDS must include at least one product id")
        self._session = session or self._create_authorized_session()

    def verify_subscription(
        self,
        *,
        purchase_token: str,
        product_id: str | None = None,
    ) -> PlaySubscriptionVerification:
        token = purchase_token.strip()
        expected_product_id = product_id.strip() if product_id else None
        if not token:
            raise PlayBillingVerificationError("purchase_token is required")

        url = (
            "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
            f"{quote(self.package_name, safe='')}/purchases/subscriptionsv2/tokens/{quote(token, safe='')}"
        )
        response = self._session.get(url, timeout=20)
        if response.status_code == 404:
            raise PlayBillingVerificationError("purchase token was not found")
        if response.status_code >= 400:
            raise PlayBillingVerificationError(f"Play Billing verification failed: HTTP {response.status_code}")

        body = response.json()
        state = str(body.get("subscriptionState", "SUBSCRIPTION_STATE_UNSPECIFIED"))
        line_item = self._select_line_item(body.get("lineItems", []), expected_product_id)
        resolved_product_id = line_item.get("productId")
        expires_at = line_item.get("expiryTime")
        active = state in ACTIVE_SUBSCRIPTION_STATES and self._expiry_is_in_future(expires_at)
        return PlaySubscriptionVerification(
            product_id=str(resolved_product_id) if resolved_product_id else None,
            subscription_state=state,
            active=active,
            expires_at=str(expires_at) if expires_at else None,
        )

    def _select_line_item(self, line_items: Any, expected_product_id: str | None) -> dict[str, Any]:
        if not isinstance(line_items, list) or not line_items:
            raise PlayBillingVerificationError("subscription response did not include line items")

        for raw_item in line_items:
            if not isinstance(raw_item, dict):
                continue
            product_id = str(raw_item.get("productId", ""))
            if expected_product_id and product_id != expected_product_id:
                continue
            if product_id in self.pro_product_ids:
                return raw_item

        if expected_product_id:
            raise PlayBillingVerificationError("purchase token product_id did not match this app's Pro product")
        raise PlayBillingVerificationError("purchase token did not include a configured Pro product")

    @staticmethod
    def _expiry_is_in_future(expires_at: Any) -> bool:
        if not expires_at:
            return False
        try:
            parsed = datetime.fromisoformat(str(expires_at).replace("Z", "+00:00"))
        except ValueError:
            return False
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone(timezone.utc) > datetime.now(timezone.utc)

    @staticmethod
    def _create_authorized_session() -> AuthorizedSession:
        credentials, _ = google.auth.default(scopes=[ANDROID_PUBLISHER_SCOPE])
        return AuthorizedSession(credentials)

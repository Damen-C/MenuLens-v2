import pytest

from app.play_billing import PlayBillingVerificationError, PlayBillingVerifier


class FakeResponse:
    def __init__(self, status_code, body):
        self.status_code = status_code
        self._body = body

    def json(self):
        return self._body


class FakeSession:
    def __init__(self, response):
        self.response = response
        self.last_url = None
        self.last_timeout = None

    def get(self, url, timeout):
        self.last_url = url
        self.last_timeout = timeout
        return self.response


def verifier_for(body, status_code=200):
    return PlayBillingVerifier(
        package_name="com.menulens.app",
        pro_product_ids={"menulens_pro_monthly"},
        session=FakeSession(FakeResponse(status_code, body)),
    )


def test_active_subscription_verifies_as_pro():
    verifier = verifier_for(
        {
            "subscriptionState": "SUBSCRIPTION_STATE_ACTIVE",
            "lineItems": [
                {
                    "productId": "menulens_pro_monthly",
                    "expiryTime": "2099-01-01T00:00:00Z",
                }
            ],
        }
    )

    result = verifier.verify_subscription(
        purchase_token="token-123",
        product_id="menulens_pro_monthly",
    )

    assert result.active is True
    assert result.product_id == "menulens_pro_monthly"
    assert result.expires_at == "2099-01-01T00:00:00Z"


def test_expired_subscription_does_not_verify_as_active():
    verifier = verifier_for(
        {
            "subscriptionState": "SUBSCRIPTION_STATE_ACTIVE",
            "lineItems": [
                {
                    "productId": "menulens_pro_monthly",
                    "expiryTime": "2000-01-01T00:00:00Z",
                }
            ],
        }
    )

    result = verifier.verify_subscription(purchase_token="token-123")

    assert result.active is False
    assert result.product_id == "menulens_pro_monthly"


def test_wrong_product_is_rejected():
    verifier = verifier_for(
        {
            "subscriptionState": "SUBSCRIPTION_STATE_ACTIVE",
            "lineItems": [
                {
                    "productId": "other_product",
                    "expiryTime": "2099-01-01T00:00:00Z",
                }
            ],
        }
    )

    with pytest.raises(PlayBillingVerificationError, match="configured Pro product"):
        verifier.verify_subscription(purchase_token="token-123")


def test_missing_purchase_token_is_rejected():
    verifier = verifier_for({})

    with pytest.raises(PlayBillingVerificationError, match="purchase_token"):
        verifier.verify_subscription(purchase_token=" ")


def test_play_404_is_rejected_as_not_found():
    verifier = verifier_for({}, status_code=404)

    with pytest.raises(PlayBillingVerificationError, match="not found"):
        verifier.verify_subscription(purchase_token="token-123")

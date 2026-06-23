from evals.scoring import score_example


def test_japanese_spacing_does_not_penalize_match():
    metrics = score_example(
        expected_jp_names=["刺身 定食", "鯛 茶漬"],
        predicted_jp_names=["刺身定食", "鯛茶漬"],
        latency_ms=1.0,
    )

    assert metrics["matched_item_count"] == 2
    assert metrics["missed_expected_jp_names"] == []
    assert metrics["extra_predicted_jp_names"] == []


def test_extra_descriptors_can_match_expected_name():
    metrics = score_example(
        expected_jp_names=["自家焙煎コーヒー"],
        predicted_jp_names=["自家焙煎コーヒー 8種以上~"],
        latency_ms=1.0,
    )

    assert metrics["matched_item_count"] == 1
    assert metrics["matches"][0]["reason"] == "expected_contained_in_prediction"


def test_generic_short_prediction_does_not_match_specific_item():
    metrics = score_example(
        expected_jp_names=["天丼"],
        predicted_jp_names=["丼"],
        latency_ms=1.0,
    )

    assert metrics["matched_item_count"] == 0
    assert metrics["missed_expected_jp_names"] == ["天丼"]
    assert metrics["extra_predicted_jp_names"] == ["丼"]


def test_missing_specific_ingredient_does_not_match_kakiage_item():
    metrics = score_example(
        expected_jp_names=["紅しょうがかきあげ", "桜えびかきあげ"],
        predicted_jp_names=["かきあげ", "えびかきあげ"],
        latency_ms=1.0,
    )

    assert metrics["matched_item_count"] == 0
    assert metrics["missed_expected_jp_names"] == ["紅しょうがかきあげ", "桜えびかきあげ"]
    assert metrics["extra_predicted_jp_names"] == ["かきあげ", "えびかきあげ"]



def test_common_kana_kanji_menu_variant_can_match():
    metrics = score_example(
        expected_jp_names=["\u3055\u3057\u307f\u5b9a\u98df"],
        predicted_jp_names=["\u523a\u8eab\u5b9a\u98df"],
        latency_ms=1.0,
    )

    assert metrics["matched_item_count"] == 1
    assert metrics["missed_expected_jp_names"] == []
    assert metrics["extra_predicted_jp_names"] == []

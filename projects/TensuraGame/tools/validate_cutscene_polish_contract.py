import json
import math
import struct
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1] / "project"
CONTRACT_PATH = PROJECT_ROOT / "data" / "cutscene-polish-acceptance.json"
CAVE_VISUALS_PATH = PROJECT_ROOT / "data" / "cave-polish-visuals.json"


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as stream:
        return json.load(stream)


def require(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def png_size(path: Path) -> tuple[int, int] | None:
    if not path.is_file():
        return None
    with path.open("rb") as stream:
        header = stream.read(24)
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", header[16:24])


def validate_timed_cues(
        sequence: dict,
        source: dict,
        errors: list[str]) -> None:
    cues = source.get("cues", [])
    actual_order = [cue.get("id") for cue in cues]
    expected_order = sequence.get("expectedOrder", [])
    require(
        actual_order == expected_order,
        f"{sequence['sequenceId']}: cue order differs from the acceptance contract",
        errors,
    )

    previous_time = -math.inf
    for cue in cues:
        cue_time = cue.get("atSeconds")
        require(
            isinstance(cue_time, (int, float)) and math.isfinite(cue_time),
            f"{sequence['sequenceId']}:{cue.get('id')}: invalid atSeconds",
            errors,
        )
        if isinstance(cue_time, (int, float)) and math.isfinite(cue_time):
            require(
                cue_time >= previous_time,
                f"{sequence['sequenceId']}:{cue.get('id')}: cues are not ordered",
                errors,
            )
            previous_time = cue_time

    duration = source.get("durationSeconds")
    require(
        isinstance(duration, (int, float))
        and math.isfinite(duration)
        and duration >= previous_time,
        f"{sequence['sequenceId']}: duration ends before its final cue",
        errors,
    )


def validate_narrative_beats(
        sequence: dict,
        source: dict,
        errors: list[str]) -> None:
    beats = source.get("beats", [])
    actual_order = [beat.get("id") for beat in beats]
    expected_order = sequence.get("expectedOrder", [])
    require(
        actual_order == expected_order,
        f"{sequence['sequenceId']}: beat order differs from the acceptance contract",
        errors,
    )
    by_id = {beat.get("id"): beat for beat in beats}

    for expected in sequence.get("presentation", []):
        beat_id = expected.get("id")
        beat = by_id.get(beat_id)
        require(
            beat is not None,
            f"{sequence['sequenceId']}:{beat_id}: beat is missing",
            errors,
        )
        if beat is None:
            continue

        for key in expected.get("requiredDataKeys", []):
            value = beat.get(key)
            require(
                value is not None and value != [] and value != "",
                f"{sequence['sequenceId']}:{beat_id}: required {key} is missing",
                errors,
            )

        expected_duration = expected.get("authoredAutoDurationSeconds")
        if expected_duration is not None:
            actual_duration = beat.get("autoDurationSeconds")
            require(
                isinstance(actual_duration, (int, float))
                and math.isclose(
                    actual_duration,
                    expected_duration,
                    rel_tol=0,
                    abs_tol=0.0001,
                ),
                (
                    f"{sequence['sequenceId']}:{beat_id}: "
                    f"expected auto duration {expected_duration}, "
                    f"found {actual_duration}"
                ),
                errors,
            )

        if expected.get("declaredSilence"):
            require(
                beat.get("lines", []) == [],
                f"{sequence['sequenceId']}:{beat_id}: declared silence has dialogue",
                errors,
            )
            require(
                beat.get("audioCues", []) == [],
                f"{sequence['sequenceId']}:{beat_id}: declared silence has audio",
                errors,
            )


def validate_contract() -> list[str]:
    errors: list[str] = []
    contract = load_json(CONTRACT_PATH)

    require(contract.get("version") == 1, "unsupported contract version", errors)
    require(
        contract.get("status") == "runtime_qa_authorized",
        "contract must be runtime_qa_authorized after task 21",
        errors,
    )
    require(
        contract.get("runtimePrerequisite")
        == "tools/run-editor-saves-isolados.cmd",
        "isolated runtime prerequisite is missing",
        errors,
    )

    sequence_ids: set[str] = set()
    for sequence in contract.get("sequences", []):
        sequence_id = sequence.get("sequenceId")
        require(
            isinstance(sequence_id, str) and bool(sequence_id.strip()),
            "sequence without a valid sequenceId",
            errors,
        )
        require(
            sequence_id not in sequence_ids,
            f"duplicate sequence contract: {sequence_id}",
            errors,
        )
        sequence_ids.add(sequence_id)

        source_path = PROJECT_ROOT / sequence.get("source", "")
        adapter_path = PROJECT_ROOT / sequence.get("adapter", "").split("#", 1)[0]
        require(source_path.is_file(), f"{sequence_id}: source file is missing", errors)
        require(adapter_path.is_file(), f"{sequence_id}: adapter file is missing", errors)
        if not source_path.is_file():
            continue

        source = load_json(source_path)
        require(
            source.get("sequenceId") == sequence_id,
            f"{sequence_id}: source sequenceId does not match",
            errors,
        )
        require(
            source.get("finalEvent"),
            f"{sequence_id}: source finalEvent is missing",
            errors,
        )
        require(
            isinstance(source.get("skippableAfterSeconds"), (int, float)),
            f"{sequence_id}: source skip threshold is missing",
            errors,
        )

        presentation_ids = [
            item.get("id") for item in sequence.get("presentation", [])
        ]
        require(
            presentation_ids == sequence.get("expectedOrder", []),
            f"{sequence_id}: presentation entries do not cover the expected order",
            errors,
        )
        for item in sequence.get("presentation", []):
            require(
                bool(item.get("intent")),
                f"{sequence_id}:{item.get('id')}: authored intent is missing",
                errors,
            )

        if sequence.get("format") == "timed_cues":
            validate_timed_cues(sequence, source, errors)
        elif sequence.get("format") == "narrative_beats":
            validate_narrative_beats(sequence, source, errors)
        else:
            errors.append(f"{sequence_id}: unsupported sequence format")

    require(
        sequence_ids == set(contract.get("scope", [])),
        "scope and sequence contracts differ",
        errors,
    )
    return errors


def validate_awakening_visual_contract(errors: list[str]) -> None:
    visuals = load_json(CAVE_VISUALS_PATH)
    awakening = visuals.get("awakeningCutscenePolish", {})
    require(
        awakening.get("status") == "runtime_accepted_2026_07_29",
        "awakening visual contract does not retain the accepted task 22 state",
        errors,
    )
    for key in ("sequence", "acceptance", "storyboardDocumentationOnly"):
        relative_path = awakening.get(key, "")
        require(
            isinstance(relative_path, str)
            and (PROJECT_ROOT / relative_path).is_file(),
            f"awakening visual contract: missing {key}",
            errors,
        )

    sequence = load_json(PROJECT_ROOT / awakening.get("sequence", ""))
    authored_cues = [cue.get("id") for cue in sequence.get("cues", [])]
    visual_cues = [
        cue.get("cue") for cue in awakening.get("cuePresentation", [])
    ]
    require(
        visual_cues == authored_cues,
        "awakening visual cues differ from the authored sequence order",
        errors,
    )

    expected_objects = {
        "CaveBackdrop",
        "CaveAwakeningEnvironmentOverlayV2",
        "CaveMagiculeMotesAmbient",
        "CaveAwakeningRuneRing",
    }
    actual_objects = {
        item.get("name") for item in awakening.get("liveObjects", {}).values()
    }
    require(
        actual_objects == expected_objects,
        "awakening visual contract does not cover the four live cave objects",
        errors,
    )

    for audit in awakening.get("pixelScaleAudit", []):
        asset_path = PROJECT_ROOT / audit.get("asset", "")
        actual_size = png_size(asset_path)
        declared_source = audit.get("sourcePixels", {})
        expected_size = (
            declared_source.get("width"),
            declared_source.get("height"),
        )
        require(
            actual_size == expected_size,
            f"{audit.get('asset')}: PNG dimensions differ from pixel scale audit",
            errors,
        )
        required = audit.get("requiredRuntimePixels")
        if required is not None and actual_size is not None:
            width = required.get("width")
            height = required.get("height")
            require(
                isinstance(width, int)
                and isinstance(height, int)
                and width > 0
                and height > 0
                and (
                    (width % actual_size[0] == 0 and height % actual_size[1] == 0)
                    or (
                        actual_size[0] % width == 0
                        and actual_size[1] % height == 0
                    )
                ),
                f"{audit.get('asset')}: required runtime size is not integer-scale",
                errors,
            )


def main() -> None:
    errors = validate_contract()
    validate_awakening_visual_contract(errors)
    if errors:
        print("Cutscene polish contract: FAILED")
        for error in errors:
            print(f"- {error}")
        raise SystemExit(1)
    print("Cutscene polish contract: OK")
    print("Sequences: 5")
    print("Awakening visual contract: OK")
    print("Status: runtime_qa_authorized (isolated launcher required)")


if __name__ == "__main__":
    main()

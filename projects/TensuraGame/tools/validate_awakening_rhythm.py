"""Confere o ritmo autorado de cave_awakening contra os limites do contrato.

Complementa tools/validate_cutscene_polish_contract.py, que valida ordem e duracao mas
nao os limites de tempo. Aqui os numeros conferidos sao os do arquivo em disco, nao
constantes de teste.

Uso: python tools/validate_awakening_rhythm.py
"""

import json
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1] / "project"
CONTRACT = PROJECT / "data" / "cutscene-polish-acceptance.json"
SOURCE = PROJECT / "data" / "cutscene-awakening.json"


def load(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as stream:
        return json.load(stream)


def main() -> int:
    contract = load(CONTRACT)
    source = load(SOURCE)
    sequence = next(
        item for item in contract["sequences"] if item["sequenceId"] == "cave_awakening"
    )
    thresholds = contract["thresholds"]
    presentation = {item["id"]: item for item in sequence["presentation"]}
    times = {cue["id"]: float(cue["atSeconds"]) for cue in source["cues"]}
    errors: list[str] = []

    order = [cue["id"] for cue in source["cues"]]
    if order != sequence["expectedOrder"]:
        errors.append(f"ordem divergente: {order} != {sequence['expectedOrder']}")

    gate = source.get("rhythmContract", {}).get("manualGateCue")
    declared_gate = next(
        (item["id"] for item in sequence["presentation"] if item.get("manualGate")), None
    )
    if gate != declared_gate:
        errors.append(f"portao manual divergente: {gate} != {declared_gate}")

    limit = float(
        presentation["veldora_foreshadow"].get(
            "maximumSecondsAfterManualDismiss",
            thresholds["maximumManualDismissToNextFeedbackSeconds"],
        )
    )
    gap = times["veldora_foreshadow"] - times[gate]
    if not 0 < gap <= limit:
        errors.append(
            f"dispensa -> veldora_foreshadow = {gap:.3f}s, precisa ser >0 e <= {limit}s"
        )

    control_limit = float(thresholds["maximumFinalFeedbackToControlSeconds"])
    to_control = times["control_handoff"] - times["veldora_foreshadow"]
    if to_control > control_limit:
        errors.append(
            f"feedback final -> controle = {to_control:.3f}s, limite {control_limit}s"
        )

    tail_limit = float(
        presentation["control_handoff"].get("maximumTailToControlSeconds", control_limit)
    )
    tail = float(source["durationSeconds"]) - times["control_handoff"]
    if tail > tail_limit:
        errors.append(f"rabo apos o handoff = {tail:.3f}s, limite {tail_limit}s")
    if tail < 0:
        errors.append("duracao termina antes do ultimo cue")

    print(f"gate {gate} @ {times[gate]:.2f}s")
    print(f"dispensa -> proximo feedback : {gap:.3f}s (limite {limit}s)")
    print(f"feedback final -> controle   : {to_control:.3f}s (limite {control_limit}s)")
    print(f"rabo apos o handoff          : {tail:.3f}s (limite {tail_limit}s)")

    if errors:
        print("\nFALHOU:")
        for error in errors:
            print(" -", error)
        return 1
    print("\nOK: cave_awakening respeita os limites do contrato.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

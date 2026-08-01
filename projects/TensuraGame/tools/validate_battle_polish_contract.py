import json
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1] / "project"
BATTLE_PATH = PROJECT_ROOT / "data" / "battle-dire-wolf-leader.json"
VISUALS_PATH = PROJECT_ROOT / "data" / "battle-dire-wolf-leader-visuals.json"
COMMANDS = (
    "ANALYZE",
    "WATER_BLADE",
    "DEFEND",
    "GOBLIN_SUPPORT",
    "PREDATOR",
    "NEGOTIATE",
)
MINIMUM_ACTION_SECONDS = 0.6


def require(condition, message):
    if not condition:
        raise AssertionError(message)


battle = json.loads(BATTLE_PATH.read_text(encoding="utf-8"))
visuals = json.loads(VISUALS_PATH.read_text(encoding="utf-8"))

require(battle["battleId"] == visuals["battleId"], "battle and visual manifests target different battles")

authored_commands = tuple(item["command"] for item in battle["commands"])
visual_commands = tuple(item["command"] for item in visuals["commands"])
require(authored_commands == COMMANDS, "battle command order differs from the six-command contract")
require(visual_commands == COMMANDS, "visual command order differs from battle data")

durations = battle["actionSeconds"]
require(durations["minimum"] >= MINIMUM_ACTION_SECONDS, "declared minimum action duration is below the legacy floor")
require(set(durations) == {"minimum", *COMMANDS}, "actionSeconds must cover exactly the six commands")
for command in COMMANDS:
    require(
        durations[command] >= durations["minimum"],
        f"{command} action duration is below the declared minimum",
    )

modes = battle["timingModes"]
require(set(modes) == {"strategic", "wide", "story"}, "timingModes must contain strategic, wide and story")
require(modes["strategic"]["scale"] == 1.0, "strategic timing must preserve the standard window")
require(modes["wide"]["scale"] > 1.0, "wide timing must be more permissive than strategic")
require(modes["story"]["scale"] == 0, "story scale 0 is the adapter sentinel for ReactionWindow.storyMode()")
require(battle["timingMode"] in modes, "selected timingMode is not declared")

signals = []
for item in visuals["commands"]:
    signal = item["actionData"]
    require(signal == f"signal:TENSURA_BATTLE_COMMAND_{item['command']}", f"invalid actionData for {item['command']}")
    require((PROJECT_ROOT / item["icon"]).is_file(), f"missing command icon for {item['command']}")
    signals.append(signal)
require(len(set(signals)) == len(COMMANDS), "battle command signals must be unique")

require(set(visuals["actionPresentation"]) == set(COMMANDS), "actionPresentation must cover all commands")
require(
    set(visuals["reactionGrades"]) == {"PERFECT", "GOOD", "EARLY", "LATE", "NONE"},
    "visual contract must cover all five reaction grades",
)
for grade, presentation in visuals["reactionGrades"].items():
    require(
        all(presentation.get(channel) for channel in ("label", "color", "shape", "motion")),
        f"{grade} must use word, color, shape and motion",
    )

print("OK: duel polish contract covers 6 commands, 3 timing modes and 5 accessible reaction grades.")

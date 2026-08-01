import json
from pathlib import Path
from PIL import Image


PROJECT_ROOT = Path(__file__).resolve().parents[1]
CONTRACT_PATH = PROJECT_ROOT / "project" / "data" / "cave-map-expansion-contract.json"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
require(contract["status"] == "prepared_not_live", "expansion contract must remain non-live before task #25")
require(contract["coordinateConvention"]["cellSize"] == 32, "cell size must remain 32")

seen_areas = set()
for area in contract["areas"]:
    area_id = area["id"]
    require(area_id not in seen_areas, f"duplicate area id: {area_id}")
    seen_areas.add(area_id)

    grid = area["targetGrid"]
    require(grid["width"] == grid["cols"] * 32, f"{area_id}: width/grid mismatch")
    require(grid["height"] == grid["rows"] * 32, f"{area_id}: height/grid mismatch")

    background_path = PROJECT_ROOT / "project" / area["background"]
    require(background_path.is_file(), f"{area_id}: missing background {background_path}")
    with Image.open(background_path) as image:
        require(image.size == (grid["width"], grid["height"]), f"{area_id}: PNG dimensions do not match target grid")
        require(image.mode == "RGBA", f"{area_id}: expanded background must be RGBA")

    translation = area["legacyTranslation"]
    anchor_ids = set()
    for anchor in area["migratedAnchors"]:
        anchor_id = anchor["id"]
        require(anchor_id not in anchor_ids, f"{area_id}: duplicate migrated anchor {anchor_id}")
        anchor_ids.add(anchor_id)
        require(anchor["to"]["x"] == anchor["from"]["x"] + translation["x"], f"{area_id}/{anchor_id}: x translation mismatch")
        require(anchor["to"]["y"] == anchor["from"]["y"] + translation["y"], f"{area_id}/{anchor_id}: y translation mismatch")
        require(0 <= anchor["to"]["x"] < grid["width"], f"{area_id}/{anchor_id}: migrated x outside target")
        require(0 <= anchor["to"]["y"] < grid["height"], f"{area_id}/{anchor_id}: migrated y outside target")

    landmark_ids = set()
    for landmark in area["authoredLandmarks"]:
        landmark_id = landmark["id"]
        require(landmark_id not in landmark_ids, f"{area_id}: duplicate landmark {landmark_id}")
        landmark_ids.add(landmark_id)
        require(0 <= landmark["x"] < grid["width"], f"{area_id}/{landmark_id}: x outside target")
        require(0 <= landmark["y"] < grid["height"], f"{area_id}/{landmark_id}: y outside target")

require(seen_areas == {"cave_awakening", "cave_gallery"}, "contract must cover exactly the two current cave areas")
require(contract["activationGate"]["task"] == 25, "activation gate must remain task #25")
print("OK: cave expansion contract covers 2 non-live areas, PNG dimensions and legacy translations.")

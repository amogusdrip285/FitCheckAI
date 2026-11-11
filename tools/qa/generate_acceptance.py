"""Generate the P0 acceptance index from authoritative cards, never mark unrun cases PASS."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/qa/p0-case-index.md"

def main():
    rows = []
    for track in "ABCD":
        text = (ROOT / f"docs/execution/tracks/{track}.md").read_text(encoding="utf-8")
        cards = re.split(r"(?m)^## (?=[A-Z]+-\d+ — )", text)[1:]
        for card in cards:
            title = card.splitlines()[0]
            task = title.split(" — ")[0]
            fields = dict(re.findall(r"(?m)^\| (\d+\. [^|]+) \| (.+?) \|$", card))
            if not fields.get("5. Priority / Scope / Milestone", "").startswith("P0"):
                continue
            acceptance = fields["10. Acceptance criteria"]
            testing = fields["11. Testing"]
            source = f"../execution/tracks/{track}.md#{task.lower()}"
            rows.append(f"| P0-{task} | {track} | [{task}]({source}) | {acceptance} | {testing} | NOT RUN at integration gate |")
    assert len(rows) == 46, f"Card count changed; review coverage before updating: {len(rows)}"
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("""# P0 acceptance case index

Generated from all 46 P0 cards; each row preserves the complete acceptance and required testing. Source cards remain authoritative.
This index maps owner/case/criteria; it does not certify external tracks. Record individual steps and expected/actual for every clause in the evidence template.
Track A's local execution is recorded separately in [track-a-acceptance.md](track-a-acceptance.md); consumers still review integration gates.

| Case ID | Owner | Card | Acceptance to demonstrate | Required procedure/environment | Integration result |
|---|---|---|---|---|---|
""" + "\n".join(rows) + "\n", encoding="utf-8")
    print(f"Indexed {len(rows)} P0 cards; all remain NOT RUN at the cross-track integration gate.")

if __name__ == "__main__":
    main()

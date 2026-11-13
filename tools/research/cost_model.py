"""Illustrative sensitivity only. No actual pricing, users or revenue are claimed."""
import argparse
import json

def estimate(users, recognition, recommendation, tryon, recognition_cost, recommendation_cost, tryon_cost, retry_probability):
    values = (users, recognition, recommendation, tryon, recognition_cost, recommendation_cost, tryon_cost)
    if any(v < 0 for v in values) or not 0 <= retry_probability <= 1:
        raise ValueError("Nonnegative inputs and retry_probability in [0,1] required")
    # At most one extra attempt per original request in this scenario, not unlimited retries.
    per_user = (recognition * recognition_cost + recommendation * recommendation_cost + tryon * tryon_cost) * (1 + retry_probability)
    return {"perUserVariableCost": round(per_user, 6), "totalVariableCost": round(users * per_user, 6)}

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inputs", required=True, help="JSON with users, counts, per-request costs and retry_probability; one consistent currency")
    args = parser.parse_args()
    with open(args.inputs, encoding="utf-8") as file:
        inputs = json.load(file)
    for multiplier in (.5, 1, 2):
        scenario = dict(inputs)
        for key in ("recognition", "recommendation", "tryon"):
            scenario[key] *= multiplier
        print(json.dumps({"usageMultiplier": multiplier, "assumptionsUnverified": True, **estimate(**scenario)}))

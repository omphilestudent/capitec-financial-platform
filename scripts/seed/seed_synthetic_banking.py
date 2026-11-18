import json, random, sys, urllib.request, urllib.error
from datetime import datetime, timedelta

GATEWAY = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8000/api/v1"
TXN_PER_CUSTOMER = 110
TAG = "SEED "

def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(GATEWAY + path, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"message": raw[:200].decode(errors="replace")}

TEMPLATES = [
    ("Synthetic salary deposit", "CREDIT", 9000, 38000),
    ("Synthetic EFT transfer in", "CREDIT", 500, 8000),
    ("Synthetic card purchase", "DEBIT", 80, 2500),
    ("Synthetic grocery purchase", "DEBIT", 150, 1800),
    ("Synthetic fuel purchase", "DEBIT", 400, 1200),
    ("Synthetic utilities payment", "DEBIT", 300, 2400),
    ("Synthetic ATM withdrawal", "DEBIT", 200, 2000),
    ("Synthetic loan repayment", "DEBIT", 2500, 7000),
]

def cents(rng, lo, hi):
    return f"{rng.randint(lo * 100, hi * 100) / 100:.2f}"

def main():
    now = datetime.now().replace(microsecond=0)
    status, customers = call("GET", "/customers")
    assert status == 200, customers
    summary = {"customers": len(customers), "accounts_created": 0, "transactions_created": 0,
               "transactions_skipped": 0, "failed": 0, "posted": 0}
    for ci, customer in enumerate(customers):
        rng = random.Random(f"seed-{customer['id']}")
        status, accounts = call("GET", f"/accounts/customer/{customer['id']}")
        if not accounts:
            status, created = call("POST", "/accounts", {"customerId": customer["id"], "accountType": "SAVINGS"})
            if status == 201:
                accounts = [created]
                summary["accounts_created"] += 1
            else:
                summary["failed"] += 1
                continue
        for account in accounts:
            status, existing = call("GET", f"/transactions/account/{account['id']}")
            if status == 200 and any(t["description"] and t["description"].startswith(TAG) for t in existing):
                summary["transactions_skipped"] += sum(1 for t in existing if t["description"] and t["description"].startswith(TAG))
                continue
            opening = rng.randint(15, 90) * 1000
            call("POST", "/transactions/funding", {
                "accountId": account["id"], "entryKind": "OPENING_BALANCE", "amount": f"{opening}.00",
                "currency": "ZAR", "idempotencyKey": f"seed-open-{account['id']}",
                "description": TAG + "opening balance"})
        plan = []
        for i in range(TXN_PER_CUSTOMER - 8):
            name, direction, lo, hi = rng.choice(TEMPLATES)
            days_back = rng.uniform(0, 15 * 30)
            when = now - timedelta(days=days_back)
            plan.append((when, name, direction, cents(rng, lo, hi)))
        for k in range(2):
            when = now - timedelta(days=rng.uniform(5, 15 * 30))
            plan.append((when, "SEED high-value transfer review", "DEBIT", "60000.00"))
        burst = now - timedelta(days=rng.uniform(20, 400))
        for k in range(6):
            plan.append((burst + timedelta(seconds=40 * k), "SEED rapid card purchase", "DEBIT", "45.00"))
        plan.sort(key=lambda p: p[0])
        for idx, (when, name, direction, amount) in enumerate(plan):
            account = accounts[idx % len(accounts)]
            if when > now:
                when = now - timedelta(minutes=1)
            body = {"accountId": account["id"], "type": direction, "amount": amount, "currency": "ZAR",
                    "description": TAG + name, "syntheticSeed": True,
                    "occurredAt": when.strftime("%Y-%m-%dT%H:%M:%S")}
            status, tx = call("POST", "/transactions", body)
            if status == 201:
                summary["transactions_created"] += 1
                if direction == "CREDIT" or amount in ("45.00",):
                    pst, _ = call("POST", f"/transactions/{tx['id']}/post")
                    if pst == 200:
                        summary["posted"] += 1
            else:
                summary["failed"] += 1
        print(f"customer {ci + 1}/{len(customers)} done", flush=True)
    print(json.dumps(summary, indent=2))

if __name__ == "__main__":
    main()

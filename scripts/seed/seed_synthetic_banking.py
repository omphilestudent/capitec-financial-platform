#!/usr/bin/env python3
"""Idempotent synthetic banking seed for the Capitec financial platform.

Runs as the "seeder" one-shot container in docker-compose.yml (python:3.12-alpine, standard
library only). It talks to the platform only through the API gateway, so it exercises the same
paths the frontend uses. It can also run on the host:

    GATEWAY_URL=http://localhost:8000/api/v1 STATEMENT_JWT_SECRET=... \
        python3 scripts/seed/seed_synthetic_banking.py

What it ensures exist (all data is synthetic; e-mail addresses use the reserved example.com domain):

  * SEED_CUSTOMER_COUNT customers (default 60, never fewer than 50); every tenth is a BUSINESS client
  * a SAVINGS account for each customer, plus a CHEQUE account for two thirds of them
  * an opening balance on each account (funding endpoint)
  * about 110 transactions per customer over the last ~15 months (salary, card, grocery, fuel,
    utilities, ATM, loan repayments). Credits are posted, so balances move.
  * fraud triggers per customer: two high-value debits and a burst of six small card purchases.
    fraud-detection raises alerts over Kafka and case-service opens cases from them.
  * one MONTHLY statement per customer for the previous calendar month (issued with a STAFF token)

Idempotency: every seeded transaction description starts with "SEED <ref> ", where the ref is
stable (for example "C007-R012"). A run reads the refs already present on each account and only
creates the missing ones, so repeated runs and runs after a partial failure never duplicate data.
Customers and accounts are matched by e-mail and account type. Nothing existing is modified.

Exit status: 0 when the data set is complete and every check passed; 1 otherwise.
"""

import base64
import hashlib
import hmac
import json
import socket
import os
import random
import sys
import time
import uuid
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone

GATEWAY = os.environ.get("GATEWAY_URL", "http://localhost:8000/api/v1").rstrip("/")
SECRET = os.environ.get("STATEMENT_JWT_SECRET", "")
ISSUER = os.environ.get("STATEMENT_JWT_ISSUER", "capitec-fin-platform")
CUSTOMER_COUNT = max(50, int(os.environ.get("SEED_CUSTOMER_COUNT", "60")))
FRAUD_WAIT_SECONDS = int(os.environ.get("SEED_FRAUD_WAIT_SECONDS", "180"))
ROUTINE_TXN_PER_CUSTOMER = 102
WORKERS = 6
EMAIL_DOMAIN = "example.com"
CURRENCY = "ZAR"

FIRST_NAMES = ["Thandi", "Sipho", "Ayanda", "Pieter", "Naledi", "Johan", "Lerato", "Mpho",
               "Zanele", "Willem", "Kagiso", "Annelie", "Themba", "Corne", "Nomsa", "Ruan",
               "Palesa", "Hendrik", "Ntombi", "Jaco"]
LAST_NAMES = ["Dlamini", "Naidoo", "van der Merwe", "Mokoena", "Botha", "Khumalo", "Pillay",
              "Nel", "Ndlovu", "Smit", "Molefe", "Jacobs", "Maseko", "Coetzee", "Sithole",
              "Le Roux", "Mahlangu", "Fourie", "Radebe", "Erasmus"]

# (description, direction, min rand, max rand)
ROUTINE_TEMPLATES = [
    ("Salary deposit", "CREDIT", 9000, 38000),
    ("EFT transfer in", "CREDIT", 500, 8000),
    ("Card purchase", "DEBIT", 80, 2500),
    ("Grocery purchase", "DEBIT", 150, 1800),
    ("Fuel purchase", "DEBIT", 400, 1200),
    ("Utilities payment", "DEBIT", 300, 2400),
    ("ATM withdrawal", "DEBIT", 200, 2000),
    ("Loan repayment", "DEBIT", 2500, 7000),
]


class SeedError(RuntimeError):
    pass


# --------------------------------------------------------------------------- HTTP helpers
def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def staff_token(ttl_minutes: int = 120) -> str:
    """HS256 token with the STAFF role, signed with STATEMENT_JWT_SECRET (same scheme as scripts/security)."""
    now = int(time.time())
    header = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    payload = b64url(json.dumps({
        "sub": "seed-operator", "roles": ["STAFF"], "iss": ISSUER,
        "iat": now, "exp": now + ttl_minutes * 60, "jti": str(uuid.uuid4()),
    }, separators=(",", ":")).encode())
    signature = hmac.new(SECRET.encode("utf-8"), f"{header}.{payload}".encode("ascii"), hashlib.sha256).digest()
    return f"{header}.{payload}.{b64url(signature)}"


def call(method, path, body=None, token=None):
    """Returns (status, parsed_json_or_None). Retries GET requests on 5xx and connection errors only;
    writes are never retried automatically, because a retry could duplicate a transaction. A failed
    write is counted, and the next run creates it, since every write is keyed by its reference."""
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    attempts = 4 if method == "GET" else 1
    last_error = None
    # A name-resolution failure (EAI_AGAIN) means the request never left this process, so retrying it
    # is safe for writes too. It is retried separately from ordinary connection errors.
    dns_failures = 0
    attempt = 0
    while attempt < attempts:
        request = urllib.request.Request(GATEWAY + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                raw = response.read()
                return response.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as error:
            raw = error.read()
            try:
                parsed = json.loads(raw) if raw else None
            except ValueError:
                parsed = {"message": raw[:300].decode("utf-8", "replace")}
            if error.code >= 500 and attempt < attempts - 1:
                attempt += 1
                time.sleep(1 + attempt)
                continue
            return error.code, parsed
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            last_error = error
            if isinstance(getattr(error, "reason", None), socket.gaierror) and dns_failures < 6:
                dns_failures += 1
                time.sleep(min(2 ** dns_failures, 20))
                continue
            attempt += 1
            time.sleep(1 + attempt)
        attempt += 1
    raise SeedError(f"{method} {path} unreachable: {last_error}")


def items(payload):
    """Accepts a bare list or a Spring page ({content: [...]})."""
    if payload is None:
        return []
    if isinstance(payload, dict) and isinstance(payload.get("content"), list):
        return payload["content"]
    return payload if isinstance(payload, list) else []


def total(payload):
    if isinstance(payload, dict) and "totalElements" in payload:
        return int(payload["totalElements"])
    return len(items(payload))


# --------------------------------------------------------------------------- synthetic data
def customer_spec(index: int) -> dict:
    first = FIRST_NAMES[index % len(FIRST_NAMES)]
    last = LAST_NAMES[(index * 7 + index // len(LAST_NAMES)) % len(LAST_NAMES)]
    if index % 10 == 9:
        return {
            "firstName": f"{last.split()[-1]} Logistics {index:03d}"[:100],
            "lastName": "Pty Ltd",
            "email": f"accounts.{index:03d}@{EMAIL_DOMAIN}",
            "phoneNumber": f"+2711{index:07d}",
            "clientType": "BUSINESS",
        }
    slug = last.lower().replace(" ", "")
    return {
        "firstName": first,
        "lastName": last,
        "email": f"{first.lower()}.{slug}.{index:03d}@{EMAIL_DOMAIN}",
        "phoneNumber": f"+2782{index:07d}",
        "clientType": "INDIVIDUAL",
    }


def money(rng: random.Random, low: int, high: int) -> str:
    return f"{rng.randint(low * 100, high * 100) / 100:.2f}"


def ref_of(description):
    """'SEED C007-R012 Card purchase' -> 'C007-R012'; anything else -> None."""
    if description and description.startswith("SEED ") and len(description.split()) >= 2:
        return description.split()[1]
    return None


def plan_transactions(code: str, now: datetime, account_ids: list):
    """Deterministic plan for one customer: list of dicts with ref, account index, direction, amount,
    time, description, and whether to post. The same customer always gets the same plan."""
    rng = random.Random(f"plan-{code}")
    plan = []
    for k in range(ROUTINE_TXN_PER_CUSTOMER):
        name, direction, low, high = rng.choice(ROUTINE_TEMPLATES)
        plan.append({
            "ref": f"{code}-R{k:03d}", "account": k % len(account_ids), "type": direction,
            "amount": money(rng, low, high), "when": now - timedelta(days=rng.uniform(0, 450)),
            "name": name, "post": direction == "CREDIT",
        })
    for k in range(2):
        plan.append({
            "ref": f"{code}-HV{k}", "account": 0, "type": "DEBIT", "amount": "60000.00",
            "when": now - timedelta(days=rng.uniform(5, 450)),
            "name": "High-value transfer review", "post": False,
        })
    burst_start = now - timedelta(days=rng.uniform(20, 400))
    for k in range(6):
        plan.append({
            "ref": f"{code}-BU{k}", "account": 0, "type": "DEBIT", "amount": "45.00",
            "when": burst_start + timedelta(seconds=40 * k),
            "name": "Rapid card purchase", "post": True,
        })
    for entry in plan:
        if entry["when"] > now:
            entry["when"] = now - timedelta(minutes=1)
    return plan


# --------------------------------------------------------------------------- seeding steps
def ensure_customers(summary):
    status, listing = call("GET", "/customers")
    if status != 200:
        raise SeedError(f"cannot list customers: {status} {listing}")
    by_email = {c["email"].lower(): c for c in items(listing)}
    for index in range(CUSTOMER_COUNT):
        spec = customer_spec(index)
        if spec["email"] in by_email:
            continue
        status, created = call("POST", "/customers", spec)
        if status == 201:
            by_email[spec["email"]] = created
            summary["customers_created"] += 1
        elif status == 409:
            # Created by someone else in the meantime; pick it up on the next listing.
            _, listing = call("GET", "/customers")
            by_email = {c["email"].lower(): c for c in items(listing)}
        else:
            summary["failures"].append(f"create customer {spec['email']}: {status} {created}")
    return [by_email[customer_spec(i)["email"]] for i in range(CUSTOMER_COUNT)
            if customer_spec(i)["email"] in by_email]


def ensure_accounts(index: int, customer: dict, summary) -> list:
    status, accounts = call("GET", f"/accounts/customer/{customer['id']}")
    accounts = items(accounts) if status == 200 else []
    have = {a.get("accountType") for a in accounts}
    wanted = ["SAVINGS"] + (["CHEQUE"] if index % 3 != 0 else [])
    for account_type in wanted:
        if account_type in have:
            continue
        status, created = call("POST", "/accounts", {"customerId": customer["id"], "accountType": account_type})
        if status == 201:
            accounts.append(created)
            summary["accounts_created"] += 1
        else:
            summary["failures"].append(f"open {account_type} for {customer['email']}: {status} {created}")
    return sorted(accounts, key=lambda a: (a.get("accountType") != "SAVINGS", a.get("accountNumber", "")))


def seed_customer(index: int, customer: dict, now: datetime, token: str) -> dict:
    """Seeds one customer's accounts. Runs in a worker thread; returns its own counters."""
    local = {"customers_created": 0, "accounts_created": 0, "transactions_created": 0,
             "transactions_existing": 0, "funding_created": 0, "posted": 0, "failures": [],
             "accounts": []}
    code = f"C{index:03d}"
    accounts = ensure_accounts(index, customer, local)
    local["accounts"] = [a["id"] for a in accounts]
    if not accounts:
        return local
    existing = {}
    for account in accounts:
        status, txns = call("GET", f"/transactions/account/{account['id']}")
        refs = set()
        for t in items(txns) if status == 200 else []:
            ref = ref_of(t.get("description"))
            if ref:
                refs.add(ref)
        existing[account["id"]] = refs

    # Opening balance on each account: idempotent by its own reference and idempotency key.
    for ai, account in enumerate(accounts):
        ref = f"{code}-A{ai}-OPEN"
        if ref in existing[account["id"]]:
            local["transactions_existing"] += 1
            continue
        rng = random.Random(f"open-{code}-{ai}")
        status, body = call("POST", "/transactions/funding", {
            "accountId": account["id"], "entryKind": "OPENING_BALANCE",
            "amount": f"{rng.randint(15, 90) * 1000}.00", "currency": CURRENCY,
            "idempotencyKey": f"seed-{ref}", "description": f"SEED {ref} Opening balance"})
        if status in (200, 201):
            local["funding_created"] += 1
        else:
            local["failures"].append(f"opening balance {ref}: {status} {body}")

    for entry in plan_transactions(code, now, [a["id"] for a in accounts]):
        account = accounts[entry["account"]]
        if entry["ref"] in existing[account["id"]]:
            local["transactions_existing"] += 1
            continue
        body = {
            "accountId": account["id"], "type": entry["type"], "amount": entry["amount"],
            "currency": CURRENCY, "description": f"SEED {entry['ref']} {entry['name']}",
            "syntheticSeed": True, "occurredAt": entry["when"].strftime("%Y-%m-%dT%H:%M:%S"),
        }
        status, created = call("POST", "/transactions", body)
        if status != 201:
            local["failures"].append(f"transaction {entry['ref']}: {status} {created}")
            continue
        local["transactions_created"] += 1
        if entry["post"]:
            status, _ = call("POST", f"/transactions/{created['id']}/post")
            if status == 200:
                local["posted"] += 1
            else:
                local["failures"].append(f"post {entry['ref']}: {status}")
    return local


def generate_statements(customers, accounts_by_customer, summary, token):
    today = datetime.now(timezone.utc).date().replace(day=1)
    last_month = (today - timedelta(days=1))
    for customer in customers:
        accounts = accounts_by_customer.get(customer["id"], [])
        if not accounts:
            continue
        status, body = call("POST", "/statements/generate", {
            "accountId": accounts[0], "periodType": "MONTHLY",
            "year": last_month.year, "month": last_month.month}, token=token)
        if status == 201:
            summary["statements_created"] += 1
        elif status == 200:
            summary["statements_existing"] += 1
        else:
            summary["failures"].append(f"statement for {customer['email']}: {status} {body}")


def check_balances(accounts_by_customer, summary):
    for account_ids in accounts_by_customer.values():
        for account_id in account_ids:
            status, _ = call("GET", f"/transactions/account/{account_id}/balance")
            if status == 200:
                summary["balances_checked"] += 1
            else:
                summary["failures"].append(f"balance {account_id}: {status}")


def wait_for_fraud_and_cases(summary):
    """Fraud alerts and cases are produced asynchronously over Kafka, so poll for them."""
    deadline = time.time() + FRAUD_WAIT_SECONDS
    while True:
        _, fraud = call("GET", "/fraud")
        _, cases = call("GET", "/cases")
        summary["fraud_alerts"] = total(fraud)
        summary["cases"] = total(cases)
        if (summary["fraud_alerts"] > 0 and summary["cases"] > 0) or time.time() >= deadline:
            return
        time.sleep(5)


# --------------------------------------------------------------------------- main
def main() -> int:
    if len(SECRET.encode("utf-8")) < 32:
        print("seed: STATEMENT_JWT_SECRET must be set and at least 32 bytes", file=sys.stderr)
        return 1

    now = datetime.now(timezone.utc).replace(tzinfo=None, microsecond=0)
    token = staff_token()
    summary = {
        "customers_target": CUSTOMER_COUNT, "customers_created": 0, "accounts_created": 0,
        "funding_created": 0, "transactions_created": 0, "transactions_existing": 0,
        "posted": 0, "statements_created": 0, "statements_existing": 0, "balances_checked": 0,
        "fraud_alerts": 0, "cases": 0, "failures": [],
    }

    print(f"seed: ensuring {CUSTOMER_COUNT} synthetic customers", flush=True)
    customers = ensure_customers(summary)
    print(f"seed: {len(customers)} synthetic customers present", flush=True)

    accounts_by_customer = {}
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = {pool.submit(seed_customer, i, c, now, token): c for i, c in enumerate(customers)}
        done = 0
        for future in futures:
            local = future.result()
            done += 1
            for key in ("customers_created", "accounts_created", "transactions_created",
                        "transactions_existing", "funding_created", "posted"):
                summary[key] += local[key]
            summary["failures"].extend(local["failures"])
            accounts_by_customer[futures[future]["id"]] = local["accounts"]
            if done % 10 == 0 or done == len(customers):
                print(f"seed: {done}/{len(customers)} customers processed", flush=True)

    generate_statements(customers, accounts_by_customer, summary, token)
    check_balances(accounts_by_customer, summary)
    wait_for_fraud_and_cases(summary)

    problems = []
    if len(customers) < 50:
        problems.append(f"only {len(customers)} customers exist (minimum 50)")
    if summary["failures"]:
        problems.append(f"{len(summary['failures'])} operations failed")
    if summary["fraud_alerts"] == 0:
        problems.append("no fraud alerts were raised by fraud-detection-service")
    if summary["cases"] == 0:
        problems.append("no cases were opened by case-service")

    print(json.dumps({k: v for k, v in summary.items() if k != "failures"}, indent=2))
    for failure in summary["failures"][:20]:
        print(f"seed: failure: {failure}", file=sys.stderr)
    if problems:
        for problem in problems:
            print(f"seed: CHECK FAILED: {problem}", file=sys.stderr)
        return 1
    print("seed: complete. Re-running this script is safe and adds nothing that already exists.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SeedError as error:
        print(f"seed: {error}", file=sys.stderr)
        sys.exit(1)

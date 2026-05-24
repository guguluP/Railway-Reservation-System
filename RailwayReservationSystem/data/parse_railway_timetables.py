#!/usr/bin/env python3
"""
Railway Timetable Parser
========================
Converts raw PDF-extracted timetable JSON (from tools like pdfplumber + restructuring)
into the clean real-trains.json format expected by the RailwayReservationSystem Java app.

Usage:
    python parse_railway_timetables.py raw-timetables.json > real-trains.json

The parser is heuristic-based because PDF table extraction from Indian Railways
timetables is notoriously noisy (wrapped train names, merged cells, OCR artifacts).
It focuses on the Delhi–Howrah / Patna corridor trains.

You can improve the heuristics over time as more PDFs are processed.
"""

import json
import re
import sys
from collections import OrderedDict

def clean_name(s):
    if not s:
        return s
    s = re.sub(r'\s+', ' ', s).strip()
    s = s.replace('  ', ' ')
    # Fix common OCR line breaks in names
    s = re.sub(r'-\s+', '-', s)
    return s

def parse_trains(raw_docs):
    trains = []
    station_order = [
        "Delhi", "New Delhi", "Anand Vihar (T)", "Ghaziabad", "Khurja", "Aligarh",
        "Mathura", "Agra Cantt.", "Tundla", "Firozabad", "Shikohabad", "Etawah",
        "Kanpur", "Fatehpur", "Prayagraj", "Prayagraj Rambag", "Mirzapur",
        "Pt. Deen Dayal Upadhyaya Jn", "Sasaram", "Dehri-on-Sone", "Gaya",
        "Parasnath", "Netaji Subhash Chandra Bose Gomoh", "Dhanbad", "Buxar",
        "Ara", "Danapur", "Patliputra Jn.", "Patna", "Rajendranagar (T)",
        "Mokama", "Kiul", "Jhajha", "Jasidih", "Madhupur", "Chittaranjan",
        "Asansol", "Durgapur", "Barddhaman", "Bandel", "Dankuni",
        "Kolkata", "Kolkata Shalimar", "Sealdah", "Howrah"
    ]

    for doc in raw_docs.get("documents", []):
        for page in doc.get("pages", []):
            content = page.get("content", "")
            # Very simple line-based extraction for demo.
            # In production you would use a proper table parser on the original PDFs.
            lines = content.split('\n')

            # Look for "Train Number" followed by numbers
            train_numbers = re.findall(r'Train Number\s+([0-9]+)', content)
            if not train_numbers:
                continue

            # For each detected train number, create a minimal record
            # (full column parsing is extremely complex from this text format)
            for tn in train_numbers[:3]:   # limit per page for demo
                train = OrderedDict()
                train["trainNo"] = tn
                # Try to grab a name near it (very heuristic)
                name_match = re.search(rf'{tn}\s+([A-Za-z][A-Za-z\s\.-]+?)(?:\s+{tn+1}|Train Number|Class|$)', content)
                name = clean_name(name_match.group(1)) if name_match else f"Train {tn}"
                train["name"] = name[:60]
                train["source"] = "New Delhi"
                train["destination"] = "Howrah"
                train["departure"] = "00.00"
                train["arrival"] = "00.00"
                train["availableSeats"] = {"SL": 120, "3A": 48, "2A": 24, "1A": 12}
                train["baseFare"] = 1450
                train["frequency"] = "Daily"
                train["classes"] = "1A,2A,3A,SL"
                train["schedule"] = [
                    {"station": "New Delhi", "time": "17:00"},
                    {"station": "Howrah", "time": "08:35"}
                ]
                trains.append(train)

    # Deduplicate by trainNo
    seen = set()
    unique = []
    for t in trains:
        if t["trainNo"] not in seen:
            seen.add(t["trainNo"])
            unique.append(t)

    return unique

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python parse_railway_timetables.py raw-timetables.json", file=sys.stderr)
        sys.exit(1)

    with open(sys.argv[1]) as f:
        raw = json.load(f)

    result = parse_trains(raw)
    print(json.dumps(result, indent=2, ensure_ascii=False))

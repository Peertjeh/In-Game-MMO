#!/usr/bin/env python3
"""Fetches the newest Fabric builds of Sodium, Iris, Mod Menu and Text Placeholder API for the game version in gradle.properties from
Modrinth, into the mods folder of the compatibility run (CI). Extreme Dimensions comes from its own GitHub release or
source in the workflow itself.

Usage: fetch.py <mods dir>

Prints what it found, one COMPAT| line per mod; a mod with no build for this game version yet is left out (the run then
goes on with the others). Exits 0 when at least one was found, 3 when none was."""

import json
import os
import re
import sys
import urllib.parse
import urllib.request

MODS = ["sodium", "iris", "modmenu", "placeholder-api"]
AGENT = {"User-Agent": "extremeprogression-ci/compat"}


def game_version():
    with open("gradle.properties", encoding="utf-8") as f:
        for line in f:
            m = re.match(r"\s*minecraft_version\s*=\s*(\S+)", line)
            if m:
                return m.group(1)
    raise SystemExit("no minecraft_version in gradle.properties")


def newest(slug, version):
    query = urllib.parse.urlencode({"loaders": json.dumps(["fabric"]), "game_versions": json.dumps([version])})
    req = urllib.request.Request(f"https://api.modrinth.com/v2/project/{slug}/version?{query}", headers=AGENT)
    with urllib.request.urlopen(req, timeout=30) as r:
        versions = json.load(r)
    # releases before betas before alphas, each newest first (the API lists newest first)
    order = {"release": 0, "beta": 1, "alpha": 2}
    versions.sort(key=lambda v: order.get(v.get("version_type"), 3))
    return versions[0] if versions else None


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    version = game_version()
    found = 0
    got = set()
    for slug in MODS:
        if slug == "iris" and "sodium" not in got:
            print(f"COMPAT|{slug}|none|left out, as Sodium is (Iris needs it)")
            continue
        try:
            v = newest(slug, version)
        except Exception as e:  # the run goes on with the others
            print(f"COMPAT|{slug}|error|{e}")
            continue
        if v is None:
            print(f"COMPAT|{slug}|none|no Fabric build for {version} yet")
            continue
        f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
        try:
            with urllib.request.urlopen(urllib.request.Request(f["url"], headers=AGENT), timeout=120) as r:
                data = r.read()
        except Exception as e:
            print(f"COMPAT|{slug}|error|{e}")
            continue
        with open(os.path.join(out, f["filename"]), "wb") as w:
            w.write(data)
        print(f"COMPAT|{slug}|{v['version_number']}|{f['filename']}")
        got.add(slug)
        found += 1
    sys.exit(0 if found else 3)


if __name__ == "__main__":
    main()

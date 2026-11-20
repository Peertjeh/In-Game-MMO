#!/usr/bin/env python3
"""
Builds the Extreme Progression wiki: one page, tools/wiki/out/wiki.html, with every skill, XP table,
specialization, trunk bonus, Mastery perk, synergy, command, permission, placeholder and setting of the mod, the
hand-written pages of tools/wiki/pages.json, and every release of CHANGELOG.md.

    python3 tools/wiki/build.py               # from the working tree
    python3 tools/wiki/build.py --ref HEAD    # from a commit: leaves out work nobody committed yet
    python3 tools/wiki/build.py --offline     # no GitHub lookup for the download links

Standard library only. Everything is read from the sources (the language file, the enums, Config, the commands, the
XP tables, the READMEs and the changelog), so a new language key, enum value, config option, command or release shows
up on the next run. The page itself is tools/wiki/template.html; the build fills in its data.

The output is a page fragment (no doctype, html, head or body): the host that publishes it wraps it.
"""

import argparse
import html
import json
import math
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
MAIN = "src/main/java/com/extremeprogression/"
CLIENT = "src/client/java/com/extremeprogression/client/"
LANG_FILE = "src/client/resources/assets/extremeprogression/lang/en_us.json"
REPO = "Peertjeh/In-Game-MMO"
MOD = "extremeprogression"

WARNINGS = []


def warn(msg):
    WARNINGS.append(msg)
    print("warning: " + msg, file=sys.stderr)


# ---------------------------------------------------------------------------------------------------------------- sources

class Source:
    """The repository, as the working tree has it or as a git revision had it."""

    def __init__(self, root, ref=None):
        self.root, self.ref = root, ref

    def _git(self, *args):
        r = subprocess.run(["git", "-C", str(self.root), *args], capture_output=True, text=True, encoding="utf-8")
        return r.stdout if r.returncode == 0 else None

    def read(self, rel):
        if self.ref:
            return self._git("show", f"{self.ref}:{rel}")
        p = self.root / rel
        return p.read_text(encoding="utf-8") if p.is_file() else None

    def need(self, rel):
        s = self.read(rel)
        if s is None:
            sys.exit(f"build.py: cannot read {rel}" + (f" at {self.ref}" if self.ref else ""))
        return s

    def files(self, rel_dir, suffix):
        if self.ref:
            out = self._git("ls-tree", "-r", "--name-only", self.ref, rel_dir) or ""
            return sorted(l for l in out.splitlines() if l.endswith(suffix))
        d = self.root / rel_dir
        return sorted(str(p.relative_to(self.root)) for p in d.rglob("*" + suffix)) if d.is_dir() else []


# ---------------------------------------------------------------------------------------------------------- reading Java

_TOK = re.compile(r"""
  (?P<doc>/\*\*(?!/)[\s\S]*?\*/)
 |(?P<com>/\*[\s\S]*?\*/|//[^\n]*)
 |(?P<str>"(?:\\.|[^"\\\n])*")
 |(?P<chr>'(?:\\.|[^'\\\n])+')
 |(?P<num>0[xX][0-9a-fA-F_]+[lL]?|(?:\d[\d_]*(?:\.[\d_]*)?|\.\d[\d_]*)(?:[eE][+-]?\d+)?[dDfFlL]?)
 |(?P<id>[A-Za-z_$][\w$]*)
 |(?P<op>::|->|\+\+|--|&&|\|\||[=!<>]=|[{}()\[\];,.<>=+\-*/%!?:&|^~@])
 |(?P<ws>\s+)
""", re.X)


class Tok:
    __slots__ = ("k", "t", "line")

    def __init__(self, k, t, line):
        self.k, self.t, self.line = k, t, line

    def __repr__(self):
        return f"{self.k}:{self.t}"

    @property
    def end_line(self):
        return self.line + self.t.count("\n")


def tokenize(src):
    out, pos, line, n = [], 0, 1, len(src)
    while pos < n:
        m = _TOK.match(src, pos)
        if not m:
            pos += 1
            continue
        k, t = m.lastgroup, m.group(m.lastgroup)
        if k != "ws":
            out.append(Tok(k, t, line))
        line += t.count("\n")
        pos = m.end()
    return out


PAIRS = {"(": ")", "{": "}", "[": "]"}


def close_of(toks, i):
    """The index of the bracket that closes the one at i."""
    o = toks[i].t
    c, d = PAIRS[o], 0
    for j in range(i, len(toks)):
        if toks[j].k != "op":
            continue
        if toks[j].t == o:
            d += 1
        elif toks[j].t == c:
            d -= 1
            if d == 0:
                return j
    return len(toks) - 1


def split_args(toks, a, b):
    """toks[a:b] split on the commas at depth 0, as (start, end) pairs."""
    out, start, d = [], a, 0
    for j in range(a, b):
        t = toks[j]
        if t.k != "op":
            continue
        if t.t in "({[":
            d += 1
        elif t.t in ")}]":
            d -= 1
        elif t.t == "," and d == 0:
            out.append((start, j))
            start = j + 1
    if start < b:
        out.append((start, b))
    return out


def code(toks):
    return " ".join(t.t for t in toks if t.k not in ("doc", "com"))


def unquote(s):
    try:
        return json.loads(s)
    except ValueError:
        return s[1:-1]


def number(t):
    s = t.replace("_", "")
    if s[:2] in ("0x", "0X"):
        return int(s.rstrip("lL"), 16)
    if s[-1] in "lL":
        return int(s[:-1])
    if s[-1] in "dDfF":
        return float(s[:-1])
    return float(s) if ("." in s or "e" in s or "E" in s) else int(s)


def jval(toks):
    """A Java expression as a Python value: literals, constants (their name), List.of(...), simple arithmetic."""
    ts = [t for t in toks if t.k not in ("doc", "com")]
    if not ts:
        return None
    if len(ts) == 1:
        t = ts[0]
        if t.k == "str":
            return unquote(t.t)
        if t.k == "num":
            return number(t.t)
        if t.t in ("true", "false"):
            return t.t == "true"
        if t.t == "null":
            return None
        return t.t
    if len(ts) == 2 and ts[0].t == "-" and ts[1].k == "num":
        return -number(ts[1].t)
    if all(t.k == "id" or t.t == "." for t in ts):
        return ts[-1].t
    if ts[-1].t == ")" and ts[0].k == "id" and any(t.t == "of" for t in ts[:4]):
        o = next(i for i, t in enumerate(ts) if t.t == "(")
        return [jval(ts[a:b]) for a, b in split_args(ts, o + 1, close_of(ts, o))]
    expr = "".join(t.t for t in ts)
    if re.fullmatch(r"[\d.\s+\-*/()]+", expr):
        try:
            return eval(expr, {"__builtins__": {}})  # digits and arithmetic only (checked above)
        except Exception:
            pass
    return code(ts)


INTERNAL = re.compile(r"design\s*§|§\d|\bround \d+|\bowner\b|milestone|appendix|mechanism [a-z]\b|^\d+\.\d+(\.\d+)?$|^the legends update", re.I)


def player_text(s):
    """Takes the notes meant for developers out of a doc comment: design references, rounds, versions."""
    if not s:
        return ""

    def paren(m):
        # (a parenthesis that holds a note for developers goes as a whole)
        if any(INTERNAL.search(p.strip()) for p in re.split(r",\s*", m.group(1))):
            return ""
        return m.group(0)

    for _ in range(2):
        s = re.sub(r"\s*\(([^()]*)\)", paren, s)
    s = re.sub(r"[✅💡]\s*", "", s)
    s = re.sub(r"\s+([.,;:])", r"\1", s)
    return re.sub(r"\s{2,}", " ", s).strip()


def clean_doc(doc):
    if not doc:
        return ""
    s = doc[3:-2]
    s = " ".join(re.sub(r"^\s*\*?\s?", "", l).strip() for l in s.split("\n"))
    s = re.sub(r"\{@(?:link|linkplain)\s+([^}\s]+)(?:\s+([^}]*))?\}",
               lambda m: m.group(2) or m.group(1).split("#")[-1].split(".")[-1].split("(")[0], s)
    s = re.sub(r"\{@(?:code|literal)\s+([^}]*)\}", r"\1", s)
    s = re.sub(r"</?(?:p|b|i|em|strong|ul|ol|li|pre|br|code|tt|a|table|tr|td|th|h\d)(?:\s[^>]*)?/?>", " ", s, flags=re.I)
    s = html.unescape(s)
    return re.sub(r"\s+", " ", s).strip()


def parse_enum(src, name):
    """The constants of an enum: name, args (as Python values), arg_toks, doc."""
    toks = tokenize(src)
    start = None
    for i, t in enumerate(toks[:-2]):
        if t.t == "enum" and toks[i + 1].t == name:
            j = i + 2
            while toks[j].t != "{":
                j += 1
            start = j
            break
    if start is None:
        warn(f"enum {name} not found")
        return []
    end = close_of(toks, start)
    out, doc, k = [], None, start + 1
    while k < end:
        t = toks[k]
        if t.k == "doc":
            doc = t.t
        elif t.k == "com" or t.t == ",":
            pass
        elif t.t == ";":
            break
        elif t.t == "@":
            k += 1
            if k + 1 < end and toks[k + 1].t == "(":
                k = close_of(toks, k + 1)
        elif t.k == "id":
            c = {"name": t.t, "args": [], "arg_toks": [], "doc": clean_doc(doc)}
            doc = None
            if toks[k + 1].t == "(":
                e = close_of(toks, k + 1)
                spans = split_args(toks, k + 2, e)
                c["arg_toks"] = [toks[a:b] for a, b in spans]
                c["args"] = [jval(x) for x in c["arg_toks"]]
                k = e
            if toks[k + 1].t == "{":
                k = close_of(toks, k + 1)
            out.append(c)
        k += 1
    return out


def method_body(src, name):
    m = re.search(r"\b" + re.escape(name) + r"\s*\([^)]*\)\s*(?:throws[^{]*)?\{", src)
    if not m:
        return ""
    i, d = m.end() - 1, 0
    for j in range(i, len(src)):
        if src[j] == "{":
            d += 1
        elif src[j] == "}":
            d -= 1
            if d == 0:
                return src[i:j + 1]
    return src[i:]


def parse_switch(src, method):
    """case A -> B; (and case A, C -> B;) in a method: {A: B}."""
    out = {}
    for m in re.finditer(r"case\s+([\w\s,]+?)\s*->\s*(\w+)\s*;", method_body(src, method)):
        for a in m.group(1).split(","):
            out[a.strip()] = m.group(2)
    return out


MODIFIERS = {"public", "private", "protected", "static", "final", "transient", "volatile"}


def parse_fields(src):
    """Every field with an initializer in a class body, with its doc comment, section comment and neighbours."""
    toks = tokenize(src)
    ci = next((i for i, t in enumerate(toks) if t.t in ("class", "enum", "interface", "record")), None)
    if ci is None:
        return []
    while toks[ci].t != "{":
        ci += 1
    end = close_of(toks, ci)
    out, doc, section, prev, k = [], None, None, None, ci + 1
    while k < end:
        t = toks[k]
        if t.k == "doc":
            doc = t
            k += 1
            continue
        if t.k == "com":
            m = re.match(r"//\s*-{2,}\s*(.+?)\s*-*\s*$", t.t)
            if m:
                section, prev = m.group(1), None
            k += 1
            continue
        j = k
        while j < end and toks[j].t not in (";", "{", "="):
            if toks[j].t == "(":
                j = close_of(toks, j)
            j += 1
        if j < end and toks[j].t == "=":
            e = j + 1
            while e < end and toks[e].t != ";":
                if toks[e].t in ("(", "{", "["):
                    e = close_of(toks, e)
                e += 1
            head = toks[k:j]
            mods = {x.t for x in head if x.t in MODIFIERS}
            typ = code([x for x in head[:-1] if x.t not in MODIFIERS])
            first_line = doc.line if doc else toks[k].line
            f = {"name": toks[j - 1].t, "mods": mods, "type": typ, "init": toks[j + 1:e], "value": jval(toks[j + 1:e]),
                 "doc": clean_doc(doc.t) if doc else "", "section": section, "line": toks[k].line,
                 "prev": prev, "contiguous": prev is not None and first_line == prev["end_line"] + 1,
                 "end_line": toks[e].line if e < len(toks) else toks[-1].line}
            out.append(f)
            prev, doc, k = f, None, e + 1
            continue
        if j < end and toks[j].t == "{":
            k = close_of(toks, j) + 1
        else:
            k = j + 1
        doc, prev = None, None
    return out


def first_token(name):
    return name.split("_")[0]


def related(a, b):
    """Two field names of one family: the same first word, or one first word the start of the other's."""
    x, y = first_token(a), first_token(b)
    return x == y or (min(len(x), len(y)) >= 5 and (x.startswith(y) or y.startswith(x)))


def field_doc(f):
    """A field's doc, with "..." continuations joined and the doc of its family for a field without one."""
    d = f["doc"]
    p = f["prev"]
    if d.startswith("...") and p:
        d = field_doc(p).rstrip(".").rstrip() + " " + d.lstrip(".").strip()
    elif not d and p and f["contiguous"] and related(f["name"], p["name"]):
        d = field_doc(p)
    return re.sub(r"\.\.\.$", ".", d)


# ------------------------------------------------------------------------------------------------------------ formatting

def human(v):
    """A number as prose shows it: 10,000 · 2.66 · 0.05."""
    if isinstance(v, bool):
        return "on" if v else "off"
    if isinstance(v, (int, float)):
        v = round(float(v), 6)
        if v.is_integer():
            i = int(v)
            return f"{i:,}" if abs(i) >= 1000 else str(i)
        return f"{v:g}"
    return str(v)


def pct(v):
    return human(round(float(v) * 100, 6)) + "%"


def file_value(f):
    """A config value as config/extremeprogression.json holds it."""
    v = f["value"]
    if isinstance(v, bool):
        return "true" if v else "false"
    if f["type"] == "double" and isinstance(v, (int, float)):
        return repr(float(v))
    if isinstance(v, str):
        return json.dumps(v)
    return str(v)


ACRONYMS = {"xp": "XP", "afk": "AFK", "pvp": "PvP", "ip": "IP", "hud": "HUD", "id": "ID", "tnt": "TNT"}


def humanize(name):
    words = [w for w in re.split(r"[_\s]+", name.strip()) if w]
    words = [ACRONYMS.get(w.lower(), w.lower()) for w in words]
    if words and words[0] == words[0].lower():
        words[0] = words[0][:1].upper() + words[0][1:]
    return " ".join(words)


def mc_text(s):
    """A Minecraft translation as readable text: %% is a %, a %s or %1$s placeholder becomes "…"."""
    if s is None:
        return ""
    return re.sub(r"%(?:(\d+)\$)?([A-Za-z%]|$)", lambda m: "%" if m.group(2) == "%" else "…", s)


def article(word):
    return ("an " if word[:1].lower() in "aeiou" else "a ") + word


def join_and(items, word="and"):
    items = [i for i in items if i]
    if len(items) <= 1:
        return "".join(items)
    return ", ".join(items[:-1]) + f" {word} " + items[-1]


# ---------------------------------------------------------------------------------------------------------- the commands

class CNode:
    def __init__(self, kind, name, argtype=""):
        self.kind, self.name, self.argtype = kind, name, argtype
        self.children, self.requires, self.executes, self.exec_ref, self.suggest = [], None, False, None, ""

    def add(self, child):
        for c in self.children:
            if c.kind == child.kind and c.name == child.name:
                c.merge(child)
                return c
        self.children.append(child)
        return child

    def merge(self, o):
        if o is self:
            return
        self.executes |= o.executes
        self.exec_ref = self.exec_ref or o.exec_ref
        self.requires = self.requires or o.requires
        self.suggest = self.suggest or o.suggest
        self.argtype = self.argtype or o.argtype
        for c in list(o.children):
            self.add(c)

    def sig(self):
        return (self.kind, self.name, self.executes, self.requires, tuple(sorted(c.sig() for c in self.children)))


class Method:
    def __init__(self, cls, name, params, ret, toks, body, doc):
        self.cls, self.name, self.params, self.ret, self.toks, self.body, self.doc = cls, name, params, ret, toks, body, doc

    @property
    def builder(self):
        return "ArgumentBuilder" in self.ret


NOT_METHODS = {"if", "for", "while", "switch", "catch", "synchronized", "return", "new", "throw", "else", "try"}


def index_methods(cls, toks):
    out = []
    depth = 0
    i = 0
    while i < len(toks):
        t = toks[i]
        if t.t == "{":
            depth += 1
        elif t.t == "}":
            depth -= 1
        elif depth == 1 and t.k == "id" and t.t not in NOT_METHODS and i + 1 < len(toks) and toks[i + 1].t == "(" \
                and i > 0 and (toks[i - 1].k == "id" or toks[i - 1].t in (">", "]")) and toks[i - 1].t not in NOT_METHODS:
            c = close_of(toks, i + 1)
            j = c + 1
            if j < len(toks) and toks[j].t == "throws":
                while j < len(toks) and toks[j].t not in ("{", ";"):
                    j += 1
            if j < len(toks) and toks[j].t == "{":
                b = close_of(toks, j)
                params = []
                for a, e in split_args(toks, i + 2, c):
                    ps = [x for x in toks[a:e] if x.k not in ("doc", "com")]
                    if ps:
                        params.append((code(ps[:-1]), ps[-1].t))
                # the return type and the doc comment: back over the modifiers and the type
                s = i - 1
                while s > 0 and (toks[s].k == "id" or toks[s].t in ("<", ">", ",", ".", "[", "]", "?")):
                    s -= 1
                ret = code([x for x in toks[s + 1:i] if x.t not in MODIFIERS])
                doc = clean_doc(toks[s].t) if toks[s].k == "doc" else ""
                out.append(Method(cls, t.t, params, ret, toks, (j, b), doc))
                i = b + 1
                continue
        i += 1
    return out


class Commands:
    """Reads the Brigadier trees the command classes register: literals, arguments, requires and executes."""

    def __init__(self, sources):
        self.methods = []
        for path, src in sources:
            cls = Path(path).stem
            self.methods += index_methods(cls, tokenize(src))
        self.names = {m.name for m in self.methods}
        self.roots = {}

    def find(self, qualifier, name, nargs, want_builder_param=False):
        ms = [m for m in self.methods if m.name == name and len(m.params) == nargs]
        if qualifier:
            q = [m for m in ms if m.cls == qualifier]
            ms = q or ms
        if want_builder_param:
            q = [m for m in ms if any("ArgumentBuilder" in p[0] for p in m.params)]
            ms = q or ms
        return ms[0] if ms else None

    def run(self):
        for m in self.methods:
            if m.name == "register" and m.params and "CommandDispatcher" in m.params[0][0]:
                self.body(m, {}, {})
        return self.roots

    def add_root(self, node):
        if node.name in self.roots:
            self.roots[node.name].merge(node)
        else:
            self.roots[node.name] = node

    def preds(self, ts, preds):
        out = set()
        for i, t in enumerate(ts):
            if t.t == "Node" and i + 2 < len(ts) and ts[i + 1].t == "." and ts[i + 2].k == "id":
                out.add(ts[i + 2].t)
            elif t.k == "id" and t.t in preds and (i == 0 or ts[i - 1].t != "."):
                out |= preds[t.t]
        return frozenset(out) if out else None

    def exec_ref(self, ts):
        for i, t in enumerate(ts):
            if t.t == "::" and i + 1 < len(ts):
                return ts[i + 1].t
        for i, t in enumerate(ts[:-1]):
            if t.k == "id" and ts[i + 1].t == "(" and t.t in self.names and (i == 0 or ts[i - 1].t != "."):
                return t.t
        return None

    def chain(self, t, i, env, preds):
        """Evaluates a builder chain starting at t[i]: (node, index after it), or (None, i)."""
        node, j = None, i
        if i + 3 < len(t) and t[i].t == "Commands" and t[i + 1].t == "." and t[i + 2].t in ("literal", "argument") and t[i + 3].t == "(":
            c = close_of(t, i + 3)
            args = split_args(t, i + 4, c)
            name = unquote(t[args[0][0]].t) if args else "?"
            if t[i + 2].t == "literal":
                node = CNode("literal", name)
            else:
                node = CNode("argument", name, code(t[args[1][0]:args[1][1]]) if len(args) > 1 else "")
            j = c + 1
        elif t[i].k == "id" and t[i].t in env and i + 1 < len(t) and t[i + 1].t in (".", ")", ",", ";"):
            node, j = env[t[i].t], i + 1
        else:
            k, qual = i, None
            while k + 2 < len(t) and t[k].k == "id" and t[k + 1].t == ".":
                qual = t[k].t
                k += 2
            if k + 1 < len(t) and t[k].k == "id" and t[k + 1].t == "(":
                c = close_of(t, k + 1)
                spans = split_args(t, k + 2, c)
                m = self.find(qual, t[k].t, len(spans))
                if m is None or not m.builder:
                    return None, i
                env2 = {}
                for (a, _), (_, pname) in zip(spans, m.params):
                    n, _ = self.chain(t, a, env, preds)
                    if n is not None:
                        env2[pname] = n
                node = self.returned(m, env2)
                if node is None:
                    return None, i
                j = c + 1
            else:
                return None, i
        while j + 2 < len(t) and t[j].t == "." and t[j + 1].k == "id" and t[j + 2].t == "(":
            name, c = t[j + 1].t, close_of(t, j + 2)
            if name == "then":
                child, _ = self.chain(t, j + 3, env, preds)
                if child is not None:
                    node.add(child)
            elif name == "requires":
                node.requires = self.preds(t[j + 3:c], preds)
            elif name == "executes":
                node.executes = True
                node.exec_ref = self.exec_ref(t[j + 3:c])
            elif name == "suggests":
                node.suggest = code(t[j + 3:c])
            j = c + 1
        return node, j

    def returned(self, m, env):
        t, (a, b) = m.toks, m.body
        depth = 0
        for i in range(a, b):
            if t[i].t == "{":
                depth += 1
            elif t[i].t == "}":
                depth -= 1
            elif t[i].t == "return" and depth == 1:
                n, _ = self.chain(t, i + 1, env, {})
                return n
        return None

    def body(self, m, env, preds):
        t, (a, b) = m.toks, m.body
        env, preds = dict(env), dict(preds)
        i = a + 1
        while i < b:
            x = t[i]
            if x.t in ("{", "}", ";") or x.k in ("doc", "com") or x.t == "else":
                i += 1
                continue
            if x.t in ("if", "while", "for", "switch") and t[i + 1].t == "(":
                i = close_of(t, i + 1) + 1
                continue
            j, d = i, 0
            while j < b:
                if t[j].t in ("(", "{", "["):
                    d += 1
                elif t[j].t in (")", "}", "]"):
                    d -= 1
                elif t[j].t == ";" and d == 0:
                    break
                j += 1
            self.statement(t, i, j, env, preds)
            i = j + 1

    def statement(self, t, i, j, env, preds):
        d = 0
        for k in range(i, j):
            if t[k].t in ("(", "{", "["):
                d += 1
            elif t[k].t in (")", "}", "]"):
                d -= 1
            elif t[k].t == "=" and d == 0 and t[k - 1].k == "id":
                name, rest = t[k - 1].t, t[k + 1:j]
                if any(x.t in ("Perms", "Node", "require") for x in rest) or (rest and rest[0].t in preds):
                    p = self.preds(rest, preds)
                    if p:
                        preds[name] = p
                    return
                n, _ = self.chain(t, k + 1, env, preds)
                if n is not None:
                    env[name] = n
                return
        if t[i].t == "dispatcher" and t[i + 1].t == "." and t[i + 2].t == "register":
            n, _ = self.chain(t, i + 4, env, preds)
            if n is not None:
                self.add_root(n)
            return
        if t[i].k == "id" and t[i].t in env:
            self.chain(t, i, env, preds)
            return
        k, qual = i, None
        while k + 2 < j and t[k].k == "id" and t[k + 1].t == ".":
            qual = t[k].t
            k += 2
        if k + 1 < j and t[k].k == "id" and t[k + 1].t == "(" and t[k].t in self.names:
            c = close_of(t, k + 1)
            spans = split_args(t, k + 2, c)
            bound = [env.get(t[s].t) if e - s == 1 else None for s, e in spans]
            m = self.find(qual, t[k].t, len(spans), want_builder_param=any(bound))
            if m is not None and not m.builder and not (m.params and "CommandDispatcher" in m.params[0][0]):
                self.body(m, {p[1]: n for p, n in zip(m.params, bound) if n is not None}, {})


# ------------------------------------------------------------------------------------------------------------- changelog

def md_inline(s):
    s = html.escape(s, quote=False)
    codes = []

    def keep(m):
        codes.append(m.group(1))
        return f"\x00{len(codes) - 1}\x00"

    s = re.sub(r"`([^`]+)`", keep, s)
    s = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", s)
    s = re.sub(r"(?<![\w*])\*(?![\s*])(.+?)(?<![\s*])\*(?![\w*])", r"<em>\1</em>", s)

    def link(m):
        href = m.group(2)
        if not re.match(r"https?://", href):
            href = f"https://github.com/{REPO}/blob/main/" + href.lstrip("./")
        return f'<a href="{html.escape(href)}">{m.group(1)}</a>'

    s = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", link, s)
    return re.sub(r"\x00(\d+)\x00", lambda m: f"<code>{codes[int(m.group(1))]}</code>", s)


def md_html(text):
    blocks, cur = [], None
    for raw in text.split("\n"):
        if not raw.strip():
            cur = None
            continue
        b = re.match(r"^(\s*)[-*] (.*)$", raw)
        h = re.match(r"^#{3,6}\s+(.*)$", raw)
        if b:
            cur = ["li", len(b.group(1)), b.group(2).strip()]
            blocks.append(cur)
        elif h:
            blocks.append(["h", h.group(1)])
            cur = None
        elif cur is not None and cur[0] == "li" and raw.startswith(" "):
            cur[2] += " " + raw.strip()
        elif cur is not None and cur[0] == "p":
            cur[1] += " " + raw.strip()
        else:
            cur = ["p", raw.strip()]
            blocks.append(cur)
    out, stack = [], []
    for blk in blocks:
        if blk[0] == "li":
            ind = blk[1]
            while stack and ind < stack[-1]:
                out.append("</li></ul>")
                stack.pop()
            if stack and ind == stack[-1]:
                out.append("</li><li>")
            else:
                out.append("<ul><li>")
                stack.append(ind)
            out.append(md_inline(blk[2]))
            continue
        while stack:
            out.append("</li></ul>")
            stack.pop()
        out.append(f"<h4>{md_inline(blk[1])}</h4>" if blk[0] == "h" else f"<p>{md_inline(blk[1])}</p>")
    while stack:
        out.append("</li></ul>")
        stack.pop()
    return "".join(out)


def vtuple(v):
    return tuple(int(x) for x in v.split("."))


def parse_changelog(text):
    out = []
    parts = re.split(r"^## +(.+?)\s*$", text, flags=re.M)
    for head, body in zip(parts[1::2], parts[2::2]):
        m = re.match(r"(\d+(?:\.\d+)+)\s*(?::\s*(.*))?$", head)
        if not m:
            warn(f"changelog heading without a version: {head}")
            continue
        v = m.group(1)
        out.append({"v": v, "name": (m.group(2) or "").strip(), "html": md_html(body.strip()),
                    "id": "v" + v.replace(".", "-"), "dl": ""})
    out.sort(key=lambda r: vtuple(r["v"]), reverse=True)
    return out


def download_links(offline):
    """Tag -> the release's .jar, from GitHub; or, when that fails, the usual name for the tags known to exist."""
    if offline:
        return {}
    jq = ('.[] | [.tag_name, (.assets[] | select(.name|endswith(".jar")) | select(.name|contains("sources")|not)'
          ' | .browser_download_url)] | @tsv')
    try:
        r = subprocess.run(["gh", "api", f"repos/{REPO}/releases", "--paginate", "--jq", jq],
                           capture_output=True, text=True, timeout=90)
        if r.returncode == 0 and r.stdout.strip():
            out = {}
            for line in r.stdout.splitlines():
                bits = line.split("\t")
                if len(bits) >= 2 and bits[0] not in out:
                    out[bits[0]] = bits[1]
            return out
        warn("gh api gave no releases: " + (r.stderr.strip().splitlines() or ["(no output)"])[-1])
    except (OSError, subprocess.SubprocessError) as e:
        warn(f"gh api failed: {e}")
    try:
        r = subprocess.run(["git", "-C", str(ROOT), "ls-remote", "--tags", "origin"], capture_output=True, text=True, timeout=60)
        tags = {m.group(1) for m in re.finditer(r"refs/tags/(v[\d.]+)$", r.stdout, re.M)} if r.returncode == 0 else set()
    except (OSError, subprocess.SubprocessError):
        tags = set()
    if not tags:
        warn("no release tags known: the changelog has no download links")
    return {t: f"https://github.com/{REPO}/releases/download/{t}/{MOD}-{t[1:]}.jar" for t in tags}


# ------------------------------------------------------------------------------------------------------------- the build

class Wiki:
    def __init__(self, src, pages, offline):
        self.src, self.pages, self.offline = src, pages, offline
        self.lang = json.loads(src.need(LANG_FILE))
        self.entries = []
        self.ids = set()

    def L(self, key, default=None):
        v = self.lang.get(key)
        if v is None:
            return default
        return mc_text(v)

    def add(self, e):
        e.setdefault("alt", "")
        e.setdefault("kind", "")
        e.setdefault("text", "")
        e.setdefault("facts", [])
        if e["id"] in self.ids:
            warn(f"two entries share the id {e['id']}")
            return
        self.ids.add(e["id"])
        self.entries.append(e)

    # --- reading -------------------------------------------------------------------------------------------------

    def read(self):
        s = self.src
        self.version = re.search(r"^version\s*=\s*(\S+)", s.need("gradle.properties"), re.M).group(1)
        skill_src = s.need(MAIN + "skill/Skill.java")
        self.categories = parse_enum(s.need(MAIN + "skill/Category.java"), "Category")
        self.skills = parse_enum(skill_src, "Skill")
        self.opposite = parse_switch(skill_src, "opposite")
        m = re.search(r"TRUNK_LEVELS\s*=\s*\{([^}]*)\}", skill_src)
        self.trunk_levels = [int(x) for x in re.findall(r"\d+", m.group(1))] if m else []
        spec_src = s.need(MAIN + "skill/Specialization.java")
        self.specs = parse_enum(spec_src, "Specialization")
        self.conflicts = parse_switch(spec_src, "conflict")
        self.triggers = {c["name"]: c for c in parse_enum(s.need(MAIN + "ability/Trigger.java"), "Trigger")}
        self.hardreqs = parse_enum(s.need(MAIN + "skill/HardRequirement.java"), "HardRequirement")
        self.slots = parse_enum(s.need(MAIN + "tree/NodeSlot.java"), "NodeSlot")
        syn_src = s.need(MAIN + "synergy/Synergy.java")
        self.synergies = parse_enum(syn_src, "Synergy")
        tj = method_body(syn_src, "toJson")
        self.strength_is = {a: b for a, b in re.findall(r"s\s*==\s*(\w+)\s*\?\s*\"([^\"]+)\"", tj)}
        d = re.findall(r":\s*\"([^\"]+)\"\s*\)", tj)
        self.strength_default = d[-1] if d else "share"
        self.perks = parse_enum(s.need(MAIN + "mastery/MasteryPerk.java"), "MasteryPerk")
        config_src = s.need(MAIN + "config/Config.java")
        self.config = [f for f in parse_fields(config_src) if {"public", "static"} <= f["mods"] and "final" not in f["mods"]]
        self.cfg = {f["name"]: f["value"] for f in self.config}
        preset_src = s.need(MAIN + "config/Preset.java")
        self.presets = [c["name"].lower() for c in parse_enum(preset_src, "Preset")]
        self.normal = {k: jval(tokenize(v)) for k, v in re.findall(r"NORMAL_VALUES\.put\(\"(\w+)\",\s*([^)]+)\)", preset_src)}
        self.perms = parse_enum(s.need(MAIN + "server/Perms.java"), "Node")
        self.read_xp()
        self.read_commands()
        self.keys = []
        for path in s.files(CLIENT, ".java"):
            for m in re.finditer(r"new\s+KeyMapping\(\s*\"([^\"]+)\"\s*,\s*InputConstants\.KEY_(\w+)", s.need(path)):
                self.keys.append((m.group(1), m.group(2)))
        self.tab_lists = {}
        tab = s.read(MAIN + "compat/TabPlaceholders.java") or ""
        for name in ("PLAYER", "WORLD"):
            m = re.search(r"\b" + name + r"\s*=\s*([^;]*);", tab)
            init = m.group(1) if m else ""
            call = re.fullmatch(r"\s*(\w+)\(\)\s*", init)
            if call:
                init = method_body(tab, call.group(1))
            names = re.findall(r"\"([a-z_]+)\"", init)
            names += [p + "<skill>" for p in re.findall(r"\"(\w+_)\"\s*\+\s*\w+\.id", init)]
            self.tab_lists[name] = [n for n in names if not n.endswith("_")]

    def read_xp(self):
        s = self.src
        fields = parse_fields(s.need(MAIN + "xp/XpTables.java"))
        self.xpf = {f["name"]: f for f in fields}
        self.xp_tables = {}
        for f in fields:
            if f["init"] and f["init"][0].t == "table":
                ts = f["init"]
                o = next(i for i, t in enumerate(ts) if t.t == "(")
                vals = [jval(ts[a:b]) for a, b in split_args(ts, o + 1, close_of(ts, o))]
                self.xp_tables[f["name"]] = list(zip(vals[0::2], vals[1::2]))
        src = s.need(MAIN + "xp/XpTables.java")
        types = {}
        for m in re.finditer(r"structures\(\"(\w+)\",\s*([\d.]+)", src):
            types.setdefault(m.group(1), number(m.group(2)))
        self.xp_tables["STRUCTURE_TYPES"] = list(types.items())
        data = s.need(MAIN + "xp/XpData.java")
        self.xp_doc = clean_doc((re.search(r"(/\*\*[\s\S]*?\*/)\s*public final class XpData", data) or [None, ""])[1] or "")
        self.xp_layout = {}
        toks = tokenize(data)
        for i, t in enumerate(toks):
            if t.t == "layout" and toks[i + 1].t == "(" and toks[i - 1].t != "void":
                spans = split_args(toks, i + 2, close_of(toks, i + 1))
                if len(spans) < 2:
                    continue
                skill = jval(toks[spans[0][0]:spans[0][1]])
                values = jval(toks[spans[1][0]:spans[1][1]]) or []
                tables = []
                for (a1, b1), (a2, b2) in zip(spans[2::2], spans[3::2]):
                    tables.append((jval(toks[a1:b1]), jval(toks[a2:b2])))
                self.xp_layout[str(skill)] = (values, tables)

    def read_commands(self):
        srcs = [(p, self.src.need(p)) for p in self.src.files(MAIN + "command", ".java")]
        self.cmd = Commands(srcs)
        self.roots = self.cmd.run()
        if not self.roots:
            warn("no commands found")

    # --- values for the hand-written pages ------------------------------------------------------------------------

    def cfg_key(self, key):
        return self.cfg.get(key.upper())

    def hours(self, level):
        h = self.cfg["HOURS_TO_MAX"] * (level / self.cfg["MAX_LEVEL"]) ** self.cfg["CURVE_EXPONENT"]
        if h < 1 / 60:
            return "seconds"
        if h < 1:
            return f"{round(h * 60)} minutes"
        if h < 10:
            return f"{h:.1f}".rstrip("0").rstrip(".") + (" hour" if round(h, 1) == 1 else " hours")
        return f"{round(h):,} hours"

    def token(self, kind, arg):
        if kind == "cfg":
            return self.cfg_key(arg)
        if kind == "xp":
            if "." in arg:
                table, key = arg.split(".", 1)
                return dict(self.xp_tables.get(table, [])).get(key)
            f = self.xpf.get(arg)
            return f["value"] if f else None
        if kind == "hours":
            return self.hours(float(arg))
        if kind == "normal":
            return self.normal.get(arg.upper())
        if kind == "node":
            slot, _, field = arg.partition(".")
            c = next((x for x in self.slots if x["name"] == slot), None)
            idx = {"maxrank": 3, "cost": 4, "level": 6}.get(field)
            return c["args"][idx] if c and idx is not None else None
        if kind == "syn":
            c = next((x for x in self.synergies if x["name"].lower() == arg), None)
            return c["args"][2] if c else None
        if kind == "perm":
            c = next((x for x in self.perms if x["name"] == arg.upper()), None)
            return f"{MOD}.{c['args'][0].replace('/', '.')}" if c else None
        if kind == "oplevel":
            c = next((x for x in self.perms if x["name"] == arg.upper()), None)
            return OP_LEVELS.get(c["args"][1]) if c else None
        if kind == "key":
            k = next((v for n, v in self.keys if n.endswith("." + arg)), None)
            return k
        if kind == "n":
            return {"skills": len(self.skills), "specs": len(self.specs), "categories": len(self.categories),
                    "synergies": len(self.synergies), "solo": sum(1 for x in self.synergies if not duo(x)),
                    "duo": sum(1 for x in self.synergies if duo(x)), "trunks": len(self.skills) * len(self.trunk_levels),
                    "perks": len(self.perks), "points": self.cfg["MAX_LEVEL"] - self.cfg["FIRST_SPECIALIZATION_LEVEL"],
                    "branch": branch_cost(self.slots), "presets": len(self.presets)}.get(arg)
        if kind == "list":
            if arg == "skills":
                return join_and([self.L(f"skill.{MOD}.{x['name'].lower()}", humanize(x['name'])) for x in self.skills])
            if arg == "categories":
                return join_and([self.L(f"category.{MOD}.{x['name'].lower()}", humanize(x['name'])) for x in self.categories])
            if arg == "opposing":
                seen, out = set(), []
                for a, b in self.opposite.items():
                    if (b, a) not in seen:
                        seen.add((a, b))
                        out.append(f"{self.skill_name(a)} ↔ {self.skill_name(b)}")
                return join_and(out)
            if arg == "trunk":
                return join_and([str(x) for x in self.trunk_levels])
            if arg == "presets":
                return join_and(self.presets, "or")
            if arg == "dimensions":
                return None
        if kind == "lang":
            return self.L(arg)
        if kind == "xph":
            v = self.cfg_key(arg)
            return None if v is None else v / self.cfg["XP_PER_HOUR"]
        if kind == "mod" and arg == "version":
            return self.version
        return None

    def expand(self, s):
        if not isinstance(s, str):
            return s

        def rep(m):
            kind, arg, fmt = m.group(1), m.group(2), m.group(3)
            v = self.token(kind, arg)
            if v is None:
                warn(f"unknown value {m.group(0)} in pages.json")
                return m.group(0)
            if fmt == "pct":
                return pct(v)
            if fmt == "x":
                return "×" + human(v)
            if fmt == "raw":
                return str(v)
            return human(v)

        return re.sub(r"\{([a-z]+):([A-Za-z0-9_.\-]+)(?:\|([a-z]+))?\}", rep, s)

    def skill_name(self, sid):
        return self.L(f"skill.{MOD}.{sid.lower()}", humanize(sid))

    def cat_of(self, sid):
        c = next((x for x in self.skills if x["name"] == sid), None)
        return str(c["args"][0]).lower() if c and c["args"] else ""

    def spec_name(self, sid):
        return self.L(f"spec.{MOD}.{sid.lower()}", humanize(sid))

    # --- entries ---------------------------------------------------------------------------------------------------

    def build(self):
        self.dutch = self.pages.get("dutch", {})
        self.gen_skills()
        self.gen_xp()
        self.gen_specs()
        self.gen_trunks()
        self.gen_mastery()
        self.gen_synergies()
        self.gen_keys()
        self.gen_commands()
        self.gen_perms()
        self.gen_placeholders()
        self.gen_settings()
        self.add_pages()
        order = [c[0] for c in CATS]
        self.entries.sort(key=lambda e: order.index(e["cat"]) if e["cat"] in order else len(order))
        for e in self.entries:
            if "refs" in e:
                e["refs"] = [r for r in e["refs"] if r in self.ids and r != e["id"]]
                if not e["refs"]:
                    del e["refs"]

    def gen_skills(self):
        notes = self.pages.get("skills", {})
        ms = {"max": self.cfg["MAX_LEVEL"], "trunk": self.trunk_levels,
              "marks": [[self.cfg["FIRST_SPECIALIZATION_LEVEL"], "1st spec"], [self.cfg["SECOND_SPECIALIZATION_LEVEL"], "2nd spec"],
                        [self.cfg["MAX_LEVEL"], "Mastery"]]}
        for sk in self.skills:
            sid, low = sk["name"], sk["name"].lower()
            cat = str(sk["args"][0]).lower()
            name = self.skill_name(sid)
            n = notes.get(low, {})
            if not n:
                warn(f"pages.json has no XP summary for the skill {low}")
            facts = [["Category", self.L(f"category.{MOD}.{cat}", humanize(cat))]]
            if sid in self.opposite:
                facts.append(["Opposing skill", self.skill_name(self.opposite[sid])])
            trunk = [f"{lvl} {self.L(f'trunk.{MOD}.{low}.{lvl}', '?')}" for lvl in self.trunk_levels]
            facts.append(["Trunk bonuses", " · ".join(trunk)])
            specs = [c for c in self.specs if c["args"] and c["args"][0] == sid]
            facts.append(["Specializations", " · ".join(
                f"{self.spec_name(c['name'])} ({self.L(f'spec.{MOD}.{c['name'].lower()}.ability', '?')})" for c in specs)])
            perks = [c for c in self.perks if c["args"] and c["args"][0] == sid]
            facts.append(["Mastery perks", " or ".join(self.L(f"mastery.{MOD}.{c['name'].lower()}", humanize(c["name"])) for c in perks)])
            syn = []
            for c in self.synergies:
                a, b = c["args"][0], c["args"][1]
                if sid in (a, b):
                    other = b if a == sid else a
                    sname = self.L(f'synergy.{MOD}.{c["name"].lower()}', humanize(c["name"]))
                    if duo(c):
                        syn.append(f"{sname} (duo: your {self.skill_name(a)}, a party member's {self.skill_name(b)})")
                    else:
                        syn.append(f"{sname} (with {self.skill_name(other)})")
            if syn:
                facts.append(["Synergies", ", ".join(syn)])
            facts.append(["XP data file", f"config/{MOD}/xp/{low}.json", 1])
            text = self.expand(n.get("xp", ""))
            if n.get("note"):
                text += "\n" + self.expand(n["note"])
            refs = [f"xp-{low}"] + [f"spec-{c['name'].lower()}" for c in specs] + [f"mastery-{c['name'].lower()}" for c in perks]
            self.add({"id": f"skill-{low}", "cat": "skill", "name": name, "alt": self.dutch.get(low, ""),
                      "kind": facts[0][1], "c": cat, "text": text, "facts": facts, "refs": refs, "ms": ms})

    def gen_xp(self):
        for sk in self.skills:
            sid, low = sk["name"], sk["name"].lower()
            values, tables = self.xp_layout.get(sid, ([], []))
            if not values and not tables:
                warn(f"no XP layout for {sid}")
                continue
            facts = [["Data file", f"config/{MOD}/xp/{low}.json", 1]]
            for v in values:
                f = self.xpf.get(v)
                if not f:
                    warn(f"XpTables has no {v}")
                    continue
                doc = player_text(field_doc(f))
                facts.append([humanize(v), human(f["value"]) + (f". {doc}" if doc else "") + f" (key {v.lower()})"])
            for key, table in tables:
                rows = self.xp_tables.get(table, [])
                facts.append([f"{key} table", " · ".join(f"{humanize(str(k).split(':')[-1])} {human(val)}" for k, val in rows)])
            name = self.skill_name(sid)
            self.add({"id": f"xp-{low}", "cat": "xp", "name": f"{name} XP", "alt": f"{self.dutch.get(low, '')} XP".strip(),
                      "kind": name, "c": str(sk["args"][0]).lower(),
                      "text": f"Every number {name} XP is counted with, as config/{MOD}/xp/{low}.json holds it. "
                              f"A table in the file is the whole table: an id it leaves out gives nothing; another mod's "
                              f"blocks, mobs and items count when listed as namespace:path.",
                      "facts": facts, "refs": [f"skill-{low}", "guide-mod-xp"]})

    def gen_specs(self):
        slot_by = {c["name"]: c for c in self.slots}
        reqs = {}
        for r in self.hardreqs:
            spec, slot, skill, level = (r["args"] + [None] * 4)[:4]
            reqs[(spec, slot)] = (skill, level)
        for c in self.specs:
            sid, low = c["name"], c["name"].lower()
            skill, cooldown, trig = (c["args"] + [None] * 3)[:3]
            k = f"spec.{MOD}.{low}"
            name = self.spec_name(sid)
            sname = self.skill_name(skill)
            facts = [["Skill", sname], ["Gear", self.L(k + ".gear", "")],
                     ["Chosen at", f"level {self.cfg['FIRST_SPECIALIZATION_LEVEL']} (first) or {self.cfg['SECOND_SPECIALIZATION_LEVEL']} (second)"]]
            ab = self.L(k + ".ability")
            if ab:
                facts.append([f"Ability: {ab}", self.L(k + ".ability.desc", "")])
                a_slots = [x for x in self.slots if str(x["args"][0]) == "ABILITY"]
                gated = [f"{roman(i + 1)} needs level {x['args'][6]}" for i, x in enumerate(a_slots) if x["args"][6]]
                costs = sorted({x["args"][4] for x in a_slots})
                facts.append(["Ability ranks", f"I to {roman(len(a_slots))}, {join_and([str(x) for x in costs], 'or')} points each"
                              + (f" ({', '.join(gated)})" if gated else "") + ". " + self.L(k + ".ability.ranks", "")])
                t = self.triggers.get(trig, {})
                facts.append(["Ability key", f"R as a first specialization, Shift + R as a second. Reached with: {trigger_text(trig, t.get('doc', ''))}"])
                if cooldown:
                    facts.append(["Cooldown", f"{cooldown} minutes at rank I (cooldown_minutes in skills.json)"])
            price = self.L(k + ".tradeoff")
            if price:
                facts.append(["Price", price])
            for slot in self.slots:
                sl = slot["name"]
                if str(slot["args"][0]) == "ABILITY":
                    continue
                node = self.L(f"{k}.{sl.lower()}")
                if not node:
                    continue
                label = ("Choice " + ("A" if sl.endswith("1") else "B") if str(slot["args"][0]) == "CHOICE" else "Passive")
                desc = self.L(f"{k}.{sl.lower()}.desc", "")
                extra = f" {slot['args'][3]} ranks, {slot['args'][4]} points a rank."
                if (sid, sl) in reqs:
                    rs, rl = reqs[(sid, sl)]
                    if f"{self.skill_name(rs)} {rl}" not in desc:
                        extra += f" Works only with {self.skill_name(rs)} {rl}."
                facts.append([f"{label}: {node}", desc + extra])
            if (sid, None) in reqs:
                rs, rl = reqs[(sid, None)]
                facts.append(["Needs", f"{self.skill_name(rs)} {rl} for part of what it does (a hard requirement)."])
            other = self.conflicts.get(sid)
            if other:
                oc = next((x for x in self.specs if x["name"] == other), None)
                reason = self.L(f"{k}.conflict_reason") or self.L(f"spec.{MOD}.{other.lower()}.conflict_reason") or ""
                facts.append(["Excludes", f"{self.spec_name(other)} ({self.skill_name(oc['args'][0]) if oc else ''})"
                              + (f": {reason}" if reason else "")])
            if skill == "EXPLORATION":
                facts.append(["Heavy armor", self.L(f"gui.{MOD}.node.light_only", "")])
            style = self.L(k + ".style", "")
            refs = [f"skill-{skill.lower()}"] + ([f"spec-{other.lower()}"] if other else []) + ["concept-specs"]
            self.add({"id": f"spec-{low}", "cat": "spec", "name": name, "alt": self.dutch.get(skill.lower(), ""), "kind": sname,
                      "c": self.cat_of(skill), "text": style,
                      "facts": facts, "refs": refs})

    def gen_trunks(self):
        for sk in self.skills:
            low = sk["name"].lower()
            for lvl in self.trunk_levels:
                k = f"trunk.{MOD}.{low}.{lvl}"
                if k not in self.lang:
                    warn(f"no {k} in the language file")
                    continue
                self.add({"id": f"trunk-{low}-{lvl}", "cat": "trunk", "name": self.L(k), "alt": self.dutch.get(low, ""),
                          "kind": self.skill_name(sk["name"]), "c": str(sk["args"][0]).lower(), "text": self.L(k + ".desc", ""),
                          "facts": [["Skill", self.skill_name(sk["name"])], ["Unlocks", f"at level {lvl}, by itself (no points)"]],
                          "refs": [f"skill-{low}", "concept-trunk"]})

    def gen_mastery(self):
        for c in self.perks:
            low = c["name"].lower()
            skill, track = c["args"][0], c["args"][1]
            other = next((x for x in self.perks if x["args"][0] == skill and x["name"] != c["name"]), None)
            facts = [["Skill", self.skill_name(skill)],
                     ["Each rank in it", f"adds {pct(self.cfg['MASTERY_PERK_STEP'])} (mastery_perk_step)"],
                     ["Ranks", f"up to {self.cfg['MASTERY_RANKS']}, one choice per Mastery rank"]]
            if other:
                facts.append(["Or instead", self.L(f"mastery.{MOD}.{other['name'].lower()}", humanize(other["name"]))])
            self.add({"id": f"mastery-{low}", "cat": "mastery", "name": self.L(f"mastery.{MOD}.{low}", humanize(c["name"])),
                      "alt": self.dutch.get(skill.lower(), ""), "kind": self.skill_name(skill), "c": self.cat_of(skill),
                      "text": self.L(f"mastery.{MOD}.{low}.desc", c["doc"]), "facts": facts,
                      "refs": [f"skill-{skill.lower()}"] + ([f"mastery-{other['name'].lower()}"] if other else []) + ["concept-mastery"]})

    def gen_synergies(self):
        lvl = self.cfg["SYNERGY_LEVEL"]
        for c in self.synergies:
            low = c["name"].lower()
            a, b, strength = c["args"][0], c["args"][1], c["args"][2]
            k = f"synergy.{MOD}.{low}"
            is_duo = duo(c)
            if is_duo:
                facts = [["You", f"{self.skill_name(a)} at level {lvl}"],
                         ["A party member near you", f"{self.skill_name(b)} at level {lvl}"]]
            else:
                facts = [["Skills", f"{self.skill_name(a)} + {self.skill_name(b)}, both at level {lvl}"]]
            facts += [["Hint", self.L(k + ".hint1", "")], ["Clearer hint", self.L(k + ".hint2", "")],
                      ["Strength", strength_text(strength, self.strength_is.get(c['name'], self.strength_default))],
                      ["In synergies.json", f"{low}: enabled, strength", 1]]
            alt = " ".join(x for x in (self.dutch.get(a.lower()), self.dutch.get(b.lower())) if x)
            self.add({"id": f"synergy-{low}", "cat": "synergy", "name": self.L(k, humanize(c["name"])), "alt": alt,
                      "kind": "Duo synergy" if is_duo else "Synergy", "c": f"{self.cat_of(a)},{self.cat_of(b)}",
                      "text": self.L(k + ".desc", c["doc"]), "facts": [f for f in facts if f[1]],
                      "refs": [f"skill-{a.lower()}", f"skill-{b.lower()}", "concept-duo" if is_duo else "concept-synergies", "concept-codex"]})

    def gen_keys(self):
        notes = self.pages.get("keys", {})
        for key, default in self.keys:
            short = key.split(".")[-1]
            self.add({"id": f"key-{short}", "cat": "screen", "name": self.L(key, humanize(short)), "kind": "Key",
                      "alt": notes.get(short, {}).get("alt", ""),
                      "text": self.expand(notes.get(short, {}).get("text", "")),
                      "facts": [["Default key", default.replace("_", " ")],
                                ["Change it", f"Options, Controls, Key Binds, under {self.L(f'key.category.{MOD}.main', 'Extreme Progression')}"]]})

    def gen_commands(self):
        notes = self.pages.get("commands", {})
        roots = self.roots
        # an alias: a whole subtree that is the same as one of the roots (/skills party = /party)
        alias, root_sigs = {}, {r.sig(): name for name, r in roots.items()}
        groups, order = {}, []

        def walk(node, path, perm, under):
            perm = node.requires or perm
            path = path + [node]
            if len(path) > 1 and node.sig() in root_sigs and root_sigs[node.sig()] == node.name and roots[node.name] is not node:
                alias[node.name] = [p.name for p in path[:-1]]
                return
            if node.executes:
                lits = [i for i, p in enumerate(path) if p.kind == "literal"]
                cut = lits[-1] + 1
                key = " ".join(p.name for p in path[:cut])
                if key not in groups:
                    groups[key] = {"lits": path[:cut], "variants": [], "perm": perm, "root": under, "refs": set()}
                    order.append(key)
                g = groups[key]
                g["variants"].append(path[cut:])
                g["perm"] = g["perm"] or perm
                if node.exec_ref:
                    g["refs"].add(node.exec_ref)
            for ch in node.children:
                walk(ch, path, perm, under)

        for name, r in roots.items():
            walk(r, [], None, name)
        kinds = {"Player": 0, "Party": 1, "Admin": 2}
        made = []
        for key in order:
            g = groups[key]
            variants = sorted(g["variants"], key=len)
            chain = all(variants[i + 1][:len(variants[i])] == variants[i] for i in range(len(variants) - 1))
            if chain:
                req = len(variants[0])
                args = variants[-1]
                usage = "/" + key + "".join(f" <{a.name}>" if i < req else f" [{a.name}]" for i, a in enumerate(args))
                usages = [usage]
            else:
                usages = ["/" + key + "".join(f" <{a.name}>" for a in v) for v in variants]
                args = [a for v in variants for a in v]
            facts = [["Usage", " · ".join(usages), 1]]
            seen = set()
            for a in args:
                if a.name in seen:
                    continue
                seen.add(a.name)
                facts.append([f"<{a.name}>", self.arg_text(a)])
            perm = g["perm"]
            if perm:
                nodes = [x for x in self.perms if x["name"] in perm]
                facts.append(["Permission", " or ".join(f"{MOD}.{x['args'][0].replace('/', '.')}" for x in nodes), 1])
                facts.append(["Without a permission mod", " or ".join(sorted({f'operator level {OP_LEVELS.get(x["args"][1], "?")}' for x in nodes}))
                              + "; in a world of your own, always"])
                kind = "Admin"
            else:
                facts.append(["Who", "Everyone"])
                kind = "Party" if g["root"] == "party" or "party" in key.split() else "Player"
            if g["root"] in alias:
                facts.append(["Also", " · ".join("/" + " ".join(alias[g["root"]]) + " " + u[1:] for u in usages), 1])
            text = notes.get(key)
            if text is None:
                docs = [m.doc for m in self.cmd.methods if m.name in g["refs"] and m.doc]
                text = player_text(docs[0]) if docs else ""
                if not text:
                    warn(f"pages.json has no description for the command /{key}")
            refs = [f"perm-{x['name'].lower()}" for x in self.perms if perm and x["name"] in perm]
            made.append((kinds[kind], len(made), {"id": "command-" + key.replace(" ", "-"), "cat": "command", "name": "/" + key,
                                                  "kind": kind, "text": self.expand(text), "facts": facts, "refs": refs,
                                                  "_perm": perm, "_usage": usages[0]}))
        made.sort(key=lambda x: (x[0], x[1]))
        self.commands = [m[2] for m in made]
        for e in self.commands:
            self.add(e)

    def arg_text(self, a):
        s, t = a.suggest, a.argtype
        skills = ", ".join(x["name"].lower() for x in self.skills)
        if "getOnlinePlayerNames" in s or "getPlayerNames" in s:
            return "the name of a player who is online"
        if "knownNames" in s:
            return "the name of any player the world knows, online or not"
        if "members" in s:
            return "a member of your party"
        if "invites" in s:
            return "the player who invited you"
        if ('"all"' in s or "all" in s.split()) and ("Skill" in s or "skillIds" in s):
            return f"all, or one skill: {skills}"
        if "skillIds" in s or re.search(r"\bSkill\s*\.\s*values", s):
            return f"a skill: {skills}"
        m = re.search(r"(\w+)\s*\.\s*values\s*\(\s*\)", s)
        if m:
            ids = self.enum_ids(m.group(1))
            if ids:
                return join_and(ids, "or")
        m = re.search(r"(integer|doubleArg|longArg|floatArg)\s*\(([^)]*)\)", t)
        if m:
            kind = "a whole number" if m.group(1) in ("integer", "longArg") else "a number"
            nums = [x.strip() for x in m.group(2).split(",") if x.strip()]
            if len(nums) == 2:
                return f"{kind} from {human(number(nums[0]))} to {human(number(nums[1]))}"
            if len(nums) == 1:
                return f"{kind}, at least {human(number(nums[0]))}"
            return kind
        if "word" in t:
            return "a word"
        return t or "a value"

    def enum_ids(self, name):
        """The ids of an enum anywhere in the main sources (constant names in lower case)."""
        if not hasattr(self, "_enum_cache"):
            self._enum_cache = {}
        if name not in self._enum_cache:
            ids = []
            for path in self.src.files(MAIN.rstrip("/"), ".java"):
                text = self.src.read(path) or ""
                if re.search(r"\benum\s+" + name + r"\b", text):
                    ids = [c["name"].lower() for c in parse_enum(text, name)]
                    break
            self._enum_cache[name] = ids
        return self._enum_cache[name]

    def gen_perms(self):
        notes = self.pages.get("permissions", {})
        for c in self.perms:
            path, level = c["args"][0], c["args"][1]
            dotted = f"{MOD}.{path.replace('/', '.')}"
            cmds = [e["_usage"] for e in self.commands if e["_perm"] and c["name"] in e["_perm"]]
            facts = [["Id", f"{MOD}:{path}", 1], ["Without a permission mod", f"operator level {OP_LEVELS.get(level, '?')}"]]
            if cmds:
                facts.append(["Commands", " · ".join(cmds)])
            facts.append(["LuckPerms", f"/lp group <group> permission set {dotted} true", 1])
            self.add({"id": f"perm-{c['name'].lower()}", "cat": "permission", "name": dotted, "kind": "Permission",
                      "alt": notes.get(c["name"].lower(), {}).get("alt", "rechten"),
                      "text": player_text(c["doc"]) + (" " + self.expand(notes[c["name"].lower()]["text"]) if c["name"].lower() in notes else ""),
                      "facts": facts, "refs": ["concept-permissions", "guide-luckperms"]
                      + [e["id"] for e in self.commands if e["_perm"] and c["name"] in e["_perm"]]})
        for e in self.commands:
            e.pop("_perm", None)
            e.pop("_usage", None)

    def gen_placeholders(self):
        readme = self.src.read("server/README.md") or ""
        sec = re.search(r"^## Every placeholder\s*$([\s\S]*?)(?=^## |\Z)", readme, re.M)
        rows = []
        if sec:
            for line in sec.group(1).splitlines():
                cells = [x.strip() for x in line.strip().strip("|").split("|")] if line.strip().startswith("|") else []
                if len(cells) >= 2 and "%" in cells[0]:
                    names = re.findall(r"%" + MOD + r":([a-z_<>]+)%", cells[0])
                    if names:
                        rows.append((names, re.sub(r"`([^`]*)`", r"\1", cells[1])))
        else:
            warn("server/README.md has no table of placeholders")
        known = {n for names, _ in rows for n in names}
        for name in self.tab_lists.get("PLAYER", []) + self.tab_lists.get("WORLD", []):
            if name not in known:
                rows.append(([name], ""))
                warn(f"the placeholder {name} is not in server/README.md's table")
        world = set(self.tab_lists.get("WORLD", []))
        skills = ", ".join(x["name"].lower() for x in self.skills)
        for names, what in rows:
            facts = [["Placeholder", " · ".join(f"%{MOD}:{n}%" for n in names), 1],
                     ["About", "the server" if all(n in world for n in names) else "each player"]]
            if any("<skill>" in n for n in names):
                facts.append(["<skill>", skills])
            what = player_text(what)
            text = what[:1].upper() + what[1:] + ("." if what and not what.endswith(".") else "")
            self.add({"id": "placeholder-" + re.sub(r"[^a-z0-9_]+", "", names[0]), "cat": "placeholder",
                      "name": ", ".join(names), "kind": "Server" if facts[1][1] == "the server" else "Player",
                      "alt": "plaatshouder", "text": text, "facts": facts, "refs": ["concept-tab"]})

    def gen_settings(self):
        notes = self.pages.get("settings", {})
        groups = self.pages.get("setting_groups", {})
        normal = {k.upper(): v for k, v in self.normal.items()}
        fields = {f["name"] for f in self.config}
        for f in self.config:
            key = f["name"].lower()
            group = next((g for p, g in groups.items() if f["name"].startswith(p)), None)
            if group is None:
                group = re.sub(r"^The ", "", player_text(f["section"] or "General")).strip() or "General"
                group = group[:1].upper() + group[1:]
            text = notes.get(key)
            text = self.expand(text) if text else player_text(field_doc(f))
            text = re.sub(r"\b([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+|[A-Z]{4,})\b", lambda m: m.group(1).lower() if m.group(1) in fields else m.group(1), text)
            if not text:
                warn(f"the setting {key} has no explanation (add one to pages.json, settings)")
            facts = [["Key", key, 1], ["Default", file_value(f), 1]]
            if f["name"] in normal:
                facts.append(["Hardcore preset", file_value(f), 1])
                facts.append(["Normal preset", file_value(dict(f, value=normal[f["name"]])), 1])
                facts.append(["Set by", "the preset, unless preset is custom"])
            facts.append(["File", f"config/{MOD}.json", 1])
            self.add({"id": f"setting-{key}", "cat": "setting", "name": humanize(key), "kind": group, "text": text,
                      "facts": facts, "refs": ["concept-presets"]})
        self.gen_data_files()

    def gen_data_files(self):
        s = self.src
        ss = s.read(MAIN + "config/SkillSettings.java") or ""
        doc = clean_doc((re.search(r"(/\*\*[\s\S]*?\*/)\s*public final class SkillSettings", ss) or [None, ""])[1] or "")
        skills = ", ".join(x["name"].lower() for x in self.skills)
        facts = [["File", f"config/{MOD}/skills.json", 1],
                 ["skills.<skill>", f"enabled (true) and xp_multiplier (1.0), for {skills}"],
                 ["abilities.<specialization>", "cooldown_minutes: " + " · ".join(f"{c['name'].lower()} {c['args'][1]}" for c in self.specs)],
                 ["requirements.<name>", "skill and level: " + " · ".join(
                     f"{r['name'].lower()} {str(r['args'][2]).lower()} {r['args'][3]}" for r in self.hardreqs)]]
        notes = self.pages.get("settings", {})
        self.add({"id": "setting-skills_json", "cat": "setting", "name": "skills.json", "kind": "Data file",
                  "text": self.expand(notes.get("skills.json", "")) or player_text(doc), "facts": facts,
                  "refs": ["concept-presets", "concept-hardreq"]})
        facts = [["File", f"config/{MOD}/synergies.json", 1],
                 ["<synergy>", "enabled (true or false) and strength"],
                 ["Strengths", " · ".join(f"{c['name'].lower()} {human(c['args'][2])}" for c in self.synergies)],
                 ["Strength is", "; ".join([f"for {self.L(f'synergy.{MOD}.{k.lower()}', humanize(k))} {v}" for k, v in self.strength_is.items()]
                                          + [f"otherwise a {self.strength_default}"])]]
        self.add({"id": "setting-synergies_json", "cat": "setting", "name": "synergies.json", "kind": "Data file",
                  "text": self.expand(notes.get("synergies.json", "Every synergy on or off, and its strength.")), "facts": facts,
                  "refs": ["concept-synergies"]})
        facts = [["Folder", f"config/{MOD}/xp/", 1]]
        for sk in self.skills:
            values, tables = self.xp_layout.get(sk["name"], ([], []))
            facts.append([f"{sk['name'].lower()}.json", ", ".join([v.lower() for v in values] + [t for t, _ in tables])])
        self.add({"id": "setting-xp_files", "cat": "setting", "name": "xp/<skill>.json", "kind": "Data file",
                  "text": self.expand(notes.get("xp/<skill>.json", "")) or player_text(self.xp_doc), "facts": facts,
                  "refs": [f"xp-{x['name'].lower()}" for x in self.skills] + ["guide-mod-xp"]})

    def add_pages(self):
        for p in self.pages.get("entries", []):
            e = {k: v for k, v in p.items() if not k.startswith("_")}
            e["text"] = self.expand(e.get("text", ""))
            if "steps" in e:
                e["steps"] = [self.expand(x) for x in e["steps"]]
            facts = []
            for f in e.get("facts", []):
                if isinstance(f, str) and f.startswith("@"):
                    facts += self.special_facts(f[1:])
                else:
                    facts.append([self.expand(f[0]), self.expand(f[1])] + list(f[2:]))
            e["facts"] = facts
            if e["cat"] not in dict(CATS):
                warn(f"pages.json: {e['id']} has the unknown category {e['cat']}")
            self.add(e)

    def special_facts(self, name):
        if name == "presets":
            return [[humanize(k.lower()), f"Hardcore {human(self.cfg[k])} · Normal {human(v)}"] for k, v in self.normal.items()]
        if name == "perms":
            return [[f"{MOD}.{c['args'][0].replace('/', '.')}", player_text(c["doc"]) + f" Without a permission mod: operator level {OP_LEVELS.get(c['args'][1], '?')}."]
                    for c in self.perms]
        if name == "hardreqs":
            out = []
            for r in self.hardreqs:
                spec, slot, skill, level = (r["args"] + [None] * 4)[:4]
                node = self.L(f"spec.{MOD}.{spec.lower()}.{slot.lower()}") if slot else None
                what = f"{node} ({self.spec_name(spec)})" if node else f"{self.spec_name(spec)}: {player_text(r['doc']).split(':')[0]}"
                out.append([what, f"{self.skill_name(skill)} {level}"])
            return out
        if name == "conflicts":
            seen, out = set(), []
            for a, b in self.conflicts.items():
                if (b, a) in seen:
                    continue
                seen.add((a, b))
                reason = self.L(f"spec.{MOD}.{a.lower()}.conflict_reason") or self.L(f"spec.{MOD}.{b.lower()}.conflict_reason") or ""
                out.append([f"{self.spec_name(a)} ↔ {self.spec_name(b)}", reason])
            return out
        if name == "opposing":
            seen, out = set(), []
            for a, b in self.opposite.items():
                if (b, a) not in seen:
                    seen.add((a, b))
                    out.append(["Pair", f"{self.skill_name(a)} ↔ {self.skill_name(b)}"])
            return out
        if name == "categories":
            out = []
            for c in self.categories:
                cid = c["name"]
                out.append([self.L(f"category.{MOD}.{cid.lower()}", humanize(cid)),
                            ", ".join(self.skill_name(s["name"]) for s in self.skills if str(s["args"][0]) == cid)])
            return out
        if name == "buttons":
            return []
        warn(f"pages.json: unknown fact list @{name}")
        return []


OP_LEVELS = {"ALL": 0, "MODERATORS": 1, "GAMEMASTERS": 2, "ADMINS": 3, "OWNERS": 4}

CATS = [["concept", "How it works"], ["guide", "How to"], ["skill", "Skills"], ["xp", "XP tables"],
        ["spec", "Specializations"], ["trunk", "Trunk bonuses"], ["mastery", "Mastery perks"], ["synergy", "Synergies"],
        ["screen", "Screens and HUD"], ["command", "Commands"], ["permission", "Permissions"],
        ["placeholder", "Placeholders"], ["setting", "Settings"]]


def strength_text(v, what):
    if what.startswith("share"):
        return f"{human(v)}: +{pct(v)}"
    return f"{human(v)} {what}"


def duo(c):
    return len(c["args"]) > 3 and c["args"][3] is True


def roman(n):
    return ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"][n] if n <= 10 else str(n)


def branch_cost(slots):
    total, choice = 0, False
    for s in slots:
        kind, cost, ranks = str(s["args"][0]), s["args"][4], s["args"][3]
        if kind == "CHOICE":
            if choice:
                continue
            choice = True
        total += cost * ranks
    return total


def trigger_text(name, doc):
    if doc:
        d = re.sub(r"\s*\([^)]*\)", "", doc).strip().rstrip(".")
        return d[:1].lower() + d[1:] if not d.startswith(("A ", "An ")) else d[:1].lower() + d[1:]
    if not name:
        return "anything"
    if name.startswith("LOOK_"):
        return article(humanize(name[5:]).lower()) + " in view"
    if name == "ANY":
        return "anything in hand"
    return humanize(name).lower()


# ------------------------------------------------------------------------------------------------------------ the output

def render(wiki, releases):
    template = (HERE / "template.html").read_text(encoding="utf-8")
    skillcats = [[c["name"].lower(), wiki.L(f"category.{MOD}.{c['name'].lower()}", humanize(c["name"]))] for c in wiki.categories]
    data = {"version": wiki.version, "cats": CATS, "skillcats": skillcats, "entries": wiki.entries, "changelog": releases}
    blob = json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    out = template.replace("__WIKI_DATA__", blob).replace("{{VERSION}}", html.escape(wiki.version))
    for k, v in wiki.pages.get("page", {}).items():
        out = out.replace("{{" + k.upper() + "}}", html.escape(wiki.expand(v)))
    left = re.findall(r"\{\{[A-Z_]+\}\}", out)
    if left:
        warn("the template still holds " + ", ".join(sorted(set(left))))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    ap.add_argument("--ref", help="build from this git revision (such as HEAD) instead of the working tree")
    ap.add_argument("--offline", action="store_true", help="no download links from GitHub")
    ap.add_argument("--out", default=str(HERE / "out" / "wiki.html"), help="where to write the page")
    args = ap.parse_args()
    src = Source(ROOT, args.ref)
    if not args.ref:
        st = subprocess.run(["git", "-C", str(ROOT), "status", "--porcelain", "--", "src", "CHANGELOG.md", "README.md",
                             "server", "gradle.properties"], capture_output=True, text=True)
        changed = [l[3:] for l in st.stdout.splitlines()] if st.returncode == 0 else []
        if changed:
            print(f"note: {len(changed)} uncommitted change(s) in the sources are included; --ref HEAD leaves them out",
                  file=sys.stderr)
    pages = json.loads((HERE / "pages.json").read_text(encoding="utf-8"))
    wiki = Wiki(src, pages, args.offline)
    wiki.read()
    wiki.build()
    releases = parse_changelog(src.need("CHANGELOG.md"))
    links = download_links(args.offline)
    for r in releases:
        r["dl"] = links.get("v" + r["v"], "")
    page = render(wiki, releases)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page, encoding="utf-8")
    counts = {}
    for e in wiki.entries:
        counts[e["cat"]] = counts.get(e["cat"], 0) + 1
    print(f"Wrote {out} ({out.stat().st_size / 1024:.0f} KB): version {wiki.version}, {len(wiki.entries)} entries, "
          f"{len(releases)} releases ({sum(1 for r in releases if r['dl'])} with a download)")
    print("  " + ", ".join(f"{dict(CATS).get(k, k)} {counts.get(k, 0)}" for k, _ in CATS))
    if WARNINGS:
        print(f"  {len(WARNINGS)} warning(s), above")


if __name__ == "__main__":
    main()

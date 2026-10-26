"""MarktSniper – zoekt op Marktplaats, 2dehands en Vinted en stuurt meldingen bij koopjes."""
import asyncio
import json
import os
import re
import sqlite3
import time
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from notifier import notify
from scrapers import adevinta_categories, search_all

DB = os.getenv("MARKTSNIPER_DB", "marktsniper.db")
HERE = os.path.dirname(os.path.abspath(__file__))

PRESETS = [
    {"name": "iPhone 13 / 14 / 15", "query": "iphone", "include": "iphone 13, iphone 14, iphone 15",
     "exclude": "hoesje, hoes, case, kapot, defect, onderdelen, scherm, icloud, glas, oplader, doosje, gezocht",
     "min_price": 100, "max_price": 400, "category_id": 820},
    {"name": "Laptop onder €250", "query": "laptop", "include": "",
     "exclude": "kapot, defect, onderdelen, tas, hoes, oplader, adapter, gezocht, toetsenbord",
     "min_price": 50, "max_price": 250, "category_id": 322},
    {"name": "PlayStation 5", "query": "ps5", "include": "ps5, playstation 5",
     "exclude": "game, spel, controller, hoes, kapot, defect, gezocht, fifa, fc",
     "min_price": 150, "max_price": 330, "category_id": 356},
    {"name": "Nintendo Switch OLED", "query": "switch oled", "include": "oled",
     "exclude": "game, spel, hoes, kapot, defect, gezocht, case", "min_price": 80, "max_price": 220,
     "category_id": 356},
    {"name": "AirPods Pro", "query": "airpods pro", "include": "airpods pro",
     "exclude": "case, hoesje, los, linker, rechter, kapot, nep, replica, gezocht",
     "min_price": 40, "max_price": 130, "category_id": None},
    {"name": "MacBook Air M1/M2", "query": "macbook air", "include": "m1, m2",
     "exclude": "kapot, defect, onderdelen, hoes, oplader, gezocht", "min_price": 250, "max_price": 600,
     "category_id": 322},
]


def db():
    con = sqlite3.connect(DB)
    con.row_factory = sqlite3.Row
    return con


def init_db():
    with db() as c:
        c.executescript("""
        CREATE TABLE IF NOT EXISTS watches(id INTEGER PRIMARY KEY, name TEXT, query TEXT, include TEXT,
            exclude TEXT, min_price REAL, max_price REAL, platforms TEXT, category_id INTEGER,
            active INTEGER DEFAULT 1, primed INTEGER DEFAULT 0, last_check REAL, last_error TEXT);
        CREATE TABLE IF NOT EXISTS hits(item_id TEXT, watch_id INTEGER, data TEXT, found_at REAL,
            notified INTEGER, PRIMARY KEY(item_id, watch_id));
        CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT);
        """)


def get_settings():
    with db() as c:
        s = {r["key"]: r["value"] for r in c.execute("SELECT * FROM settings")}
    s.setdefault("interval", "120")
    return s


def terms(text):
    return [t.strip().lower() for t in (text or "").split(",") if t.strip()]


def matches(watch, item):
    title = item["title"].lower()
    text = title + " " + item.get("description", "").lower()
    lo, hi = watch["min_price"] or 0, watch["max_price"] or 1e9
    if not (lo <= item["price"] <= hi) or item["price"] <= 0:
        return False
    inc = terms(watch["include"])
    # "iphone 14" moet als los woord voorkomen, zodat "iphone 14" niet matcht op "iphone 141"
    if inc and not any(re.search(r"\b" + re.escape(t) + r"\b", title) for t in inc):
        return False
    return not any(re.search(r"\b" + re.escape(t), text) for t in terms(watch["exclude"]))


def row_to_watch(r):
    w = dict(r)
    w["platforms"] = json.loads(w["platforms"])
    return w


async def check_watch(client, w, settings):
    items, errors = await search_all(client, w)
    new = []
    with db() as c:
        for it in items:
            if not matches(w, it):
                continue
            cur = c.execute("INSERT OR IGNORE INTO hits VALUES(?,?,?,?,?)",
                            (it["id"], w["id"], json.dumps(it), time.time(), int(bool(w["primed"]))))
            if cur.rowcount and w["primed"]:
                new.append(it)
        c.execute("UPDATE watches SET primed=1, last_check=?, last_error=? WHERE id=?",
                  (time.time(), json.dumps(errors) if errors else None, w["id"]))
    for it in new:
        await notify(client, w["name"], it, settings)
    return len(new)


async def poller():
    async with httpx.AsyncClient(follow_redirects=True) as client:
        while True:
            settings = get_settings()
            with db() as c:
                watches = [row_to_watch(r) for r in c.execute("SELECT * FROM watches WHERE active=1")]
            for w in watches:
                try:
                    await check_watch(client, w, settings)
                except Exception as e:  # noqa: BLE001
                    print("fout bij", w["name"], e)
                await asyncio.sleep(2)
            await asyncio.sleep(max(30, int(settings.get("interval", 120))))


@asynccontextmanager
async def lifespan(_):
    init_db()
    task = asyncio.create_task(poller())
    yield
    task.cancel()


app = FastAPI(title="MarktSniper", lifespan=lifespan)


class WatchIn(BaseModel):
    name: str
    query: str
    include: str = ""
    exclude: str = ""
    min_price: float = 0
    max_price: float = 250
    platforms: list[str] = ["marktplaats", "2dehands", "vinted"]
    category_id: int | None = None
    active: bool = True


@app.get("/")
def index():
    return FileResponse(os.path.join(HERE, "static", "index.html"))


@app.get("/api/presets")
def presets():
    return PRESETS


_cat_cache = []


@app.get("/api/categories")
async def categories():
    if not _cat_cache:
        async with httpx.AsyncClient() as client:
            try:
                _cat_cache.extend(await adevinta_categories(client))
            except Exception:  # noqa: BLE001
                pass
    return _cat_cache


@app.get("/api/watches")
def list_watches():
    with db() as c:
        out = []
        for r in c.execute("SELECT * FROM watches ORDER BY id DESC"):
            w = row_to_watch(r)
            w["hit_count"] = c.execute("SELECT COUNT(*) FROM hits WHERE watch_id=?", (w["id"],)).fetchone()[0]
            out.append(w)
    return out


def _save(w: WatchIn, wid=None):
    vals = (w.name, w.query, w.include, w.exclude, w.min_price, w.max_price,
            json.dumps(w.platforms), w.category_id, int(w.active))
    with db() as c:
        if wid is None:
            wid = c.execute("INSERT INTO watches(name,query,include,exclude,min_price,max_price,platforms,"
                            "category_id,active) VALUES(?,?,?,?,?,?,?,?,?)", vals).lastrowid
        else:
            # filters gewijzigd: opnieuw "primen" zodat je geen spam krijgt van oude advertenties
            c.execute("UPDATE watches SET name=?,query=?,include=?,exclude=?,min_price=?,max_price=?,"
                      "platforms=?,category_id=?,active=?,primed=0 WHERE id=?", vals + (wid,))
            c.execute("DELETE FROM hits WHERE watch_id=?", (wid,))
    return wid


@app.post("/api/watches")
async def create_watch(w: WatchIn):
    wid = _save(w)
    await run_now(wid)
    return {"id": wid}


@app.put("/api/watches/{wid}")
async def update_watch(wid: int, w: WatchIn):
    _save(w, wid)
    await run_now(wid)
    return {"ok": True}


@app.delete("/api/watches/{wid}")
def delete_watch(wid: int):
    with db() as c:
        c.execute("DELETE FROM watches WHERE id=?", (wid,))
        c.execute("DELETE FROM hits WHERE watch_id=?", (wid,))
    return {"ok": True}


@app.post("/api/watches/{wid}/run")
async def run_now(wid: int):
    with db() as c:
        r = c.execute("SELECT * FROM watches WHERE id=?", (wid,)).fetchone()
    if not r:
        raise HTTPException(404)
    async with httpx.AsyncClient(follow_redirects=True) as client:
        n = await check_watch(client, row_to_watch(r), get_settings())
    return {"new": n}


@app.get("/api/hits")
def hits(watch_id: int | None = None, limit: int = 200):
    q = "SELECT h.*, w.name AS watch_name FROM hits h JOIN watches w ON w.id=h.watch_id"
    args = []
    if watch_id:
        q += " WHERE watch_id=?"
        args.append(watch_id)
    q += " ORDER BY found_at DESC LIMIT ?"
    with db() as c:
        rows = c.execute(q, args + [limit]).fetchall()
    return [{**json.loads(r["data"]), "watch_id": r["watch_id"], "watch_name": r["watch_name"],
             "found_at": r["found_at"], "notified": bool(r["notified"])} for r in rows]


@app.get("/api/settings")
def read_settings():
    return get_settings()


@app.put("/api/settings")
def write_settings(s: dict[str, str]):
    with db() as c:
        for k, v in s.items():
            c.execute("INSERT OR REPLACE INTO settings VALUES(?,?)", (k, str(v)))
    return get_settings()


@app.post("/api/test-notification")
async def test_notification():
    async with httpx.AsyncClient() as client:
        await notify(client, "Test", {"title": "Dit is een testmelding van MarktSniper", "price": 1,
                                      "platform": "marktsniper", "location": "", "url": "https://ntfy.sh"},
                     get_settings())
    return {"ok": True}


app.mount("/static", StaticFiles(directory=os.path.join(HERE, "static")), name="static")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", 8000)))

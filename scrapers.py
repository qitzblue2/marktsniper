"""Zoekers voor Marktplaats, 2dehands en Vinted. Elke zoeker geeft een lijst dicts terug:
{id, platform, title, price, url, image, location, description}"""
import httpx

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/130.0 Safari/537.36", "Accept-Language": "nl-NL,nl;q=0.9"}

ADEVINTA = {"marktplaats": "https://www.marktplaats.nl", "2dehands": "https://www.2dehands.be"}


async def search_adevinta(client, platform, query, min_price, max_price, category_id=None):
    base = ADEVINTA[platform]
    params = {"query": query, "limit": 50, "offset": 0,
              "sortBy": "SORT_INDEX", "sortOrder": "DECREASING",
              "attributeRanges[]": f"PriceCents:{int((min_price or 0) * 100)}:{int((max_price or 999999) * 100)}"}
    if category_id:
        params["l1CategoryId"] = category_id
    r = await client.get(f"{base}/lrp/api/search", params=params, headers=UA, timeout=20)
    r.raise_for_status()
    out = []
    for l in r.json().get("listings", []):
        cents = (l.get("priceInfo") or {}).get("priceCents") or 0
        img = (l.get("imageUrls") or [None])[0]
        out.append({
            "id": f"{platform}:{l['itemId']}", "platform": platform,
            "title": l.get("title", ""), "price": cents / 100,
            "url": base + l.get("vipUrl", ""),
            "image": ("https:" + img) if img and img.startswith("//") else img,
            "location": (l.get("location") or {}).get("cityName", ""),
            "description": l.get("description", "")[:300],
        })
    return out


async def adevinta_categories(client, platform="marktplaats"):
    r = await client.get(f"{ADEVINTA[platform]}/lrp/api/search", params={"query": "a", "limit": 1}, headers=UA, timeout=20)
    r.raise_for_status()
    return [{"id": c["id"], "name": c["name"]} for c in r.json().get("searchCategoryOptions", [])]


class Vinted:
    """Vinted vereist eerst een sessie-cookie van de homepage."""
    base = "https://www.vinted.nl"

    def __init__(self):
        self.has_cookie = False

    async def search(self, client, query, min_price, max_price):
        if not self.has_cookie:
            await client.get(self.base, headers=UA, timeout=20)
            self.has_cookie = True
        params = {"search_text": query, "order": "newest_first", "per_page": 50}
        if min_price:
            params["price_from"] = min_price
        if max_price:
            params["price_to"] = max_price
        r = await client.get(f"{self.base}/api/v2/catalog/items", params=params,
                             headers={**UA, "Accept": "application/json"}, timeout=20)
        if r.status_code in (401, 403):
            self.has_cookie = False
        r.raise_for_status()
        out = []
        for it in r.json().get("items", []):
            price = it.get("price")
            price = float(price.get("amount", 0)) if isinstance(price, dict) else float(price or 0)
            out.append({
                "id": f"vinted:{it['id']}", "platform": "vinted",
                "title": it.get("title", ""), "price": price,
                "url": it.get("url") or f"{self.base}/items/{it['id']}",
                "image": (it.get("photo") or {}).get("url"),
                "location": "", "description": it.get("brand_title") or "",
            })
        return out


vinted = Vinted()


async def search_all(client, watch):
    """Zoek op alle aangevinkte platforms. Fouten per platform worden teruggegeven, niet gegooid."""
    results, errors = [], {}
    for p in watch["platforms"]:
        try:
            if p in ADEVINTA:
                results += await search_adevinta(client, p, watch["query"], watch["min_price"],
                                                 watch["max_price"], watch.get("category_id"))
            elif p == "vinted":
                results += await vinted.search(client, watch["query"], watch["min_price"], watch["max_price"])
        except Exception as e:  # noqa: BLE001
            errors[p] = str(e)[:200]
    return results, errors

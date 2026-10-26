"""Meldingen via ntfy (gratis push-app) en/of Telegram."""
import os
import httpx

NTFY_TOPIC = os.getenv("NTFY_TOPIC", "")
NTFY_SERVER = os.getenv("NTFY_SERVER", "https://ntfy.sh")
TG_TOKEN = os.getenv("TELEGRAM_TOKEN", "")
TG_CHAT = os.getenv("TELEGRAM_CHAT_ID", "")


async def notify(client: httpx.AsyncClient, watch_name: str, item: dict, settings: dict):
    topic = settings.get("ntfy_topic") or NTFY_TOPIC
    title = f"🎯 {watch_name}: €{item['price']:.0f}"
    body = f"{item['title']}\n{item['platform']} · {item['location']}".strip(" ·")
    if topic:
        headers = {"Title": title.encode("utf-8"), "Click": item["url"], "Tags": "moneybag"}
        if item.get("image"):
            headers["Attach"] = item["image"]
        try:
            await client.post(f"{NTFY_SERVER}/{topic}", content=body.encode(), headers=headers, timeout=15)
        except Exception as e:  # noqa: BLE001
            print("ntfy fout:", e)
    token = settings.get("telegram_token") or TG_TOKEN
    chat = settings.get("telegram_chat_id") or TG_CHAT
    if token and chat:
        try:
            await client.post(f"https://api.telegram.org/bot{token}/sendMessage", timeout=15,
                              json={"chat_id": chat, "text": f"{title}\n{body}\n{item['url']}"})
        except Exception as e:  # noqa: BLE001
            print("telegram fout:", e)

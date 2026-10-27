"""A pretend Xtream Codes provider for the screenshot run, serving the invented catalogue.

Usage: python3 server.py <port> <dir with img/>
The emulator reaches it at http://10.0.2.2:<port>. Nothing here is a real provider or real content.
"""
import base64
import json
import os
import sys
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalogue import (  # noqa: E402
    EPISODE_PLOTS,
    HARBOUR_EPISODES,
    LIVE_GROUPS,
    MOVIE_CATEGORIES,
    MOVIES,
    PROGRAMMES,
    SERIES,
    SERIES_CATEGORIES,
)

MOVIE_BY_ID = {m[0]: m for m in MOVIES}
SERIES_BY_ID = {s[0]: s for s in SERIES}


def channels():
    n = 0
    out = []
    for gid, _name, names in LIVE_GROUPS:
        for name in names:
            n += 1
            out.append((gid, n, name))
    return out


class Handler(SimpleHTTPRequestHandler):
    def base(self):
        return f"http://{self.headers.get('Host')}"

    def img(self, name):
        path = os.path.join(self.directory, "img", name)
        return f"{self.base()}/img/{name}" if os.path.exists(path) else ""

    def send_json(self, value):
        body = json.dumps(value).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        if url.path == "/player_api.php":
            return self.send_json(self.api(q))
        if url.path == "/demo/seed.json":
            return self.send_json(self.seed(q.get("scenario", "many")))
        return super().do_GET()

    # ---- player_api.php ----

    def api(self, q):
        action = q.get("action")
        if action is None:
            return {
                "user_info": {"auth": 1, "status": "Active", "username": "demo", "exp_date": None,
                              "active_cons": "0", "max_connections": "1", "allowed_output_formats": ["ts", "m3u8"]},
                "server_info": {"timezone": "Europe/Dublin"},
            }
        if action == "get_live_categories":
            return [{"category_id": gid, "category_name": name} for gid, name, _ in LIVE_GROUPS]
        if action == "get_live_streams":
            return [
                {"stream_id": 500 + n, "num": n, "name": name, "stream_icon": self.img(f"logo-{n}.png"),
                 "epg_channel_id": f"ch{n}", "category_id": gid}
                for gid, n, name in channels() if gid == q.get("category_id")
            ]
        if action == "get_short_epg":
            n = int(q.get("stream_id", "500")) - 500
            now = int(time.time())
            start = now - (now % 1800) - 1800 + (n * 600) % 1800
            listings = []
            for i in range(8):
                length = 1800 * (1 + (n + i) % 3)
                title = PROGRAMMES[(n + i) % len(PROGRAMMES)]
                listings.append({
                    "title": base64.b64encode(title.encode()).decode(),
                    "description": base64.b64encode(f"{title} on demo channel {n}.".encode()).decode(),
                    "start_timestamp": str(start), "stop_timestamp": str(start + length),
                })
                start += length
            return {"epg_listings": listings}
        if action == "get_vod_categories":
            return [{"category_id": cid, "category_name": name} for cid, name, _ in MOVIE_CATEGORIES]
        if action == "get_series_categories":
            return [{"category_id": cid, "category_name": name} for cid, name, _ in SERIES_CATEGORIES]
        if action == "get_vod_streams":
            ids = next((ids for cid, _n, ids in MOVIE_CATEGORIES if cid == q.get("category_id")), [])
            # Like most panels: no synopsis or backdrop in the list, only in the details.
            return [
                {"stream_id": m[0], "name": m[1], "stream_icon": self.img(f"poster-{m[0]}.png"),
                 "rating": str(m[8]), "container_extension": "mp4"}
                for m in (MOVIE_BY_ID[i] for i in ids)
            ]
        if action == "get_vod_info":
            m = MOVIE_BY_ID.get(int(q.get("vod_id", "0")))
            if not m:
                return {}
            backdrop = self.img(f"backdrop-{m[0]}.png")
            return {
                "info": {"plot": m[4], "genre": m[5], "releasedate": f"{m[6]}-05-01", "duration_secs": m[7] * 60,
                         "rating": str(m[8]), "cast": "Aoife Byrne, Tom Keane, Maya Ortiz",
                         "director": "Ellen Doyle", "backdrop_path": [backdrop] if backdrop else [],
                         "youtube_trailer": "" if m[0] % 2 else "demoTrailer01"},
                "movie_data": {"container_extension": "mp4"},
            }
        if action == "get_series":
            ids = next((ids for cid, _n, ids in SERIES_CATEGORIES if cid == q.get("category_id")), [])
            out = []
            for s in (SERIES_BY_ID[i] for i in ids):
                backdrop = self.img(f"backdrop-{s[0]}.png")
                out.append({"series_id": s[0], "name": s[1], "cover": self.img(f"poster-{s[0]}.png"),
                            "plot": s[4], "genre": s[5], "releaseDate": f"{s[6]}-01-10", "rating": str(s[7]),
                            "backdrop_path": [backdrop] if backdrop else []})
            return out
        if action == "get_series_info":
            s = SERIES_BY_ID.get(int(q.get("series_id", "0")))
            if not s:
                return {}
            backdrop = self.img(f"backdrop-{s[0]}.png")
            return {
                "info": {"name": s[1], "plot": s[4], "genre": s[5], "releaseDate": f"{s[6]}-01-10",
                         "rating": str(s[7]), "cast": "Niamh Walsh, Peter Hale, Sara Quinn",
                         "backdrop_path": [backdrop] if backdrop else [], "youtube_trailer": "demoTrailer01"},
                "episodes": self.episodes(s),
            }
        return []

    def episodes(self, s):
        sid = s[0]
        seasons = HARBOUR_EPISODES if sid == 3001 else {
            season: [f"{s[1]} - S{season:02d}E{e:02d} - Part {e}" for e in range(1, 7)] for season in range(1, s[8] + 1)
        }
        out = {}
        for season, titles in seasons.items():
            out[str(season)] = [
                {"id": str(sid * 1000 + season * 100 + e), "episode_num": e, "season": season, "title": title,
                 "container_extension": "mp4",
                 "info": {"plot": EPISODE_PLOTS[(e - 1) % len(EPISODE_PLOTS)], "duration_secs": (42 + e % 4) * 60,
                          "movie_image": self.img(f"still-{sid}-{season}-{e}.png")}}
                for e, title in enumerate(titles, start=1)
            ]
        return out

    # ---- what the demo set-up screen writes before the app opens ----

    def seed(self, scenario):
        base = self.base()
        now = int(time.time() * 1000)

        def movie_point(mid, fraction, age):
            m = MOVIE_BY_ID[mid]
            backdrop = self.img(f"backdrop-{mid}.png")
            return {"key": f"movie:{mid}", "title": m[1], "subtitle": "", "poster": self.img(f"poster-{mid}.png"),
                    "url": f"{base}/movie/demo/demo/{mid}.mp4", "position": int(m[7] * 60000 * fraction),
                    "duration": m[7] * 60000, "updated": now - age, "seriesId": -1,
                    # Older saved points have no wide picture; one is left without, to show the fallback.
                    "image": backdrop if mid != 2003 else ""}

        def episode_point(sid, season, e, title, fraction, age):
            s = SERIES_BY_ID[sid]
            eid = sid * 1000 + season * 100 + e
            return {"key": f"episode:{eid}", "title": s[1], "subtitle": f"S{season} E{e} · {title}",
                    "poster": self.img(f"poster-{sid}.png"), "url": f"{base}/series/demo/demo/{eid}.mp4",
                    "position": int(43 * 60000 * fraction), "duration": 43 * 60000, "updated": now - age,
                    "seriesId": sid, "image": self.img(f"still-{sid}-{season}-{e}.png")}

        if scenario == "single":
            resume = [movie_point(2001, 0.42, 1000), episode_point(3001, 1, 2, "Low Tide", 0.35, 2000)]
        else:
            resume = [
                movie_point(2001, 0.42, 1000), episode_point(3001, 1, 2, "Low Tide", 0.35, 2000),
                movie_point(2005, 0.18, 3000), episode_point(3002, 1, 3, "Part 3", 0.7, 4000),
                movie_point(2003, 0.66, 5000), episode_point(3005, 1, 1, "Part 1", 0.2, 6000),
                movie_point(2009, 0.55, 7000),
            ]
        favourites = [
            {"kind": "MOVIE", "id": 2002, "name": "Northern Lights", "poster": self.img("poster-2002.png"),
             "categoryId": "10", "extension": "mp4", "rating": 7.6},
            {"kind": "MOVIE", "id": 2007, "name": "A House by the Sea", "poster": self.img("poster-2007.png"),
             "categoryId": "11", "extension": "mp4", "rating": 7.2},
            {"kind": "SERIES", "id": 3005, "name": "Far Country", "poster": self.img("poster-3005.png"),
             "categoryId": "20", "rating": 8.4},
        ]
        return {"server": base, "username": "demo", "password": "demo", "resume": resume, "favourites": favourites}


if __name__ == "__main__":
    port = int(sys.argv[1])
    directory = os.path.abspath(sys.argv[2])
    ThreadingHTTPServer(("0.0.0.0", port), lambda *a, **k: Handler(*a, directory=directory, **k)).serve_forever()

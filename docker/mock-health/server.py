from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

STATE = {"healthy": True}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)

        if parsed.path == "/health":
            if STATE["healthy"]:
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b'{"status":"ok"}')
            else:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(b'{"status":"fail"}')
            return

        if parsed.path == "/toggle":
            params = parse_qs(parsed.query)
            healthy = params.get("healthy", [None])[0]
            if healthy is None:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(b'missing healthy query param')
                return

            STATE["healthy"] = healthy.lower() == "true"
            self.send_response(200)
            self.end_headers()
            body = ('{"healthy": %s}' % ('true' if STATE["healthy"] else 'false')).encode()
            self.wfile.write(body)
            return

        self.send_response(404)
        self.end_headers()

    def log_message(self, format, *args):
        return


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 18080), Handler).serve_forever()

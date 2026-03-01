from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

DEFAULT_COMPONENTS = ["mailer"]
STATE = {name: True for name in DEFAULT_COMPONENTS}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)

        if parsed.path == "/health":
            component = "mailer"
            if STATE.get(component, True):
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b'{"status":"ok"}')
            else:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(b'{"status":"fail"}')
            return

        if parsed.path.startswith("/health/"):
            component = parsed.path.split("/", 2)[2]
            healthy = STATE.get(component, True)
            if healthy:
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
            component = params.get("component", ["mailer"])[0]
            if healthy is None:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(b'missing healthy query param')
                return

            STATE[component] = healthy.lower() == "true"
            self.send_response(200)
            self.end_headers()
            body = (
                '{"component":"%s","healthy": %s}'
                % (component, 'true' if STATE[component] else 'false')
            ).encode()
            self.wfile.write(body)
            return

        self.send_response(404)
        self.end_headers()

    def log_message(self, format, *args):
        return


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 18080), Handler).serve_forever()

from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

DEFAULT_COMPONENTS = ["mailer", "homepage", "sitemap"]
STATE = {name: True for name in DEFAULT_COMPONENTS}


class Handler(BaseHTTPRequestHandler):
    def _send_text(self, status, body, content_type):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.end_headers()
        self.wfile.write(body.encode())

    def do_GET(self):
        parsed = urlparse(self.path)

        if parsed.path == "/health":
            component = "mailer"
            if STATE.get(component, True):
                self._send_text(200, '{"status":"ok"}', "application/json")
            else:
                self._send_text(500, '{"status":"fail"}', "application/json")
            return

        if parsed.path.startswith("/health/"):
            component = parsed.path.split("/", 2)[2]
            healthy = STATE.get(component, True)
            if healthy:
                self._send_text(200, '{"status":"ok"}', "application/json")
            else:
                self._send_text(500, '{"status":"fail"}', "application/json")
            return

        if parsed.path == "/checks/html":
            component = "homepage"
            if STATE.get(component, True):
                self._send_text(
                    200,
                    "<html><head><title>Example</title></head><body>OK</body></html>",
                    "text/html; charset=utf-8",
                )
            else:
                self._send_text(
                    500,
                    "<html><head><title>Error</title></head><body>FAIL</body></html>",
                    "text/html; charset=utf-8",
                )
            return

        if parsed.path == "/checks/sitemap.xml":
            component = "sitemap"
            if STATE.get(component, True):
                self._send_text(
                    200,
                    '<?xml version="1.0" encoding="UTF-8"?><urlset><url><loc>https://example.com/</loc></url></urlset>',
                    "application/xml; charset=utf-8",
                )
            else:
                self._send_text(
                    500,
                    '<?xml version="1.0" encoding="UTF-8"?><error>unavailable</error>',
                    "application/xml; charset=utf-8",
                )
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

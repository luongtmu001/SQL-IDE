/*
 * Phase 0 benchmark server: serves the SAME pages to Edge (Chromium, what the Python app runs on) and to
 * JavaFX WebView (WebKit, what this app runs on) and stores what they measure in target/perf.
 *
 * Read only on the Python project: it only answers GET requests for files under its static/js.
 *
 *   node src/test/intellisense-perf/server.js [port]
 */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '../../..');
const PY_JS = process.env.PY_STATIC_JS || 'D:/Luong/NB_Personal/Python/IDE-SQL-Lite_New_Pyweb_Claude/static/js';
const JAVA_PAGE = path.join(ROOT, 'target/classes/monaco');
const OUT = path.join(ROOT, 'target/perf');
const PORT = Number(process.argv[2] || 8765);
fs.mkdirSync(OUT, { recursive: true });

const TYPES = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.ttf': 'font/ttf', '.woff2': 'font/woff2', '.svg': 'image/svg+xml' };
const received = {};

function sendFile(res, file) {
    fs.readFile(file, (err, data) => {
        if (err) { res.writeHead(404); res.end('not found ' + file); return; }
        res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
        res.end(data);
    });
}

/** The real editor.html of the app with only a base URL and the measuring script added. */
function editorPage(res) {
    let html = fs.readFileSync(path.join(JAVA_PAGE, 'editor.html'), 'utf8');
    html = html.replace('<head>', '<head><base href="/java/">');
    html = html.replace('<script src="vs/loader.js"></script>', '<script src="/bench/editor-bench.js"></script><script src="vs/loader.js"></script>');
    res.writeHead(200, { 'Content-Type': 'text/html', 'Cache-Control': 'no-store' });
    res.end(html);
}

http.createServer((req, res) => {
    const url = new URL(req.url, 'http://x');
    const p = decodeURIComponent(url.pathname);
    if (req.method === 'POST' && p === '/result') {
        let body = '';
        req.on('data', (d) => { body += d; });
        req.on('end', () => {
            const name = url.searchParams.get('name');
            fs.writeFileSync(path.join(OUT, name + '.json'), body);
            received[name] = true;
            console.log('[result] ' + name + ' ' + body.length + ' bytes');
            res.writeHead(200);
            res.end('ok');
        });
        return;
    }
    if (p === '/status') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(received));
        return;
    }
    if (p === '/bench/editor') return editorPage(res);
    if (p.startsWith('/bench/')) return sendFile(res, path.join(__dirname, p.slice('/bench/'.length)));
    if (p.startsWith('/java/')) return sendFile(res, path.join(JAVA_PAGE, p.slice('/java/'.length)));
    if (p.startsWith('/py/')) return sendFile(res, path.join(PY_JS, p.slice('/py/'.length)));
    res.writeHead(404);
    res.end();
}).listen(PORT, '127.0.0.1', () => console.log('perf server on ' + PORT));

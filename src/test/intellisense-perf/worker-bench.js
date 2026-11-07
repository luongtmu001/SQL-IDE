/* The analyzer of this project inside a Web Worker: the same text as engine.html's 16KB measurement. */
self.window = self;
self.performance = self.performance || { now: Date.now };
['sql-dialects', 'sql-tokenizer', 'scope-resolver', 'context-analyzer'].forEach(function (f) {
    importScripts('/java/intellisense/engine/' + f + '.js');
});
var line = "SELECT o.OrderID, c.CustomerName, o.Total FROM dbo.Orders o INNER JOIN dbo.Customers c ON o.CustomerID = c.CustomerID WHERE o.Total > 100.0;\n";
var text = '';
while (text.length < 16000) text += line;
text += 'SELECT o. FROM dbo.Orders o;';
var off = text.indexOf('SELECT o. FROM') + 9, t = [];
self.SqlContextAnalyzer.analyze(text, off, 'sqlserver');
for (var i = 0; i < 25; i++) {
    var variant = text.slice(0, off) + 'x'.repeat(i % 3) + text.slice(off);
    var t0 = performance.now();
    self.SqlContextAnalyzer.analyze(variant, off + (i % 3), 'sqlserver');
    t.push(performance.now() - t0);
}
t.sort(function (a, b) { return a - b; });
self.postMessage({ analyze16KB: { n: t.length, median: t[12], max: t[24] } });

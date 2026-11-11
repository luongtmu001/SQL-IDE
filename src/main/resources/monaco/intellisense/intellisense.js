/*
 * Port of LLTool static/js/intellisense.js: data provider, popup and controller.
 * The candidate pipeline itself is the unchanged engine in intellisense/engine/.
 *
 * Differences from the Python version:
 *  - metadata only through the Java provider (no /api fetches and no second set of Map caches)
 *  - detail panel: can be turned off, or shown on demand with the arrow keys (Settings)
 *  - names are escaped, the list is built in one go, a slow detail answer never overwrites a newer selection
 *  - the Script tab loads its definition only when it is open, and is coloured with Monaco
 *  - analyze runs after a short coalescing delay instead of on every keystroke
 */
(function (window) {
    "use strict";

    var Host = window.IntelliSenseHost;

    var ICON_MAP = {
        table: "symbol-class", view: "symbol-interface", procedure: "symbol-method", function: "symbol-function",
        trigger: "symbol-event", synonym: "symbol-reference", sequence: "symbol-number", column: "symbol-field",
        pk_column: "key", keyword: "symbol-keyword", sql_function: "symbol-function", temp_table: "symbol-struct",
        table_variable: "symbol-struct", scalar_variable: "symbol-variable", parameter: "symbol-variable",
        data_type: "symbol-type-parameter", user_data_type: "symbol-type-parameter", snippet: "symbol-snippet",
        join_condition: "symbol-reference", linked_server: "symbol-package", foreign_server: "symbol-package",
        remote_table: "symbol-class", remote_view: "symbol-interface", remote_column: "symbol-field",
        remote_database: "symbol-module", remote_schema: "symbol-namespace", database: "symbol-module", schema: "symbol-namespace"
    };

    var DEFINITION_TYPES = ["table", "view", "procedure", "function", "trigger"];
    var LIST_WIDTH = 420, DETAIL_WIDTH = 320, POPUP_HEIGHT = 280, MIN_HEIGHT = 130, EDGE = 8;
    var LEAVE_THRESHOLD = 35;
    var DETAIL_LOAD_DELAY_MS = 120;
    /** Longer scripts are shown as plain text: colouring runs on the FX thread. */
    var COLORIZE_LIMIT = 30000;
    var LARGE_POOL = 1500, LARGE_POOL_DELAY_MS = 120;

    function esc(s) {
        return String(s == null ? "" : s).replace(/[&<>"']/g, function (m) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[m];
        });
    }

    function icon(type, extra) {
        return '<i class="codicon codicon-' + (ICON_MAP[type] || "symbol-misc") + (extra ? " " + extra : "") + '" aria-hidden="true"></i>';
    }

    function formatDisplayType(type) {
        return String(type || "").charAt(0).toUpperCase() + String(type || "").slice(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. DATA PROVIDER: AppMetadataCache in front of the Java provider
    // ─────────────────────────────────────────────────────────────────────────
    class IntelliSenseDataProvider {
        get cache() { return window.AppMetadataCache || null; }
        get provider() { return window.AppMetadataProvider; }

        /** Errors are returned as they are but never cached. */
        _load(cached, loader, store) {
            if (cached) return Promise.resolve(cached);
            return loader().then(function (items) {
                if (items && !items.hasError && !items.loading) store(items);
                return items || [];
            });
        }

        fetchObjects(c, db, schema) {
            if (!c) return Promise.resolve([]);
            var m = this.cache;
            return this._load(m.getObjects(c, db, schema), () => this.provider.getObjects(c, db, schema), (r) => m.setObjects(c, db, schema, r));
        }
        fetchColumns(c, db, schema, table) {
            if (!c || !table) return Promise.resolve([]);
            var m = this.cache;
            return this._load(m.getColumns(c, db, schema, table), () => this.provider.getColumns(c, db, schema, table), (r) => m.setColumns(c, db, schema, table, r));
        }
        fetchParameters(c, db, schema, name) {
            if (!c || !name) return Promise.resolve([]);
            var m = this.cache;
            return this._load(m.getParameters(c, db, schema, name), () => this.provider.getParameters(c, db, schema, name), (r) => m.setParameters(c, db, schema, name, r));
        }
        fetchDefinition(c, db, schema, name, type) {
            if (!c || !name) return Promise.resolve(null);
            var m = this.cache;
            var cached = m.getDefinition(c, db, schema, name, type);
            if (cached) return Promise.resolve(cached);
            return this.provider.getDefinition(c, db, schema, name, type).then(function (def) {
                if (def) m.setDefinition(c, db, schema, name, type, def);
                return def || null;
            });
        }
        fetchForeignKeys(c, db, schema, table) {
            if (!c) return Promise.resolve([]);
            var m = this.cache;
            return this._load(m.getForeignKeys(c, db, schema, table), () => this.provider.getForeignKeys(c, db, schema, table), (r) => m.setForeignKeys(c, db, schema, table, r));
        }
        fetchDataTypes(c, db) {
            var types = window.SqlContextAnalyzer.SQL_DATA_TYPES || [];
            if (!c) return Promise.resolve({ types: types, user_types: [] });
            var m = this.cache;
            var cached = m.getSupportedTypes(c, db);
            if (cached) return Promise.resolve(cached);
            return this.provider.getUserTypes(c, db, window.ActiveSchema).then(function (user) {
                var res = { types: types, user_types: user || [] };
                if (!user || !user.hasError) m.setSupportedTypes(c, db, res);
                return res;
            });
        }
        getDatabases(c) {
            if (!c) return Promise.resolve([]);
            var m = this.cache;
            var cached = m.getDatabases(c);
            if (cached && cached.length) return Promise.resolve(cached);
            return this.provider.getDatabases(c).then(function (r) { if (r && !r.hasError) m.setDatabases(c, r); return r || []; });
        }
        fetchDatabases(c) { return this.getDatabases(c); }
        getSchemas(c, db) {
            if (!c) return Promise.resolve([]);
            var m = this.cache;
            var cached = m.getSchemas(c, db);
            if (cached && cached.length) return Promise.resolve(cached);
            return this.provider.getSchemas(c, db).then(function (r) { if (r && !r.hasError) m.setSchemas(c, db, r); return r || []; });
        }
        fetchSchemas(c, db) { return this.getSchemas(c, db); }
        getLinkedServers(c) { return this.provider.getLinkedServers(c); }
        getRemoteDatabases(c, server) { return this.provider.getRemoteDatabases(c, server); }
        getRemoteSchemas(c, server, db) { return this.provider.getRemoteSchemas(c, server, db); }
        getRemoteObjects(c, server, db, schema) { return this.provider.getRemoteObjects(c, server, db, schema); }
        getRemoteColumns(c, server, db, schema, table) { return this.provider.getRemoteColumns(c, server, db, schema, table); }
        getForeignServers(c) { return this.provider.getForeignServers(c); }
        getForeignTables(c, schema) { return this.provider.getForeignTables(c, schema); }

        invalidateConnection(c) {
            if (this.cache && this.cache.invalidateConnection) this.cache.invalidateConnection(c);
        }
        invalidateDatabase(c, db) {
            if (this.cache && this.cache.invalidateDatabase) this.cache.invalidateDatabase(c, db);
        }
        invalidateObject(c, db, schema, name) {
            if (this.cache && this.cache.invalidateObject) this.cache.invalidateObject(c, db, schema, name);
        }
        invalidateDefinition(c, db, schema, name) { this.invalidateObject(c, db, schema, name); }
        invalidateParameters(c, db, schema, name) { this.invalidateObject(c, db, schema, name); }
        clearCache() {
            if (this.cache && this.cache.invalidateAll) this.cache.invalidateAll();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. POPUP
    // ─────────────────────────────────────────────────────────────────────────
    class IntelliSensePopupUI {
        constructor() {
            this.popup = null;
            this.candidates = [];
            this.selectedIndex = -1;
            this.isVisible = false;
            this.panelOpen = false;
            this.activeTab = "summary";
            this.listWidth = LIST_WIDTH;
            this.mouseEnteredPopup = false;
            this.popupHasBeenDragged = false;
            this.isDraggingPopup = false;
            this.word = "";
            this.tabCtx = null;
            this.dataProvider = null;
            this.anchor = null;
            this.onItemChosen = null;
            this.onDismiss = null;
            this.onRetryMetadata = null;
            this._detailToken = 0;
            this._detailTimer = null;
        }

        init() {
            var el = document.createElement("div");
            el.id = "ide-intellisense-popup";
            el.className = "ide-intellisense-popup monaco-component";
            el.style.display = "none";
            el.innerHTML =
                '<div class="isp-list-panel">' +
                '  <div class="isp-header" title="Kéo để di chuyển">' +
                '    <i class="codicon codicon-gripper" aria-hidden="true"></i>' +
                '    <span class="isp-title">Suggestions</span>' +
                '    <span class="isp-count">0</span>' +
                '    <i class="codicon codicon-layout-sidebar-right isp-toggle" title="Mở hoặc đóng chi tiết (→ / ←)"></i>' +
                "  </div>" +
                '  <div class="isp-list"></div>' +
                "</div>" +
                '<div class="isp-splitter" title="Kéo để đổi kích thước"></div>' +
                '<div class="isp-detail-panel">' +
                '  <div class="isp-tabs">' +
                '    <span class="isp-tab" data-tab="summary">Summary</span>' +
                '    <span class="isp-tab" data-tab="script">Script</span>' +
                '    <i class="codicon codicon-copy isp-copy" title="Sao chép script"></i>' +
                "  </div>" +
                '  <div class="isp-body"></div>' +
                "</div>";
            document.body.appendChild(el);
            this.popup = el;
            this.listPanel = el.querySelector(".isp-list-panel");
            this.listEl = el.querySelector(".isp-list");
            this.splitterEl = el.querySelector(".isp-splitter");
            this.detailPanel = el.querySelector(".isp-detail-panel");
            this.body = el.querySelector(".isp-body");
            this.countBadge = el.querySelector(".isp-count");
            this.toggleEl = el.querySelector(".isp-toggle");
            this.copyEl = el.querySelector(".isp-copy");
            this._initDrag(el.querySelector(".isp-header"));
            this._initSplitter();
            this._initEvents();
            this._syncTabs();
        }

        _initDrag(header) {
            var self = this, startX = 0, startY = 0, startLeft = 0, startTop = 0;
            header.addEventListener("mousedown", function (e) {
                if (e.target === self.toggleEl) return;
                self.isDraggingPopup = true;
                self.popupHasBeenDragged = true;
                startX = e.clientX; startY = e.clientY;
                var r = self.popup.getBoundingClientRect();
                startLeft = r.left; startTop = r.top;
                e.preventDefault();
            });
            document.addEventListener("mousemove", function (e) {
                if (!self.isDraggingPopup) return;
                self.popup.style.left = (startLeft + e.clientX - startX) + "px";
                self.popup.style.top = (startTop + e.clientY - startY) + "px";
            });
            document.addEventListener("mouseup", function () { self.isDraggingPopup = false; });
        }

        _initSplitter() {
            var self = this, dragging = false, startX = 0, startWidth = 0;
            this.splitterEl.addEventListener("mousedown", function (e) {
                dragging = true;
                startX = e.clientX;
                startWidth = self.listPanel.getBoundingClientRect().width;
                self.splitterEl.classList.add("dragging");
                e.preventDefault();
            });
            document.addEventListener("mousemove", function (e) {
                if (!dragging) return;
                self.listWidth = Math.max(220, Math.min(600, startWidth + e.clientX - startX));
                self._applySize();
            });
            document.addEventListener("mouseup", function () {
                if (!dragging) return;
                dragging = false;
                self.splitterEl.classList.remove("dragging");
            });
        }

        _initEvents() {
            var self = this;
            // Virtual scroll event for list performance
            this.listEl.addEventListener("scroll", function () {
                self._onListScroll();
            });
            // The list is rebuilt while typing: one listener on the container instead of one per row.
            this.listEl.addEventListener("mouseover", function (e) {
                var row = e.target.closest(".isp-item");
                if (row && Number(row.dataset.index) !== self.selectedIndex) self.selectIndex(Number(row.dataset.index), false);
            });
            this.listEl.addEventListener("mousedown", function (e) {
                e.preventDefault();   // the editor keeps the focus
                var retry = e.target.closest(".isp-retry");
                if (retry) {
                    if (typeof self.onRetryMetadata === "function") self.onRetryMetadata();
                    return;
                }
                var row = e.target.closest(".isp-item");
                if (row && typeof self.onItemChosen === "function") {
                    self.selectIndex(Number(row.dataset.index), false);
                    self.onItemChosen(self.candidates[Number(row.dataset.index)]);
                }
            });
            this.popup.addEventListener("mouseenter", function () { self.mouseEnteredPopup = true; });
            this.popup.querySelector(".isp-tabs").addEventListener("mousedown", function (e) {
                e.preventDefault();
                var tab = e.target.dataset ? e.target.dataset.tab : null;
                if (tab) {
                    self.activeTab = tab;
                    self._syncTabs();
                    self._refreshDetail();
                } else if (e.target === self.copyEl) {
                    self._copyScript();
                }
            });
            this.toggleEl.addEventListener("mousedown", function (e) {
                e.preventDefault();
                e.stopPropagation();
                self.setPanelOpen(!self.panelOpen);
            });
            document.addEventListener("mousemove", function (e) {
                if (!self.isVisible || !self.mouseEnteredPopup || self.isDraggingPopup || self.splitterEl.classList.contains("dragging")) return;
                var r = self.popup.getBoundingClientRect();
                var inside = e.clientX >= r.left - LEAVE_THRESHOLD && e.clientX <= r.right + LEAVE_THRESHOLD
                    && e.clientY >= r.top - LEAVE_THRESHOLD && e.clientY <= r.bottom + LEAVE_THRESHOLD;
                if (!inside) {
                    self.hide();
                    if (typeof self.onDismiss === "function") self.onDismiss();
                }
            });
            document.addEventListener("mousedown", function (e) {
                if (self.isVisible && !self.popup.contains(e.target)) {
                    self.hide();
                    if (typeof self.onDismiss === "function") self.onDismiss();
                }
            });
        }

        _syncTabs() {
            var active = this.activeTab;
            this.popup.querySelectorAll(".isp-tab").forEach(function (t) {
                t.classList.toggle("active", t.dataset.tab === active);
            });
            this.copyEl.style.display = active === "script" ? "" : "none";
        }

        /** Sizes to the free room: an editor pane can be much smaller than the popup. */
        _applySize() {
            // A list of only keywords has nothing to show beside it, unless the user asked for the panel with →.
            var open = this.panelOpen && (Host.detailOnDemand() || this.candidates.some(function (c) { return c.type !== "keyword"; }));
            this.splitterEl.style.display = open ? "" : "none";
            this.detailPanel.style.display = open ? "" : "none";
            var maxW = Math.max(200, window.innerWidth - 2 * EDGE);
            var listW = Math.min(this.listWidth, maxW);
            var total = open ? Math.min(listW + 5 + DETAIL_WIDTH, maxW) : listW;
            if (open && total - listW - 5 < 180) listW = Math.max(200, total - 185);
            this.listPanel.style.width = listW + "px";
            this.popup.style.width = (total + 2) + "px";
            this.toggleEl.style.display = Host.detailOnDemand() ? "" : "none";
            this.toggleEl.classList.toggle("on", open);
        }

        _place(coords) {
            if (this.popupHasBeenDragged) return;
            var c = coords || { top: 100, bottom: 120, left: 100 };
            var below = window.innerHeight - c.bottom - EDGE - 4;
            var above = c.top - EDGE - 4;
            var height = POPUP_HEIGHT, top;
            if (below >= POPUP_HEIGHT || below >= above) {
                height = Math.max(MIN_HEIGHT, Math.min(POPUP_HEIGHT, below));
                top = c.bottom + 4;
            } else {
                height = Math.max(MIN_HEIGHT, Math.min(POPUP_HEIGHT, above));
                top = c.top - height - 4;
            }
            this.popup.style.height = height + "px";
            this.popup.style.top = Math.max(EDGE, top) + "px";
            var width = this.popup.offsetWidth;
            var left = c.left;
            if (left + width > window.innerWidth - EDGE) left = window.innerWidth - width - EDGE;
            this.popup.style.left = Math.max(EDGE, left) + "px";
        }

        _onListScroll() {
            if (!this.candidates || this.candidates.length <= 25) return;
            var ITEM_HEIGHT = 24;
            var scrollTop = this.listEl.scrollTop || 0;
            var viewportHeight = this.listEl.clientHeight || 200;
            var startIndex = Math.max(0, Math.floor(scrollTop / ITEM_HEIGHT) - 5);
            var endIndex = Math.min(this.candidates.length, Math.ceil((scrollTop + viewportHeight) / ITEM_HEIGHT) + 5);

            if (this._virtualStart !== startIndex || this._virtualEnd !== endIndex) {
                this._renderVisibleItems();
            }
        }

        _renderVisibleItems() {
            if (!this.candidates || this.candidates.length === 0) {
                this.listEl.innerHTML = "";
                return;
            }
            var ITEM_HEIGHT = 24;
            var totalCount = this.candidates.length;
            var lowerWord = (this.word || "").toLowerCase();

            // Direct rendering for small lists (<= 25 items)
            if (totalCount <= 25) {
                var html = "";
                if (this._hasError) {
                    html += '<div class="isp-error"><span><i class="codicon codicon-warning" aria-hidden="true"></i> Không tải được metadata</span><button type="button" class="isp-retry">Thử lại</button></div>';
                }
                for (var i = 0; i < totalCount; i++) {
                    var c = this.candidates[i];
                    var isActive = i === this.selectedIndex;
                    html += '<div class="isp-item' + (c.type === "keyword" ? " is-keyword" : "") + (isActive ? " active" : "") + '" data-index="' + i + '">'
                        + icon(c.type, c.type === "database" && c.isSystemDb ? "is-system" : "")
                        + '<span class="isp-name">' + highlight(displayName(c), lowerWord) + "</span>"
                        + '<span class="isp-badge">[' + esc(c.displayType || formatDisplayType(c.type)) + "]</span></div>";
                }
                if (this._isTruncated) html += '<div class="isp-truncated">Còn nhiều mục, gõ tiếp để lọc</div>';
                this.listEl.innerHTML = html;
                return;
            }

            // Virtualized window rendering for large candidate lists
            var totalHeight = totalCount * ITEM_HEIGHT;
            var scrollTop = this.listEl.scrollTop || 0;
            var viewportHeight = this.listEl.clientHeight || 200;
            var startIndex = Math.max(0, Math.floor(scrollTop / ITEM_HEIGHT) - 5);
            var endIndex = Math.min(totalCount, Math.ceil((scrollTop + viewportHeight) / ITEM_HEIGHT) + 5);
            var offsetY = startIndex * ITEM_HEIGHT;

            this._virtualStart = startIndex;
            this._virtualEnd = endIndex;

            var itemsHtml = "";
            for (var i = startIndex; i < endIndex; i++) {
                var c = this.candidates[i];
                var isActive = i === this.selectedIndex;
                itemsHtml += '<div class="isp-item' + (c.type === "keyword" ? " is-keyword" : "") + (isActive ? " active" : "") + '" data-index="' + i + '">'
                    + icon(c.type, c.type === "database" && c.isSystemDb ? "is-system" : "")
                    + '<span class="isp-name">' + highlight(displayName(c), lowerWord) + "</span>"
                    + '<span class="isp-badge">[' + esc(c.displayType || formatDisplayType(c.type)) + "]</span></div>";
            }

            var html = "";
            if (this._hasError) {
                html += '<div class="isp-error"><span><i class="codicon codicon-warning" aria-hidden="true"></i> Không tải được metadata</span><button type="button" class="isp-retry">Thử lại</button></div>';
            }
            html += '<div class="isp-virtual-spacer" style="height:' + totalHeight + 'px; position:relative; overflow:hidden;">'
                 + '  <div class="isp-virtual-window" style="position:absolute; top:' + offsetY + 'px; left:0; right:0;">'
                 + itemsHtml
                 + '  </div>'
                 + '</div>';
            if (this._isTruncated) {
                html += '<div class="isp-truncated">Còn nhiều mục, gõ tiếp để lọc</div>';
            }
            this.listEl.innerHTML = html;
        }

        render(candidates, cursorCoords, tabCtx, dataProvider, word, errorInfo, neutral) {
            var hasError = Boolean((candidates && candidates.hasError) || (candidates && candidates.meta && candidates.meta.errors && candidates.meta.errors.length > 0) || errorInfo);
            if (!this.popup || (!hasError && (!candidates || candidates.length === 0))) {
                this.hide();
                return;
            }
            this.candidates = candidates || [];
            this.tabCtx = tabCtx || {};
            this.dataProvider = dataProvider;
            this.word = word || "";
            this.anchor = cursorCoords;

            if (!this.isVisible) {
                this.panelOpen = Host.detailEnabled() && !Host.detailOnDemand();
            }
            this.popup.style.display = "flex";
            this.isVisible = true;
            this.mouseEnteredPopup = false;
            this._applySize();
            this._place(cursorCoords);

            this._hasError = hasError;
            this._isTruncated = Boolean(candidates && (candidates.isTruncated || (candidates.meta && candidates.meta.truncated)));
            this.listEl.scrollTop = 0;
            this._virtualStart = -1;
            this._virtualEnd = -1;
            this._renderVisibleItems();

            this.countBadge.textContent = this._isTruncated ? this.candidates.length + "+" : this.candidates.length;

            if (this.candidates.length > 0 && !(neutral && !word)) {
                this._deferDetail = true;
                this.selectIndex(0, false);
                this._deferDetail = false;
            } else if (this.candidates.length > 0) {
                // Opened by itself after a space: Enter must still break the line. ↓ starts choosing.
                this.selectedIndex = -1;
                this._detailToken++;
                this.body.innerHTML = '<div class="isp-muted">Nhấn ↓ để chọn gợi ý.</div>';
            } else {
                this.selectedIndex = -1;
                this.body.innerHTML = '<div class="isp-muted">Chọn một mục để xem chi tiết.</div>';
            }
        }

        selectIndex(index, shouldScroll) {
            if (index < 0 || index >= this.candidates.length) return;
            var prev = this.selectedIndex;
            this.selectedIndex = index;

            var ITEM_HEIGHT = 24;
            if (shouldScroll) {
                var targetTop = index * ITEM_HEIGHT;
                var clientH = this.listEl.clientHeight || 200;
                if (targetTop < this.listEl.scrollTop) {
                    this.listEl.scrollTop = targetTop;
                } else if (targetTop + ITEM_HEIGHT > this.listEl.scrollTop + clientH) {
                    this.listEl.scrollTop = targetTop + ITEM_HEIGHT - clientH;
                }
            }

            if (prev >= 0 && prev !== index) {
                var prevRow = this.listEl.querySelector('.isp-item[data-index="' + prev + '"]');
                if (prevRow) prevRow.classList.remove("active");
            }
            var curRow = this.listEl.querySelector('.isp-item[data-index="' + index + '"]');
            if (curRow) {
                curRow.classList.add("active");
            } else if (this.candidates.length > 25) {
                // Selected index moved outside virtual slice, re-render visible window
                this._renderVisibleItems();
            }
            this._refreshDetail();
        }

        moveSelection(delta) {
            if (this.candidates.length === 0) return;
            var n = this.candidates.length;
            var next = this.selectedIndex === -1 ? (delta > 0 ? 0 : n - 1) : this.selectedIndex + delta;
            if (next < 0) next = n - 1;
            if (next >= n) next = 0;
            this.selectIndex(next, true);
        }

        /** → / ← and the header button. Opening is what starts loading the detail. */
        setPanelOpen(open) {
            if (!Host.detailEnabled() || this.panelOpen === open) return;
            this.panelOpen = open;
            this._applySize();
            this._place(this.anchor);
            this._refreshDetail();
        }

        _refreshDetail() {
            if (!this.panelOpen) {
                this._detailToken++;
                clearTimeout(this._detailTimer);
                return;
            }
            var item = this.getSelectedItem();
            if (item) this.updateDetailPreview(item);
        }

        // ── detail panel ────────────────────────────────────────────────────
        updateDetailPreview(item) {
            var self = this, tabCtx = this.tabCtx || {}, dp = this.dataProvider;
            var token = ++this._detailToken;
            clearTimeout(this._detailTimer);
            if (!dp) return;
            var schema = item.schema || tabCtx.schema;
            var db = item.database || tabCtx.database;
            var isCurrent = function () { return token === self._detailToken && self.isVisible; };

            // While typing the top item changes with every key: draw once the keys pause.
            if (this._deferDetail) {
                this._deferDetail = false;
                this._detailTimer = setTimeout(function () {
                    if (isCurrent()) self.updateDetailPreview(item);
                }, DETAIL_LOAD_DELAY_MS);
                return;
            }

            if (this.activeTab === "script") {
                var local = localScript(item);
                if (local !== null) { this._showScript(local, isCurrent); return; }
                var cachedDef = window.AppMetadataCache.getDefinition(tabCtx.connectionId, db, schema, item.name, item.type);
                if (cachedDef) { this._showScript(cachedDef, isCurrent); return; }
                this.body.innerHTML = '<div class="isp-muted"><i class="codicon codicon-loading codicon-modifier-spin" aria-hidden="true"></i> Đang tải định nghĩa…</div>';
                this._detailTimer = setTimeout(function () {
                    dp.fetchDefinition(tabCtx.connectionId, db, schema, item.name, item.type).then(function (def) {
                        if (isCurrent()) self._showScript(def || "-- Không đọc được định nghĩa.", isCurrent);
                    });
                }, DETAIL_LOAD_DELAY_MS);
                return;
            }

            var head = '<div class="isp-object">' + icon(item.type) + " " + esc(item.schema ? item.schema + "." : "") + esc(displayName(item)) + "</div>";
            if (item.type === "table" || item.type === "view") {
                this._loadSummary(head, "cột", isCurrent,
                    window.AppMetadataCache.getColumns(tabCtx.connectionId, db, schema, item.name),
                    function () { return dp.fetchColumns(tabCtx.connectionId, db, schema, item.name); },
                    columnsTable);
            } else if (item.type === "procedure" || item.type === "function") {
                this._loadSummary(head, "tham số", isCurrent,
                    window.AppMetadataCache.getParameters(tabCtx.connectionId, db, schema, item.name),
                    function () { return dp.fetchParameters(tabCtx.connectionId, db, schema, item.name); },
                    parametersTable);
            } else {
                this.body.innerHTML = head + staticSummary(item);
            }
        }

        _loadSummary(head, what, isCurrent, cached, load, toHtml) {
            var self = this;
            if (cached) { this.body.innerHTML = head + toHtml(cached); return; }
            this.body.innerHTML = head + '<div class="isp-muted"><i class="codicon codicon-loading codicon-modifier-spin" aria-hidden="true"></i> Đang tải ' + what + "…</div>";
            this._detailTimer = setTimeout(function () {
                load().then(function (rows) {
                    if (!isCurrent()) return;
                    if (rows && rows.hasError) {
                        self.body.innerHTML = head + '<div class="isp-muted">Không tải được ' + what + ".</div>";
                    } else {
                        self.body.innerHTML = head + toHtml(rows || []);
                    }
                });
            }, DETAIL_LOAD_DELAY_MS);
        }

        _showScript(text, isCurrent) {
            var self = this;
            this.lastScript = text;
            var pre = document.createElement("pre");
            pre.className = "isp-script";
            pre.textContent = text;
            this.body.innerHTML = "";
            this.body.appendChild(pre);
            if (text.length > COLORIZE_LIMIT) return;
            monaco.editor.colorize(text, "sql", { tabSize: 4 }).then(function (html) {
                if (isCurrent() && self.activeTab === "script") pre.innerHTML = html;
            }, function () { /* stays plain text */ });
        }

        _copyScript() {
            var pre = this.body.querySelector(".isp-script");
            if (!pre || !window.javaBridge || !window.javaBridge.setClipboardText) return;
            window.javaBridge.setClipboardText(this.lastScript || pre.textContent);
            var copy = this.copyEl;
            copy.classList.remove("codicon-copy");
            copy.classList.add("codicon-check");
            setTimeout(function () {
                copy.classList.remove("codicon-check");
                copy.classList.add("codicon-copy");
            }, 800);
        }

        getSelectedItem() {
            return this.selectedIndex >= 0 && this.selectedIndex < this.candidates.length ? this.candidates[this.selectedIndex] : null;
        }

        hide() {
            if (!this.popup) return;
            this.popup.style.display = "none";
            this.isVisible = false;
            this.panelOpen = false;
            this.mouseEnteredPopup = false;
            this.popupHasBeenDragged = false;
            this.isDraggingPopup = false;
            this.selectedIndex = -1;
            this.candidates = [];
            this._detailToken++;
            clearTimeout(this._detailTimer);
        }
    }

    /**
     * The scope resolver lists columns it finds written in the statement, and that includes the half-typed
     * "o.Ord" itself. Without this the typed word comes first and Enter would "complete" it to itself.
     */
    function dropTypedEcho(list, wordLower) {
        if (!list || !wordLower) return list;
        for (var i = list.length - 1; i >= 0; i--) {
            var c = list[i];
            if ((c.type === "column" || c.type === "pk_column") && !c.dataType && String(c.name).toLowerCase() === wordLower) {
                list.splice(i, 1);
            }
        }
        return list;
    }

    function displayName(c) {
        return c.type === "keyword" ? Host.applyKeywordCase(c.name) : c.name;
    }

    function highlight(name, lowerWord) {
        if (!lowerWord) return esc(name);
        var i = String(name).toLowerCase().indexOf(lowerWord);
        if (i < 0) return esc(name);
        return esc(name.slice(0, i)) + "<b>" + esc(name.slice(i, i + lowerWord.length)) + "</b>" + esc(name.slice(i + lowerWord.length));
    }

    function columnsTable(cols) {
        if (!cols.length) return '<div class="isp-muted">Không có thông tin cột.</div>';
        return '<table class="isp-table"><thead><tr><th>Name</th><th>Data type</th><th class="isp-narrow">Null</th><th class="isp-narrow">PK</th></tr></thead><tbody>'
            + cols.map(function (c) {
                return '<tr><td class="isp-mono">' + esc(c.name) + '</td><td class="isp-type">' + esc(c.data_type) + "</td><td>" + (c.is_nullable ? "YES" : "NO") + "</td><td>"
                    + (c.is_pk ? '<i class="codicon codicon-key" aria-label="Khóa chính"></i>' : "") + "</td></tr>";
            }).join("") + "</tbody></table>";
    }

    function parametersTable(params) {
        if (!params.length) return '<div class="isp-muted">Không có tham số.</div>';
        return '<table class="isp-table"><thead><tr><th>Parameter</th><th>Type</th><th class="isp-narrow">Output</th></tr></thead><tbody>'
            + params.map(function (p) {
                return '<tr><td class="isp-mono">' + esc(p.name) + '</td><td class="isp-type">' + esc(p.data_type) + "</td><td>" + (p.is_output ? "OUT" : "IN") + "</td></tr>";
            }).join("") + "</tbody></table>";
    }

    function row(label, value) {
        return value ? '<div class="isp-kv"><span>' + esc(label) + "</span> " + esc(value) + "</div>" : "";
    }

    function staticSummary(item) {
        switch (item.type) {
            case "column":
            case "pk_column":
                return row("Bảng:", item.tableName ? (item.schema ? item.schema + "." : "") + item.tableName + (item.tableAlias ? " (" + item.tableAlias + ")" : "") : "")
                    + row("Kiểu dữ liệu:", item.dataType || "Không rõ")
                    + row("Cho phép NULL:", item.isNullable ? "YES" : "NO")
                    + row("Khóa chính:", item.isPk ? "YES" : "NO");
            case "snippet":
                return row("Loại:", "SQL Snippet") + '<div class="isp-kv">' + esc(item.description || "") + "</div>"
                    + '<div class="isp-muted">Nhấn Enter để chèn, hoặc Tab ngay sau đúng từ khóa snippet.</div>';
            case "temp_table":
            case "table_variable":
                if (item.columns && item.columns.length) {
                    return '<table class="isp-table"><thead><tr><th>Column</th><th>Data type</th></tr></thead><tbody>'
                        + item.columns.map(function (c) {
                            return '<tr><td class="isp-mono">' + esc(c.name || c) + '</td><td class="isp-type">' + esc(c.dataType || c.data_type || "") + "</td></tr>";
                        }).join("") + "</tbody></table>";
                }
                return row("Loại:", item.displayType);
            case "scalar_variable":
            case "parameter":
                return row("Loại:", item.displayType) + row("Kiểu dữ liệu:", item.dataType);
            case "linked_server":
                return row("Loại:", "Linked Server") + row("Chi tiết:", item.detail)
                    + '<div class="isp-muted">Cú pháp: [Server].[Database].[Schema].[Table]</div>';
            case "foreign_server":
                return row("Loại:", "Foreign Server") + row("Chi tiết:", item.detail);
            case "database":
                return row("Loại:", item.isSystemDb ? "System Database" : "User Database") + row("Chi tiết:", item.detail);
            default:
                return row("Loại:", item.displayType) + row("Trỏ tới:", item.targetObject);
        }
    }

    /** Script text that needs no server round trip; null for objects with a real definition. */
    function localScript(item) {
        if (DEFINITION_TYPES.indexOf(item.type) >= 0) return null;
        switch (item.type) {
            case "column":
            case "pk_column":
                return "-- Column: " + item.name + " " + (item.dataType || "") + "\n-- Table: " + (item.tableName || "N/A");
            case "data_type":
            case "user_data_type":
                return "-- Data Type: " + item.name + "\n-- Category: " + item.displayType;
            case "snippet":
                return window.AppSnippets.expandSnippetBody(item.snippetData || {}).expandedText;
            case "temp_table":
            case "table_variable":
                return "-- Local Script Object: " + item.name + " (" + item.displayType + ")";
            case "scalar_variable":
            case "parameter":
                return "-- Variable / Parameter: " + item.name + " " + (item.dataType || "");
            case "linked_server":
                return "-- Linked Server: [" + item.name + "]\n-- Usage: SELECT * FROM [" + item.name + "].[database].[schema].[table];";
            case "foreign_server":
                return '-- Foreign Server: "' + item.name + '"';
            case "database":
                return "-- Database: [" + item.name + "]\nUSE [" + item.name + "];\nGO";
            default:
                return "-- Không có định nghĩa cho [" + (item.displayType || item.type) + "]";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. CONTROLLER
    // ─────────────────────────────────────────────────────────────────────────
    class IntelliSenseController {
        constructor(dataProvider, popupUI) {
            this.dataProvider = dataProvider;
            this.popupUI = popupUI;
            this.activeAdapter = null;
            this.activeContext = null;
            this.activeCandidates = null;
            this.rawCandidates = null;
            this.debounceTimer = null;
            this.changeTimer = null;
            this.pendingChange = null;
            this.isConfirmingCompletion = false;
            this._currentRequestId = 0;
            this._lastContextCategory = null;
            this.triggerController = new window.IntelliSenseTriggerController();

            this.popupUI.onItemChosen = (item) => {
                if (this.activeAdapter) this.chooseItem(this.activeAdapter, item);
            };
            this.popupUI.onDismiss = () => {
                this.activeContext = null;
                this.activeCandidates = null;
                this.triggerController.clearActiveCandidates();
                this.triggerController.clearActiveToken();
                this.triggerController.setState(window.CompletionState.IDLE, "dismissed");
            };
            this.popupUI.onRetryMetadata = () => {
                Host.clearFailures();
                var tab = this.getActiveTabContext();
                if (tab.connectionId) window.AppMetadataCache.invalidateConnection(tab.connectionId);
                if (this.activeAdapter) this.trigger(this.activeAdapter, true, "explicit");
            };
        }

        get completionEngine() {
            var e = window.DefaultCompletionEngine;
            if (!e.dataProvider) e.setDataProvider(this.dataProvider);
            if (!e.metadataCache) e.setMetadataCache(window.AppMetadataCache);
            return e;
        }

        normalizeAdapter(ed) {
            if (!ed) return null;
            if (ed instanceof window.EditorAdapter) return ed;
            return new window.EditorAdapter(ed);
        }

        attachEditor(editorWrapper) {
            var adapter = this.normalizeAdapter(editorWrapper);
            if (!adapter || adapter._intellisenseAttached) return;
            adapter._intellisenseAttached = true;
            this.activeAdapter = adapter;
            var raw = adapter.rawEditor;

            // Signature help and hover stay Monaco's own widgets, with the LLTool resolvers behind them.
            var R = window.SqlSignatureHelpResolver;
            if (R) {
                R.setDataProvider(this.dataProvider);
                R.setMetadataCache(window.AppMetadataCache);
                R.registerMonacoProvider();
            }
            var H = window.SqlHoverResolver;
            if (H) {
                H.setDataProvider(this.dataProvider);
                H.setMetadataCache(window.AppMetadataCache);
                H.registerMonacoProvider();
            }

            adapter.on("change", (ed, changeObj) => this._onChange(adapter, changeObj));

            adapter.on("cursorActivity", () => {
                if (!this.popupUI.isVisible) return;   // never opens on cursor movement alone
                var decision = this.triggerController.decideCursor(adapter.getCursorOffset(), adapter.getCursor().line, true);
                if (decision.action === "close") this.hidePopup();
            });

            adapter.on("keydown", (ed, e) => {
                if (this.handleKeyDown(adapter, e)) {
                    e.preventDefault();
                    e.stopPropagation();
                }
            });

            raw.onDidChangeModel(() => {
                this.hidePopup();
                this.triggerController.setState(window.CompletionState.IDLE, "tab_switched");
            });
            raw.onDidBlurEditorText(() => {
                setTimeout(() => {
                    if (this.popupUI.isVisible && !raw.hasTextFocus() && !this.popupUI.popup.contains(document.activeElement)) this.hidePopup();
                }, 0);
            });
            raw.addCommand(monaco.KeyCode.F12, () => this.goToDefinition());
        }

        _onChange(adapter, changeObj) {
            if (!Host.isEnabled()) return;
            if (changeObj && (changeObj.origin === "setValue" || changeObj.origin === "undo")) {
                this.hidePopup();
                return;
            }
            if (this.isConfirmingCompletion) {
                this.isConfirmingCompletion = false;
                clearTimeout(this.debounceTimer);
                clearTimeout(this.changeTimer);
                this.pendingChange = null;
                return;
            }
            // One more letter of a word: no need to analyze the script, which runs on the thread that paints the app.
            if (this._typedWordChar(adapter, changeObj)) {
                clearTimeout(this.changeTimer);
                this.pendingChange = null;
                return;
            }
            // Fast typing: analyze once for the burst, the FX thread keeps painting.
            this.pendingChange = changeObj;
            clearTimeout(this.changeTimer);
            this.changeTimer = setTimeout(() => {
                var change = this.pendingChange;
                this.pendingChange = null;
                this._decideChange(adapter, change);
            }, 20);
        }

        /**
         * A letter added to (or removed from) the word being typed changes nothing but the word. With the popup open
         * only the list is narrowed; with a word that already found nothing, a longer one won't find anything either.
         * @return true when handled here
         */
        _typedWordChar(adapter, changeObj) {
            var text = changeObj && Array.isArray(changeObj.text) ? changeObj.text.join("") : null;
            var removed = changeObj && changeObj.removed && changeObj.removed.length > 0;
            var changes = changeObj && changeObj.changes;
            if (text === null || !changes || changes.length !== 1 || adapter.hasSelection()) { this._noMatchWord = null; return false; }
            var typed = /^[\w$#]$/.test(text) && !removed;
            var backspace = text === "" && removed && changes[0].rangeLength === 1;
            if (!typed && !backspace) { this._noMatchWord = null; return false; }

            var cursor = adapter.getCursor();
            var line = adapter.getLine(cursor.line);
            if (/[\w$#]/.test(line.charAt(cursor.ch))) { this._noMatchWord = null; return false; }   // in the middle of a word
            var match = /[\w$#]+$/.exec(line.slice(0, cursor.ch));
            var word = match ? match[0] : "";
            var offset = adapter.getCursorOffset();

            if (!this.popupUI.isVisible) {
                var none = this._noMatchWord;
                if (typed && none && none.line === cursor.line && offset - word.length === none.start
                    && word.toLowerCase().indexOf(none.word) === 0) return true;
                this._noMatchWord = null;
                return false;
            }

            var ctx = this.activeContext;
            if (!ctx || !word || ctx.isQuotedIdentifier || !this.rawCandidates) return false;
            var before = String(ctx.currentWord || "");
            // the analyzer's word may be more than these letters (@var): then it has to look again
            if (word !== (typed ? before + text : before.slice(0, -1))) return false;
            var was = ctx.replacementRange;
            if (was && was.end - was.start !== before.length) return false;   // replaces more than the word
            var range = { start: offset - word.length, end: offset };
            this.activeContext = Object.assign(Object.create(Object.getPrototypeOf(ctx)), ctx, { currentWord: word, replacementRange: range, prefixRange: range });
            this.triggerController.setState(window.CompletionState.REFRESHING, "typing");

            clearTimeout(this.debounceTimer);
            var pool = this.rawCandidates;
            if (pool.length > LARGE_POOL) {
                this.debounceTimer = setTimeout(() => this.refresh(adapter, this.activeContext), LARGE_POOL_DELAY_MS);
            } else {
                this.refresh(adapter, this.activeContext);
            }
            return true;
        }

        /**
         * The names of the connection arrived after the list was asked for.
         * @param unchanged nothing was typed since: the user is waiting for this list
         */
        metadataArrived(unchanged) {
            var adapter = this.activeAdapter;
            if (!adapter || !adapter.rawEditor.hasTextFocus()) return;
            if (!this.popupUI.isVisible && !unchanged) return;
            this.activeCandidates = null;
            this.rawCandidates = null;
            this._noMatchWord = null;
            // opened after a space: stays neutral, Enter must still break the line
            this.trigger(adapter, false, this._openedBySpace ? "rule_space" : "automatic");
        }

        _decideChange(adapter, changeObj) {
            var tabCtx = this.getActiveTabContext();
            var context = window.SqlContextAnalyzer.analyze(adapter.getText(), adapter.getCursorOffset(), tabCtx.dbType);
            this.activeContext = context;
            var decision = this.triggerController.decideChange(changeObj, context, this.popupUI.isVisible, adapter.hasSelection());

            if ((decision.action === "none" || decision.reason === "delimiter") && this._grammarWantsPopup(changeObj, context, tabCtx)) {
                this.hidePopup();
                this.activeContext = context;
                this._currentRequestId++;
                this.debounceTimer = setTimeout(() => this.trigger(adapter, false, "rule_space"), 60);
            } else if (decision.action === "close") {
                this.hidePopup();
            } else if (decision.action === "refresh") {
                clearTimeout(this.debounceTimer);
                // Re-ranking a whole big catalog takes ~200 ms on the FX thread: wait for a pause in the typing.
                var pool = this.rawCandidates || this.activeCandidates;
                if (pool && pool.length > LARGE_POOL) {
                    this.debounceTimer = setTimeout(() => this.refresh(adapter, this.activeContext), LARGE_POOL_DELAY_MS);
                } else {
                    this.refresh(adapter, context);
                }
            } else if (decision.action === "trigger") {
                // A list still loading for the previous text is out of date. If it were allowed to finish it could
                // come back empty and hide the popup, cancelling the trigger scheduled here.
                this._currentRequestId++;
                var delay = decision.reason === "character" ? 120 : 80;
                clearTimeout(this.debounceTimer);
                this.debounceTimer = setTimeout(() => this.trigger(adapter, false, decision.reason), delay);
            }
        }

        /** A space where the next word is predictable (UPDATE t <SET>, SET NOCOUNT <ON>, ...). */
        _grammarWantsPopup(changeObj, context, tabCtx) {
            var text = changeObj && Array.isArray(changeObj.text) ? changeObj.text.join("") : "";
            if (text !== " " || !context || context.currentWord) return false;
            if (!context.fullSql) context.fullSql = this.activeAdapter.getText();
            var rule = window.SqlCompletionRules.resolve(context, tabCtx);
            return Boolean(rule && rule.auto);
        }

        handleKeyDown(adapter, e) {
            if (!Host.isEnabled()) return false;
            var isVisible = this.popupUI.isVisible;
            var hasSelection = adapter.hasSelection();
            var keyName = e.key || (e.browserEvent ? e.browserEvent.key : "");
            var plain = !e.shiftKey && !e.ctrlKey && !e.altKey && !e.metaKey;

            if (keyName === "Tab" && plain) {
                var expanded = window.AppSnippets.tryExpandSnippet(adapter);
                if (expanded) {
                    this.hidePopup();
                    return true;
                }
                if (isVisible) this.hidePopup();
                return false;   // Tab never accepts a suggestion: the editor indents
            }

            // Detail on demand: → opens the panel (and stays in the popup), ← closes it. With the panel
            // closed, ← is an ordinary caret move and the cursor rules below close the popup.
            if (isVisible && plain && !hasSelection && Host.detailOnDemand()) {
                if (keyName === "ArrowRight") {
                    this.popupUI.setPanelOpen(true);
                    return true;
                }
                if (keyName === "ArrowLeft" && this.popupUI.panelOpen) {
                    this.popupUI.setPanelOpen(false);
                    return true;
                }
            }

            if (keyName === "Escape") Host.clearWaiting();

            var decision = this.triggerController.decideKeyDown(e, isVisible, hasSelection);
            if (decision.action === "trigger") {
                this.trigger(adapter, true, "explicit");
                return true;
            }
            if (decision.action === "close") {
                this.hidePopup();
                return true;
            }
            if (decision.action === "accept") {
                var selected = this.popupUI.getSelectedItem();
                if (selected) {
                    this.chooseItem(adapter, selected);
                    return true;
                }
                this.hidePopup();
                return false;
            }
            if (decision.action === "navigate") {
                this.popupUI.moveSelection(decision.step || 1);
                return true;
            }
            return false;
        }

        getActiveTabContext() {
            var s = window.AppTabs.getActiveTabState();
            return { tabId: s.tabId, connectionId: s.connectionId, database: s.database, schema: s.schema, dbType: s.dbType };
        }

        refresh(adapter, context) {
            if (!this.popupUI.isVisible) return;
            var current = context || this.activeContext;
            if (!current) { this.hidePopup(); return; }
            if (current.isInsideComment || current.isInsideString) { this.hidePopup(); return; }

            // Clause, statement or tables changed: the pool of candidates is no longer valid
            var stmtStart = current.statementRange ? current.statementRange.start : 0;
            var tables = (current.queryTables || []).map(function (t) { return (t.table || "").toLowerCase(); }).sort().join(",");
            var category = stmtStart + "::" + tables + "::" + ((current.isAlterContext && current.alterObjectType)
                ? "alter_" + current.alterObjectType
                : ((current.execContext && current.execContext.isExec) ? "exec" : (current.clause || "")));
            var tabCtx = this.getActiveTabContext();
            if (this._lastContextCategory && category && this._lastContextCategory !== category) {
                this.activeCandidates = null;
                this.rawCandidates = null;
                this._lastContextCategory = category;
                this.collectAndShowCandidates(adapter, current, tabCtx);
                return;
            }
            this._lastContextCategory = category;

            var wordLower = (current.currentWord || "").toLowerCase();
            var pool = this.rawCandidates || this.activeCandidates;
            if (pool && pool.length > 0) {
                var ranked = this.rankAndFilterCandidates(pool, wordLower, current);
                if (!ranked || ranked.length === 0) { this.hidePopup(); return; }
                this.activeCandidates = ranked;
                this.popupUI.render(ranked, adapter.getCursorCoordinates(), tabCtx, this.dataProvider, wordLower);
                this.triggerController.setActiveCandidates(ranked);
                // the token grows with the typing: without this the caret "leaves" it after a few letters
                var token = this.triggerController.getActiveToken();
                if (token) this.triggerController.setActiveToken(Object.assign(token, { end: adapter.getCursorOffset() }));
                this.triggerController.setState(window.CompletionState.VISIBLE, "refreshed");
                return;
            }
            this.collectAndShowCandidates(adapter, current, tabCtx);
        }

        async trigger(ed, isManual, reason) {
            if (!Host.isEnabled()) return;
            var adapter = this.normalizeAdapter(ed) || this.activeAdapter;
            if (!adapter) return;
            reason = reason || "automatic";
            this.activeAdapter = adapter;
            this.triggerController.setState(window.CompletionState.OPENING, reason);
            this._openedBySpace = reason === "rule_space";

            var tabCtx = this.getActiveTabContext();
            var context = window.SqlContextAnalyzer.analyze(adapter.getText(), adapter.getCursorOffset(), tabCtx.dbType);
            var prev = this.activeContext;
            var prevQualifier = prev && prev.qualifier ? prev.qualifier.toLowerCase() : "";
            var currQualifier = context && context.qualifier ? context.qualifier.toLowerCase() : "";
            var prevStmt = prev && prev.statementRange ? prev.statementRange.start : 0;
            var currStmt = context && context.statementRange ? context.statementRange.start : 0;
            if (prevStmt !== currStmt || (context && context.triggerChar === ".") || reason === "character" || prevQualifier !== currQualifier) {
                this.activeCandidates = null;
                this.rawCandidates = null;
            }
            this.activeContext = context;

            if (context.isInsideComment || context.isInsideString) { this.hidePopup(); return; }

            var eager = Boolean(reason && (reason.indexOf("space") >= 0 || reason === "automatic" || reason === "explicit"));
            if (!isManual && !(context.currentWord.length > 0) && context.triggerChar !== "." && !context.hasFollowUp
                && ["FROM", "JOIN", "ON"].indexOf(context.clause) < 0 && !context.isAlterContext
                && !(context.execContext && context.execContext.isExec) && !eager
                && !context.isInRoutineBody && !context.isQuotedIdentifier) {
                this.hidePopup();
                return;
            }
            await this.collectAndShowCandidates(adapter, context, tabCtx);
        }

        async collectAndShowCandidates(adapter, context, tabCtx) {
            var requestId = ++this._currentRequestId;
            var ranked = await this.completionEngine.getCompletions(adapter.getText(), adapter.getCursor(), context, tabCtx);
            if (requestId !== this._currentRequestId) return;   // a newer keystroke won

            var wordLower = (context.currentWord || "").toLowerCase();
            dropTypedEcho(ranked, wordLower);
            if (!ranked || ranked.length === 0) {
                if (ranked && (ranked.hasError || (ranked.meta && ranked.meta.errors && ranked.meta.errors.length > 0))) {
                    this.popupUI.render([], adapter.getCursorCoordinates(), tabCtx, this.dataProvider, wordLower, ranked);
                    return;
                }
                this._rememberNoMatch(adapter, context);
                this.hidePopup();
                return;
            }
            this.rawCandidates = ranked.rawCandidates ? ranked.rawCandidates : ranked;
            this.activeCandidates = ranked;
            this.triggerController.setActiveCandidates(ranked);
            var offset = adapter.getCursorOffset();
            var wordLen = (context.currentWord || "").length;
            this.triggerController.setActiveToken({
                word: context.currentWord,
                qualifier: context.qualifier,
                start: context.prefixRange && context.prefixRange.start !== undefined ? context.prefixRange.start : offset - wordLen,
                end: offset,
                line: adapter.getCursor().line
            });
            this.triggerController.setState(window.CompletionState.VISIBLE, "rendered");
            this.popupUI.render(ranked, adapter.getCursorCoordinates(), tabCtx, this.dataProvider, wordLower, null, this._openedBySpace);
        }

        /** A word that found nothing: longer versions of it are not looked up again (see _typedWordChar). */
        _rememberNoMatch(adapter, context) {
            var word = context && context.currentWord ? String(context.currentWord).toLowerCase() : "";
            if (!word || !/^[\w$#]+$/.test(word)) { this._noMatchWord = null; return; }
            this._noMatchWord = { word: word, start: adapter.getCursorOffset() - word.length, line: adapter.getCursor().line };
        }

        rankAndFilterCandidates(list, wordLower, context) {
            var engine = this.completionEngine;
            return dropTypedEcho(engine.ranker.rank(engine.filter.filter(list || [], wordLower), context, wordLower), wordLower);
        }

        async chooseItem(ed, item) {
            var adapter = this.normalizeAdapter(ed) || this.activeAdapter;
            if (!adapter || !item) { this.hidePopup(); return; }
            this.triggerController.setState(window.CompletionState.ACCEPTING, "item_selected");

            var currentContext = this.activeContext;
            this.isConfirmingCompletion = true;
            clearTimeout(this.debounceTimer);
            clearTimeout(this.changeTimer);
            this.popupUI.hide();

            try {
                var tab = this.getActiveTabContext();
                if (window.CompletionRanker && window.CompletionRanker.recordMru) {
                    window.CompletionRanker.recordMru(tab.dbType || "common", tab.connectionId || "default", item.type, item.name || item.label);
                }
                window.AppMetadataCache.clearCompletions();
            } catch (mruErr) { /* history is a nicety */ }

            var wrapper = adapter._editor;
            wrapper.pinView();
            try {
                await this._insert(adapter, item, currentContext);
            } finally {
                wrapper.unpinView();
            }
        }

        async _insert(adapter, item, currentContext) {
            var cursor = adapter.getCursor();
            var line = adapter.getLine(cursor.line);
            var wordLen = currentContext ? (currentContext.currentWord || "").length : 0;
            var fromPos = { line: cursor.line, ch: cursor.ch - wordLen };
            var toPos = cursor;
            if (currentContext && currentContext.replacementRange) {
                fromPos = adapter.posFromIndex(currentContext.replacementRange.start);
                toPos = adapter.posFromIndex(currentContext.replacementRange.end);
            }
            if (item.replacementRange) {
                fromPos = adapter.posFromIndex(item.replacementRange.start);
                toPos = adapter.posFromIndex(item.replacementRange.end);
            }

            // Snippet: real tab stops through Monaco's snippet controller
            if (item.type === "snippet" && item.snippetData) {
                adapter.setSelection(fromPos, toPos);
                var snipBody = item.snippetData.monacoBody || item.snippetData.body;
                if (typeof window.convertRedGateToMonacoSnippet === "function") {
                    snipBody = window.convertRedGateToMonacoSnippet(snipBody);
                }
                if (!Host.insertSnippet(snipBody)) {
                    adapter.replaceRange(window.AppSnippets.expandSnippetBody(item.snippetData).expandedText, fromPos, toPos);
                }
                adapter.focus();
                return;
            }

            // ALTER / CREATE OR ALTER / CREATE OR REPLACE <object>: bring in the object's definition
            var typedLine = line.substring(0, cursor.ch).trim();
            var hasAlterKeyword = /\b(ALTER|CREATE\s+OR\s+(?:ALTER|REPLACE))\s+(?:PROCEDURE|PROC|FUNCTION|FUNC|TRIGGER|VIEW)\b/i.test(typedLine);
            var isAlterContext = Boolean((currentContext && currentContext.isAlterContext) || hasAlterKeyword);
            if (isAlterContext && ["procedure", "function", "trigger", "view"].indexOf(item.type) >= 0) {
                var tabCtx = this.getActiveTabContext();
                var schema = item.schema || tabCtx.schema || "dbo";
                var defScript = await this.dataProvider.fetchDefinition(tabCtx.connectionId, tabCtx.database, schema, item.name, item.type, tabCtx.dbType);
                if (defScript) {
                    this._replaceWithDefinition(adapter, item, currentContext, defScript, typedLine, cursor, line);
                    return;
                }
            }

            var text = item.insertText || item.name;
            if (item.type === "keyword") {
                var follow = window.SqlContextAnalyzer.FOLLOW_UP_KEYWORDS && window.SqlContextAnalyzer.FOLLOW_UP_KEYWORDS[item.name];
                text = Host.applyKeywordCase(text);
                if ((follow || item.followSpace) && !text.endsWith(" ")) text += " ";
            }
            if (item.type === "keyword" && text === "(") {
                adapter.replaceRange("()", fromPos, toPos);
                adapter.setCursor(adapter.posFromIndex(adapter.indexFromPos(fromPos) + 1));
                adapter.focus();
                setTimeout(() => this.trigger(adapter, false, "rule_space"), 30);
                return;
            }
            adapter.replaceRange(text, fromPos, toPos);
            adapter.setCursor(adapter.posFromIndex(adapter.indexFromPos(fromPos) + text.length));
            adapter.focus();
            // IF EXISTS <...>, GROUP BY <columns>: the next part is offered straight away
            if (item.type === "keyword" && item.followSpace) {
                setTimeout(() => this.trigger(adapter, false, "rule_space"), 30);
            }
        }

        _replaceWithDefinition(adapter, item, currentContext, defScript, typedLine, cursor, line) {
            var action = currentContext && currentContext.alterAction ? currentContext.alterAction.toUpperCase() : "";
            if (!action) {
                if (/\bCREATE\s+OR\s+ALTER\b/i.test(typedLine)) action = "CREATE OR ALTER";
                else if (/\bCREATE\s+OR\s+REPLACE\b/i.test(typedLine)) action = "CREATE OR REPLACE";
                else action = "ALTER";
            }
            var script = defScript.replace(
                /^\s*(?:CREATE(?:\s+OR\s+(?:ALTER|REPLACE))?|ALTER)\s+(VIEW|PROCEDURE|PROC|FUNCTION|TRIGGER)\b/im,
                action + " $1");
            var from = { line: cursor.line, ch: 0 };
            if (currentContext && typeof currentContext.alterStartOffset === "number" && currentContext.alterStartOffset >= 0) {
                from = adapter.posFromIndex(currentContext.alterStartOffset);
            }
            adapter.replaceRange(script, from, { line: cursor.line, ch: line.length });

            var escaped = item.name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
            var regex = new RegExp(
                "\\b(?:ALTER|CREATE(?:\\s+OR\\s+(?:ALTER|REPLACE))?)\\s+(?:PROCEDURE|PROC|FUNCTION|FUNC|TRIGGER|VIEW)\\s+(?:\\[?[\\w#@$]+\\]?\\.|\"[^\"]+\"\\.|`[^`]+`\\.|[\\w#@$]+\\.)?(?:\\["
                + escaped + "\\]|\"" + escaped + "\"|`" + escaped + "`|" + escaped + ")", "i");
            var match = regex.exec(adapter.getText());
            if (match) {
                var target = adapter.posFromIndex(match.index + match[0].length);
                adapter.setCursor(target);
                adapter.scrollIntoView(target, 50);
            }
            adapter.focus();
        }

        async goToDefinition() {
            var adapter = this.activeAdapter;
            if (!adapter || !Host.isEnabled()) return;
            var cursor = adapter.getCursor();
            var range = adapter.findWordAt(cursor);
            var word = adapter.getRange(range.anchor, range.head).trim().replace(/^[\["`]+|[\]"`]+$/g, "").trim();
            var tab = this.getActiveTabContext();
            if (!word || !tab.connectionId) return;
            var objects = await this.dataProvider.fetchObjects(tab.connectionId, tab.database, tab.schema);
            var lower = word.toLowerCase();
            var match = (objects || []).filter(function (o) { return o.name.toLowerCase() === lower; })[0];
            if (!match) return;
            var script = await this.dataProvider.fetchDefinition(tab.connectionId, tab.database, match.schema || tab.schema, match.name, match.type, tab.dbType);
            if (script) window.AppTabs.createTab({ title: match.name + ".sql", content: script });
        }

        hidePopup() {
            this._currentRequestId++;
            clearTimeout(this.debounceTimer);
            var engine = window.DefaultCompletionEngine;
            if (engine && typeof engine.cancelPending === "function") engine.cancelPending();
            this.popupUI.hide();
            this.activeContext = null;
            this.activeCandidates = null;
            this.rawCandidates = null;
            this._lastContextCategory = null;
            this.triggerController.clearActiveCandidates();
            this.triggerController.clearActiveToken();
            this.triggerController.setState(window.CompletionState.IDLE, "hidden");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. PUBLIC FACADE
    // ─────────────────────────────────────────────────────────────────────────
    var dataProvider = new IntelliSenseDataProvider();
    var popupUI = new IntelliSensePopupUI();
    var controller = new IntelliSenseController(dataProvider, popupUI);

    window.AppIntelliSense = {
        init: function () { popupUI.init(); },
        attachEditor: function (ed) { controller.attachEditor(ed); },
        triggerIntelliSense: function (ed, isManual, reason) { return controller.trigger(ed, isManual, reason); },
        hidePopup: function () { controller.hidePopup(); },
        goToDefinition: function () { return controller.goToDefinition(); },
        isPopupVisible: function () { return popupUI.isVisible; },
        getController: function () { return controller; },
        getPopupUI: function () { return popupUI; },
        getDataProvider: function () { return dataProvider; },
        getCompletionEngine: function () { return controller.completionEngine; },
        getTriggerController: function () { return controller.triggerController; },
        invalidateConnection: function (c) { dataProvider.invalidateConnection(c); },
        invalidateDatabase: function (c, db) { dataProvider.invalidateDatabase(c, db); },
        invalidateAll: function () { dataProvider.clearCache(); },
        getActiveTabContext: function () { return controller.getActiveTabContext(); }
    };
    window.IntelliSense = window.AppIntelliSense;
})(window);

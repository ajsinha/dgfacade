/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * DGFacade — Custom JavaScript (v1.6.1)
 * Chakra-Inspired Theme with Dark/Light Toggle
 */

(function() {
    'use strict';

    const DGFacade = {

        // ─── Theme Toggle (Dark/Light) ─────────────────────────────────
        toggleTheme: function() {
            const html = document.documentElement;
            const current = html.getAttribute('data-theme') || 'dark';
            const next = current === 'dark' ? 'light' : 'dark';
            html.setAttribute('data-theme', next);
            localStorage.setItem('dg-theme', next);
            this._updateThemeIcon(next);
        },

        // Keep backward compat — old code called toggleDarkMode
        toggleDarkMode: function() { this.toggleTheme(); },

        _initTheme: function() {
            const saved = localStorage.getItem('dg-theme') || 'dark';
            document.documentElement.setAttribute('data-theme', saved);
            this._updateThemeIcon(saved);
        },

        _updateThemeIcon: function(theme) {
            const sunIcon = document.getElementById('themeIconSun');
            const moonIcon = document.getElementById('themeIconMoon');
            if (sunIcon && moonIcon) {
                if (theme === 'dark') {
                    sunIcon.style.display = 'none';
                    moonIcon.style.display = 'inline';
                } else {
                    sunIcon.style.display = 'inline';
                    moonIcon.style.display = 'none';
                }
            }
            // Legacy icon support
            const icon = document.getElementById('darkModeIcon');
            if (icon) {
                icon.className = theme === 'dark' ? 'fas fa-sun' : 'fas fa-moon';
            }
        },

        getTheme: function() {
            return document.documentElement.getAttribute('data-theme') || 'dark';
        },

        // ─── Toast Notifications ───────────────────────────────────────
        showToast: function(message, type, duration) {
            type = type || 'info';
            duration = duration || 4000;
            var container = document.getElementById('toastContainer') || this._createToastContainer();
            var colorMap = {
                success: 'var(--dg-success)',
                danger: 'var(--dg-danger)',
                warning: 'var(--dg-warning)',
                info: 'var(--dg-info)'
            };
            var icons = { success:'check-circle', danger:'exclamation-circle', warning:'exclamation-triangle', info:'info-circle' };

            var toastEl = document.createElement('div');
            toastEl.className = 'toast show';
            toastEl.setAttribute('role', 'alert');
            toastEl.style.cssText = 'background:var(--dg-bg-card);border:1px solid var(--dg-border);border-radius:10px;box-shadow:var(--dg-shadow-lg);overflow:hidden;min-width:300px;margin-bottom:8px;';

            toastEl.innerHTML =
                '<div style="display:flex;align-items:center;padding:10px 14px;border-bottom:1px solid var(--dg-border);background:rgba(var(--dg-primary-rgb),0.04);">' +
                    '<i class="fas fa-' + (icons[type]||'info-circle') + ' me-2" style="color:' + (colorMap[type]||colorMap.info) + ';"></i>' +
                    '<strong class="me-auto" style="color:var(--dg-text-heading);font-size:0.78rem;">' + type.charAt(0).toUpperCase() + type.slice(1) + '</strong>' +
                    '<button type="button" style="background:none;border:none;color:var(--dg-text-muted);cursor:pointer;font-size:1.1rem;padding:0 4px;" onclick="this.closest(\'.toast\').remove()">&times;</button>' +
                '</div>' +
                '<div style="padding:10px 14px;font-size:0.82rem;color:var(--dg-text-primary);">' + message + '</div>';

            container.appendChild(toastEl);
            setTimeout(function() {
                toastEl.style.transition = 'opacity 0.3s';
                toastEl.style.opacity = '0';
                setTimeout(function() { toastEl.remove(); }, 300);
            }, duration);
        },

        _createToastContainer: function() {
            var c = document.createElement('div');
            c.id = 'toastContainer';
            c.className = 'toast-container position-fixed top-0 end-0 p-3';
            c.style.zIndex = '9999';
            document.body.appendChild(c);
            return c;
        },

        // ─── Favicon Badge ─────────────────────────────────────────────
        setFaviconBadge: function(count) {
            var canvas = document.createElement('canvas');
            canvas.width = 32; canvas.height = 32;
            var ctx = canvas.getContext('2d');

            // Purple circle with DG
            ctx.fillStyle = '#7c3aed';
            ctx.beginPath(); ctx.arc(16,16,14,0,Math.PI*2); ctx.fill();
            ctx.fillStyle = '#fff'; ctx.font = 'bold 14px JetBrains Mono,monospace';
            ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
            ctx.fillText('DG',16,16);

            if (count > 0) {
                ctx.fillStyle = '#ef4444';
                ctx.beginPath(); ctx.arc(24,6,8,0,Math.PI*2); ctx.fill();
                ctx.fillStyle = '#fff'; ctx.font = 'bold 10px sans-serif';
                ctx.fillText(count>9?'9+':String(count),24,7);
            }

            var link = document.querySelector("link[rel*='icon']");
            if (!link) { link = document.createElement('link'); link.rel = 'icon'; document.head.appendChild(link); }
            link.href = canvas.toDataURL('image/png');
        },

        _pollFaviconBadge: function() {
            var self = this;
            fetch('/api/v1/ingesters')
                .then(function(r) { return r.ok ? r.json() : null; })
                .then(function(data) { if (data) self.setFaviconBadge(data.active || 0); })
                .catch(function(){});
        },

        // ─── Keyboard Shortcuts ────────────────────────────────────────
        _initKeyboardShortcuts: function() {
            var self = this;
            document.addEventListener('keydown', function(e) {
                var tag = (e.target.tagName||'').toLowerCase();
                if (tag==='input'||tag==='textarea'||tag==='select') return;

                if ((e.ctrlKey||e.metaKey) && e.key==='k') { e.preventDefault(); self.showCommandPalette(); return; }
                if ((e.ctrlKey||e.metaKey) && e.key==='d') { e.preventDefault(); self.toggleTheme(); return; }
                if ((e.ctrlKey||e.metaKey) && e.shiftKey && e.key==='L') { e.preventDefault(); window.location.href='/monitoring/logs'; return; }
                if ((e.ctrlKey||e.metaKey) && e.shiftKey && e.key==='H') { e.preventDefault(); window.location.href='/health'; return; }
                if (e.key==='?' && !e.ctrlKey && !e.metaKey) { self.showKeyboardShortcuts(); }
            });
        },

        showKeyboardShortcuts: function() {
            var existing = document.getElementById('shortcutsModal');
            if (existing) existing.remove();

            var modal = document.createElement('div');
            modal.id = 'shortcutsModal'; modal.className = 'modal fade';
            modal.innerHTML =
                '<div class="modal-dialog modal-dialog-centered">' +
                    '<div class="modal-content">' +
                        '<div class="modal-header">' +
                            '<h5 class="modal-title"><i class="fas fa-keyboard me-2" style="color:var(--dg-primary);"></i>Keyboard Shortcuts</h5>' +
                            '<button type="button" class="btn-close" data-bs-dismiss="modal"></button>' +
                        '</div>' +
                        '<div class="modal-body">' +
                            '<table class="table table-sm mb-0">' +
                                '<tbody>' +
                                    '<tr><td><kbd>Ctrl</kbd>+<kbd>K</kbd></td><td>Command Palette</td></tr>' +
                                    '<tr><td><kbd>Ctrl</kbd>+<kbd>D</kbd></td><td>Toggle Theme</td></tr>' +
                                    '<tr><td><kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>L</kbd></td><td>Live Logs</td></tr>' +
                                    '<tr><td><kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>H</kbd></td><td>Health Check</td></tr>' +
                                    '<tr><td><kbd>?</kbd></td><td>Show Shortcuts</td></tr>' +
                                '</tbody>' +
                            '</table>' +
                        '</div>' +
                    '</div>' +
                '</div>';
            document.body.appendChild(modal);
            new bootstrap.Modal(modal).show();
            modal.addEventListener('hidden.bs.modal', function() { modal.remove(); });
        },

        // ─── Command Palette ───────────────────────────────────────────
        showCommandPalette: function() {
            if (document.getElementById('commandPalette')) { document.getElementById('commandPalette').remove(); return; }

            var pages = [
                {label:'Dashboard',       icon:'tachometer-alt',  url:'/'},
                {label:'Handlers',        icon:'cogs',            url:'/monitoring/handlers'},
                {label:'Handler Playground', icon:'flask',         url:'/playground'},
                {label:'Health Check',    icon:'heartbeat',       url:'/health'},
                {label:'Live Logs',       icon:'scroll',          url:'/monitoring/logs'},
                {label:'Cluster',         icon:'network-wired',   url:'/monitoring/cluster'},
                {label:'Ingestion',       icon:'download',        url:'/monitoring/ingestion'},
                {label:'Brokers',         icon:'server',          url:'/admin/brokers'},
                {label:'Input Channels',  icon:'sign-in-alt',     url:'/admin/input-channels'},
                {label:'Output Channels', icon:'sign-out-alt',    url:'/admin/output-channels'},
                {label:'Ingesters',       icon:'download',        url:'/admin/ingesters'},
                {label:'Admin Handlers',  icon:'cogs',            url:'/admin/handlers'},
                {label:'Python Workers',  icon:'python',          url:'/admin/python-workers', fab:true},
                {label:'Users',           icon:'users',           url:'/admin/users'},
                {label:'API Keys',        icon:'key',             url:'/admin/apikeys'},
                {label:'Help',            icon:'book',            url:'/help'},
                {label:'About',           icon:'info-circle',     url:'/about'}
            ];

            var overlay = document.createElement('div');
            overlay.id = 'commandPalette';
            overlay.addEventListener('click', function(e) { if (e.target===overlay) overlay.remove(); });

            var box = document.createElement('div');
            box.className = 'dg-palette-box';

            var input = document.createElement('input');
            input.type = 'text'; input.placeholder = 'Search pages...  (Esc to close)';

            var list = document.createElement('div');
            list.style.cssText = 'max-height:300px;overflow-y:auto;';

            function render(filter) {
                var term = (filter||'').toLowerCase();
                list.innerHTML = '';
                var matches = pages.filter(function(p) { return p.label.toLowerCase().includes(term); });
                matches.forEach(function(p, i) {
                    var item = document.createElement('a');
                    item.href = p.url;
                    if (i===0) item.classList.add('dg-palette-active');
                    item.innerHTML = '<i class="' + (p.fab?'fab':'fas') + ' fa-' + p.icon + '"></i>' + p.label;
                    item.addEventListener('mouseenter', function() {
                        list.querySelectorAll('a').forEach(function(a) { a.classList.remove('dg-palette-active'); });
                        item.classList.add('dg-palette-active');
                    });
                    list.appendChild(item);
                });
            }

            input.addEventListener('input', function() { render(input.value); });
            input.addEventListener('keydown', function(e) {
                if (e.key==='Escape') { overlay.remove(); return; }
                if (e.key==='Enter') {
                    var first = list.querySelector('a.dg-palette-active') || list.querySelector('a');
                    if (first) window.location.href = first.href;
                }
                if (e.key==='ArrowDown' || e.key==='ArrowUp') {
                    e.preventDefault();
                    var items = Array.from(list.querySelectorAll('a'));
                    var idx = items.findIndex(function(a) { return a.classList.contains('dg-palette-active'); });
                    items.forEach(function(a) { a.classList.remove('dg-palette-active'); });
                    if (e.key==='ArrowDown') idx = Math.min(idx+1, items.length-1);
                    else idx = Math.max(idx-1, 0);
                    if (items[idx]) { items[idx].classList.add('dg-palette-active'); items[idx].scrollIntoView({block:'nearest'}); }
                }
            });

            box.appendChild(input);
            box.appendChild(list);
            overlay.appendChild(box);
            document.body.appendChild(overlay);
            render('');
            input.focus();
        },

        // ─── Utility Functions ─────────────────────────────────────────
        formatNumber: function(num) { return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g,","); },
        formatBytes: function(bytes,d) {
            if (bytes===0) return '0 B'; d=d||2;
            var k=1024,s=['B','KB','MB','GB','TB'],i=Math.floor(Math.log(bytes)/Math.log(k));
            return parseFloat((bytes/Math.pow(k,i)).toFixed(d))+' '+s[i];
        },
        copyToClipboard: function(text) {
            var self = this;
            navigator.clipboard.writeText(text)
                .then(function() { self.showToast('Copied to clipboard!','success',2000); })
                .catch(function() { self.showToast('Failed to copy','danger',2000); });
        }
    };

    // ── JSON Formatter ──────────────────────────────────────────────
    var JsonFormatter = {
        format: function(json) {
            if (typeof json==='string') { try { json=JSON.parse(json); } catch(e) { return json; } }
            return JSON.stringify(json,null,2);
        },
        highlight: function(json) {
            if (typeof json==='string') { try { json=JSON.parse(json); } catch(e) { return this.escapeHtml(json); } }
            return this.syntaxHighlight(JSON.stringify(json,null,2));
        },
        syntaxHighlight: function(str) {
            str = this.escapeHtml(str);
            return str.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
                function(match) {
                    var cls='json-number';
                    if (/^"/.test(match)) cls = /:$/.test(match)?'json-key':'json-string';
                    else if (/true|false/.test(match)) cls='json-boolean';
                    else if (/null/.test(match)) cls='json-null';
                    return '<span class="'+cls+'">'+match+'</span>';
                });
        },
        escapeHtml: function(str) {
            var div=document.createElement('div'); div.appendChild(document.createTextNode(str)); return div.innerHTML;
        }
    };

    // ── Init ────────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function() {
        DGFacade._initTheme();
        DGFacade._initKeyboardShortcuts();

        document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(function(el) { new bootstrap.Tooltip(el); });
        document.querySelectorAll('[data-bs-toggle="popover"]').forEach(function(el) { new bootstrap.Popover(el); });
        document.querySelectorAll('.alert-dismissible').forEach(function(alert) {
            setTimeout(function() { try { bootstrap.Alert.getOrCreateInstance(alert).close(); } catch(e){} }, 5000);
        });
        document.querySelectorAll('pre code.json').forEach(function(block) {
            try { block.innerHTML = JsonFormatter.highlight(block.textContent); } catch(e){}
        });

        DGFacade._pollFaviconBadge();
        setInterval(function() { DGFacade._pollFaviconBadge(); }, 30000);

        console.log('DGFacade v1.6.1 UI initialized [theme=' + DGFacade.getTheme() + ']');
    });

    window.DGFacade = DGFacade;
    window.JsonFormatter = JsonFormatter;

})();

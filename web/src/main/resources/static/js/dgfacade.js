/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * DGFacade — Custom JavaScript (v1.6.0)
 * Enhancements: Dark Mode, Toast Notifications, Favicon Badge, Keyboard Shortcuts, Command Palette
 */

(function() {
    'use strict';

    // ════════════════════════════════════════════════════════════════════
    //  DGFacade — Core Namespace
    // ════════════════════════════════════════════════════════════════════

    const DGFacade = {

        // ─── Dark Mode (Enhancement 9) ─────────────────────────────────
        toggleDarkMode: function() {
            const isDark = document.body.classList.toggle('dg-dark-mode');
            localStorage.setItem('dg-dark-mode', isDark ? 'true' : 'false');
            this._updateDarkModeIcon(isDark);
        },

        _initDarkMode: function() {
            const saved = localStorage.getItem('dg-dark-mode');
            if (saved === 'true') {
                document.body.classList.add('dg-dark-mode');
                this._updateDarkModeIcon(true);
            }
        },

        _updateDarkModeIcon: function(isDark) {
            const icon = document.getElementById('darkModeIcon');
            if (icon) {
                icon.className = isDark ? 'fas fa-sun' : 'fas fa-moon';
            }
        },

        // ─── Toast Notifications (Enhancement 10) ─────────────────────
        showToast: function(message, type, duration) {
            type = type || 'info';
            duration = duration || 4000;
            const container = document.getElementById('toastContainer') || this._createToastContainer();
            const icons = { success: 'check-circle', danger: 'exclamation-circle', warning: 'exclamation-triangle', info: 'info-circle' };
            const id = 'toast-' + Date.now();

            const toastEl = document.createElement('div');
            toastEl.id = id;
            toastEl.className = 'toast show border-0 shadow-lg';
            toastEl.setAttribute('role', 'alert');
            toastEl.innerHTML =
                '<div class="toast-header bg-' + type + ' text-white">' +
                    '<i class="fas fa-' + (icons[type] || 'info-circle') + ' me-2"></i>' +
                    '<strong class="me-auto">' + type.charAt(0).toUpperCase() + type.slice(1) + '</strong>' +
                    '<button type="button" class="btn-close btn-close-white" onclick="this.closest(\'.toast\').remove()"></button>' +
                '</div>' +
                '<div class="toast-body">' + message + '</div>';

            container.appendChild(toastEl);

            // Auto-dismiss with fade
            setTimeout(function() {
                toastEl.style.transition = 'opacity 0.3s';
                toastEl.style.opacity = '0';
                setTimeout(function() { toastEl.remove(); }, 300);
            }, duration);
        },

        _createToastContainer: function() {
            const c = document.createElement('div');
            c.id = 'toastContainer';
            c.className = 'toast-container position-fixed top-0 end-0 p-3';
            c.style.zIndex = '9999';
            document.body.appendChild(c);
            return c;
        },

        // ─── Favicon Badge (Enhancement 11) ────────────────────────────
        setFaviconBadge: function(count) {
            const canvas = document.createElement('canvas');
            canvas.width = 32;
            canvas.height = 32;
            const ctx = canvas.getContext('2d');

            // Draw base icon (blue circle with network)
            ctx.fillStyle = '#0079c1';
            ctx.beginPath();
            ctx.arc(16, 16, 14, 0, Math.PI * 2);
            ctx.fill();
            ctx.fillStyle = '#fff';
            ctx.font = 'bold 16px sans-serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText('DG', 16, 16);

            if (count > 0) {
                // Badge circle
                const badgeX = 24, badgeY = 6, badgeR = 8;
                ctx.fillStyle = '#dc3545';
                ctx.beginPath();
                ctx.arc(badgeX, badgeY, badgeR, 0, Math.PI * 2);
                ctx.fill();
                ctx.fillStyle = '#fff';
                ctx.font = 'bold 10px sans-serif';
                ctx.fillText(count > 9 ? '9+' : String(count), badgeX, badgeY + 1);
            }

            let link = document.querySelector("link[rel*='icon']");
            if (!link) {
                link = document.createElement('link');
                link.rel = 'icon';
                document.head.appendChild(link);
            }
            link.href = canvas.toDataURL('image/png');
        },

        _pollFaviconBadge: function() {
            const self = this;
            // Poll ingestion stats for active count
            fetch('/api/v1/ingesters')
                .then(function(r) { return r.ok ? r.json() : null; })
                .then(function(data) {
                    if (data) self.setFaviconBadge(data.active || 0);
                })
                .catch(function() { /* silent */ });
        },

        // ─── Keyboard Shortcuts (Enhancement 12) ──────────────────────
        _initKeyboardShortcuts: function() {
            const self = this;
            document.addEventListener('keydown', function(e) {
                // Don't trigger in input/textarea
                const tag = (e.target.tagName || '').toLowerCase();
                if (tag === 'input' || tag === 'textarea' || tag === 'select') return;

                // Ctrl+K — Command Palette
                if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
                    e.preventDefault();
                    self.showCommandPalette();
                    return;
                }
                // Ctrl+D — Dark Mode
                if ((e.ctrlKey || e.metaKey) && e.key === 'd') {
                    e.preventDefault();
                    self.toggleDarkMode();
                    return;
                }
                // Ctrl+Shift+L — Logs
                if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'L') {
                    e.preventDefault();
                    window.location.href = '/monitoring/logs';
                    return;
                }
                // Ctrl+Shift+H — Health
                if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'H') {
                    e.preventDefault();
                    window.location.href = '/health';
                    return;
                }
                // ? — Show shortcuts
                if (e.key === '?' && !e.ctrlKey && !e.metaKey) {
                    self.showKeyboardShortcuts();
                }
            });
        },

        showKeyboardShortcuts: function() {
            const existing = document.getElementById('shortcutsModal');
            if (existing) existing.remove();

            const modal = document.createElement('div');
            modal.id = 'shortcutsModal';
            modal.className = 'modal fade';
            modal.innerHTML =
                '<div class="modal-dialog modal-dialog-centered">' +
                    '<div class="modal-content">' +
                        '<div class="modal-header">' +
                            '<h5 class="modal-title"><i class="fas fa-keyboard me-2"></i>Keyboard Shortcuts</h5>' +
                            '<button type="button" class="btn-close" data-bs-dismiss="modal"></button>' +
                        '</div>' +
                        '<div class="modal-body">' +
                            '<table class="table table-sm mb-0">' +
                                '<tbody>' +
                                    '<tr><td><kbd>Ctrl</kbd> + <kbd>K</kbd></td><td>Quick Search / Command Palette</td></tr>' +
                                    '<tr><td><kbd>Ctrl</kbd> + <kbd>D</kbd></td><td>Toggle Dark Mode</td></tr>' +
                                    '<tr><td><kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>L</kbd></td><td>Live Logs</td></tr>' +
                                    '<tr><td><kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>H</kbd></td><td>Health Check</td></tr>' +
                                    '<tr><td><kbd>?</kbd></td><td>Show this dialog</td></tr>' +
                                '</tbody>' +
                            '</table>' +
                        '</div>' +
                    '</div>' +
                '</div>';
            document.body.appendChild(modal);
            new bootstrap.Modal(modal).show();
            modal.addEventListener('hidden.bs.modal', function() { modal.remove(); });
        },

        // ─── Command Palette (Enhancement 12) ─────────────────────────
        showCommandPalette: function() {
            const existing = document.getElementById('commandPalette');
            if (existing) { existing.remove(); return; }

            const pages = [
                { label: 'Dashboard', icon: 'tachometer-alt', url: '/' },
                { label: 'Handlers', icon: 'cogs', url: '/monitoring/handlers' },
                { label: 'Playground', icon: 'flask', url: '/playground' },
                { label: 'Health Check', icon: 'heartbeat', url: '/health' },
                { label: 'Live Logs', icon: 'scroll', url: '/monitoring/logs' },
                { label: 'Cluster', icon: 'network-wired', url: '/monitoring/cluster' },
                { label: 'Ingestion', icon: 'download', url: '/monitoring/ingestion' },
                { label: 'Brokers', icon: 'server', url: '/admin/brokers' },
                { label: 'Input Channels', icon: 'sign-in-alt', url: '/admin/input-channels' },
                { label: 'Output Channels', icon: 'sign-out-alt', url: '/admin/output-channels' },
                { label: 'Users', icon: 'users', url: '/admin/users' },
                { label: 'API Keys', icon: 'key', url: '/admin/apikeys' },
                { label: 'Help', icon: 'book', url: '/help' },
                { label: 'Operational Tooling', icon: 'tools', url: '/help/operational-tooling' },
                { label: 'About', icon: 'info-circle', url: '/about' },
            ];

            const overlay = document.createElement('div');
            overlay.id = 'commandPalette';
            overlay.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.5);z-index:10000;display:flex;justify-content:center;padding-top:15vh;';
            overlay.addEventListener('click', function(e) { if (e.target === overlay) overlay.remove(); });

            const box = document.createElement('div');
            box.style.cssText = 'background:var(--bs-body-bg,#fff);border-radius:12px;width:480px;max-height:400px;box-shadow:0 20px 60px rgba(0,0,0,0.3);overflow:hidden;';

            const input = document.createElement('input');
            input.type = 'text';
            input.placeholder = 'Search pages...';
            input.className = 'form-control border-0 rounded-0 px-4 py-3';
            input.style.fontSize = '1.1rem';

            const list = document.createElement('div');
            list.style.cssText = 'max-height:300px;overflow-y:auto;';

            function render(filter) {
                const term = (filter || '').toLowerCase();
                list.innerHTML = '';
                const matches = pages.filter(function(p) { return p.label.toLowerCase().includes(term); });
                matches.forEach(function(p, i) {
                    const item = document.createElement('a');
                    item.href = p.url;
                    item.className = 'd-flex align-items-center px-4 py-2 text-decoration-none';
                    item.style.cssText = 'color:inherit;' + (i === 0 ? 'background:var(--bs-primary-bg-subtle, #e7f1ff);' : '');
                    item.innerHTML = '<i class="fas fa-' + p.icon + ' me-3 text-muted" style="width:20px;text-align:center;"></i>' + p.label;
                    item.addEventListener('mouseenter', function() {
                        list.querySelectorAll('a').forEach(function(a) { a.style.background = ''; });
                        item.style.background = 'var(--bs-primary-bg-subtle, #e7f1ff)';
                    });
                    list.appendChild(item);
                });
            }

            input.addEventListener('input', function() { render(input.value); });
            input.addEventListener('keydown', function(e) {
                if (e.key === 'Escape') { overlay.remove(); return; }
                if (e.key === 'Enter') {
                    var first = list.querySelector('a');
                    if (first) window.location.href = first.href;
                }
                if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                    e.preventDefault();
                    var items = list.querySelectorAll('a');
                    var idx = -1;
                    items.forEach(function(a, i) {
                        if (a.style.background) idx = i;
                    });
                    items.forEach(function(a) { a.style.background = ''; });
                    if (e.key === 'ArrowDown') idx = Math.min(idx + 1, items.length - 1);
                    else idx = Math.max(idx - 1, 0);
                    if (items[idx]) items[idx].style.background = 'var(--bs-primary-bg-subtle, #e7f1ff)';
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
        formatNumber: function(num) {
            return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
        },

        formatBytes: function(bytes, decimals) {
            if (bytes === 0) return '0 Bytes';
            decimals = decimals || 2;
            var k = 1024;
            var sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
            var i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(decimals)) + ' ' + sizes[i];
        },

        copyToClipboard: function(text) {
            var self = this;
            navigator.clipboard.writeText(text).then(function() {
                self.showToast('Copied to clipboard!', 'success', 2000);
            }).catch(function() {
                self.showToast('Failed to copy', 'danger', 2000);
            });
        }
    };

    // ════════════════════════════════════════════════════════════════════
    //  JSON Formatter
    // ════════════════════════════════════════════════════════════════════

    const JsonFormatter = {
        format: function(json) {
            if (typeof json === 'string') {
                try { json = JSON.parse(json); } catch (e) { return json; }
            }
            return JSON.stringify(json, null, 2);
        },

        highlight: function(json) {
            if (typeof json === 'string') {
                try { json = JSON.parse(json); } catch (e) { return this.escapeHtml(json); }
            }
            return this.syntaxHighlight(JSON.stringify(json, null, 2));
        },

        syntaxHighlight: function(str) {
            str = this.escapeHtml(str);
            return str.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
                function(match) {
                    var cls = 'json-number';
                    if (/^"/.test(match)) {
                        cls = /:$/.test(match) ? 'json-key' : 'json-string';
                    } else if (/true|false/.test(match)) {
                        cls = 'json-boolean';
                    } else if (/null/.test(match)) {
                        cls = 'json-null';
                    }
                    return '<span class="' + cls + '">' + match + '</span>';
                }
            );
        },

        escapeHtml: function(str) {
            var div = document.createElement('div');
            div.appendChild(document.createTextNode(str));
            return div.innerHTML;
        }
    };

    // ════════════════════════════════════════════════════════════════════
    //  Initialize on DOM Ready
    // ════════════════════════════════════════════════════════════════════

    document.addEventListener('DOMContentLoaded', function() {
        // Initialize tooltips & popovers
        document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(function(el) { new bootstrap.Tooltip(el); });
        document.querySelectorAll('[data-bs-toggle="popover"]').forEach(function(el) { new bootstrap.Popover(el); });

        // Auto-dismiss alerts after 5s
        document.querySelectorAll('.alert-dismissible').forEach(function(alert) {
            setTimeout(function() {
                try { bootstrap.Alert.getOrCreateInstance(alert).close(); } catch(e) {}
            }, 5000);
        });

        // Format JSON in code blocks
        document.querySelectorAll('pre code.json').forEach(function(block) {
            try { block.innerHTML = JsonFormatter.highlight(block.textContent); } catch (e) {}
        });

        // Init enhancements
        DGFacade._initDarkMode();
        DGFacade._initKeyboardShortcuts();

        // Favicon badge polling (every 30s)
        DGFacade._pollFaviconBadge();
        setInterval(function() { DGFacade._pollFaviconBadge(); }, 30000);

        console.log('DGFacade v1.6.0 UI initialized');
    });

    // Export
    window.DGFacade = DGFacade;
    window.JsonFormatter = JsonFormatter;
    // Backward compat
    window.Kuber = DGFacade;

})();

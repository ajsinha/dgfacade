/**
 * DGFacade Table Enhancement — Pagination, Sorting & Column Search
 *
 * Usage: Add class "dg-table" to any <table> element.
 *   Optional data attributes:
 *     data-page-size="10"        — default rows per page (default: 10)
 *     data-page-sizes="5,10,25,50,All"  — dropdown options
 *     data-no-search="true"      — disable column search row
 *     data-no-sort="true"        — disable column sorting
 *
 * Automatically applied on DOMContentLoaded.
 */
(function () {
    'use strict';

    const DEFAULT_PAGE_SIZE = 10;
    const DEFAULT_PAGE_SIZES = [5, 10, 25, 50, 'All'];

    function initDGTable(table) {
        if (table.dataset.dgInit) return; // already initialized
        table.dataset.dgInit = 'true';

        const thead = table.querySelector('thead');
        const tbody = table.querySelector('tbody');
        if (!thead || !tbody) return;

        const headerRow = thead.querySelector('tr');
        if (!headerRow) return;
        const headers = Array.from(headerRow.querySelectorAll('th'));
        const colCount = headers.length;
        if (colCount === 0) return;

        // Gather all data rows (skip empty-state rows — those with colspan + empty-state styling)
        var allRows = Array.from(tbody.querySelectorAll('tr')).filter(function (tr) {
            var cells = tr.querySelectorAll('td');
            if (cells.length === 0) return false;
            if (cells.length === 1) {
                var cell = cells[0];
                var span = parseInt(cell.getAttribute('colspan')) || cell.colSpan || 1;
                if (span >= colCount) {
                    // Distinguish empty-state rows from category/section header rows:
                    // Empty-state rows typically have: large padding (py-4/py-5), fa-inbox icon, "no ... yet" text
                    var text = (cell.textContent || '').toLowerCase().trim();
                    var isEmptyState = cell.classList.contains('py-5') || cell.classList.contains('py-4') ||
                        /\bno\b.*\b(yet|found|recorded|available|data|results|entries)\b/.test(text) ||
                        cell.querySelector('.fa-inbox, .fa-search');
                    if (isEmptyState) return false;
                    // Otherwise it's a category/section header row — keep it
                }
            }
            return true;
        });

        // Keep empty-state rows to show when no data at all
        var emptyRows = Array.from(tbody.querySelectorAll('tr')).filter(function (tr) {
            return !allRows.includes(tr);
        });

        // Config
        const pageSizeAttr = parseInt(table.dataset.pageSize) || DEFAULT_PAGE_SIZE;
        const pageSizesAttr = table.dataset.pageSizes
            ? table.dataset.pageSizes.split(',').map(function (s) { return s.trim() === 'All' ? 'All' : parseInt(s.trim()); })
            : DEFAULT_PAGE_SIZES;
        const noSearch = table.dataset.noSearch === 'true';
        const noSort = table.dataset.noSort === 'true';

        // State
        var state = {
            pageSize: pageSizeAttr,
            currentPage: 1,
            sortCol: -1,
            sortDir: 'asc',
            filters: new Array(colCount).fill(''),
            filteredRows: allRows.slice()
        };

        // ─── Build toolbar (above table) ──────────────────────────────
        var toolbar = document.createElement('div');
        toolbar.className = 'dg-table-toolbar';

        // Left: rows per page dropdown
        var leftDiv = document.createElement('div');
        leftDiv.className = 'dg-table-toolbar-left';

        var label = document.createElement('span');
        label.textContent = 'Show ';
        label.className = 'dg-table-label';
        leftDiv.appendChild(label);

        var select = document.createElement('select');
        select.className = 'form-select form-select-sm dg-table-pagesize';
        pageSizesAttr.forEach(function (sz) {
            var opt = document.createElement('option');
            opt.value = sz;
            opt.textContent = sz;
            if (sz === state.pageSize || (sz === 'All' && state.pageSize >= allRows.length)) opt.selected = true;
            select.appendChild(opt);
        });
        leftDiv.appendChild(select);

        var labelEnd = document.createElement('span');
        labelEnd.textContent = ' entries';
        labelEnd.className = 'dg-table-label';
        leftDiv.appendChild(labelEnd);

        toolbar.appendChild(leftDiv);

        // Right: page info + nav
        var rightDiv = document.createElement('div');
        rightDiv.className = 'dg-table-toolbar-right';

        var pageInfo = document.createElement('span');
        pageInfo.className = 'dg-table-info';
        rightDiv.appendChild(pageInfo);

        var nav = document.createElement('div');
        nav.className = 'dg-table-nav';

        var btnFirst = createBtn('<i class="fas fa-angle-double-left"></i>', 'First');
        var btnPrev = createBtn('<i class="fas fa-angle-left"></i>', 'Previous');
        var pageIndicator = document.createElement('span');
        pageIndicator.className = 'dg-table-page-indicator';
        var btnNext = createBtn('<i class="fas fa-angle-right"></i>', 'Next');
        var btnLast = createBtn('<i class="fas fa-angle-double-right"></i>', 'Last');

        nav.appendChild(btnFirst);
        nav.appendChild(btnPrev);
        nav.appendChild(pageIndicator);
        nav.appendChild(btnNext);
        nav.appendChild(btnLast);
        rightDiv.appendChild(nav);

        toolbar.appendChild(rightDiv);

        // Insert toolbar before table (or before table-responsive wrapper)
        var wrapper = table.closest('.table-responsive') || table;
        wrapper.parentNode.insertBefore(toolbar, wrapper);

        // ─── Build search row (inside thead) ──────────────────────────
        if (!noSearch) {
            var searchRow = document.createElement('tr');
            searchRow.className = 'dg-table-search-row';
            headers.forEach(function (th, i) {
                var td = document.createElement('th');
                td.className = th.className;
                td.style.padding = '4px 6px';
                var input = document.createElement('input');
                input.type = 'text';
                input.className = 'form-control form-control-sm dg-table-search-input';
                input.placeholder = 'Search…';
                input.setAttribute('data-col', i);
                input.addEventListener('input', function () {
                    state.filters[i] = this.value.toLowerCase().trim();
                    applyFilters();
                });
                td.appendChild(input);
                searchRow.appendChild(td);
            });
            thead.appendChild(searchRow);
        }

        // ─── Sorting ──────────────────────────────────────────────────
        if (!noSort) {
            headers.forEach(function (th, i) {
                th.style.cursor = 'pointer';
                th.style.userSelect = 'none';
                th.classList.add('dg-table-sortable');

                // Add sort icon
                var icon = document.createElement('i');
                icon.className = 'fas fa-sort dg-sort-icon';
                th.appendChild(icon);

                th.addEventListener('click', function (e) {
                    // Don't sort if clicking inside a search input
                    if (e.target.tagName === 'INPUT') return;
                    if (state.sortCol === i) {
                        state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
                    } else {
                        state.sortCol = i;
                        state.sortDir = 'asc';
                    }
                    applySort();
                    updateSortIcons();
                    render();
                });
            });
        }

        // ─── Events ───────────────────────────────────────────────────
        select.addEventListener('change', function () {
            state.pageSize = this.value === 'All' ? state.filteredRows.length || allRows.length : parseInt(this.value);
            state.currentPage = 1;
            render();
        });

        btnFirst.addEventListener('click', function () { state.currentPage = 1; render(); });
        btnPrev.addEventListener('click', function () { if (state.currentPage > 1) { state.currentPage--; render(); } });
        btnNext.addEventListener('click', function () { if (state.currentPage < totalPages()) { state.currentPage++; render(); } });
        btnLast.addEventListener('click', function () { state.currentPage = totalPages(); render(); });

        // ─── Core logic ───────────────────────────────────────────────
        function getCellText(tr, colIndex) {
            var cells = tr.querySelectorAll('td');
            if (colIndex >= cells.length) return '';
            return (cells[colIndex].textContent || '').trim();
        }

        function applyFilters() {
            state.filteredRows = allRows.filter(function (tr) {
                return state.filters.every(function (filter, i) {
                    if (!filter) return true;
                    return getCellText(tr, i).toLowerCase().indexOf(filter) !== -1;
                });
            });
            if (state.sortCol >= 0) applySort();
            state.currentPage = 1;
            render();
        }

        function applySort() {
            var col = state.sortCol;
            var dir = state.sortDir === 'asc' ? 1 : -1;
            state.filteredRows.sort(function (a, b) {
                var va = getCellText(a, col);
                var vb = getCellText(b, col);
                // Try numeric comparison
                var na = parseFloat(va.replace(/[^0-9.\-]/g, ''));
                var nb = parseFloat(vb.replace(/[^0-9.\-]/g, ''));
                if (!isNaN(na) && !isNaN(nb)) return (na - nb) * dir;
                return va.localeCompare(vb) * dir;
            });
        }

        function updateSortIcons() {
            headers.forEach(function (th, i) {
                var icon = th.querySelector('.dg-sort-icon');
                if (!icon) return;
                icon.className = 'fas dg-sort-icon ';
                if (state.sortCol === i) {
                    icon.className += state.sortDir === 'asc' ? 'fa-sort-up' : 'fa-sort-down';
                } else {
                    icon.className += 'fa-sort';
                }
            });
        }

        function totalPages() {
            var sz = (select.value === 'All') ? state.filteredRows.length : state.pageSize;
            if (sz <= 0) sz = 1;
            return Math.max(1, Math.ceil(state.filteredRows.length / sz));
        }

        function render() {
            var sz = (select.value === 'All') ? state.filteredRows.length : state.pageSize;
            if (sz <= 0) sz = state.filteredRows.length || 1;
            var tp = totalPages();
            if (state.currentPage > tp) state.currentPage = tp;

            var start = (state.currentPage - 1) * sz;
            var end = Math.min(start + sz, state.filteredRows.length);
            var pageRows = state.filteredRows.slice(start, end);

            // Clear tbody
            while (tbody.firstChild) tbody.removeChild(tbody.firstChild);

            if (pageRows.length === 0) {
                // Show empty state or "no results"
                if (emptyRows.length > 0 && state.filters.every(function (f) { return !f; })) {
                    emptyRows.forEach(function (r) { tbody.appendChild(r); });
                } else {
                    var emptyTr = document.createElement('tr');
                    var emptyTd = document.createElement('td');
                    emptyTd.colSpan = colCount;
                    emptyTd.className = 'text-center py-4 text-muted';
                    emptyTd.innerHTML = '<i class="fas fa-search me-2"></i>No matching records found';
                    emptyTr.appendChild(emptyTd);
                    tbody.appendChild(emptyTr);
                }
            } else {
                pageRows.forEach(function (r) { tbody.appendChild(r); });
            }

            // Update info
            if (state.filteredRows.length === 0) {
                pageInfo.textContent = '0 of ' + allRows.length + ' entries';
            } else {
                pageInfo.textContent = (start + 1) + '–' + end + ' of ' + state.filteredRows.length +
                    (state.filteredRows.length < allRows.length ? ' (filtered from ' + allRows.length + ')' : '') +
                    (allRows.length === 1 ? ' entry' : ' entries');
            }

            // Update page indicator
            pageIndicator.textContent = state.currentPage + ' / ' + tp;

            // Update button states
            btnFirst.disabled = state.currentPage <= 1;
            btnPrev.disabled = state.currentPage <= 1;
            btnNext.disabled = state.currentPage >= tp;
            btnLast.disabled = state.currentPage >= tp;
        }

        // Initial render
        render();
    }

    function createBtn(html, title) {
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn-sm btn-outline-secondary dg-table-nav-btn';
        btn.innerHTML = html;
        btn.title = title;
        return btn;
    }

    // ─── Auto-init on load ────────────────────────────────────────────
    function initAll() {
        document.querySelectorAll('table.dg-table').forEach(initDGTable);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAll);
    } else {
        initAll();
    }

    // Auto-detect dynamically added tables via MutationObserver
    if (typeof MutationObserver !== 'undefined') {
        var observer = new MutationObserver(function (mutations) {
            var found = false;
            mutations.forEach(function (m) {
                m.addedNodes.forEach(function (node) {
                    if (node.nodeType === 1) {
                        if (node.matches && node.matches('table.dg-table') && !node.dataset.dgInit) found = true;
                        if (node.querySelectorAll) {
                            node.querySelectorAll('table.dg-table:not([data-dg-init])').forEach(function () { found = true; });
                        }
                    }
                });
            });
            if (found) initAll();
        });
        document.addEventListener('DOMContentLoaded', function () {
            observer.observe(document.body, { childList: true, subtree: true });
        });
    }

    // Expose for dynamic tables
    window.DGTable = { init: initDGTable, initAll: initAll };
})();

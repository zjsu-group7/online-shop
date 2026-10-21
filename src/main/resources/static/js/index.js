/* ============================================================
   买家端 · 商品列表：分页 / 排序 / 关键词搜索 / 分类筛选
   对应接口：GET /api/products、GET /api/categories
   单品小铺 · 迭代 2 · 邱圣洁
   ============================================================ */
(function () {
  'use strict';

  var App = window.App;
  var Api = window.Api;
  var $ = function (id) { return document.getElementById(id); };

  var PAGE_SIZE = 12; /* 每页 12 件（Wiki 规定） */

  var state = {
    page: 1,
    size: PAGE_SIZE,
    q: '',
    categoryId: '',
    sort: (Api.SORT_OPTIONS && Api.SORT_OPTIONS[0] ? Api.SORT_OPTIONS[0].value : '')
  };

  var els = {
    offlineTip: $('offline-tip'),
    loading: $('list-loading'),
    empty: $('list-empty'),
    body: $('list-body'),
    grid: $('product-grid'),
    pagination: $('pagination'),
    q: $('filter-q'),
    category: $('filter-category'),
    sort: $('filter-sort'),
    searchBtn: $('filter-search-btn'),
    resetBtn: $('filter-reset'),
    summary: $('filter-summary')
  };

  /* ==================== URL 参数同步（便于分享与刷新保持） ==================== */

  function readUrlState() {
    var params = new URLSearchParams(location.search);
    state.q = params.get('q') || '';
    state.categoryId = params.get('categoryId') || '';
    state.sort = params.get('sort') || state.sort;
    state.page = Math.max(1, parseInt(params.get('page') || '1', 10) || 1);
  }

  function writeUrlState() {
    var params = new URLSearchParams();
    if (state.q) params.set('q', state.q);
    if (state.categoryId) params.set('categoryId', state.categoryId);
    if (state.sort) params.set('sort', state.sort);
    if (state.page > 1) params.set('page', String(state.page));
    var qs = params.toString();
    history.replaceState(null, '', location.pathname + (qs ? '?' + qs : ''));
  }

  /* ==================== 筛选栏 ==================== */

  function initSortSelect() {
    var html = '';
    (Api.SORT_OPTIONS || []).forEach(function (opt) {
      html += '<option value="' + App.escapeHtml(opt.value) + '">' + App.escapeHtml(opt.label) + '</option>';
    });
    els.sort.innerHTML = html;
    els.sort.value = state.sort;
  }

  async function loadCategories() {
    try {
      var tree = await Api.getCategories();
      renderCategoryOptions(tree || []);
    } catch (e) {
      /* 类别加载失败不阻塞列表 */
      App.showToast('分类加载失败：' + e.message, 'error');
    }
  }

  /* 两级类别：一级用 optgroup，选项为二级类别 */
  function renderCategoryOptions(tree) {
    var html = '<option value="">全部分类</option>';
    (tree || []).forEach(function (parent) {
      var children = parent.children || [];
      if (!children.length) {
        html += '<option value="' + App.escapeHtml(parent.id) + '">' + App.escapeHtml(parent.name) + '</option>';
        return;
      }
      html += '<optgroup label="' + App.escapeHtml(parent.name) + '">';
      children.forEach(function (child) {
        html += '<option value="' + App.escapeHtml(child.id) + '">' + App.escapeHtml(child.name) + '</option>';
      });
      html += '</optgroup>';
    });
    els.category.innerHTML = html;
    if (state.categoryId) els.category.value = state.categoryId;
  }

  function updateSummary(total) {
    var parts = [];
    if (state.q) parts.push('关键词「' + state.q + '」');
    if (state.categoryId && els.category.selectedIndex > 0) {
      parts.push('分类「' + els.category.options[els.category.selectedIndex].text + '」');
    }
    if (total !== undefined && total !== null) parts.push('共 ' + total + ' 件');
    if (!parts.length) { els.summary.hidden = true; return; }
    els.summary.textContent = parts.join(' · ');
    els.summary.hidden = false;
  }

  /* ==================== 列表加载 ==================== */

  async function loadList() {
    App.show(els.loading, true);
    App.show(els.empty, false);
    App.show(els.body, false);
    App.show(els.offlineTip, false);

    try {
      var data = await Api.listProducts({
        page: state.page,
        size: state.size,
        sort: state.sort,
        q: state.q,
        categoryId: state.categoryId
      });
      renderList(data);
    } catch (e) {
      App.show(els.loading, false);
      if (e.status === 0) {
        App.show(els.offlineTip, true);
        App.show(els.empty, true);
      } else {
        App.showToast(e.message, 'error');
        App.show(els.empty, true);
      }
    }
  }

  function renderList(data) {
    App.show(els.loading, false);

    var items = (data && data.items) || [];
    var total = (data && data.total) || 0;
    var page = (data && data.page) || state.page;
    var size = (data && data.size) || state.size;

    updateSummary(total);

    if (!items.length) {
      App.show(els.empty, true);
      App.show(els.body, false);
      return;
    }

    var html = '';
    items.forEach(function (p) {
      var cover = '';
      if (p.images && p.images.length) cover = App.resolveImageUrl(p.images[0]);
      else if (p.coverImage) cover = App.resolveImageUrl(p.coverImage);
      else if (p.image) cover = App.resolveImageUrl(p.image);
      if (!cover) cover = App.PLACEHOLDER_IMAGE;

      var stockText = '';
      if (p.status === 'ON_SALE') {
        stockText = (p.stock === 0 ? '已售罄' : '库存 ' + App.escapeHtml(p.stock));
      }

      html +=
        '<a class="product-card" href="product.html?id=' + encodeURIComponent(p.id) + '">' +
          '<div class="pc-media">' +
            '<img src="' + App.escapeHtml(cover) + '" alt="' + App.escapeHtml(p.name || '') + '" data-fallback="1">' +
            (p.status !== 'ON_SALE' ? '<span class="pc-mask">已下架</span>' : '') +
          '</div>' +
          '<div class="pc-body">' +
            '<div class="pc-name">' + App.escapeHtml(p.name || '未命名商品') + '</div>' +
            '<div class="pc-meta">' +
              (p.categoryName ? '<span class="pc-cat">' + App.escapeHtml(p.categoryName) + '</span>' : '') +
              (stockText ? '<span class="pc-stock">' + stockText + '</span>' : '') +
            '</div>' +
            '<div class="pc-foot">' +
              '<span class="pc-price"><span class="price-symbol">&yen;</span>' + App.escapeHtml(App.formatPrice(p.price)) + '</span>' +
              '<span class="pc-time">' + App.escapeHtml(App.formatTime(p.createdAt)) + '</span>' +
            '</div>' +
          '</div>' +
        '</a>';
    });

    els.grid.innerHTML = html;
    Array.prototype.forEach.call(els.grid.querySelectorAll('img[data-fallback]'), App.bindImageFallback);

    App.show(els.empty, false);
    App.show(els.body, true);

    App.renderPagination(els.pagination, page, size, total, function (target) {
      state.page = target;
      writeUrlState();
      loadList();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  /* ==================== 事件绑定 ==================== */

  els.searchBtn.addEventListener('click', function () {
    state.q = els.q.value.trim();
    state.categoryId = els.category.value;
    state.sort = els.sort.value;
    state.page = 1;
    writeUrlState();
    loadList();
  });

  els.q.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') { e.preventDefault(); els.searchBtn.click(); }
  });

  els.category.addEventListener('change', function () {
    state.categoryId = this.value;
    state.page = 1;
    writeUrlState();
    loadList();
  });

  els.sort.addEventListener('change', function () {
    state.sort = this.value;
    state.page = 1;
    writeUrlState();
    loadList();
  });

  els.resetBtn.addEventListener('click', function () {
    els.q.value = '';
    els.category.value = '';
    els.sort.value = (Api.SORT_OPTIONS && Api.SORT_OPTIONS[0] ? Api.SORT_OPTIONS[0].value : '');
    state.q = '';
    state.categoryId = '';
    state.sort = els.sort.value;
    state.page = 1;
    writeUrlState();
    loadList();
  });

  /* ==================== 启动 ==================== */

  App.renderCustomerNav();
  readUrlState();
  els.q.value = state.q;
  initSortSelect();
  loadCategories();
  loadList();
})();

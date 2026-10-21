/* ============================================================
   卖家后台：登录 / 类别管理 / 发布商品 / 库存 / 重新上架 / 客户与购买历史
   对应接口：
     POST /api/seller/login、POST /api/seller/logout
     GET POST PUT DELETE /api/seller/categories
     POST /api/seller/products、GET /api/seller/products
     PUT /api/seller/products/{id}/stock
     POST /api/seller/products/{id}/reshelf
     POST /api/seller/products/{id}/result
     GET /api/seller/customers、GET /api/seller/customers/{id}/orders
   单品小铺 · 迭代 2 · 邱圣洁
   ============================================================ */
(function () {
  'use strict';

  var App = window.App;
  var Api = window.Api;
  var $ = function (id) { return document.getElementById(id); };

  var MAX_IMAGES = 9;

  /* 统一把返回结构归一成数组，兼容「裸数组 / {items} / {products} / {customers}」 */
  function toArray(data) {
    if (!data) return [];
    if (Array.isArray(data)) return data;
    return data.items || data.products || data.customers || data.list || [];
  }

  var els = {
    loginView: $('login-view'),
    dashboardView: $('dashboard-view'),
    headerActions: $('header-actions'),
    offlineTip: $('offline-tip'),
    /* 登录 */
    loginForm: $('login-form'),
    loginUsername: $('login-username'),
    loginPassword: $('login-password'),
    loginUsernameError: $('login-username-error'),
    loginPasswordError: $('login-password-error'),
    loginError: $('login-error'),
    loginSubmit: $('login-submit'),
    sellerName: $('seller-name'),
    refreshBtn: $('refresh-btn'),
    logoutBtn: $('logout-btn'),
    /* 发布 */
    publishForm: $('publish-form'),
    pubName: $('pub-name'),
    pubNameCounter: $('pub-name-counter'),
    pubNameError: $('pub-name-error'),
    pubCat1: $('pub-cat1'),
    pubCat2: $('pub-cat2'),
    pubCatError: $('pub-cat-error'),
    pubPrice: $('pub-price'),
    pubPriceError: $('pub-price-error'),
    pubStock: $('pub-stock'),
    pubStockError: $('pub-stock-error'),
    pubImages: $('pub-images'),
    pubImagesPick: $('pub-images-pick'),
    pubImagesList: $('pub-images-list'),
    pubImagesCount: $('pub-images-count'),
    pubDescToolbar: $('pub-desc-toolbar'),
    pubDescArea: $('pub-desc-area'),
    pubDescError: $('pub-desc-error'),
    publishSubmit: $('publish-submit'),
    /* 类别 */
    catLoading: $('cat-loading'),
    catTree: $('cat-tree'),
    catForm: $('cat-form'),
    catName: $('cat-name'),
    catNameError: $('cat-name-error'),
    catParent: $('cat-parent'),
    catSubmit: $('cat-submit'),
    /* 商品 */
    productsLoading: $('products-loading'),
    productsEmpty: $('products-empty'),
    productsBody: $('products-body'),
    productsTbody: $('products-tbody'),
    productsCount: $('products-count'),
    /* 客户 */
    customersLoading: $('customers-loading'),
    customersEmpty: $('customers-empty'),
    customersBody: $('customers-body'),
    customersTbody: $('customers-tbody'),
    customersCount: $('customers-count'),
    coBox: $('customer-orders'),
    coTitle: $('co-title'),
    coTbody: $('co-tbody'),
    coClose: $('co-close'),
    /* 弹窗 */
    stockModal: $('stock-modal'),
    stockModalName: $('stock-modal-name'),
    stockInput: $('stock-input'),
    stockInputError: $('stock-input-error'),
    stockConfirm: $('stock-confirm'),
    stockCancel: $('stock-cancel'),
    failModal: $('fail-modal'),
    failModalName: $('fail-modal-name'),
    failAction: $('fail-action'),
    failConfirm: $('fail-confirm'),
    failCancel: $('fail-cancel')
  };

  var categories = [];      /* 类别树 */
  var products = [];        /* 我的商品 */
  var publishImages = [];   /* 已选图片 Base64，按添加顺序 */
  var editor = null;        /* 富文本编辑器 */
  var editingCatId = null;  /* 正在编辑的类别 id */
  var pendingProductId = null;

  /* ==================== 视图切换 ==================== */

  function showView(dashboard) {
    App.show(els.loginView, !dashboard);
    App.show(els.dashboardView, dashboard);
    App.show(els.headerActions, dashboard);
  }

  function handleAuthExpired() {
    Api.setSellerSession(null);
    showView(false);
    els.loginError.textContent = '登录已失效，请重新登录';
    els.loginError.hidden = false;
  }

  /* ==================== 登录 / 退出 ==================== */

  els.loginForm.addEventListener('submit', async function (e) {
    e.preventDefault();
    els.loginError.hidden = true;

    var username = els.loginUsername.value.trim();
    var password = els.loginPassword.value;
    var valid = true;

    if (!username) { App.fieldError(els.loginUsernameError, '请输入用户名'); valid = false; } else App.fieldError(els.loginUsernameError, '');
    if (!password) { App.fieldError(els.loginPasswordError, '请输入密码'); valid = false; } else App.fieldError(els.loginPasswordError, '');
    if (!valid) return;

    App.setLoading(els.loginSubmit, true, '登录中');
    try {
      var data = await Api.sellerLogin(username, password);
      var token = data && (data.token || data.xToken);
      if (!token) throw Api.ApiError(0, '登录接口未返回 token');
      Api.setSellerSession(token, (data && data.username) || username);
      els.sellerName.textContent = Api.getSellerName() || username;
      els.loginPassword.value = '';
      showView(true);
      App.showToast('登录成功，欢迎回来', 'success');
      refreshAll();
    } catch (err) {
      if (err.status === 401) { els.loginError.textContent = '用户名或密码错误'; }
      else if (err.status === 0) { els.loginError.textContent = '无法连接服务器，请确认后端已启动'; }
      else { els.loginError.textContent = err.message; }
      els.loginError.hidden = false;
    } finally {
      App.setLoading(els.loginSubmit, false);
    }
  });

  els.logoutBtn.addEventListener('click', async function () {
    try { await Api.sellerLogout(); } catch (e) { /* 失败也本地退出 */ }
    Api.setSellerSession(null);
    showView(false);
    App.showToast('已退出登录', 'info');
  });

  els.refreshBtn.addEventListener('click', function () { refreshAll(); });

  function refreshAll() {
    loadCategories();
    loadProducts();
    loadCustomers();
  }

  /* ==================== 类别管理 ==================== */

  async function loadCategories() {
    App.show(els.catLoading, true);
    try {
      var data = await Api.getCategories();
      categories = toArray(data);
      renderCategoryTree();
      fillPublishCategorySelects();
      fillParentSelect();
      App.show(els.offlineTip, false);
    } catch (e) {
      if (Api.isAuthError(e)) { handleAuthExpired(); return; }
      App.showToast(e.message, 'error');
    } finally {
      App.show(els.catLoading, false);
    }
  }

  function renderCategoryTree() {
    if (!categories.length) { els.catTree.innerHTML = '<p class="field-hint">暂无类别，先在下方新增一个一级类别</p>'; return; }
    var html = '';
    categories.forEach(function (parent) {
      html += '<div class="cat-row">' +
        '<span class="cat-name-l1">' + App.escapeHtml(parent.name) + '</span>' +
        '<span class="cat-ops">' +
          '<button type="button" class="btn btn-ghost btn-xs" data-cat-edit="' + App.escapeHtml(parent.id) + '">重命名</button>' +
          '<button type="button" class="btn btn-ghost-danger btn-xs" data-cat-del="' + App.escapeHtml(parent.id) + '">删除</button>' +
        '</span></div>';
      (parent.children || []).forEach(function (child) {
        html += '<div class="cat-row cat-row-l2">' +
          '<span class="cat-name-l2">' + App.escapeHtml(child.name) + '</span>' +
          '<span class="cat-ops">' +
            '<button type="button" class="btn btn-ghost btn-xs" data-cat-edit="' + App.escapeHtml(child.id) + '">重命名</button>' +
            '<button type="button" class="btn btn-ghost-danger btn-xs" data-cat-del="' + App.escapeHtml(child.id) + '">删除</button>' +
          '</span></div>';
      });
    });
    els.catTree.innerHTML = html;
  }

  function fillPublishCategorySelects() {
    var html1 = '<option value="">请选择</option>';
    categories.forEach(function (c) {
      html1 += '<option value="' + App.escapeHtml(c.id) + '">' + App.escapeHtml(c.name) + '</option>';
    });
    els.pubCat1.innerHTML = html1;
    resetCat2();
  }

  function resetCat2() {
    els.pubCat2.innerHTML = '<option value="">请先选一级类别</option>';
    els.pubCat2.disabled = true;
  }

  els.pubCat1.addEventListener('change', function () {
    var parent = categories.filter(function (c) { return String(c.id) === String(this.value); }.bind(this))[0];
    var children = (parent && parent.children) || [];
    if (!children.length) {
      els.pubCat2.innerHTML = '<option value="">该一级类别下暂无二级类别</option>';
      els.pubCat2.disabled = true;
      return;
    }
    var html = '<option value="">请选择</option>';
    children.forEach(function (c) {
      html += '<option value="' + App.escapeHtml(c.id) + '">' + App.escapeHtml(c.name) + '</option>';
    });
    els.pubCat2.innerHTML = html;
    els.pubCat2.disabled = false;
    App.fieldError(els.pubCatError, '');
  });

  els.pubCat2.addEventListener('change', function () { App.fieldError(els.pubCatError, ''); });

  function fillParentSelect() {
    var html = '<option value="">（作为一级类别）</option>';
    categories.forEach(function (c) {
      html += '<option value="' + App.escapeHtml(c.id) + '">' + App.escapeHtml(c.name) + '</option>';
    });
    els.catParent.innerHTML = html;
  }

  /* 类别新增 / 保存修改 */
  els.catForm.addEventListener('submit', async function (e) {
    e.preventDefault();

    var name = els.catName.value.trim();
    var parentId = els.catParent.value;
    if (!name) { App.fieldError(els.catNameError, '请输入类别名称'); return; }
    App.fieldError(els.catNameError, '');

    var body = { name: name };
    if (parentId) body.parentId = Number(parentId);

    App.setLoading(els.catSubmit, true, '保存中');
    try {
      if (editingCatId) await Api.updateCategory(editingCatId, body);
      else await Api.createCategory(body);
      App.showToast(editingCatId ? '类别已更新' : '类别已新增', 'success');
      cancelEditCategory();
      els.catForm.reset();
      await loadCategories();
    } catch (err) {
      if (Api.isAuthError(err)) { handleAuthExpired(); return; }
      App.fieldError(els.catNameError, err.message);
    } finally {
      App.setLoading(els.catSubmit, false);
    }
  });

  function enterEditCategory(id, name, parentId) {
    editingCatId = id;
    els.catName.value = name;
    els.catParent.value = parentId ? String(parentId) : '';
    els.catSubmit.textContent = '保存修改';
    if (!document.getElementById('cat-cancel')) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.id = 'cat-cancel';
      btn.className = 'btn btn-ghost btn-block';
      btn.style.marginTop = '8px';
      btn.textContent = '取消编辑';
      btn.addEventListener('click', cancelEditCategory);
      els.catSubmit.parentNode.insertBefore(btn, els.catSubmit.nextSibling);
    }
    els.catName.focus();
  }

  function cancelEditCategory() {
    editingCatId = null;
    els.catSubmit.textContent = '新增类别';
    var btn = document.getElementById('cat-cancel');
    if (btn) btn.parentNode.removeChild(btn);
    els.catForm.reset();
  }

  /* 类别树操作：重命名 / 删除 */
  els.catTree.addEventListener('click', async function (e) {
    var editBtn = e.target.closest ? e.target.closest('[data-cat-edit]') : null;
    var delBtn = e.target.closest ? e.target.closest('[data-cat-del]') : null;

    if (editBtn) {
      var id = editBtn.getAttribute('data-cat-edit');
      var found = null;
      categories.forEach(function (p) {
        if (String(p.id) === String(id)) found = { name: p.name, parentId: null };
        (p.children || []).forEach(function (c) {
          if (String(c.id) === String(id)) found = { name: c.name, parentId: p.id };
        });
      });
      if (found) enterEditCategory(id, found.name, found.parentId);
      return;
    }

    if (delBtn) {
      var delId = delBtn.getAttribute('data-cat-del');
      if (!confirm('确定删除该类别吗？类别下有商品时不能删除。')) return;
      try {
        await Api.deleteCategory(delId);
        App.showToast('类别已删除', 'success');
        await loadCategories();
      } catch (err) {
        if (Api.isAuthError(err)) { handleAuthExpired(); return; }
        App.showToast(err.message, 'error');
      }
    }
  });

  /* ==================== 发布商品 ==================== */

  App.createRichEditor(els.pubDescToolbar, els.pubDescArea);

  els.pubName.addEventListener('input', function () {
    els.pubNameCounter.textContent = this.value.length + ' / 100';
    App.fieldError(els.pubNameError, '');
  });
  els.pubPrice.addEventListener('input', function () { App.fieldError(els.pubPriceError, ''); });
  els.pubStock.addEventListener('input', function () { App.fieldError(els.pubStockError, ''); });

  /* 多图上传：按添加顺序，最多 9 张 */
  els.pubImagesPick.addEventListener('click', function () { els.pubImages.click(); });

  els.pubImages.addEventListener('change', function () {
    var remain = MAX_IMAGES - publishImages.length;
    if (remain <= 0) { App.showToast('最多只能上传 ' + MAX_IMAGES + ' 张图片', 'error'); els.pubImages.value = ''; return; }
    App.readImagesAsDataUrl(els.pubImages.files, remain, function (list) {
      publishImages = publishImages.concat(list);
      renderImageList();
      els.pubImages.value = '';
    });
  });

  function renderImageList() {
    els.pubImagesCount.textContent = '已选 ' + publishImages.length + ' / ' + MAX_IMAGES;
    if (!publishImages.length) { els.pubImagesList.innerHTML = ''; return; }
    var html = '';
    publishImages.forEach(function (src, idx) {
      html += '<div class="img-item">' +
        '<img src="' + App.escapeHtml(src) + '" alt="第 ' + (idx + 1) + ' 张">' +
        '<span class="img-index">第 ' + (idx + 1) + ' 张</span>' +
        '<span class="img-ops">' +
          (idx > 0 ? '<button type="button" class="btn btn-ghost btn-xs" data-img-up="' + idx + '">上移</button>' : '') +
          (idx < publishImages.length - 1 ? '<button type="button" class="btn btn-ghost btn-xs" data-img-down="' + idx + '">下移</button>' : '') +
          '<button type="button" class="btn btn-ghost-danger btn-xs" data-img-del="' + idx + '">移除</button>' +
        '</span></div>';
    });
    els.pubImagesList.innerHTML = html;
  }

  els.pubImagesList.addEventListener('click', function (e) {
    var up = e.target.closest ? e.target.closest('[data-img-up]') : null;
    var down = e.target.closest ? e.target.closest('[data-img-down]') : null;
    var del = e.target.closest ? e.target.closest('[data-img-del]') : null;
    var idx;
    if (up) {
      idx = Number(up.getAttribute('data-img-up'));
      var t = publishImages[idx - 1]; publishImages[idx - 1] = publishImages[idx]; publishImages[idx] = t;
      renderImageList();
    } else if (down) {
      idx = Number(down.getAttribute('data-img-down'));
      var t2 = publishImages[idx + 1]; publishImages[idx + 1] = publishImages[idx]; publishImages[idx] = t2;
      renderImageList();
    } else if (del) {
      publishImages.splice(Number(del.getAttribute('data-img-del')), 1);
      renderImageList();
    }
  });

  els.publishForm.addEventListener('submit', async function (e) {
    e.preventDefault();

    var name = els.pubName.value.trim();
    var categoryId = els.pubCat2.value;
    var priceStr = els.pubPrice.value.trim();
    var stockStr = els.pubStock.value.trim();
    var valid = true;

    if (!name) { App.fieldError(els.pubNameError, '请输入商品名称'); valid = false; }
    else if (name.length > 100) { App.fieldError(els.pubNameError, '名称不能超过 100 字'); valid = false; }
    else App.fieldError(els.pubNameError, '');

    if (!categoryId) { App.fieldError(els.pubCatError, '请选择到二级类别'); valid = false; }
    else App.fieldError(els.pubCatError, '');

    if (!/^\d+(\.\d{1,2})?$/.test(priceStr) || !(parseFloat(priceStr) > 0)) {
      App.fieldError(els.pubPriceError, '价格必须大于 0，最多两位小数'); valid = false;
    } else App.fieldError(els.pubPriceError, '');

    if (!/^\d+$/.test(stockStr)) { App.fieldError(els.pubStockError, '库存必须为不小于 0 的整数'); valid = false; }
    else App.fieldError(els.pubStockError, '');

    if (!valid) return;

    var body = {
      name: name,
      description: els.pubDescArea.innerHTML,
      price: parseFloat(priceStr),
      categoryId: Number(categoryId),
      stock: parseInt(stockStr, 10)
    };
    if (publishImages.length) body.images = publishImages.slice();

    App.setLoading(els.publishSubmit, true, '发布中');
    try {
      await Api.publishProduct(body);
      App.showToast('商品发布成功', 'success');
      resetPublishForm();
      loadProducts();
    } catch (err) {
      if (Api.isAuthError(err)) { handleAuthExpired(); return; }
      App.showToast(err.message, 'error');
    } finally {
      App.setLoading(els.publishSubmit, false);
    }
  });

  function resetPublishForm() {
    els.publishForm.reset();
    els.pubNameCounter.textContent = '0 / 100';
    els.pubDescArea.innerHTML = '';
    publishImages = [];
    renderImageList();
    resetCat2();
    App.fieldError(els.pubNameError, '');
    App.fieldError(els.pubCatError, '');
    App.fieldError(els.pubPriceError, '');
    App.fieldError(els.pubStockError, '');
  }

  /* ==================== 我的商品 ==================== */

  async function loadProducts() {
    App.show(els.productsLoading, true);
    App.show(els.productsEmpty, false);
    App.show(els.productsBody, false);
    try {
      var data = await Api.listSellerProducts();
      products = toArray(data);
      renderProducts();
    } catch (e) {
      App.show(els.productsLoading, false);
      if (Api.isAuthError(e)) { handleAuthExpired(); return; }
      if (e.status === 0) App.show(els.offlineTip, true);
      else App.showToast(e.message, 'error');
    }
  }

  function renderProducts() {
    App.show(els.productsLoading, false);
    els.productsCount.textContent = products.length ? ('共 ' + products.length + ' 件') : '';

    if (!products.length) { App.show(els.productsEmpty, true); App.show(els.productsBody, false); return; }

    var html = '';
    products.forEach(function (p) {
      var status = p.status;
      var ops = '';
      ops += '<button type="button" class="btn btn-ghost btn-xs" data-stock="' + App.escapeHtml(p.id) + '">改库存</button>';
      if (status === 'OFF_SHELF') {
        ops += '<button type="button" class="btn btn-primary btn-xs" data-reshelf="' + App.escapeHtml(p.id) + '">重新上架</button>';
      }
      if (status === 'FROZEN' || status === 'IN_TRADE') {
        ops += '<button type="button" class="btn btn-primary btn-xs" data-ok="' + App.escapeHtml(p.id) + '">标记成功</button>';
        ops += '<button type="button" class="btn btn-ghost-danger btn-xs" data-fail="' + App.escapeHtml(p.id) + '">标记失败</button>';
      }
      html += '<tr>' +
        '<td>' + App.escapeHtml(p.name || '-') + '</td>' +
        '<td>' + App.escapeHtml(p.categoryName || '-') + '</td>' +
        '<td>&yen;' + App.escapeHtml(App.formatPrice(p.price)) + '</td>' +
        '<td>' + App.escapeHtml(p.stock === null || p.stock === undefined ? '-' : p.stock) + '</td>' +
        '<td>' + App.badgeHtml(App.PRODUCT_BADGE, status) + '</td>' +
        '<td class="td-ops">' + ops + '</td>' +
      '</tr>';
    });
    els.productsTbody.innerHTML = html;
    App.show(els.productsEmpty, false);
    App.show(els.productsBody, true);
  }

  els.productsTbody.addEventListener('click', function (e) {
    var stockBtn = e.target.closest ? e.target.closest('[data-stock]') : null;
    var reshelfBtn = e.target.closest ? e.target.closest('[data-reshelf]') : null;
    var okBtn = e.target.closest ? e.target.closest('[data-ok]') : null;
    var failBtn = e.target.closest ? e.target.closest('[data-fail]') : null;
    var id;

    if (stockBtn) {
      id = stockBtn.getAttribute('data-stock');
      var p = findProduct(id);
      pendingProductId = id;
      els.stockModalName.textContent = p ? (p.name + '（当前库存 ' + (p.stock || 0) + '）') : '';
      els.stockInput.value = p ? (p.stock || 0) : 0;
      App.fieldError(els.stockInputError, '');
      els.stockModal.hidden = false;
      els.stockInput.focus();
      return;
    }
    if (reshelfBtn) {
      runAction(reshelfBtn.getAttribute('data-reshelf'), function (pid) { return Api.reshelfProduct(pid); }, '商品已重新上架');
      return;
    }
    if (okBtn) {
      id = okBtn.getAttribute('data-ok');
      if (!confirm('标记交易成功将扣减 1 件库存并直接下架该商品，确定吗？')) return;
      runAction(id, function (pid) { return Api.markResult(pid, 'SUCCESS'); }, '交易成功，商品已下架');
      return;
    }
    if (failBtn) {
      id = failBtn.getAttribute('data-fail');
      var pf = findProduct(id);
      pendingProductId = id;
      els.failModalName.textContent = pf ? pf.name : '';
      els.failAction.value = 'REQUEUE';
      els.failModal.hidden = false;
    }
  });

  function findProduct(id) {
    return products.filter(function (p) { return String(p.id) === String(id); })[0] || null;
  }

  async function runAction(productId, fn, successMsg) {
    try {
      await fn(productId);
      App.showToast(successMsg, 'success');
      loadProducts();
      loadCustomers();
    } catch (err) {
      if (Api.isAuthError(err)) { handleAuthExpired(); return; }
      App.showToast(err.message, 'error');
      loadProducts();
    }
  }

  /* 改库存弹窗 */
  els.stockCancel.addEventListener('click', function () { els.stockModal.hidden = true; });

  els.stockConfirm.addEventListener('click', async function () {
    var v = els.stockInput.value.trim();
    if (!/^\d+$/.test(v)) { App.fieldError(els.stockInputError, '库存必须为不小于 0 的整数'); return; }
    App.fieldError(els.stockInputError, '');
    App.setLoading(els.stockConfirm, true, '保存中');
    try {
      await Api.updateStock(pendingProductId, parseInt(v, 10));
      App.showToast('库存已更新', 'success');
      els.stockModal.hidden = true;
      loadProducts();
    } catch (err) {
      if (Api.isAuthError(err)) { handleAuthExpired(); return; }
      App.showToast(err.message, 'error');
    } finally {
      App.setLoading(els.stockConfirm, false);
    }
  });

  /* 标记失败弹窗 */
  els.failCancel.addEventListener('click', function () { els.failModal.hidden = true; });

  els.failConfirm.addEventListener('click', async function () {
    var action = els.failAction.value;
    els.failModal.hidden = true;
    await runAction(pendingProductId, function (pid) {
      return Api.markResult(pid, 'FAIL', action);
    }, action === 'REQUEUE' ? '交易失败，该意向已重新排队' : '交易失败，该意向已作废');
  });

  /* ==================== 客户列表与购买历史 ==================== */

  async function loadCustomers() {
    App.show(els.customersLoading, true);
    try {
      var data = await Api.listCustomers();
      renderCustomers(toArray(data));
    } catch (e) {
      App.show(els.customersLoading, false);
      if (Api.isAuthError(e)) { handleAuthExpired(); return; }
      if (e.status !== 0) App.showToast(e.message, 'error');
    }
  }

  function renderCustomers(list) {
    App.show(els.customersLoading, false);
    els.customersCount.textContent = list.length ? ('共 ' + list.length + ' 位') : '';

    if (!list.length) { App.show(els.customersEmpty, true); App.show(els.customersBody, false); return; }

    var html = '';
    list.forEach(function (c) {
      var id = c.customerId !== undefined ? c.customerId : c.id;
      html += '<tr>' +
        '<td>' + App.escapeHtml(c.username || c.name || '-') + '</td>' +
        '<td>' + App.escapeHtml(c.phone || '-') + '</td>' +
        '<td><button type="button" class="btn btn-ghost btn-xs" data-co="' + App.escapeHtml(id) +
          '" data-coname="' + App.escapeHtml(c.username || c.name || '') + '">购买历史</button></td>' +
      '</tr>';
    });
    els.customersTbody.innerHTML = html;
    App.show(els.customersEmpty, false);
    App.show(els.customersBody, true);
  }

  els.customersTbody.addEventListener('click', async function (e) {
    var btn = e.target.closest ? e.target.closest('[data-co]') : null;
    if (!btn) return;
    var id = btn.getAttribute('data-co');
    var name = btn.getAttribute('data-coname');
    els.coTitle.textContent = '「' + (name || id) + '」的购买历史';
    els.coTbody.innerHTML = '<tr><td colspan="4">加载中…</td></tr>';
    els.coBox.hidden = false;
    try {
      var data = await Api.getCustomerOrders(id);
      renderCustomerOrders(toArray(data));
    } catch (err) {
      if (Api.isAuthError(err)) { handleAuthExpired(); return; }
      els.coTbody.innerHTML = '<tr><td colspan="4">加载失败：' + App.escapeHtml(err.message) + '</td></tr>';
    }
  });

  function renderCustomerOrders(list) {
    if (!list.length) { els.coTbody.innerHTML = '<tr><td colspan="4">该客户暂无下单记录</td></tr>'; return; }
    var html = '';
    list.forEach(function (it) {
      html += '<tr>' +
        '<td>' + App.escapeHtml(it.productName || it.name || '-') + '</td>' +
        '<td>' + App.escapeHtml(App.formatTime(it.submittedAt)) + '</td>' +
        '<td>' + App.badgeHtml(App.ORDER_BADGE, it.result) + '</td>' +
        '<td>' + App.escapeHtml(App.formatTime(it.resultAt)) + '</td>' +
      '</tr>';
    });
    els.coTbody.innerHTML = html;
  }

  els.coClose.addEventListener('click', function () { els.coBox.hidden = true; });

  /* ==================== 启动 ==================== */

  (function bootstrap() {
    if (!Api.isSellerLogin()) { showView(false); return; }
    els.sellerName.textContent = Api.getSellerName() || '卖家';
    showView(true);
    refreshAll();
  })();
})();

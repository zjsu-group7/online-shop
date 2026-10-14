/* ============================================================
   卖家后台逻辑：登录 / 当前商品 / 发布商品 / 意向列表 / 轮询刷新
   对应接口：POST /api/seller/login、GET /api/seller/overview、
            POST /api/seller/products、POST /api/seller/logout
   单品小铺 · 迭代1 · 邱圣洁
   ============================================================ */
(function () {
  'use strict';

  var App = window.App;
  var Api = window.Api;
  var $ = function (id) { return document.getElementById(id); };

  var POLL_INTERVAL = 8000; /* 自动轮询：8 秒，方便演示时实时看到新意向 */
  var pollTimer = null;
  var currentProduct = null; /* 当前商品（含 id/status/frozenBy），供交易操作使用 */

  var els = {
    /* 视图切换 */
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
    /* 顶栏 */
    sellerName: $('seller-name'),
    refreshBtn: $('refresh-btn'),
    logoutBtn: $('logout-btn'),
    /* 当前商品 */
    adminProductLoading: $('admin-product-loading'),
    adminProductEmpty: $('admin-product-empty'),
    adminProductBody: $('admin-product-body'),
    adminProductImage: $('admin-product-image'),
    adminProductName: $('admin-product-name'),
    adminProductDesc: $('admin-product-desc'),
    adminProductPrice: $('admin-product-price'),
    adminProductStatus: $('admin-product-status'),
    /* 发布表单 */
    publishBlocked: $('publish-blocked'),
    publishForm: $('publish-form'),
    publishFields: $('publish-fields'),
    pubName: $('pub-name'),
    pubNameCounter: $('pub-name-counter'),
    pubNameError: $('pub-name-error'),
    pubDesc: $('pub-desc'),
    pubDescCounter: $('pub-desc-counter'),
    pubPrice: $('pub-price'),
    pubPriceError: $('pub-price-error'),
    pubImage: $('pub-image'),
    pubImagePick: $('pub-image-pick'),
    pubImagePreview: $('pub-image-preview'),
    pubImageRemove: $('pub-image-remove'),
    publishSubmit: $('publish-submit'),
    /* 意向列表 */
    queueLoading: $('queue-loading'),
    queueEmpty: $('queue-empty'),
    queueBody: $('queue-body'),
    queueTbody: $('queue-tbody'),
    queueCount: $('queue-count'),
    /* 商品操作 */
    productActions: $('admin-product-actions'),
    actFreeze: $('act-freeze'),
    actUnfreeze: $('act-unfreeze'),
    actOffshelf: $('act-offshelf'),
    actResultSuccess: $('act-result-success'),
    actResultFail: $('act-result-fail')
  };

  var imageDataUrl = null; /* 已选择的图片 dataURL，未选为 null */

  function show(el, on) { if (el) el.hidden = !on; }

  function fieldError(el, msg) {
    if (!msg) { show(el, false); return; }
    el.textContent = msg;
    show(el, true);
  }

  /* ==================== 视图切换 ==================== */

  function showView(dashboard) {
    show(els.loginView, !dashboard);
    show(els.dashboardView, dashboard);
    show(els.headerActions, dashboard);
  }

  function handleAuthExpired(message) {
    Api.setToken(null);
    stopPolling();
    showView(false);
    els.loginError.textContent = message || '登录已失效，请重新登录';
    show(els.loginError, true);
  }

  /* ==================== 登录 ==================== */

  els.loginForm.addEventListener('submit', async function (e) {
    e.preventDefault();
    show(els.loginError, false);

    var username = els.loginUsername.value.trim();
    var password = els.loginPassword.value;
    var valid = true;

    if (!username) { fieldError(els.loginUsernameError, '请输入用户名'); valid = false; }
    else fieldError(els.loginUsernameError);

    if (!password) { fieldError(els.loginPasswordError, '请输入密码'); valid = false; }
    else fieldError(els.loginPasswordError);

    if (!valid) return;

    App.setLoading(els.loginSubmit, true);
    try {
      var data = await Api.login(username, password);
      var token = data && (data.token || data.xToken);
      if (!token) throw Api.ApiError(0, '登录接口未返回 token');
      Api.setToken(token, (data && data.username) || username);

      els.sellerName.textContent = Api.getUsername() || username;
      els.loginPassword.value = '';
      showView(true);
      await refreshOverview(true);
      startPolling();
      App.showToast('登录成功，欢迎回来', 'success');
    } catch (err) {
      if (err.status === 401) {
        els.loginError.textContent = '用户名或密码错误';
        show(els.loginError, true);
      } else {
        els.loginError.textContent = err.message;
        show(els.loginError, true);
      }
    } finally {
      App.setLoading(els.loginSubmit, false);
    }
  });

  /* ==================== 退出登录 ==================== */

  els.logoutBtn.addEventListener('click', async function () {
    App.setLoading(els.logoutBtn, true);
    try { await Api.logout(); } catch (e) { /* 即使失败也本地退出 */ }
    Api.setToken(null);
    stopPolling();
    showView(false);
    App.showToast('已退出登录', 'info');
  });

  /* ==================== 总览（当前商品 + 意向列表） ==================== */

  async function refreshOverview(showLoading) {
    if (showLoading) {
      show(els.adminProductLoading, true);
      show(els.queueLoading, true);
      show(els.adminProductEmpty, false);
      show(els.adminProductBody, false);
      show(els.queueBody, false);
      show(els.queueEmpty, false);
    }
    try {
      var data = await Api.getOverview();
      show(els.offlineTip, false);
      renderOverview(data);
    } catch (e) {
      if (Api.isAuthError(e)) {
        handleAuthExpired();
        return;
      }
      if (e.status === 0) {
        show(els.offlineTip, true); /* 骨架模式 */
        renderOverview(null);
      } else if (showLoading) {
        App.showToast(e.message, 'error');
      } /* 轮询静默失败不打扰 */
    }
  }

  function renderOverview(data) {
    var product = data && data.product;
    var queue = (data && data.queue) || [];
    var canPublish = !!(data && data.canPublish);

    renderAdminProduct(product);
    renderQueue(queue);
    setPublishState(canPublish);
  }

  function renderAdminProduct(p) {
    show(els.adminProductLoading, false);
    if (!p) {
      currentProduct = null;
      show(els.adminProductEmpty, true);
      show(els.adminProductBody, false);
      return;
    }
    currentProduct = p;
    els.adminProductName.textContent = p.name || '未命名商品';
    els.adminProductDesc.textContent = p.description || '暂无描述';
    els.adminProductPrice.textContent = App.formatPrice(p.price);
    els.adminProductStatus.innerHTML = App.badgeHtml(App.PRODUCT_BADGE, p.status);

    var img = App.resolveImageUrl(p.imageUrl || p.image);
    els.adminProductImage.src = img || App.PLACEHOLDER_IMAGE;
    App.bindImageFallback(els.adminProductImage);

    show(els.adminProductEmpty, false);
    show(els.adminProductBody, true);
    renderProductActions(p);
  }

  /* 商品操作按钮：按商品状态显示 冻结/解冻/下架/标记结果 */
  function renderProductActions(p) {
    var onSale = p.status === 'ON_SALE';
    var frozen = p.status === 'FROZEN';
    var autoFrozen = frozen && p.frozenBy === 'AUTO';
    var manualFrozen = frozen && p.frozenBy === 'MANUAL';

    show(els.actFreeze, onSale);
    show(els.actUnfreeze, manualFrozen);
    show(els.actOffshelf, onSale || manualFrozen);
    show(els.actResultSuccess, autoFrozen);
    show(els.actResultFail, autoFrozen);
    show(els.productActions, onSale || frozen);
  }

  function renderQueue(queue) {
    show(els.queueLoading, false);
    els.queueCount.textContent = queue.length ? '共 ' + queue.length + ' 条' : '';

    if (!queue.length) {
      show(els.queueEmpty, true);
      show(els.queueBody, false);
      return;
    }

    var canDeal = !!(currentProduct && currentProduct.status === 'ON_SALE');
    var rows = '';
    for (var i = 0; i < queue.length; i++) {
      var it = queue[i] || {};
      var position = (it.position !== null && it.position !== undefined) ? it.position : '-';
      var action = '';
      if (it.status === 'WAITING') {
        action = '<button type="button" class="btn btn-primary btn-xs" data-deal="' + it.id + '"' +
                 (canDeal ? '' : ' disabled title="商品不在售，无法选定"') + '>选定</button>';
      } else if (it.status === 'IN_TRADE') {
        action = '<span class="queue-selected">已选定</span>';
      }
      rows +=
        '<tr>' +
          '<td><span class="position-num">' + App.escapeHtml(position) + '</span></td>' +
          '<td>' + App.escapeHtml(it.name || '-') + '</td>' +
          '<td class="td-mono">' + App.escapeHtml(it.phone || '-') + '</td>' +
          '<td>' + App.badgeHtml(App.INTENT_BADGE, it.status) + '</td>' +
          '<td>' + App.escapeHtml(App.formatTime(it.submittedAt)) + '</td>' +
          '<td>' + action + '</td>' +
        '</tr>';
    }
    els.queueTbody.innerHTML = rows;
    show(els.queueEmpty, false);
    show(els.queueBody, true);
  }

  /* ==================== 发布商品 ==================== */

  function setPublishState(canPublish) {
    show(els.publishBlocked, !canPublish);
    els.publishFields.disabled = !canPublish;
  }

  /* 字数统计 */
  function bindCounter(input, counter, max) {
    input.addEventListener('input', function () {
      counter.textContent = this.value.length + ' / ' + max;
    });
  }

  els.pubName.addEventListener('input', function () { fieldError(els.pubNameError); });
  els.pubPrice.addEventListener('input', function () { fieldError(els.pubPriceError); });

  /* 图片选择与预览（JPG / PNG，<=5MB，转 dataURL） */
  els.pubImagePick.addEventListener('click', function () { els.pubImage.click(); });

  els.pubImage.addEventListener('change', function () {
    var file = this.files && this.files[0];
    if (!file) return;

    if (!/^image\/(jpeg|png)$/i.test(file.type)) {
      App.showToast('图片仅支持 JPG / PNG 格式', 'error');
      resetImage();
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      App.showToast('图片大小不能超过 5MB', 'error');
      resetImage();
      return;
    }
    var reader = new FileReader();
    reader.onload = function () {
      imageDataUrl = reader.result;
      els.pubImagePreview.src = imageDataUrl;
      show(els.pubImagePreview, true);
      show(els.pubImageRemove, true);
    };
    reader.onerror = function () {
      App.showToast('图片读取失败，请重试', 'error');
      resetImage();
    };
    reader.readAsDataURL(file);
  });

  els.pubImageRemove.addEventListener('click', resetImage);

  function resetImage() {
    imageDataUrl = null;
    els.pubImage.value = '';
    show(els.pubImagePreview, false);
    show(els.pubImageRemove, false);
  }

  els.publishForm.addEventListener('submit', async function (e) {
    e.preventDefault();

    var name = els.pubName.value.trim();
    var desc = els.pubDesc.value.trim();
    var priceStr = els.pubPrice.value.trim();
    var valid = true;

    if (!name) { fieldError(els.pubNameError, '请输入商品名称'); valid = false; }
    else if (name.length > 100) { fieldError(els.pubNameError, '名称不能超过 100 字'); valid = false; }
    else fieldError(els.pubNameError);

    if (!/^\d+(\.\d{1,2})?$/.test(priceStr) || !(parseFloat(priceStr) > 0)) {
      fieldError(els.pubPriceError, '价格必须大于 0，最多两位小数');
      valid = false;
    } else fieldError(els.pubPriceError);

    if (!valid) return;

    var body = {
      name: name,
      description: desc,
      price: parseFloat(priceStr)
    };
    if (imageDataUrl) body.image = imageDataUrl; /* 不传则后端使用占位图 */

    App.setLoading(els.publishSubmit, true);
    try {
      await Api.publishProduct(body);
      App.showToast('商品发布成功', 'success');
      resetPublishForm();
      await refreshOverview(true);
    } catch (err) {
      if (Api.isAuthError(err)) {
        handleAuthExpired();
      } else {
        App.showToast(err.message, 'error');
        /* 可能是他人已抢先发布，刷新看最新状态 */
        if (err.status === 400) refreshOverview(false);
      }
    } finally {
      App.setLoading(els.publishSubmit, false);
    }
  });

  function resetPublishForm() {
    els.publishForm.reset();
    els.pubNameCounter.textContent = '0 / 100';
    els.pubDescCounter.textContent = '0 / 2000';
    resetImage();
    fieldError(els.pubNameError);
    fieldError(els.pubPriceError);
  }

  /* ==================== 轮询与刷新 ==================== */

  els.refreshBtn.addEventListener('click', async function () {
    App.setLoading(els.refreshBtn, true);
    await refreshOverview(false);
    App.setLoading(els.refreshBtn, false);
  });

  function startPolling() {
    stopPolling();
    pollTimer = setInterval(function () {
      if (document.visibilityState !== 'visible' || !Api.getToken()) return;
      refreshOverview(false); /* 静默轮询：演示时买家一提交，卖家这边 8 秒内自动出现 */
    }, POLL_INTERVAL);
  }

  function stopPolling() {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
  }

  /* ==================== 交易操作：选定买家 / 冻结解冻下架 / 标记结果 ==================== */

  async function runProductAction(btn, fn, successMsg) {
    if (!currentProduct || !currentProduct.id) return;
    App.setLoading(btn, true);
    try {
      await fn(currentProduct.id);
      App.showToast(successMsg, 'success');
      await refreshOverview(true);
    } catch (err) {
      if (Api.isAuthError(err)) { handleAuthExpired(); }
      else { App.showToast(err.message, 'error'); refreshOverview(false); }
    } finally {
      App.setLoading(btn, false);
    }
  }

  els.actFreeze.addEventListener('click', function () {
    runProductAction(els.actFreeze, Api.freezeProduct, '商品已手动冻结');
  });
  els.actUnfreeze.addEventListener('click', function () {
    runProductAction(els.actUnfreeze, Api.unfreezeProduct, '商品已解冻');
  });
  els.actOffshelf.addEventListener('click', function () {
    runProductAction(els.actOffshelf, Api.offshelfProduct, '商品已下架');
  });

  async function markTrade(btn, result) {
    if (!currentProduct || !currentProduct.id) return;
    App.setLoading(btn, true);
    try {
      await Api.markResult(currentProduct.id, result);
      App.showToast(result === 'SUCCESS' ? '交易成功，商品已下架' : '交易失败，商品已恢复在售', 'success');
      await refreshOverview(true);
    } catch (err) {
      if (Api.isAuthError(err)) { handleAuthExpired(); }
      else { App.showToast(err.message, 'error'); refreshOverview(false); }
    } finally {
      App.setLoading(btn, false);
    }
  }

  els.actResultSuccess.addEventListener('click', function () { markTrade(els.actResultSuccess, 'SUCCESS'); });
  els.actResultFail.addEventListener('click', function () { markTrade(els.actResultFail, 'FAIL'); });

  /* 意向列表「选定」按钮：事件委托 */
  els.queueTbody.addEventListener('click', async function (e) {
    var btn = (e.target && e.target.closest) ? e.target.closest('[data-deal]') : null;
    if (!btn) return;
    if (!currentProduct || !currentProduct.id) return;
    var intentId = btn.getAttribute('data-deal');
    App.setLoading(btn, true);
    try {
      await Api.dealIntent(currentProduct.id, parseInt(intentId, 10));
      App.showToast('已选定买家，商品进入交易冻结', 'success');
      await refreshOverview(true);
    } catch (err) {
      if (Api.isAuthError(err)) { handleAuthExpired(); }
      else { App.showToast(err.message, 'error'); refreshOverview(false); }
    } finally {
      App.setLoading(btn, false);
    }
  });

  /* ==================== 启动 ==================== */

  async function bootstrap() {
    bindCounter(els.pubName, els.pubNameCounter, 100);
    bindCounter(els.pubDesc, els.pubDescCounter, 2000);

    if (!Api.getToken()) {
      showView(false);
      return;
    }
    /* 已有 token：先试总览，401 则回到登录页 */
    els.sellerName.textContent = Api.getUsername() || '卖家';
    showView(true);
    await refreshOverview(true);
    if (Api.getToken()) startPolling();
  }

  bootstrap();
})();

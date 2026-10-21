/* ============================================================
   买家端 · 商品详情：多图有序展示 + 富文本渲染 + 登录下单
   对应接口：GET /api/products/{id}、POST /api/customer/orders
   单品小铺 · 迭代 2 · 邱圣洁
   ============================================================ */
(function () {
  'use strict';

  var App = window.App;
  var Api = window.Api;
  var $ = function (id) { return document.getElementById(id); };

  var productId = new URLSearchParams(location.search).get('id');
  var currentProduct = null;
  var currentImages = [];

  var els = {
    loading: $('detail-loading'),
    offline: $('detail-offline'),
    empty: $('detail-empty'),
    body: $('detail-body'),
    name: $('detail-name'),
    status: $('detail-status'),
    category: $('detail-category'),
    stock: $('detail-stock'),
    time: $('detail-time'),
    price: $('detail-price'),
    mainImg: $('gallery-main-img'),
    thumbs: $('gallery-thumbs'),
    desc: $('detail-desc'),
    orderBox: $('order-box'),
    loginTip: $('order-login-tip'),
    disabledTip: $('order-disabled-tip'),
    orderForm: $('order-form'),
    orderName: $('order-name'),
    orderPhone: $('order-phone'),
    nameError: $('order-name-error'),
    phoneError: $('order-phone-error'),
    orderSubmit: $('order-submit')
  };

  /* ==================== 详情加载 ==================== */

  async function loadDetail() {
    if (!productId) {
      App.show(els.loading, false);
      App.show(els.empty, true);
      return;
    }
    try {
      var p = await Api.getProduct(productId);
      renderDetail(p);
    } catch (e) {
      App.show(els.loading, false);
      if (e.status === 0) App.show(els.offline, true);
      else if (e.status === 404) App.show(els.empty, true);
      else App.showToast(e.message, 'error');
    }
  }

  function renderDetail(p) {
    if (!p) { App.show(els.loading, false); App.show(els.empty, true); return; }
    currentProduct = p;

    els.name.textContent = p.name || '未命名商品';
    els.status.innerHTML = App.badgeHtml(App.PRODUCT_BADGE, p.status);
    els.category.textContent = p.categoryName || '';
    els.category.hidden = !p.categoryName;
    els.price.textContent = App.formatPrice(p.price);
    els.time.textContent = p.createdAt ? ('发布于 ' + App.formatTime(p.createdAt)) : '';

    /* 库存展示 */
    if (p.status === 'ON_SALE') {
      els.stock.textContent = (Number(p.stock) > 0) ? ('库存 ' + p.stock + ' 件') : '已售罄';
    } else {
      els.stock.textContent = '';
    }

    /* 富文本描述（服务端白名单过滤 + 前端二次过滤） */
    App.renderRichText(els.desc, p.description);

    /* 多图：按后端返回顺序展示（已按 sort 排序） */
    var imgs = [];
    if (p.images && p.images.length) imgs = p.images;
    else if (p.image) imgs = [p.image];
    else if (p.coverImage) imgs = [p.coverImage];
    currentImages = imgs;

    renderGallery(imgs);

    App.show(els.loading, false);
    App.show(els.empty, false);
    App.show(els.body, true);

    renderOrderArea(p);
  }

  function renderGallery(imgs) {
    if (!imgs.length) {
      els.mainImg.src = App.PLACEHOLDER_IMAGE;
      els.thumbs.innerHTML = '';
      els.thumbs.hidden = true;
      return;
    }
    els.mainImg.src = App.resolveImageUrl(imgs[0]) || App.PLACEHOLDER_IMAGE;
    App.bindImageFallback(els.mainImg);

    if (imgs.length === 1) { els.thumbs.innerHTML = ''; els.thumbs.hidden = true; return; }

    var html = '';
    imgs.forEach(function (src, idx) {
      html += '<button type="button" class="thumb' + (idx === 0 ? ' is-active' : '') +
        '" data-idx="' + idx + '"><img src="' + App.escapeHtml(App.resolveImageUrl(src)) +
        '" alt="第 ' + (idx + 1) + ' 张"></button>';
    });
    els.thumbs.innerHTML = html;
    els.thumbs.hidden = false;
  }

  els.thumbs.addEventListener('click', function (e) {
    var btn = e.target && e.target.closest ? e.target.closest('[data-idx]') : null;
    if (!btn) return;
    var idx = Number(btn.getAttribute('data-idx'));
    if (!currentImages[idx]) return;
    els.mainImg.src = App.resolveImageUrl(currentImages[idx]) || App.PLACEHOLDER_IMAGE;
    Array.prototype.forEach.call(els.thumbs.querySelectorAll('.thumb'), function (b) {
      b.classList.toggle('is-active', b === btn);
    });
  });

  /* ==================== 下单区 ==================== */

  function renderOrderArea(p) {
    var logged = Api.isCustomerLogin();
    var canBuy = p.status === 'ON_SALE' && Number(p.stock) > 0;

    App.show(els.loginTip, !logged);
    App.show(els.orderForm, logged && canBuy);

    if (logged && !canBuy) {
      els.disabledTip.textContent = (p.status !== 'ON_SALE')
        ? '该商品已下架，暂不可下单。'
        : '该商品库存为 0，暂不可下单，请等店主补货。';
      App.show(els.disabledTip, true);
    } else {
      App.show(els.disabledTip, false);
    }

    /* 已登录且有默认资料时，预填姓名电话由用户自己确认，此处不自动填充敏感信息 */
  }

  els.orderForm.addEventListener('submit', async function (e) {
    e.preventDefault();

    if (!Api.isCustomerLogin()) {
      App.showToast('请先登录后再下单', 'error');
      setTimeout(function () {
        location.href = 'login.html?redirect=' + encodeURIComponent('product.html?id=' + productId);
      }, 800);
      return;
    }

    var name = els.orderName.value.trim();
    var phone = els.orderPhone.value.trim();
    var valid = true;

    if (!name) { App.fieldError(els.nameError, '请输入姓名'); valid = false; }
    else App.fieldError(els.nameError, '');

    if (!phone) { App.fieldError(els.phoneError, '请输入联系电话'); valid = false; }
    else App.fieldError(els.phoneError, '');

    if (!valid) return;

    App.setLoading(els.orderSubmit, true, '提交中');
    try {
      await Api.createOrder(currentProduct.id, { name: name, phone: phone });
      App.showToast('下单成功，请等待店主确认', 'success');
      els.orderForm.reset();
      setTimeout(function () { location.href = 'orders.html'; }, 900);
    } catch (err) {
      if (Api.isAuthError(err)) {
        Api.setCustomerSession(null);
        App.showToast('登录已失效，请重新登录', 'error');
        setTimeout(function () {
          location.href = 'login.html?redirect=' + encodeURIComponent('product.html?id=' + productId);
        }, 900);
      } else {
        App.showToast(err.message, 'error');
        if (err.status === 400) loadDetail(); /* 商品状态可能已变化，刷新 */
      }
    } finally {
      App.setLoading(els.orderSubmit, false);
    }
  });

  /* ==================== 启动 ==================== */

  App.renderCustomerNav();
  loadDetail();
})();

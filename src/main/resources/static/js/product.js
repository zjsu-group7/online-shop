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
    buyForm: $('buy-form'),
    qtyVal: $('buy-qty'),
    qtyMinus: $('buy-qty-minus'),
    qtyPlus: $('buy-qty-plus'),
    addCartBtn: $('buy-add-cart'),
    favBtn: $('buy-favorite')
  };

  var buyQty = 1;

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

  /* ==================== 收藏 / 加购 / 数量（迭代 3） ==================== */

  function renderOrderArea(p) {
    var logged = Api.isCustomerLogin();
    var canBuy = p.status === 'ON_SALE' && Number(p.stock) > 0;

    App.show(els.loginTip, !logged);
    App.show(els.buyForm, logged);

    if (logged && !canBuy) {
      els.disabledTip.textContent = (p.status !== 'ON_SALE')
        ? '该商品已下架，暂不可加购下单。'
        : '该商品库存为 0，暂不可下单，可先收藏，等店主补货。';
      App.show(els.disabledTip, true);
      els.addCartBtn.disabled = true;
      els.qtyMinus.disabled = true;
      els.qtyPlus.disabled = true;
    } else {
      App.show(els.disabledTip, false);
      els.addCartBtn.disabled = !logged;
      els.qtyMinus.disabled = !logged;
      els.qtyPlus.disabled = !logged;
    }

    buyQty = 1;
    els.qtyVal.textContent = '1';
    if (logged) refreshFavoriteState();
  }

  function requireLogin(back) {
    App.showToast('请先登录', 'error');
    setTimeout(function () {
      location.href = 'login.html?redirect=' + encodeURIComponent(back || ('product.html?id=' + productId));
    }, 800);
  }

  /* 数量步进：上限 = 库存（防误加超量，后端仍会最终校验） */
  els.qtyPlus.addEventListener('click', function () {
    var max = currentProduct ? Math.max(Number(currentProduct.stock) || 1, 1) : 99;
    if (buyQty < max) { buyQty += 1; els.qtyVal.textContent = String(buyQty); }
  });
  els.qtyMinus.addEventListener('click', function () {
    if (buyQty > 1) { buyQty -= 1; els.qtyVal.textContent = String(buyQty); }
  });

  /* 加入购物车（重复加入数量累加） */
  els.addCartBtn.addEventListener('click', async function () {
    if (!Api.isCustomerLogin()) { requireLogin(); return; }
    App.setLoading(els.addCartBtn, true, '加购中');
    try {
      await Api.addToCart(currentProduct.id, buyQty);
      App.showToast('已加入购物车（数量 ' + buyQty + '）', 'success');
    } catch (err) {
      if (Api.isAuthError(err)) { Api.setCustomerSession(null); requireLogin(); return; }
      App.showToast(err.message || '加购失败', 'error');
    } finally {
      App.setLoading(els.addCartBtn, false);
    }
  });

  /* 收藏 / 取消收藏（去重） */
  async function refreshFavoriteState() {
    try {
      var data = await Api.isFavorite(productId);
      updateFavBtn(!!(data && data.favorited));
    } catch (e) { /* 静默：按钮维持默认态 */ }
  }

  function updateFavBtn(fav) {
    els.favBtn.textContent = fav ? '♥ 已收藏' : '♡ 收藏';
    els.favBtn.classList.toggle('btn-ghost-danger', !!fav);
  }

  els.favBtn.addEventListener('click', async function () {
    if (!Api.isCustomerLogin()) { requireLogin(); return; }
    var fav = els.favBtn.textContent.indexOf('已收藏') >= 0;
    App.setLoading(els.favBtn, true);
    try {
      if (fav) {
        await Api.removeFavorite(currentProduct.id);
        updateFavBtn(false);
        App.showToast('已取消收藏', 'success');
      } else {
        await Api.addFavorite(currentProduct.id);
        updateFavBtn(true);
        App.showToast('已加入收藏', 'success');
      }
    } catch (err) {
      if (Api.isAuthError(err)) { Api.setCustomerSession(null); requireLogin(); return; }
      App.showToast(err.message || '操作失败', 'error');
    } finally {
      App.setLoading(els.favBtn, false);
    }
  });

  /* ==================== 启动 ==================== */

  App.renderCustomerNav();
  loadDetail();
})();

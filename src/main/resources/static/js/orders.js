/* ============================================================
   买家端 · 我的订单（仅成功 / 失败）
   对应接口：GET /api/customer/orders
   单品小铺 · 迭代 2 · 邱圣洁
   ============================================================ */
(function () {
  'use strict';

  var App = window.App;
  var Api = window.Api;
  var $ = function (id) { return document.getElementById(id); };

  App.renderCustomerNav();

  if (!App.requireCustomerLogin('orders.html')) return;

  var els = {
    loading: $('orders-loading'),
    empty: $('orders-empty'),
    body: $('orders-body'),
    tbody: $('orders-tbody'),
    count: $('orders-count')
  };

  async function loadOrders() {
    App.show(els.loading, true);
    App.show(els.empty, false);
    App.show(els.body, false);

    try {
      var data = await Api.getMyOrders();
      renderOrders((data && data.items) || [], data && data.total);
    } catch (e) {
      App.show(els.loading, false);
      if (Api.isAuthError(e)) {
        Api.setCustomerSession(null);
        App.showToast('登录已失效，请重新登录', 'error');
        setTimeout(function () { location.href = 'login.html?redirect=orders.html'; }, 800);
      } else if (e.status === 0) {
        App.showToast('无法连接服务器，请确认后端已启动', 'error');
      } else {
        App.showToast(e.message, 'error');
      }
    }
  }

  function renderOrders(items, total) {
    App.show(els.loading, false);
    els.count.textContent = (Number(total) || items.length) ? ('共 ' + (Number(total) || items.length) + ' 条') : '';

    if (!items.length) {
      App.show(els.empty, true);
      App.show(els.body, false);
      return;
    }

    var html = '';
    items.forEach(function (it) {
      var cover = it.coverImage ? App.resolveImageUrl(it.coverImage) : App.PLACEHOLDER_IMAGE;
      html +=
        '<tr>' +
          '<td>' +
            '<div class="order-cell">' +
              '<img class="order-cover" src="' + App.escapeHtml(cover) + '" alt="" data-fallback="1">' +
              '<a class="order-name" href="product.html?id=' + encodeURIComponent(it.productId) + '">' +
                App.escapeHtml(it.productName || '-') + '</a>' +
            '</div>' +
          '</td>' +
          '<td>' + App.escapeHtml(App.formatTime(it.submittedAt)) + '</td>' +
          '<td>' + App.badgeHtml(App.ORDER_BADGE, it.result) + '</td>' +
          '<td>' + App.escapeHtml(App.formatTime(it.resultAt)) + '</td>' +
        '</tr>';
    });
    els.tbody.innerHTML = html;
    Array.prototype.forEach.call(els.tbody.querySelectorAll('img[data-fallback]'), App.bindImageFallback);

    App.show(els.empty, false);
    App.show(els.body, true);
  }

  loadOrders();
})();

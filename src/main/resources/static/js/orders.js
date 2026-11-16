/* ============================================================
   买家端 · 我的订单：五态状态机展示 + 倒序翻页 + 取消入口
   对应接口：GET /api/customer/orders、POST /api/customer/orders/{id}/cancel
   状态：CREATED→CONFIRMED→PREPARED→SHIPPED→COMPLETED（另含 CANCELLED 终态）
   买家取消窗口：SHIPPED 前（即 CREATED / CONFIRMED / PREPARED）
   单品小铺 · 迭代 3（挑战需求 B）· 邱圣洁
   ============================================================ */
(function () {
  'use strict';

  var App = window.App;
  var Api = window.Api;
  var $ = function (id) { return document.getElementById(id); };

  var PAGE_SIZE = 10;
  var page = 1;
  var total = 0;

  var els = {
    loading: $('orders-loading'),
    empty: $('orders-empty'),
    body: $('orders-body'),
    list: $('orders-list'),
    pager: $('orders-pagination'),
    count: $('orders-count')
  };

  async function load() {
    App.show(els.loading, true);
    App.show(els.body, false);
    App.show(els.empty, false);
    try {
      var data = await Api.listMyOrders(page, PAGE_SIZE);
      total = Number(data && data.total) || 0;
      var items = (data && data.items) || [];
      els.count.textContent = total ? ('共 ' + total + ' 单') : '';
      App.show(els.empty, items.length === 0);
      App.show(els.body, items.length > 0);
      if (items.length) renderList(items);
    } catch (e) {
      if (Api.isAuthError(e)) {
        Api.setCustomerSession(null);
        App.showToast('请先登录', 'error');
        setTimeout(function () {
          location.href = 'login.html?redirect=' + encodeURIComponent('orders.html');
        }, 800);
        return;
      }
      App.showToast(e.message || '加载失败', 'error');
    } finally {
      App.show(els.loading, false);
    }
  }

  /* 买家可取消窗口：开始发货前（CUSTOMER_SUBMITTED / SELLER_CONFIRMED / READY_TO_SHIP） */
  function canCancel(status) {
    return status === 'CUSTOMER_SUBMITTED' || status === 'SELLER_CONFIRMED' || status === 'READY_TO_SHIP';
  }

  function renderList(items) {
    var html = '';
    items.forEach(function (o) {
      var rows = (o.items || []).map(function (it) {
        return '<li>' + App.escapeHtml(it.productName || '商品') +
          ' × ' + it.quantity + '　￥' + App.formatPrice(it.unitPrice) +
          (it.available === false ? ' <span class="badge badge-failed">无货</span>' : '') + '</li>';
      }).join('');

      var cancelBtn = canCancel(o.status)
        ? '<button type="button" class="btn btn-ghost-danger btn-xs" data-cancel="' + o.id + '">取消订单</button>'
        : '';
      var statusHtml = o.statusText
        ? '<span class="badge ' + ((App.ORDER_STATUS[o.status] || [])[1] || 'badge-offshelf') + '">' + App.escapeHtml(o.statusText) + '</span>'
        : App.badgeHtml(App.ORDER_STATUS, o.status);

      html += '<section class="card" style="margin-bottom:12px">'
        + '<div class="card-title-row">'
          + '<h4 class="card-title" style="margin:0">订单 ' + App.escapeHtml(o.orderNo || '') + '</h4>'
          + '<span>' + statusHtml + '</span>'
        + '</div>'
        + '<ul class="order-items-list">' + rows + '</ul>'
        + '<p style="font-size:13px;color:var(--text-muted);margin:8px 0 0">'
          + '合计：<b style="color:var(--danger)">￥' + App.formatPrice(o.totalAmount) + '</b>'
          + '　共 ' + (o.itemCount || (o.items || []).length) + ' 种商品'
          + '　下单时间：' + App.formatTime(o.createdAt)
          + (o.cancelReason ? ('　取消原因：' + App.escapeHtml(o.cancelReason)) : '')
        + '</p>'
        + '<div style="margin-top:10px">' + cancelBtn + '</div>'
        + '</section>';
    });
    els.list.innerHTML = html;
    App.renderPagination(els.pager, page, PAGE_SIZE, total, function (p) {
      page = p;
      load();
    });
  }

  /* 取消订单（事件委托，传数字订单 id） */
  els.list.addEventListener('click', async function (e) {
    var btn = e.target && e.target.closest ? e.target.closest('[data-cancel]') : null;
    if (!btn) return;
    var orderId = btn.getAttribute('data-cancel');
    if (!confirm('确定取消该订单吗？取消后释放锁定库存，订单保留在历史中。')) return;
    App.setLoading(btn, true);
    try {
      await Api.cancelMyOrder(orderId);
      App.showToast('订单已取消', 'success');
      load();
    } catch (err) {
      if (Api.isAuthError(err)) {
        Api.setCustomerSession(null);
        App.showToast('登录已失效，请重新登录', 'error');
        setTimeout(function () {
          location.href = 'login.html?redirect=' + encodeURIComponent('orders.html');
        }, 800);
        return;
      }
      App.showToast(err.message || '取消失败', 'error');
      load(); /* 409：状态可能已变化，刷新拿最新 */
    } finally {
      App.setLoading(btn, false);
    }
  });

  /* ==================== 启动 ==================== */

  App.renderCustomerNav();
  load();
})();

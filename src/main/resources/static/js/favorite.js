/* ============================================================
   买家端 · 收藏：列表 / 取消收藏 / 去购物车下单
   对应接口：GET /api/customer/favorites、DELETE /api/customer/favorites/{pid}
   单品小铺 · 迭代 3（挑战需求 B）· 邱圣洁
   ============================================================ */
(function () {
  'use strict';

  var App = window.App;
  var Api = window.Api;
  var $ = function (id) { return document.getElementById(id); };

  var items = [];

  var els = {
    loading: $('fav-loading'),
    empty: $('fav-empty'),
    body: $('fav-body'),
    tbody: $('fav-tbody'),
    count: $('fav-count')
  };

  async function load() {
    App.show(els.loading, true);
    App.show(els.body, false);
    App.show(els.empty, false);
    try {
      var data = await Api.getFavorites();
      items = (data && data.items) || [];
      render();
    } catch (e) {
      handleAuth(e, load);
    } finally {
      App.show(els.loading, false);
    }
  }

  function render() {
    els.count.textContent = items.length ? ('共 ' + items.length + ' 件') : '';
    App.show(els.empty, items.length === 0);
    App.show(els.body, items.length > 0);
    if (!items.length) return;

    var html = '';
    items.forEach(function (it) {
      /* 可购判定：优先用后端 available 标注（兼容 MOCK 的 status/stock） */
      var buyable = it.available !== undefined
        ? !!it.available
        : (it.status === 'ON_SALE' && Number(it.stock) > 0);
      var reason = it.unavailableReason
        || (it.status && it.status !== 'ON_SALE' ? '已下架' : '无货');
      var badge = buyable
        ? '<span class="badge badge-on-sale">有货</span>'
        : '<span class="badge badge-failed">' + App.escapeHtml(reason) + '</span>';
      html += '<tr>'
        + '<td><a href="product.html?id=' + it.productId + '">' + App.escapeHtml(it.name || '商品') + '</a></td>'
        + '<td>￥' + App.formatPrice(it.price) + '</td>'
        + '<td>' + (buyable ? '可购' : App.escapeHtml(reason)) + '</td>'
        + '<td>' + badge + '</td>'
        + '<td>'
          + '<button type="button" class="btn btn-ghost btn-xs" data-act="cart" data-pid="' + it.productId + '">加入购物车</button>'
          + '<button type="button" class="btn btn-ghost-danger btn-xs" data-act="unfav" data-pid="' + it.productId + '">取消收藏</button>'
        + '</td>'
        + '</tr>';
    });
    els.tbody.innerHTML = html;
  }

  els.tbody.addEventListener('click', async function (e) {
    var btn = e.target && e.target.closest ? e.target.closest('[data-act]') : null;
    if (!btn) return;
    var pid = Number(btn.getAttribute('data-pid'));
    var act = btn.getAttribute('data-act');

    if (act === 'unfav') {
      if (!confirm('确定取消收藏该商品吗？')) return;
      App.setLoading(btn, true);
      try {
        await Api.removeFavorite(pid);
        items = items.filter(function (x) { return x.productId !== pid; });
        render();
        App.showToast('已取消收藏', 'success');
      } catch (err) { handleAuth(err, load); }
      finally { App.setLoading(btn, false); }
      return;
    }

    if (act === 'cart') {
      App.setLoading(btn, true);
      try {
        await Api.addToCart(pid, 1);
        App.showToast('已加入购物车', 'success');
      } catch (err) { handleAuth(err, load); }
      finally { App.setLoading(btn, false); }
    }
  });

  function handleAuth(err, retry) {
    if (Api.isAuthError(err)) {
      Api.setCustomerSession(null);
      App.showToast('请先登录', 'error');
      setTimeout(function () {
        location.href = 'login.html?redirect=' + encodeURIComponent('favorite.html');
      }, 800);
      return;
    }
    App.showToast(err.message || '操作失败', 'error');
  }

  /* ==================== 启动 ==================== */

  App.renderCustomerNav();
  load();
})();

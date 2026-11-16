/* ============================================================
   买家端 · 购物车：列表 / 改数量 / 删除 / 一键清空 / 多选下单 / 多选转收藏
   契约（后端最终版）：
     GET|POST /api/customer/cart、PUT|DELETE /api/customer/cart/{itemId}、DELETE /api/customer/cart
     POST /api/customer/cart/to-favorites { itemIds } → { transferred, skipped }
     CartItem: { itemId, productId, quantity, name, price, coverImage, available, unavailableReason }
   单品小铺 · 迭代 4（挑战需求 B）· 邱圣洁
   ============================================================ */
(function () {
  'use strict';

  var App = window.App;
  var Api = window.Api;
  var $ = function (id) { return document.getElementById(id); };

  var items = [];          /* CartItem[]（以 itemId 为主键） */
  var selected = {};       /* itemId -> true */
  var shortages = [];      /* 最近一次下单的缺货清单 */

  var els = {
    loading: $('cart-loading'),
    empty: $('cart-empty'),
    body: $('cart-body'),
    tbody: $('cart-tbody'),
    count: $('cart-count'),
    checkAll: $('cart-check-all'),
    selectedNum: $('cart-selected'),
    total: $('cart-total'),
    orderBtn: $('cart-order'),
    toFavBtn: $('cart-to-fav'),
    removeBtn: $('cart-remove'),
    shortageTip: $('cart-shortage')
  };

  /* ==================== 加载与渲染 ==================== */

  async function load() {
    App.show(els.loading, true);
    App.show(els.body, false);
    App.show(els.empty, false);
    try {
      var data = await Api.getCart();
      items = (data && data.items) || [];
      shortages = [];
      render();
    } catch (e) {
      handleAuth(e, load);
    } finally {
      App.show(els.loading, false);
    }
  }

  /* 可购判定：优先用后端 available 标注（兼容 MOCK 的 status/stock） */
  function isBuyable(it) {
    if (it.available !== undefined) return !!it.available;
    return it.status === 'ON_SALE' && Number(it.stock) > 0;
  }

  function unavailableText(it) {
    if (it.unavailableReason) return it.unavailableReason;
    if (it.status && it.status !== 'ON_SALE') return '已下架';
    return '无货';
  }

  function render() {
    var count = items.reduce(function (s, it) { return s + Number(it.quantity || 0); }, 0);
    els.count.textContent = count ? ('共 ' + items.length + ' 种商品 / ' + count + ' 件') : '';
    App.show(els.empty, items.length === 0);
    App.show(els.body, items.length > 0);
    if (!items.length) { selected = {}; renderBar(); return; }

    var html = '';
    items.forEach(function (it) {
      var buyable = isBuyable(it);
      var statusBadge = buyable
        ? '<span class="badge badge-on-sale">有货</span>'
        : '<span class="badge badge-failed">' + App.escapeHtml(unavailableText(it)) + '</span>';
      html += '<tr data-iid="' + it.itemId + '">'
        + '<td><input type="checkbox" class="cart-check row-check" data-iid="' + it.itemId + '"' +
          (selected[it.itemId] ? ' checked' : '') + '></td>'
        + '<td><a href="product.html?id=' + it.productId + '">' + App.escapeHtml(it.name || '商品') + '</a></td>'
        + '<td>￥' + App.formatPrice(it.price) + '</td>'
        + '<td><span class="qty-stepper">'
          + '<button type="button" data-act="minus" data-iid="' + it.itemId + '">−</button>'
          + '<span class="qty-val">' + it.quantity + '</span>'
          + '<button type="button" data-act="plus" data-iid="' + it.itemId + '">＋</button>'
          + '</span></td>'
        + '<td>￥' + App.formatPrice((Number(it.price) || 0) * (Number(it.quantity) || 0)) + '</td>'
        + '<td>' + statusBadge + (buyable ? '' : '<br><span style="font-size:12px;color:var(--text-muted)">下单时逐件校验</span>') + '</td>'
        + '<td><button type="button" class="btn btn-ghost-danger btn-xs" data-act="del" data-iid="' + it.itemId + '">删除</button></td>'
        + '</tr>';
    });
    els.tbody.innerHTML = html;
    renderBar();
  }

  function checkedItemIds() {
    return items.filter(function (it) { return selected[it.itemId]; }).map(function (it) { return it.itemId; });
  }

  function renderBar() {
    var ids = checkedItemIds();
    var sum = items.filter(function (it) { return selected[it.itemId]; })
      .reduce(function (s, it) { return s + (Number(it.price) || 0) * (Number(it.quantity) || 0); }, 0);
    els.selectedNum.textContent = ids.length;
    els.total.textContent = '￥' + App.formatPrice(sum);
    els.orderBtn.disabled = ids.length === 0;
    els.toFavBtn.disabled = ids.length === 0;
    els.removeBtn.disabled = ids.length === 0;
    els.checkAll.checked = items.length > 0 && ids.length === items.length;

    if (shortages.length) {
      els.shortageTip.textContent = '以下商品缺货或已下架，未加入订单（保留在购物车）：' +
        shortages.map(function (s) { return s.name + '（要 ' + s.requested + ' 件，仅 ' + s.stock + ' 件）'; }).join('、');
      App.show(els.shortageTip, true);
    } else {
      App.show(els.shortageTip, false);
    }
  }

  /* ==================== 交互 ==================== */

  els.checkAll.addEventListener('change', function () {
    var on = els.checkAll.checked;
    selected = {};
    items.forEach(function (it) { if (on) selected[it.itemId] = true; });
    Array.prototype.forEach.call(els.tbody.querySelectorAll('.row-check'), function (cb) {
      cb.checked = on;
    });
    renderBar();
  });

  /* 行内操作：数量步进 / 删除（事件委托，按 itemId） */
  els.tbody.addEventListener('click', async function (e) {
    var btn = e.target && e.target.closest ? e.target.closest('[data-act]') : null;
    if (!btn) return;
    var iid = Number(btn.getAttribute('data-iid'));
    var act = btn.getAttribute('data-act');
    var it = items.find(function (x) { return x.itemId === iid; });
    if (!it) return;

    if (act === 'del') {
      if (!confirm('确定从购物车删除「' + (it.name || '该商品') + '」吗？')) return;
      App.setLoading(btn, true);
      try {
        await Api.removeCartItem(iid);
        items = items.filter(function (x) { return x.itemId !== iid; });
        delete selected[iid];
        render();
        App.showToast('已删除', 'success');
      } catch (err) { handleAuth(err, load); }
      finally { App.setLoading(btn, false); }
      return;
    }

    if (act === 'plus' || act === 'minus') {
      var next = Number(it.quantity) + (act === 'plus' ? 1 : -1);
      if (next < 1) return; /* quantity >= 1，删除走删除接口 */
      App.setLoading(btn, true);
      try {
        await Api.updateCartItem(iid, next);
        it.quantity = next;
        render();
      } catch (err) { handleAuth(err, load); }
      finally { App.setLoading(btn, false); }
    }
  });

  els.tbody.addEventListener('change', function (e) {
    var cb = e.target && e.target.closest ? e.target.closest('.row-check') : null;
    if (!cb) return;
    var iid = Number(cb.getAttribute('data-iid'));
    if (cb.checked) selected[iid] = true; else delete selected[iid];
    renderBar();
  });

  /* 删除所选 */
  els.removeBtn.addEventListener('click', async function () {
    var ids = checkedItemIds();
    if (!ids.length) return;
    if (!confirm('确定删除所选 ' + ids.length + ' 种商品吗？')) return;
    App.setLoading(els.removeBtn, true, '删除中');
    try {
      for (var i = 0; i < ids.length; i++) await Api.removeCartItem(ids[i]);
      App.showToast('已删除所选商品', 'success');
      load();
    } catch (err) { handleAuth(err, load); }
    finally { App.setLoading(els.removeBtn, false); }
  });

  /* 多选转收藏（转移：调后端 to-favorites，购物车移除、进收藏） */
  els.toFavBtn.addEventListener('click', async function () {
    var ids = checkedItemIds();
    if (!ids.length) return;
    App.setLoading(els.toFavBtn, true, '转移中');
    try {
      var data = await Api.cartToFavorites(ids);
      var t = data && data.transferred;
      var skipped = (data && data.skipped) || [];
      var msg = '已将 ' + (t !== undefined ? t : ids.length) + ' 种商品转为收藏';
      if (skipped.length) msg += '，' + skipped.length + ' 项跳过';
      App.showToast(msg, 'success');
      load();
    } catch (err) { handleAuth(err, load); }
    finally { App.setLoading(els.toFavBtn, false); }
  });

  /* 提交下单（入参为购物车条目 cartItemIds；逐件缺货校验：缺货项不进订单、保留购物车） */
  els.orderBtn.addEventListener('click', async function () {
    var ids = checkedItemIds();   /* itemId 数组 == 后端的 cartItemIds */
    if (!ids.length) return;
    App.setLoading(els.orderBtn, true, '下单中');
    try {
      var data = await Api.submitOrder(ids);
      var order = data && data.order;
      /* 后端字段为 rejected（逐件缺货/下架提示） */
      var rejected = (data && data.rejected) || [];
      shortages = rejected.map(function (r) {
        return {
          name: r.name || r.productName || '商品',
          requested: r.quantity || r.requested || 1,
          stock: r.stock !== undefined ? r.stock : 0
        };
      });
      if (order) {
        var msg = '下单成功！订单号 ' + (order.orderNo || order.id) + '，共 ' + (order.items || []).length + ' 种商品';
        if (rejected.length) msg += '；另有 ' + rejected.length + ' 项缺货未下单';
        App.showToast(msg, 'success');
      } else {
        App.showToast('所选商品均已缺货或下架，未生成订单', 'error');
      }
      load();
    } catch (err) {
      /* 全部缺货时后端 400，rejected 明细挂在错误对象上 */
      if (err && err.rejected && err.rejected.length) {
        shortages = err.rejected.map(function (r) {
          return {
            name: r.name || r.productName || '商品',
            requested: r.quantity || r.requested || 1,
            stock: r.stock !== undefined ? r.stock : 0
          };
        });
        App.showToast('所选商品均已缺货或下架，未生成订单', 'error');
        load();
        return;
      }
      handleAuth(err, load);
    }
    finally { App.setLoading(els.orderBtn, false); }
  });

  /* 401 统一处理：清会话跳登录 */
  function handleAuth(err, retry) {
    if (Api.isAuthError(err)) {
      Api.setCustomerSession(null);
      App.showToast('请先登录', 'error');
      setTimeout(function () {
        location.href = 'login.html?redirect=' + encodeURIComponent('cart.html');
      }, 800);
      return;
    }
    App.showToast(err.message || '操作失败', 'error');
    if (err.status === 409 || err.status === 400) load(); /* 状态冲突时刷新 */
  }

  /* ==================== 启动 ==================== */

  App.renderCustomerNav();
  load();
})();

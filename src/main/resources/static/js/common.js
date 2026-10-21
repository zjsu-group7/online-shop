/* ============================================================
   公共工具：Toast / 转义 / 格式化 / 分页 / 富文本 / 多图 / 登录守卫
   单品小铺 · 迭代 2 · 邱圣洁
   ============================================================ */
(function (global) {
  'use strict';

  /* ---------- Toast 轻提示 ---------- */
  var toastTimer = null;
  function showToast(message, type) {
    var el = document.getElementById('toast');
    if (!el) {
      el = document.createElement('div');
      el.id = 'toast';
      document.body.appendChild(el);
    }
    el.className = 'toast toast-' + (type || 'info');
    el.textContent = message;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.hidden = true; }, 3200);
  }

  /* ---------- HTML 转义（防 XSS） ---------- */
  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  /* ---------- 富文本：白名单过滤后渲染 ----------
     后端已做白名单过滤；这里再兜一层，去掉脚本与事件属性，双保险。 */
  var ALLOWED_TAGS = ['P', 'BR', 'STRONG', 'B', 'EM', 'I', 'U', 'S', 'UL', 'OL', 'LI',
    'H1', 'H2', 'H3', 'H4', 'BLOCKQUOTE', 'SPAN', 'DIV', 'A', 'IMG', 'HR', 'TABLE',
    'THEAD', 'TBODY', 'TR', 'TD', 'TH'];

  function sanitizeHtml(html) {
    if (!html) return '';
    var doc = new DOMParser().parseFromString('<div id="root">' + html + '</div>', 'text/html');
    var root = doc.getElementById('root');
    if (!root) return '';

    function walk(node) {
      var children = Array.prototype.slice.call(node.childNodes);
      for (var i = 0; i < children.length; i++) {
        var child = children[i];
        if (child.nodeType === 1) {
          if (ALLOWED_TAGS.indexOf(child.tagName) < 0) {
            /* 不保留标签，但保留其中的文字 */
            var text = doc.createTextNode(child.textContent || '');
            node.replaceChild(text, child);
            continue;
          }
          var attrs = Array.prototype.slice.call(child.attributes);
          for (var j = 0; j < attrs.length; j++) {
            var name = attrs[j].name.toLowerCase();
            var keep = false;
            if (child.tagName === 'A' && name === 'href') keep = true;
            if (child.tagName === 'IMG' && (name === 'src' || name === 'alt')) keep = true;
            if (!keep) child.removeAttribute(attrs[j].name);
          }
          if (child.tagName === 'A') {
            child.setAttribute('target', '_blank');
            child.setAttribute('rel', 'noopener noreferrer');
          }
          walk(child);
        } else if (child.nodeType !== 3) {
          node.removeChild(child);
        }
      }
    }
    walk(root);
    return root.innerHTML;
  }

  function renderRichText(el, html) {
    if (!el) return;
    if (!html) { el.textContent = ''; return; }
    var plain = String(html).replace(/<[^>]*>/g, '').trim();
    if (!plain && String(html).indexOf('<img') < 0) { el.textContent = ''; return; }
    el.innerHTML = sanitizeHtml(html);
  }

  /* ---------- 格式化 ---------- */
  function formatPrice(value) {
    var n = Number(value);
    return Number.isFinite(n) ? n.toFixed(2) : String(value == null ? '' : value);
  }

  function pad2(n) { return n < 10 ? '0' + n : '' + n; }

  function formatTime(iso) {
    if (!iso) return '-';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return String(iso);
    return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) +
      ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
  }

  /* ---------- 状态字典 ---------- */
  var PRODUCT_BADGE = {
    ON_SALE: ['在售', 'badge-on-sale'],
    FROZEN: ['交易中', 'badge-frozen'],
    IN_TRADE: ['交易中', 'badge-frozen'],
    OFF_SHELF: ['已下架', 'badge-offshelf'],
    SOLD: ['已售出', 'badge-offshelf']
  };

  /* 我的订单只有成功 / 失败两种 */
  var ORDER_BADGE = {
    SUCCESS: ['交易成功', 'badge-traded'],
    FAIL: ['交易失败', 'badge-failed'],
    FAILED: ['交易失败', 'badge-failed']
  };

  function badgeHtml(map, key) {
    var item = map[key] || [key || '未知', 'badge-offshelf'];
    return '<span class="badge ' + item[1] + '">' + escapeHtml(item[0]) + '</span>';
  }

  /* ---------- 图片地址解析 ---------- */
  function resolveImageUrl(img) {
    if (!img) return '';
    if (/^(data:|https?:|\/\/)/i.test(String(img))) return img;
    return global.Api ? global.Api.uploadUrl(img) : String(img);
  }

  var PLACEHOLDER_IMAGE =
    "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 400 300'%3E" +
    "%3Crect width='400' height='300' fill='%23eef0f6'/%3E" +
    "%3Cpath d='M168 118h18l10-16h28l10 16h18a12 12 0 0 1 12 12v76a12 12 0 0 1-12 12H168a12 12 0 0 1-12-12v-76a12 12 0 0 1 12-12z' fill='%23c3c8d9'/%3E" +
    "%3Ccircle cx='200' cy='162' r='19' fill='%23eef0f6'/%3E" +
    "%3C/svg%3E";

  function bindImageFallback(imgEl) {
    if (!imgEl) return;
    imgEl.addEventListener('error', function () {
      if (imgEl.src !== PLACEHOLDER_IMAGE) imgEl.src = PLACEHOLDER_IMAGE;
    });
  }

  /* ---------- 按钮加载态 ---------- */
  function setLoading(btn, loading, loadingText) {
    if (!btn) return;
    if (loading) {
      if (!btn.dataset.origText) btn.dataset.origText = btn.textContent;
      btn.classList.add('btn-loading');
      btn.disabled = true;
      if (loadingText) btn.textContent = loadingText;
    } else {
      btn.classList.remove('btn-loading');
      if (btn.dataset.origText) btn.textContent = btn.dataset.origText;
      btn.disabled = false;
    }
  }

  /* ---------- 表单错误提示 ---------- */
  function show(el, on) { if (el) el.hidden = !on; }

  function fieldError(el, msg) {
    if (!el) return;
    if (!msg) { el.hidden = true; return; }
    el.textContent = msg;
    el.hidden = false;
  }

  /* ---------- 分页渲染 ----------
     响应统一 { page, size, total, items[] }；onChange(page) 回调 */
  function renderPagination(container, page, size, total, onChange) {
    if (!container) return;
    var totalPages = Math.max(1, Math.ceil((Number(total) || 0) / (Number(size) || 12)));
    var cur = Math.min(Math.max(1, Number(page) || 1), totalPages);

    var html = '';
    html += '<button type="button" class="page-btn" data-page="' + (cur - 1) + '"' +
      (cur <= 1 ? ' disabled' : '') + '>上一页</button>';

    /* 最多显示 7 个页码 */
    var start = Math.max(1, cur - 3);
    var end = Math.min(totalPages, start + 6);
    start = Math.max(1, end - 6);
    for (var p = start; p <= end; p++) {
      html += '<button type="button" class="page-btn' + (p === cur ? ' is-current' : '') +
        '" data-page="' + p + '">' + p + '</button>';
    }

    html += '<button type="button" class="page-btn" data-page="' + (cur + 1) + '"' +
      (cur >= totalPages ? ' disabled' : '') + '>下一页</button>';
    html += '<span class="page-total">共 ' + (Number(total) || 0) + ' 件 / ' + totalPages + ' 页</span>';

    container.innerHTML = html;
    container.hidden = false;

    container.onclick = function (e) {
      var btn = e.target && e.target.closest ? e.target.closest('[data-page]') : null;
      if (!btn || btn.disabled) return;
      var target = Number(btn.getAttribute('data-page'));
      if (!target || target === cur) return;
      onChange(target);
    };
  }

  /* ---------- 多图上传：选图 → Base64 ---------- */
  function readImagesAsDataUrl(fileList, max, onDone) {
    var files = Array.prototype.slice.call(fileList || []);
    if (!files.length) { onDone([]); return; }
    if (files.length > max) {
      showToast('最多只能上传 ' + max + ' 张图片', 'error');
      files = files.slice(0, max);
    }
    var results = [];
    var pending = files.length;
    files.forEach(function (file) {
      if (!/^image\//i.test(file.type)) {
        showToast('仅支持图片文件：' + file.name, 'error');
        if (--pending === 0) onDone(results);
        return;
      }
      if (file.size > 5 * 1024 * 1024) {
        showToast('单张图片不能超过 5MB：' + file.name, 'error');
        if (--pending === 0) onDone(results);
        return;
      }
      var reader = new FileReader();
      reader.onload = function () {
        results.push(reader.result);
        if (--pending === 0) onDone(results);
      };
      reader.onerror = function () {
        showToast('图片读取失败：' + file.name, 'error');
        if (--pending === 0) onDone(results);
      };
      reader.readAsDataURL(file);
    });
  }

  /* ---------- 登录守卫：未登录跳转登录页 ---------- */
  function requireCustomerLogin(redirect) {
    if (global.Api && Api.isCustomerLogin && Api.isCustomerLogin()) return true;
    var back = redirect || location.pathname.substring(location.pathname.lastIndexOf('/') + 1);
    location.href = 'login.html?redirect=' + encodeURIComponent(back);
    return false;
  }

  /* ---------- 顶部导航登录态渲染 ---------- */
  function renderCustomerNav() {
    var guestBox = document.getElementById('nav-guest');
    var userBox = document.getElementById('nav-user');
    var nameEl = document.getElementById('nav-username');
    var logoutBtn = document.getElementById('nav-logout');

    var login = !!(global.Api && Api.isCustomerLogin && Api.isCustomerLogin());
    show(guestBox, !login);
    show(userBox, login);
    if (login && nameEl) nameEl.textContent = Api.getCustomerName() || '买家';

    if (logoutBtn && !logoutBtn.dataset.bound) {
      logoutBtn.dataset.bound = '1';
      logoutBtn.addEventListener('click', async function () {
        try { await Api.customerLogout(); } catch (e) { /* 失败也本地退出 */ }
        Api.setCustomerSession(null);
        showToast('已退出登录', 'info');
        setTimeout(function () { location.href = 'index.html'; }, 600);
      });
    }
  }

  global.App = {
    showToast: showToast,
    escapeHtml: escapeHtml,
    sanitizeHtml: sanitizeHtml,
    renderRichText: renderRichText,
    formatPrice: formatPrice,
    formatTime: formatTime,
    badgeHtml: badgeHtml,
    PRODUCT_BADGE: PRODUCT_BADGE,
    ORDER_BADGE: ORDER_BADGE,
    resolveImageUrl: resolveImageUrl,
    PLACEHOLDER_IMAGE: PLACEHOLDER_IMAGE,
    bindImageFallback: bindImageFallback,
    setLoading: setLoading,
    show: show,
    fieldError: fieldError,
    renderPagination: renderPagination,
    readImagesAsDataUrl: readImagesAsDataUrl,
    requireCustomerLogin: requireCustomerLogin,
    renderCustomerNav: renderCustomerNav
  };
})(window);

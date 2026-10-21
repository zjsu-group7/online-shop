/* ============================================================
   买家端 · 个人中心
   对应接口：GET /api/customer/me、PUT /api/customer/me
   单品小铺 · 迭代 2 · 邱圣洁
   ============================================================ */
(function () {
  'use strict';

  var App = window.App;
  var Api = window.Api;
  var $ = function (id) { return document.getElementById(id); };

  App.renderCustomerNav();

  if (!App.requireCustomerLogin('profile.html')) return;

  var els = {
    loading: $('profile-loading'),
    card: $('profile-card'),
    error: $('profile-error'),
    form: $('profile-form'),
    username: $('pf-username'),
    phone: $('pf-phone'),
    phoneError: $('pf-phone-error'),
    place: $('pf-place'),
    placeError: $('pf-place-error'),
    placeCounter: $('pf-place-counter'),
    created: $('pf-created'),
    submit: $('profile-submit')
  };

  function showError(msg) {
    if (!msg) { els.error.hidden = true; return; }
    els.error.textContent = msg;
    els.error.hidden = false;
  }

  els.place.addEventListener('input', function () {
    els.placeCounter.textContent = this.value.length + ' / 200';
    App.fieldError(els.placeError, '');
  });

  async function loadProfile() {
    try {
      var me = await Api.getMe();
      els.username.value = me.username || '';
      els.phone.value = me.phone || '';
      els.place.value = me.defaultTradePlace || '';
      els.placeCounter.textContent = (me.defaultTradePlace || '').length + ' / 200';
      els.created.value = App.formatTime(me.createdAt);
      App.show(els.loading, false);
      els.card.hidden = false;
    } catch (e) {
      App.show(els.loading, false);
      if (Api.isAuthError(e)) {
        Api.setCustomerSession(null);
        App.showToast('登录已失效，请重新登录', 'error');
        setTimeout(function () { location.href = 'login.html?redirect=profile.html'; }, 800);
      } else if (e.status === 0) {
        App.showToast('无法连接服务器，请确认后端已启动', 'error');
      } else {
        App.showToast(e.message, 'error');
      }
    }
  }

  els.form.addEventListener('submit', async function (e) {
    e.preventDefault();
    showError('');

    var phone = els.phone.value.trim();
    var place = els.place.value.trim();
    var valid = true;

    if (!phone) { App.fieldError(els.phoneError, '请输入联系电话'); valid = false; }
    else App.fieldError(els.phoneError, '');

    if (place.length > 200) { App.fieldError(els.placeError, '默认交易地点不能超过 200 字'); valid = false; }
    else App.fieldError(els.placeError, '');

    if (!valid) return;

    App.setLoading(els.submit, true, '保存中');
    try {
      await Api.updateMe({ phone: phone, defaultTradePlace: place });
      App.showToast('资料已更新', 'success');
    } catch (err) {
      if (Api.isAuthError(err)) {
        Api.setCustomerSession(null);
        App.showToast('登录已失效，请重新登录', 'error');
        setTimeout(function () { location.href = 'login.html?redirect=profile.html'; }, 800);
      } else {
        showError(err.message);
      }
    } finally {
      App.setLoading(els.submit, false);
    }
  });

  loadProfile();
})();

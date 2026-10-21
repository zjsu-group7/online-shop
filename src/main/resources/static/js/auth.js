/* ============================================================
   买家端 · 登录 / 注册
   对应接口：POST /api/customer/login、POST /api/customer/register
   单品小铺 · 迭代 2 · 邱圣洁
   ============================================================ */
(function () {
  'use strict';

  var App = window.App;
  var Api = window.Api;
  var $ = function (id) { return document.getElementById(id); };

  function redirectBack(extra) {
    var params = new URLSearchParams(location.search);
    var back = params.get('redirect');
    var target = back ? back : 'index.html';
    if (extra) target += (target.indexOf('?') >= 0 ? '&' : '?') + extra;
    location.href = target;
  }

  /* ==================== 登录 ==================== */

  var loginForm = $('login-form');
  if (loginForm) {
    var lu = $('login-username'), lp = $('login-password');
    var lue = $('login-username-error'), lpe = $('login-password-error');
    var lerr = $('login-error'), lbtn = $('login-submit');

    function showBanner(msg) {
      if (!msg) { lerr.hidden = true; return; }
      lerr.textContent = msg;
      lerr.hidden = false;
    }

    loginForm.addEventListener('submit', async function (e) {
      e.preventDefault();
      showBanner('');

      var username = lu.value.trim();
      var password = lp.value;
      var valid = true;

      if (!username) { App.fieldError(lue, '请输入用户名'); valid = false; } else App.fieldError(lue, '');
      if (!password) { App.fieldError(lpe, '请输入密码'); valid = false; } else App.fieldError(lpe, '');
      if (!valid) return;

      App.setLoading(lbtn, true, '登录中');
      try {
        var data = await Api.customerLogin(username, password);
        var token = data && data.token;
        if (!token) throw Api.ApiError(0, '登录接口未返回 token');
        var customer = data.customer || {};
        Api.setCustomerSession(token, customer.username || username);
        App.showToast('登录成功，欢迎回来', 'success');
        setTimeout(function () { redirectBack(); }, 600);
      } catch (err) {
        if (err.status === 401) showBanner(err.message || '用户名或密码错误');
        else if (err.status === 0) showBanner('无法连接服务器，请确认后端已启动');
        else showBanner(err.message);
      } finally {
        App.setLoading(lbtn, false);
      }
    });
  }

  /* ==================== 注册 ==================== */

  var registerForm = $('register-form');
  if (registerForm) {
    var ru = $('reg-username'), rp = $('reg-password'), rp2 = $('reg-password2');
    var rph = $('reg-phone'), rpl = $('reg-place');
    var rue = $('reg-username-error'), rpe = $('reg-password-error'),
        rp2e = $('reg-password2-error'), rphe = $('reg-phone-error'), rple = $('reg-place-error');
    var rerr = $('register-error'), rbtn = $('register-submit'), rcounter = $('reg-place-counter');

    rpl.addEventListener('input', function () {
      rcounter.textContent = this.value.length + ' / 200';
      App.fieldError(rple, '');
    });

    registerForm.addEventListener('submit', async function (e) {
      e.preventDefault();
      rerr.hidden = true;

      var username = ru.value.trim();
      var password = rp.value;
      var password2 = rp2.value;
      var phone = rph.value.trim();
      var place = rpl.value.trim();
      var valid = true;

      if (!username) { App.fieldError(rue, '请输入用户名'); valid = false; }
      else if (!/^[A-Za-z0-9_.-]{3,50}$/.test(username)) {
        App.fieldError(rue, '用户名只能包含字母、数字、下划线、点或短横线，且至少 3 位'); valid = false;
      } else App.fieldError(rue, '');

      if (!password) { App.fieldError(rpe, '请输入密码'); valid = false; }
      else if (password.length < 8) { App.fieldError(rpe, '密码不少于 8 位'); valid = false; }
      else App.fieldError(rpe, '');

      if (password2 !== password) { App.fieldError(rp2e, '两次输入的密码不一致'); valid = false; }
      else App.fieldError(rp2e, '');

      if (!phone) { App.fieldError(rphe, '请输入联系电话'); valid = false; }
      else App.fieldError(rphe, '');

      if (place.length > 200) { App.fieldError(rple, '默认交易地点不能超过 200 字'); valid = false; }
      else App.fieldError(rple, '');

      if (!valid) return;

      var body = { username: username, password: password, phone: phone };
      if (place) body.defaultTradePlace = place;

      App.setLoading(rbtn, true, '注册中');
      try {
        await Api.register(body);
        App.showToast('注册成功，请登录', 'success');
        setTimeout(function () { location.href = 'login.html'; }, 800);
      } catch (err) {
        if (err.status === 409) { App.fieldError(rue, '用户名已被占用'); }
        else if (err.status === 0) { rerr.textContent = '无法连接服务器，请确认后端已启动'; rerr.hidden = false; }
        else { rerr.textContent = err.message; rerr.hidden = false; }
      } finally {
        App.setLoading(rbtn, false);
      }
    });
  }
})();

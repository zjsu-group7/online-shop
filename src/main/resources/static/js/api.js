/* ============================================================
   API 层 · 迭代 2（挑战需求 A）
   单品小铺 · 邱圣洁（前端 / 联调）

   依据：
   - 客户域：李宇轩《客户账号模块 · 接口与实现说明》v1.1（/api/customer/**）
   - 商品域：Wiki《接口设计》v2 + 李婉婷建表 SQL
   - 统一响应体 { code, message, data }，HTTP 状态码与 code 对齐

   注意：
   - 客户 token 与卖家 token 完全隔离，分别存放
   - 所有需要鉴权的请求统一注入 X-Token
   - MOCK = true 时返回假数据，后端未就绪也能跑页面（联调后置为 false）
   ============================================================ */
(function (global) {
  'use strict';

  /* ---------- 联调开关：后端接口就绪后置为 false ---------- */
  var MOCK = false;

  /* ---------- 买家接口前缀 ----------
     Wiki 8.1 写的是 /api/buyer/*，李宇轩实际实现是 /api/customer/*。
     口径确认后只改这一行即可，页面代码不用动。 */
  var BUYER = '/api/customer';

  /* ---------- 排序值：待周琳皓 / 李婉婷确认，确认后只改这里 ---------- */
  var SORT_OPTIONS = [
    { label: '最新发布', value: 'createdAt,desc' },
    { label: '最早发布', value: 'createdAt,asc' },
    { label: '价格从低到高', value: 'price,asc' },
    { label: '价格从高到低', value: 'price,desc' }
  ];

  var CUSTOMER_TOKEN_KEY = 'customer_token';
  var CUSTOMER_NAME_KEY = 'customer_username';
  var SELLER_TOKEN_KEY = 'seller_token';
  var SELLER_NAME_KEY = 'seller_username';

  /* ---------- 错误对象 ---------- */
  function ApiError(status, message) {
    var err = new Error(message);
    err.name = 'ApiError';
    err.status = status || 0;
    return err;
  }

  /* ---------- Token 管理（客户 / 卖家分离） ---------- */
  function readKey(k) { try { return localStorage.getItem(k) || ''; } catch (e) { return ''; } }

  function getCustomerToken() { return readKey(CUSTOMER_TOKEN_KEY); }
  function getCustomerName() { return readKey(CUSTOMER_NAME_KEY); }
  function setCustomerSession(token, username) {
    try {
      if (token) {
        localStorage.setItem(CUSTOMER_TOKEN_KEY, token);
        if (username) localStorage.setItem(CUSTOMER_NAME_KEY, username);
      } else {
        localStorage.removeItem(CUSTOMER_TOKEN_KEY);
        localStorage.removeItem(CUSTOMER_NAME_KEY);
      }
    } catch (e) { /* 隐私模式忽略 */ }
  }

  function getSellerToken() { return readKey(SELLER_TOKEN_KEY); }
  function getSellerName() { return readKey(SELLER_NAME_KEY); }
  function setSellerSession(token, username) {
    try {
      if (token) {
        localStorage.setItem(SELLER_TOKEN_KEY, token);
        if (username) localStorage.setItem(SELLER_NAME_KEY, username);
      } else {
        localStorage.removeItem(SELLER_TOKEN_KEY);
        localStorage.removeItem(SELLER_NAME_KEY);
      }
    } catch (e) { /* 隐私模式忽略 */ }
  }

  /* ---------- 默认错误文案 ---------- */
  function defaultErrorMessage(status) {
    switch (status) {
      case 400: return '请求参数有误，请检查后重试';
      case 401: return '未登录或登录已失效，请重新登录';
      case 403: return '没有权限执行该操作';
      case 404: return '请求的资源不存在';
      case 409: return '状态冲突，请刷新后重试';
      default: return '服务器开小差了，请稍后重试';
    }
  }

  /* ---------- 核心请求 ---------- */
  async function request(path, options) {
    var opt = options || {};
    var method = opt.method || 'GET';
    var headers = {};

    if (opt.body !== undefined) headers['Content-Type'] = 'application/json';

    /* X-Token 注入：customer 用客户 token，seller 用卖家 token */
    if (opt.auth === 'customer') {
      var ct = getCustomerToken();
      if (ct) headers['X-Token'] = ct;
    } else if (opt.auth === 'seller' || opt.auth === true) {
      var st = getSellerToken();
      if (st) headers['X-Token'] = st;
    }

    var url = path;
    if (opt.params) {
      var qs = [];
      for (var k in opt.params) {
        if (Object.prototype.hasOwnProperty.call(opt.params, k)) {
          var v = opt.params[k];
          if (v !== null && v !== undefined && v !== '') qs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
        }
      }
      if (qs.length) url += (url.indexOf('?') >= 0 ? '&' : '?') + qs.join('&');
    }

    var resp;
    try {
      resp = await fetch(url, {
        method: method,
        headers: headers,
        body: opt.body === undefined ? undefined : JSON.stringify(opt.body)
      });
    } catch (err) {
      throw ApiError(0, '无法连接服务器：请确认后端已启动（http://localhost:8080）');
    }

    var payload = null;
    var text = await resp.text().catch(function () { return ''; });
    if (text) { try { payload = JSON.parse(text); } catch (e) { payload = null; } }

    if (!resp.ok) {
      throw ApiError(resp.status, (payload && payload.message) || defaultErrorMessage(resp.status));
    }

    if (payload && typeof payload === 'object' && Object.prototype.hasOwnProperty.call(payload, 'code')) {
      if (typeof payload.code === 'number' && payload.code >= 400) {
        throw ApiError(payload.code, payload.message || defaultErrorMessage(payload.code));
      }
      return Object.prototype.hasOwnProperty.call(payload, 'data') ? payload.data : payload;
    }
    return payload;
  }

  /* ============================================================
     假数据（MOCK = true 时使用）
     ============================================================ */
  var MOCK_CATEGORIES = [
    { id: 1, name: '数码电子', children: [{ id: 11, name: '手机平板' }, { id: 12, name: '电脑配件' }] },
    { id: 2, name: '图书教材', children: [{ id: 21, name: '专业课教材' }, { id: 22, name: '考研资料' }] },
    { id: 3, name: '生活用品', children: [{ id: 31, name: '代步工具' }] }
  ];

  function mockProduct(i) {
    return {
      id: i,
      name: '示例商品 ' + i,
      description: '<p>这是一段<strong>富文本</strong>描述，支持 <em>斜体</em> 与列表：</p><ul><li>成色：九成新</li><li>配件齐全</li></ul>',
      price: 99 + i * 10,
      categoryId: 11,
      categoryName: '手机平板',
      stock: i % 3 === 0 ? 0 : 5 - (i % 5),
      status: i % 3 === 0 ? 'OFF_SHELF' : 'ON_SALE',
      images: [],
      createdAt: new Date(Date.now() - i * 3600000).toISOString()
    };
  }

  function mockPage(params) {
    var page = Number(params && params.page) || 1;
    var size = Number(params && params.size) || 12;
    var total = 27;
    var items = [];
    var start = (page - 1) * size;
    for (var i = 1; i <= Math.min(size, Math.max(total - start, 0)); i++) {
      items.push(mockProduct(start + i));
    }
    return { page: page, size: size, total: total, items: items };
  }

  /* ============================================================
     接口清单
     ============================================================ */
  var api = {
    ApiError: ApiError,
    MOCK: MOCK,
    SORT_OPTIONS: SORT_OPTIONS,

    /* ---------- 客户（买家）会话 ---------- */
    getCustomerToken: getCustomerToken,
    getCustomerName: getCustomerName,
    setCustomerSession: setCustomerSession,
    isCustomerLogin: function () { return !!getCustomerToken(); },

    /* ---------- 卖家会话 ---------- */
    getSellerToken: getSellerToken,
    getSellerName: getSellerName,
    setSellerSession: setSellerSession,
    isSellerLogin: function () { return !!getSellerToken(); },

    isAuthError: function (e) { return !!e && e.status === 401; },

    /* 静态资源地址：后端可能返回文件名或相对路径 */
    uploadUrl: function (file) { return '/uploads/' + encodeURIComponent(file); },

    /* ==================== 客户域（李宇轩） ==================== */

    /* 1. 注册：{ username, password, phone, defaultTradePlace? } */
    register: function (body) {
      if (MOCK) return Promise.resolve({ id: 1, username: body.username, phone: body.phone, defaultTradePlace: body.defaultTradePlace || null, createdAt: new Date().toISOString() });
      return request(BUYER + '/register', { method: 'POST', body: body });
    },

    /* 2. 登录：{ username, password } → { token, expiresAt, customer } */
    customerLogin: function (username, password) {
      if (MOCK) return Promise.resolve({ token: 'mock-token', expiresAt: null, customer: { id: 1, username: username, phone: '13800000000', defaultTradePlace: '', createdAt: new Date().toISOString() } });
      return request(BUYER + '/login', { method: 'POST', body: { username: username, password: password } });
    },

    /* 3. 退出 */
    customerLogout: function () {
      if (MOCK) return Promise.resolve(null);
      return request(BUYER + '/logout', { method: 'POST', auth: 'customer' });
    },

    /* 4. 个人中心 */
    getMe: function () {
      if (MOCK) return Promise.resolve({ id: 1, username: 'buyer01', phone: '13800000000', defaultTradePlace: '图书馆门口', createdAt: new Date().toISOString() });
      return request(BUYER + '/me', { auth: 'customer' });
    },

    /* 5. 修改资料：{ phone, defaultTradePlace? }，传 "" 表示清空 */
    updateMe: function (body) {
      if (MOCK) return Promise.resolve(Object.assign({ id: 1, username: 'buyer01' }, body));
      return request(BUYER + '/me', { method: 'PUT', auth: 'customer', body: body });
    },

    /* 6. 下单：{ name, phone, productId } → { code, intentId }
       注：李宇轩文档 v1.1 请求体只有 name/phone，但迭代 2 是多商品，
       必须带 productId 才能知道买哪件。已同步李宇轩确认，此处先按带 productId 发送。 */
    createOrder: function (productId, body) {
      var payload = Object.assign({ productId: productId }, body || {});
      if (MOCK) return Promise.resolve({ code: 'MOCKCODE', intentId: 1 });
      return request(BUYER + '/orders', { method: 'POST', auth: 'customer', body: payload });
    },

    /* 7. 我的下单记录（仅成功 / 失败）→ { total, items[] } */
    getMyOrders: function () {
      if (MOCK) {
        return Promise.resolve({
          total: 2,
          items: [
            { intentId: 12, productId: 3, productName: '二手自行车', coverImage: '', submittedAt: new Date().toISOString(), result: 'SUCCESS', resultAt: new Date().toISOString() },
            { intentId: 8, productId: 2, productName: '台灯', coverImage: '', submittedAt: new Date(Date.now() - 86400000).toISOString(), result: 'FAIL', resultAt: new Date().toISOString() }
          ]
        });
      }
      return request(BUYER + '/orders', { auth: 'customer' });
    },

    /* ==================== 商品域（李婉婷 / 周琳皓） ==================== */

    /* 商品列表：{ page, size, sort, q, categoryId } → { page, size, total, items[] } */
    listProducts: function (params) {
      if (MOCK) return Promise.resolve(mockPage(params || {}));
      return request('/api/products', { params: params || {} });
    },

    /* 商品详情 */
    getProduct: function (id) {
      if (MOCK) return Promise.resolve(mockProduct(Number(id) || 1));
      return request('/api/products/' + encodeURIComponent(id));
    },

    /* 类别树：CategoryNode[] { id, name, children[] } */
    getCategories: function () {
      if (MOCK) return Promise.resolve(MOCK_CATEGORIES);
      return request('/api/categories');
    },

    /* ==================== 卖家端（李婉婷 / 华天遥） ==================== */

    sellerLogin: function (username, password) {
      return request('/api/seller/login', { method: 'POST', body: { username: username, password: password } });
    },

    sellerLogout: function () {
      return request('/api/seller/logout', { method: 'POST', auth: 'seller' });
    },

    /* 后台总览：迭代 2 改为「商品列表 + 选定商品的意向队列」 */
    getOverview: function () {
      return request('/api/seller/overview', { auth: 'seller' });
    },

    /* 发布商品：{ name, description, price, categoryId(必填二级), stock, images[] }
       images 为 Base64 data URI 数组，最多 9 张；image 仅在 images 为空时生效 */
    publishProduct: function (body) {
      return request('/api/seller/products', { method: 'POST', auth: 'seller', body: body });
    },

    /* 卖家商品列表 */
    listSellerProducts: function () {
      return request('/api/seller/products', { auth: 'seller' });
    },

    /* 标记交易结果：{ result: 'SUCCESS'|'FAIL', failedAction?: 'VOID'|'REQUEUE' }
       failedAction 仅 FAIL 时必填 */
    markResult: function (productId, result, failedAction) {
      var body = { result: result };
      if (failedAction) body.failedAction = failedAction;
      return request('/api/seller/products/' + encodeURIComponent(productId) + '/result', {
        method: 'POST', auth: 'seller', body: body
      });
    },

    /* 修改库存：{ stock } */
    updateStock: function (productId, stock) {
      return request('/api/seller/products/' + encodeURIComponent(productId) + '/stock', {
        method: 'PUT', auth: 'seller', body: { stock: stock }
      });
    },

    /* 重新上架（库存为 0 时后端拒绝） */
    reshelfProduct: function (productId) {
      return request('/api/seller/products/' + encodeURIComponent(productId) + '/reshelf', {
        method: 'POST', auth: 'seller'
      });
    },

    /* 类别管理 */
    listSellerCategories: function () {
      return request('/api/seller/categories', { auth: 'seller' });
    },
    createCategory: function (body) {
      return request('/api/seller/categories', { method: 'POST', auth: 'seller', body: body });
    },
    updateCategory: function (id, body) {
      return request('/api/seller/categories/' + encodeURIComponent(id), { method: 'PUT', auth: 'seller', body: body });
    },
    deleteCategory: function (id) {
      return request('/api/seller/categories/' + encodeURIComponent(id), { method: 'DELETE', auth: 'seller' });
    },

    /* 客户列表与购买历史 */
    listCustomers: function () {
      return request('/api/seller/customers', { auth: 'seller' });
    },
    getCustomerOrders: function (customerId) {
      return request('/api/seller/customers/' + encodeURIComponent(customerId) + '/orders', { auth: 'seller' });
    }
  };

  global.Api = api;
})(window);

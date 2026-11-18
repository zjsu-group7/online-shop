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

  /* 后端购物车/收藏条目把商品信息嵌在 product 字段（嵌套 ProductView），
     前端统一拍平为 productId/name/price/coverImage/status/stock 等扁平字段，供页面直接取用 */
  function flattenProductFields(item) {
    if (!item || !item.product) return item;
    var p = item.product;
    if (item.productId == null) item.productId = p.id;
    if (item.name == null) item.name = p.name;
    if (item.price == null) item.price = p.price;
    if (item.coverImage == null) item.coverImage = p.imageUrl || (p.images && p.images[0]);
    if (item.status == null) item.status = p.status;
    if (item.stock == null) item.stock = p.stock;
    return item;
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

    /* 发起交易：卖家从意向队列中选定买家，商品进入交易冻结
       入参 { intentId }；选定后商品状态变 FROZEN，需再标记交易结果 */
    startTrade: function (productId, intentId) {
      return request('/api/seller/products/' + encodeURIComponent(productId) + '/deal', {
        method: 'POST', auth: 'seller', body: { intentId: intentId }
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
    },

    /* ==================== 迭代 4（挑战需求 B）· 按后端最终契约实现 ====================
       购物车条目以 itemId 为操作主键；每条带 available / unavailableReason 标注 */

    /* 我的购物车 → { items: [CartItem], kinds, totalQuantity }
       CartItem: { itemId, productId, quantity, name, price, coverImage, available, unavailableReason } */
    getCart: function () {
      if (MOCK) return Promise.resolve({ items: MOCK_CART.map(function (c) { return Object.assign({}, c); }), kinds: MOCK_CART.length, totalQuantity: MOCK_CART.reduce(function (s, c) { return s + c.quantity; }, 0) });
      return request(BUYER + '/cart', { auth: 'customer' }).then(function (data) {
        if (data && data.items) data.items = data.items.map(flattenProductFields);
        return data;
      });
    },

    /* 加购：{ productId, quantity? } 同商品重复加入数量累加，缺省 1 */
    addToCart: function (productId, quantity) {
      if (MOCK) {
        var hit = MOCK_CART.find(function (c) { return c.productId === Number(productId); });
        if (hit) hit.quantity += (quantity || 1);
        else MOCK_CART.unshift({ itemId: MOCK_CART_SEQ++, productId: Number(productId), quantity: quantity || 1, name: '示例商品', price: 99, coverImage: '', available: true, unavailableReason: '' });
        return Promise.resolve({ productId: Number(productId), quantity: quantity || 1 });
      }
      return request(BUYER + '/cart', { method: 'POST', auth: 'customer', body: { productId: Number(productId), quantity: quantity || 1 } });
    },

    /* 改数量：PUT /cart/{itemId}，quantity >= 1（0 由后端 400，删除走删除接口） */
    updateCartItem: function (itemId, quantity) {
      if (MOCK) {
        var hit2 = MOCK_CART.find(function (c) { return c.itemId === Number(itemId); });
        if (hit2) hit2.quantity = Number(quantity);
        return Promise.resolve({ itemId: Number(itemId), quantity: Number(quantity) });
      }
      return request(BUYER + '/cart/' + encodeURIComponent(itemId), { method: 'PUT', auth: 'customer', body: { quantity: Number(quantity) } });
    },

    /* 删除单条（仅限本人条目） */
    removeCartItem: function (itemId) {
      if (MOCK) {
        MOCK_CART = MOCK_CART.filter(function (c) { return c.itemId !== Number(itemId); });
        return Promise.resolve(null);
      }
      return request(BUYER + '/cart/' + encodeURIComponent(itemId), { method: 'DELETE', auth: 'customer' });
    },

    /* 一键清空 */
    clearCart: function () {
      if (MOCK) { MOCK_CART = []; return Promise.resolve(null); }
      return request(BUYER + '/cart', { method: 'DELETE', auth: 'customer' });
    },

    /* 购物车多选转收藏（转移）：{ itemIds: [...] } → { transferred, skipped } */
    cartToFavorites: function (itemIds) {
      if (MOCK) {
        var ids = (itemIds || []).map(Number);
        var transferred = 0, skipped = [];
        ids.forEach(function (iid) {
          var c = MOCK_CART.find(function (x) { return x.itemId === iid; });
          if (!c) { skipped.push(iid); return; }
          if (!MOCK_FAV.some(function (f) { return f.productId === c.productId; })) {
            MOCK_FAV.unshift({ productId: c.productId, name: c.name, price: c.price, coverImage: c.coverImage, available: c.available, unavailableReason: c.unavailableReason });
          }
          MOCK_CART = MOCK_CART.filter(function (x) { return x.itemId !== iid; });
          transferred += 1;
        });
        return Promise.resolve({ transferred: transferred, skipped: skipped });
      }
      return request(BUYER + '/cart/to-favorites', { method: 'POST', auth: 'customer', body: { itemIds: (itemIds || []).map(Number) } });
    },

    /* ---------- 收藏（买家） ---------- */

    /* 我的收藏（最近收藏在前）→ { items: [FavoriteItem] } */
    getFavorites: function () {
      if (MOCK) return Promise.resolve({ items: MOCK_FAV.map(function (f) { return Object.assign({}, f); }) });
      return request(BUYER + '/favorites', { auth: 'customer' }).then(function (data) {
        if (data && data.items) data.items = data.items.map(flattenProductFields);
        return data;
      });
    },

    /* 收藏（幂等，重复收藏返回原条目） */
    addFavorite: function (productId) {
      if (MOCK) {
        if (!MOCK_FAV.some(function (f) { return f.productId === Number(productId); })) {
          MOCK_FAV.unshift({ productId: Number(productId), name: '示例商品', price: 99, coverImage: '', available: true, unavailableReason: '' });
        }
        return Promise.resolve(null);
      }
      return request(BUYER + '/favorites', { method: 'POST', auth: 'customer', body: { productId: Number(productId) } });
    },

    /* 取消收藏（未收藏时幂等成功） */
    removeFavorite: function (productId) {
      if (MOCK) {
        MOCK_FAV = MOCK_FAV.filter(function (f) { return f.productId !== Number(productId); });
        return Promise.resolve(null);
      }
      return request(BUYER + '/favorites/' + encodeURIComponent(productId), { method: 'DELETE', auth: 'customer' });
    },

    /* 是否已收藏 → { favorited } */
    isFavorite: function (productId) {
      if (MOCK) return Promise.resolve({ favorited: MOCK_FAV.some(function (f) { return f.productId === Number(productId); }) });
      return request(BUYER + '/favorites/' + encodeURIComponent(productId), { auth: 'customer' });
    },

    /* ---------- 订单（买家 · 周琳皓订单核心域最终契约，迭代 4） ----------
       POST /api/customer/purchase-orders { cartItemIds: [..] } → { order, rejected }
       GET  /api/customer/purchase-orders?page=&size=  → 本人订单分页（创建时间/ID 倒序）
       POST /api/customer/purchase-orders/{id}/cancel  → 已取消订单（id 为数字订单 id）
       状态：CUSTOMER_SUBMITTED → SELLER_CONFIRMED → READY_TO_SHIP → SHIPPED → COMPLETED，另有 CANCELLED */

    /* 购物车多选下单：入参是购物车条目 itemId 数组（cartItemIds，正是我们购物车的主键）
       → { order, rejected: [{ cartItemId?, productId?, name, reason, ... }] }（逐件校验，缺货不进订单、保留购物车） */
    submitOrder: function (cartItemIds) {
      if (MOCK) {
        var ids = (cartItemIds || []).map(Number);
        var order = { id: MOCK_ORDER_SEQ++, orderNo: 'MOCK' + Date.now(),
          status: 'CUSTOMER_SUBMITTED', statusText: '客户下单',
          totalAmount: 0, itemCount: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
          allowedActions: ['CONFIRM'], cancelReason: null, items: [] };
        var rejected = [];
        ids.forEach(function (iid) {
          var c = MOCK_CART.find(function (x) { return x.itemId === iid; });
          if (!c) { rejected.push({ cartItemId: iid, name: '未知商品', reason: '条目不存在' }); return; }
          if (c.available) {
            order.items.push({ productId: c.productId, productName: c.name, coverImage: c.coverImage,
              unitPrice: c.price, quantity: c.quantity, subtotal: c.price * c.quantity, available: true });
            order.totalAmount += c.price * c.quantity;
            MOCK_CART = MOCK_CART.filter(function (x) { return x.itemId !== iid; });
          } else {
            rejected.push({ cartItemId: iid, productId: c.productId, name: c.name, reason: c.unavailableReason || '缺货' });
          }
        });
        if (!order.items.length) {
          var err = ApiError(400, '所选商品均已缺货或下架，未生成订单');
          err.rejected = rejected;
          throw err;
        }
        order.itemCount = order.items.length;
        MOCK_ORDERS.unshift(order);
        return Promise.resolve({ order: order, rejected: rejected });
      }
      return request(BUYER + '/purchase-orders', {
        method: 'POST', auth: 'customer', body: { cartItemIds: (cartItemIds || []).map(Number) }
      });
    },

    /* 本人订单列表（创建时间 + ID 倒序，翻页）→ PageResult<OrderView>（李宇轩订单管理域，富视图） */
    listMyOrders: function (page, size) {
      if (MOCK) {
        var pg = Number(page) || 1, sz = Number(size) || 12;
        return Promise.resolve({ page: pg, size: sz, total: MOCK_ORDERS.length,
          items: MOCK_ORDERS.slice((pg - 1) * sz, pg * sz).map(function (o) { return Object.assign({}, o); }) });
      }
      return request('/api/orders', { auth: 'customer', params: { page: page || 1, size: size || 12 } });
    },

    /* 买家取消订单（传订单号 orderNo，开始发货后由后端拒绝） */
    cancelMyOrder: function (orderNo) {
      if (MOCK) {
        var o = MOCK_ORDERS.find(function (x) { return x.orderNo === String(orderNo); });
        if (!o) throw ApiError(404, '订单不存在');
        if (o.status === 'SHIPPED' || o.status === 'COMPLETED' || o.status === 'CANCELLED') throw ApiError(409, '订单已开始发货，无法取消');
        o.status = 'CANCELLED'; o.statusText = '已取消'; o.allowedActions = [];
        return Promise.resolve(Object.assign({}, o));
      }
      return request('/api/orders/' + encodeURIComponent(orderNo) + '/cancel', {
        method: 'POST', auth: 'customer'
      });
    },

    /* ---------- 卖家订单管理（迭代 4） ----------
       GET  /api/seller/orders?status=&page=&size=
       POST /api/seller/orders/{orderNo}/advance  { action: CONFIRM|PACK|SHIP|COMPLETE }
       POST /api/seller/orders/{orderNo}/cancel
       卖家侧由李宇轩接入 OrderService.advanceBySeller/cancelBySeller，只允许推进到下一状态 */

    /* 订单列表：status 可选 CUSTOMER_SUBMITTED/SELLER_CONFIRMED/READY_TO_SHIP/SHIPPED/COMPLETED/CANCELLED */
    listSellerOrders: function (page, size, status) {
      if (MOCK) {
        var pg2 = Number(page) || 1, sz2 = Number(size) || 10;
        var list = status ? MOCK_ORDERS.filter(function (o) { return o.status === status; }) : MOCK_ORDERS;
        return Promise.resolve({ page: pg2, size: sz2, total: list.length,
          items: list.slice((pg2 - 1) * sz2, pg2 * sz2).map(function (o) { return Object.assign({}, o); }) });
      }
      return request('/api/seller/orders', { auth: 'seller', params: { page: page || 1, size: size || 10, status: status || '' } });
    },

    /* 推进订单状态：action = CONFIRM / PACK / SHIP / COMPLETE（跳级或回退后端 409） */
    advanceOrder: function (orderNo, action) {
      if (MOCK) {
        var o2 = MOCK_ORDERS.find(function (x) { return x.orderNo === String(orderNo); });
        if (!o2) throw ApiError(404, '订单不存在');
        var MAP = {
          CONFIRM: ['SELLER_CONFIRMED', '商家确认', ['PACK']],
          PACK: ['READY_TO_SHIP', '备货完成', ['SHIP']],
          SHIP: ['SHIPPED', '开始发货', ['COMPLETE']],
          COMPLETE: ['COMPLETED', '交易完成', []]
        };
        var m = MAP[String(action || '').toUpperCase()];
        if (!m || (o2.allowedActions || []).indexOf(String(action).toUpperCase()) < 0) {
          throw ApiError(409, '当前状态不允许该操作（只允许推进到下一个状态）');
        }
        o2.status = m[0]; o2.statusText = m[1]; o2.allowedActions = m[2];
        return Promise.resolve(Object.assign({}, o2));
      }
      return request('/api/seller/orders/' + encodeURIComponent(orderNo) + '/advance', {
        method: 'POST', auth: 'seller', body: { action: String(action || '').toUpperCase() }
      });
    },

    /* 卖家取消订单（交易完成前均可） */
    cancelSellerOrder: function (orderNo) {
      if (MOCK) {
        var o3 = MOCK_ORDERS.find(function (x) { return x.orderNo === String(orderNo); });
        if (!o3) throw ApiError(404, '订单不存在');
        if (o3.status === 'COMPLETED' || o3.status === 'CANCELLED') throw ApiError(409, '订单已结束，无法取消');
        o3.status = 'CANCELLED'; o3.statusText = '已取消'; o3.allowedActions = [];
        return Promise.resolve(Object.assign({}, o3));
      }
      return request('/api/seller/orders/' + encodeURIComponent(orderNo) + '/cancel', { method: 'POST', auth: 'seller' });
    }
  };

  /* ---------- 迭代 4 MOCK 数据（联调后由 MOCK 开关控制） ---------- */
  var MOCK_CART_SEQ = 1;
  var MOCK_CART = [
    { itemId: MOCK_CART_SEQ++, productId: 1, quantity: 1, name: '示例商品 1', price: 99, coverImage: '', available: true, unavailableReason: '' },
    { itemId: MOCK_CART_SEQ++, productId: 2, quantity: 2, name: '示例商品 2', price: 149, coverImage: '', available: false, unavailableReason: '已下架' }
  ];
  var MOCK_FAV = [
    { productId: 3, name: '示例商品 3', price: 199, coverImage: '', available: true, unavailableReason: '' }
  ];
  var MOCK_ORDER_SEQ = 1;
  var MOCK_ORDERS = [];

  global.Api = api;
})(window);

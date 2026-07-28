(function () {
  'use strict';

  var KEY = 'mydealer_demo_v2';
  var API = window.DemoApi;
  var defaults = {
    users: [
      {id: 1, name: 'Мария Покупатель', email: 'buyer@example.test', role: 'buyer'},
      {id: 2, name: 'Green Farm', email: 'vendor@example.test', role: 'vendor'}
    ],
    session: null,
    activeOrderId: null
  };

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function load() {
    var stored;
    var result = clone(defaults);

    try {
      stored = JSON.parse(localStorage.getItem(KEY) || '{}');
      result = Object.assign(result, stored);
    } catch (loadError) {
      result = clone(defaults);
    }

    result.users = (result.users || []).map(function (account) {
      return {
        id: Number(account.id),
        name: account.name,
        email: account.email,
        role: account.role,
        status: account.status || 'active'
      };
    });

    if (!result.session || !result.session.token) {
      result.session = null;
    }

    result.activeOrderId = result.activeOrderId ? Number(result.activeOrderId) : null;
    return result;
  }

  var db = load();
  db.products = [];
  db.cart = {items: [], total: 0};
  db.orders = [];
  db.messages = [];

  var route = 'dashboard';
  var notice = '';
  var error = '';
  var busy = false;
  var marketReady = false;
  var recovery = {email: '', code: ''};

  function save() {
    localStorage.setItem(KEY, JSON.stringify({
      users: db.users,
      session: db.session,
      activeOrderId: db.activeOrderId
    }));
  }

  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>'"]/g, function (character) {
      return ({'&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'})[character];
    });
  }

  function user() {
    if (!db.session || !db.session.token) {
      return null;
    }

    return db.users.find(function (account) {
      return account.id === db.session.userId;
    }) || null;
  }

  function token() {
    return db.session ? db.session.token : '';
  }

  function upsertUser(apiUser) {
    var account = db.users.find(function (candidate) {
      return candidate.id === Number(apiUser.id) || candidate.email.toLowerCase() === String(apiUser.email).toLowerCase();
    });

    if (!account) {
      account = {};
      db.users.push(account);
    }

    account.id = Number(apiUser.id);
    account.name = apiUser.name;
    account.email = apiUser.email;
    account.role = apiUser.role;
    account.status = apiUser.status || 'active';
    return account;
  }

  function acceptAuthentication(payload) {
    var account = upsertUser(payload.user);
    db.session = {
      userId: account.id,
      token: payload.token,
      expiresAt: payload.expires_at || null
    };
    marketReady = false;
    save();
  }

  function clearSession() {
    db.session = null;
    db.products = [];
    db.cart = {items: [], total: 0};
    db.orders = [];
    db.messages = [];
    db.activeOrderId = null;
    marketReady = false;
    save();
  }

  function setMessage(successMessage, errorMessage) {
    notice = successMessage || '';
    error = errorMessage || '';
  }

  function apiErrorMessage(apiError) {
    var field;
    var first;

    if (!apiError) {
      return 'Не удалось выполнить запрос.';
    }
    if (apiError.code === 'AUTH_CREDENTIALS_INVALID') {
      return 'Неверный email или пароль.';
    }
    if (apiError.code === 'AUTH_TOKEN_INVALID' || apiError.code === 'AUTH_TOKEN_REQUIRED') {
      return 'Сессия завершена. Войдите заново.';
    }
    if (apiError.code === 'PASSWORD_RESET_CODE_INVALID') {
      return 'Код восстановления неверен или истёк.';
    }
    if (apiError.code === 'CART_EMPTY') {
      return 'Корзина пуста.';
    }
    if (apiError.code === 'ORDER_STATUS_INVALID') {
      return 'Этот переход статуса заказа недоступен.';
    }
    if (apiError.code === 'API_NETWORK_ERROR') {
      return 'Сервер временно недоступен.';
    }

    if (apiError.errors) {
      for (field in apiError.errors) {
        if (Object.prototype.hasOwnProperty.call(apiError.errors, field)) {
          first = apiError.errors[field];
          if (Array.isArray(first) && first.length) {
            if (String(first[0]).indexOf('already been taken') !== -1) {
              return 'Пользователь с таким email уже существует.';
            }
            return String(first[0]);
          }
        }
      }
    }

    return apiError.message || 'Не удалось выполнить запрос.';
  }

  function statusLabel(status) {
    return ({
      moderation: 'На модерации',
      published: 'Опубликован',
      new: 'Новый',
      confirmed: 'Подтверждён',
      completed: 'Завершён'
    })[status] || status;
  }

  function money(value) {
    return Number(value || 0).toFixed(2).replace('.00', '');
  }

  function go(nextRoute) {
    route = nextRoute;
    setMessage();
    render();
  }

  function loadOrderMessages(orderId, successMessage) {
    if (!orderId) {
      db.messages = [];
      busy = false;
      render();
      return;
    }

    busy = true;
    render();
    API.listOrderMessages(token(), orderId).then(function (payload) {
      db.messages = payload.messages || [];
      db.activeOrderId = Number(orderId);
      busy = false;
      setMessage(successMessage || '');
      save();
      render();
    }, function (apiError) {
      busy = false;
      setMessage('', apiErrorMessage(apiError));
      render();
    });
  }

  function refreshMarket(successMessage, nextRoute) {
    var account = user();
    var cartRequest;

    if (!account) {
      return;
    }

    busy = true;
    marketReady = false;
    if (nextRoute) {
      route = nextRoute;
    }
    render();

    cartRequest = account.role === 'buyer'
      ? API.getCart(token())
      : Promise.resolve({items: [], total: 0});

    Promise.all([
      API.listProducts(token()),
      cartRequest,
      API.listOrders(token())
    ]).then(function (payloads) {
      db.products = payloads[0].products || [];
      db.cart = {items: payloads[1].items || [], total: Number(payloads[1].total || 0)};
      db.orders = payloads[2].orders || [];

      if (!db.activeOrderId || !db.orders.some(function (order) { return Number(order.id) === Number(db.activeOrderId); })) {
        db.activeOrderId = db.orders.length ? Number(db.orders[0].id) : null;
      }

      marketReady = true;
      busy = false;
      setMessage(successMessage || '');
      save();
      render();

      if (route === 'chat' && db.activeOrderId) {
        loadOrderMessages(db.activeOrderId);
      }
    }, function (apiError) {
      busy = false;
      if (apiError.code === 'AUTH_TOKEN_INVALID' || apiError.code === 'AUTH_TOKEN_REQUIRED') {
        clearSession();
        route = 'auth-login';
      }
      setMessage('', apiErrorMessage(apiError));
      render();
    });
  }

  function auth(view) {
    var titles = {
      login: 'Вход в MyDealer',
      register: 'Регистрация',
      forgot: 'Восстановление пароля',
      reset: 'Введите код восстановления'
    };
    var submitLabels = {
      login: 'Войти',
      register: 'Создать аккаунт',
      forgot: 'Получить код',
      reset: 'Изменить пароль'
    };
    var fields = '';

    if (view === 'register') {
      fields += '<div class="field"><label>Имя / название</label><input name="name" required></div>';
      fields += '<div class="field"><label>Роль</label><select name="role"><option value="buyer">Покупатель</option><option value="vendor">Вендор</option></select></div>';
    }

    fields += '<div class="field"><label>Email</label><input type="email" name="email" value="' + esc(view === 'reset' ? recovery.email : '') + '" ' + (view === 'reset' ? 'readonly' : '') + ' required></div>';

    if (view === 'reset') {
      fields += '<div class="field"><label>Код из инструкции</label><input name="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" value="' + esc(recovery.code) + '" required></div>';
      fields += '<div class="field"><label>Новый пароль</label><input type="password" name="password" minlength="6" required></div>';
    } else if (view !== 'forgot') {
      fields += '<div class="field"><label>Пароль</label><input type="password" name="password" minlength="6" required></div>';
    }

    return '<div class="auth-wrap">' +
      '<section class="auth-panel">' +
        '<div class="brand">MyDealer <span>App</span></div>' +
        '<h1 class="auth-title">' + titles[view] + '</h1>' +
        '<p class="auth-sub">Авторизация, каталог, корзина, заказы и сообщения подключены к восстановленному Laravel API.</p>' +
        (notice ? '<div class="notice">' + esc(notice) + '</div>' : '') +
        (error ? '<div class="error">' + esc(error) + '</div>' : '') +
        '<form class="form" data-form="' + view + '">' + fields +
          '<button class="button" type="submit" ' + (busy ? 'disabled' : '') + '>' + (busy ? 'Подождите…' : submitLabels[view]) + '</button>' +
        '</form>' +
        '<div class="auth-links">' +
          (view !== 'login' ? '<button class="link" data-action="auth-login">Войти</button>' : '<button class="link" data-action="auth-register">Регистрация</button>') +
          (view !== 'forgot' && view !== 'reset' ? '<button class="link" data-action="auth-forgot">Забыли пароль?</button>' : '') +
        '</div>' +
        '<div class="demo-box"><b>API-демо:</b><br>Покупатель: buyer@example.test / demo123<br>Вендор: vendor@example.test / demo123</div>' +
      '</section>' +
      '<section class="auth-visual"><div><h1>Лучшие продукты без посредников</h1><p>Клубная платформа для покупателей и поставщиков качественных товаров.</p></div></section>' +
    '</div>';
  }

  function nav(account) {
    var items = account.role === 'buyer'
      ? [['dashboard', 'Обзор'], ['catalog', 'Каталог'], ['cart', 'Корзина'], ['orders', 'Заказы'], ['chat', 'Сообщения']]
      : [['dashboard', 'Обзор'], ['products', 'Мои товары'], ['add-product', 'Добавить товар'], ['vendor-orders', 'Заказы'], ['chat', 'Сообщения']];

    return items.map(function (item) {
      return '<button class="' + (route === item[0] ? 'active' : '') + '" data-route="' + item[0] + '">' + item[1] + '</button>';
    }).join('');
  }

  function shell(content) {
    var account = user();
    var alerts = (notice ? '<div class="notice">' + esc(notice) + '</div>' : '') + (error ? '<div class="error">' + esc(error) + '</div>' : '');
    return '<header class="topbar"><div class="brand">MyDealer <span>App</span> <span class="pill">MYSQL DEMO</span></div><div class="top-actions"><div class="role-switch"><button class="' + (account.role === 'buyer' ? 'active' : '') + '" data-action="switch-buyer">Покупатель</button><button class="' + (account.role === 'vendor' ? 'active' : '') + '" data-action="switch-vendor">Вендор</button></div><button class="button ghost" data-action="logout" ' + (busy ? 'disabled' : '') + '>Выйти</button></div></header><div class="layout"><aside class="sidebar"><nav class="nav">' + nav(account) + '</nav><div class="footer-note">Laravel 8 API.<br>MySQL 8 demo data.<br>Web-demo 0.7.0.</div></aside><main class="main">' + alerts + content + '</main></div><nav class="mobile-nav">' + nav(account) + '</nav>';
  }

  function dashboard(account) {
    var published = db.products.filter(function (product) { return product.status === 'published'; });
    return '<div class="page-head"><div><h1>Здравствуйте, ' + esc(account.name) + '</h1><p>' + (account.role === 'buyer' ? 'Откройте каталог отобранных продуктов.' : 'Управляйте товарами и заказами покупателей.') + '</p></div>' + (account.role === 'buyer' ? '<button class="button" data-route="catalog">Перейти в каталог</button>' : '<button class="button" data-route="add-product">Добавить товар</button>') + '</div><div class="grid grid-3"><div class="card"><div class="muted">' + (account.role === 'buyer' ? 'Товаров в каталоге' : 'Ваших товаров') + '</div><div class="stat">' + (account.role === 'buyer' ? published.length : db.products.length) + '</div></div><div class="card"><div class="muted">Заказы</div><div class="stat">' + db.orders.length + '</div></div><div class="card"><div class="muted">Корзина / чат</div><div class="stat">' + (account.role === 'buyer' ? db.cart.items.length : db.messages.length) + '</div></div></div><div style="margin-top:18px">' + (account.role === 'buyer' ? catalog(true) : products(true)) + '</div>';
  }

  function catalog(compact) {
    var availableProducts = db.products.filter(function (product) { return product.status === 'published'; });
    return '<div class="' + (compact ? '' : 'page-head') + '">' + (compact ? '<h2>Рекомендуемые товары</h2>' : '<div><h1>Каталог</h1><p>Опубликованные товары из MySQL.</p></div>') + '</div><div class="grid grid-4">' + availableProducts.map(function (product) {
      return '<article class="card product"><div class="product-media">' + esc(product.emoji || '🌿') + '</div><div class="product-body"><span class="pill gold">' + esc(product.category) + '</span><h3>' + esc(product.name) + '</h3><div class="muted">' + esc(product.vendorName || '') + ' · ' + esc(product.unit) + '</div><div class="price">€' + money(product.price) + '</div><button class="button" data-action="add-cart" data-id="' + product.id + '" ' + (busy ? 'disabled' : '') + '>В корзину</button></div></article>';
    }).join('') + (availableProducts.length ? '' : '<div class="card muted">Опубликованных товаров пока нет.</div>') + '</div>';
  }

  function cart() {
    var rows = db.cart.items || [];
    return '<div class="page-head"><div><h1>Корзина</h1><p>Корзина хранится в MySQL и доступна после перезагрузки.</p></div></div><div class="card">' +
      (rows.length
        ? rows.map(function (row) {
          return '<div class="cart-row"><div><b>' + esc(row.product.name) + '</b><div class="muted">' + esc(row.product.unit) + '</div></div><div>' + row.quantity + ' × €' + money(row.product.price) + '</div><button class="button ghost" data-action="remove-cart" data-id="' + row.productId + '" ' + (busy ? 'disabled' : '') + '>Удалить</button></div>';
        }).join('') + '<div style="display:flex;justify-content:space-between;align-items:center;margin-top:18px"><h2>Итого: €' + money(db.cart.total) + '</h2><button class="button" data-action="checkout" ' + (busy ? 'disabled' : '') + '>Оформить заказ</button></div>'
        : '<div class="muted" style="padding:30px;text-align:center">Корзина пуста</div>') + '</div>';
  }

  function orderButtons(order, vendorMode) {
    var buttons = '<button class="button ghost" data-action="open-chat" data-id="' + order.id + '">Чат</button>';
    if (vendorMode && order.status === 'new') {
      buttons += '<button class="button secondary" data-action="confirm-order" data-id="' + order.id + '">Подтвердить</button>';
    }
    if (vendorMode && order.status === 'confirmed') {
      buttons += '<button class="button secondary" data-action="complete-order" data-id="' + order.id + '">Завершить</button>';
    }
    return buttons;
  }

  function orderRows(vendorMode) {
    return db.orders.map(function (order) {
      var names = (order.items || []).map(function (item) { return esc(item.name) + ' × ' + item.quantity; }).join(', ');
      return '<div class="item"><div><h3>Заказ №' + order.id + '</h3><p>' + esc(vendorMode ? order.buyerName : order.vendorName) + ' · ' + esc(String(order.createdAt).substring(0, 10)) + ' · €' + money(order.total) + '</p><p class="muted">' + names + '</p></div><div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap"><span class="pill">' + esc(statusLabel(order.status)) + '</span>' + orderButtons(order, vendorMode) + '</div></div>';
    }).join('') || '<div class="muted" style="padding:30px;text-align:center">Заказов пока нет</div>';
  }

  function orders() {
    return '<div class="page-head"><div><h1>Мои заказы</h1><p>История и текущие статусы из MySQL.</p></div></div><div class="card"><div class="list">' + orderRows(false) + '</div></div>';
  }

  function products(compact) {
    return '<div class="' + (compact ? '' : 'page-head') + '">' + (compact ? '<h2>Ваши товары</h2>' : '<div><h1>Мои товары</h1><p>Карточки и статусы демонстрационной модерации.</p></div><button class="button" data-route="add-product">Добавить товар</button>') + '</div><div class="card"><div class="list">' + db.products.map(function (product) {
      var publish = product.status === 'moderation' ? '<button class="button secondary" data-action="publish-product" data-id="' + product.id + '" ' + (busy ? 'disabled' : '') + '>Одобрить демо-модерацию</button>' : '';
      return '<div class="item"><div><h3>' + esc(product.emoji || '🌿') + ' ' + esc(product.name) + '</h3><p>' + esc(product.category) + ' · €' + money(product.price) + ' / ' + esc(product.unit) + '</p></div><div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap"><span class="pill ' + (product.status === 'moderation' ? 'warn' : '') + '">' + esc(statusLabel(product.status)) + '</span>' + publish + '</div></div>';
    }).join('') + (db.products.length ? '' : '<div class="muted">Товаров пока нет.</div>') + '</div></div>';
  }

  function addProduct() {
    return '<div class="page-head"><div><h1>Новый товар</h1><p>Карточка будет сохранена в MySQL со статусом модерации.</p></div></div><div class="card" style="max-width:720px"><form class="form" data-form="add-product"><div class="field"><label>Название</label><input name="name" required></div><div class="field"><label>Категория</label><select name="category"><option>Деликатесы</option><option>Молочные продукты</option><option>Морепродукты</option><option>Выпечка</option><option>Овощи и фрукты</option></select></div><div class="field"><label>Цена, €</label><input type="number" min="0.01" step="0.01" name="price" value="12"></div><div class="field"><label>Единица</label><input name="unit" value="1 шт."></div><div class="field"><label>Описание</label><textarea name="description" rows="4" required></textarea></div><button class="button" ' + (busy ? 'disabled' : '') + '>Отправить на модерацию</button></form></div>';
  }

  function vendorOrders() {
    return '<div class="page-head"><div><h1>Заказы покупателей</h1><p>Подтверждение и завершение заказов через Laravel API.</p></div></div><div class="card"><div class="list">' + orderRows(true) + '</div></div>';
  }

  function chat() {
    var active = db.orders.find(function (order) { return Number(order.id) === Number(db.activeOrderId); });
    var orderTabs = db.orders.map(function (order) {
      return '<button class="button ' + (active && Number(active.id) === Number(order.id) ? '' : 'ghost') + '" data-action="open-chat" data-id="' + order.id + '">№' + order.id + '</button>';
    }).join('');

    if (!active) {
      return '<div class="page-head"><div><h1>Сообщения</h1><p>Для чата сначала нужен заказ.</p></div></div><div class="card muted">Доступных заказов нет.</div>';
    }

    return '<div class="page-head"><div><h1>Сообщения по заказу №' + active.id + '</h1><p>Диалог покупателя и вендора хранится в MySQL.</p></div></div><div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:14px">' + orderTabs + '</div><div class="card"><div class="chat">' + db.messages.map(function (message) {
      return '<div class="bubble ' + (user() && Number(message.authorId) === Number(user().id) ? 'me' : '') + '"><b>' + esc(message.authorName) + '</b><br>' + esc(message.text) + '<div class="muted" style="font-size:11px;margin-top:5px">' + esc(String(message.createdAt).substring(11, 16)) + '</div></div>';
    }).join('') + '</div><form class="chat-form" data-form="chat"><input name="text" required placeholder="Введите сообщение"><button class="button" ' + (busy ? 'disabled' : '') + '>Отправить</button></form></div>';
  }

  function app() {
    var account = user();
    var content;

    if (!account) {
      return auth(route.indexOf('auth-') === 0 ? route.substring(5) : 'login');
    }

    if (!marketReady) {
      return shell('<div class="card" style="padding:40px;text-align:center"><h2>Загрузка данных MyDealer…</h2><p class="muted">Каталог, корзина и заказы запрашиваются из Laravel API.</p></div>');
    }

    content = route === 'dashboard' ? dashboard(account)
      : route === 'catalog' ? catalog(false)
      : route === 'cart' ? cart()
      : route === 'orders' ? orders()
      : route === 'products' ? products(false)
      : route === 'add-product' ? addProduct()
      : route === 'vendor-orders' ? vendorOrders()
      : route === 'chat' ? chat()
      : dashboard(account);

    return shell(content);
  }

  function render() {
    document.getElementById('app').innerHTML = app();
  }

  function runApi(promise, onSuccess) {
    busy = true;
    render();

    promise.then(function (payload) {
      busy = false;
      onSuccess(payload);
    }, function (apiError) {
      busy = false;
      setMessage('', apiErrorMessage(apiError));
      render();
    });
  }

  function loginDemo(email) {
    setMessage();
    runApi(API.login(email, 'demo123'), function (payload) {
      acceptAuthentication(payload);
      route = 'dashboard';
      refreshMarket();
    });
  }

  document.addEventListener('click', function (event) {
    var routeElement = event.target.closest('[data-route]');
    var element;
    var action;
    var id;

    if (routeElement) {
      route = routeElement.dataset.route;
      setMessage();
      render();
      if (route === 'chat' && db.activeOrderId) {
        loadOrderMessages(db.activeOrderId);
      }
      return;
    }

    element = event.target.closest('[data-action]');
    if (!element) {
      return;
    }

    action = element.dataset.action;
    id = Number(element.dataset.id || 0);

    if (action === 'auth-login') {
      go('auth-login');
    } else if (action === 'auth-register') {
      go('auth-register');
    } else if (action === 'auth-forgot') {
      go('auth-forgot');
    } else if (action === 'logout') {
      if (busy) {
        return;
      }
      busy = true;
      render();
      API.logout(token()).then(function () {
        clearSession();
        busy = false;
        route = 'auth-login';
        setMessage('Вы вышли из аккаунта.');
        render();
      }, function () {
        clearSession();
        busy = false;
        route = 'auth-login';
        setMessage('Локальная сессия завершена.');
        render();
      });
    } else if (action === 'switch-buyer') {
      loginDemo('buyer@example.test');
    } else if (action === 'switch-vendor') {
      loginDemo('vendor@example.test');
    } else if (action === 'add-cart') {
      runApi(API.addCartItem(token(), id, 1), function (payload) {
        db.cart = {items: payload.items || [], total: Number(payload.total || 0)};
        setMessage('Товар добавлен в корзину.');
        render();
      });
    } else if (action === 'remove-cart') {
      runApi(API.removeCartItem(token(), id), function (payload) {
        db.cart = {items: payload.items || [], total: Number(payload.total || 0)};
        setMessage('Товар удалён из корзины.');
        render();
      });
    } else if (action === 'checkout') {
      runApi(API.checkout(token()), function () {
        refreshMarket('Заказ создан и сохранён в MySQL.', 'orders');
      });
    } else if (action === 'publish-product') {
      runApi(API.publishProduct(token(), id), function () {
        refreshMarket('Товар опубликован после демонстрационной модерации.', 'products');
      });
    } else if (action === 'confirm-order') {
      runApi(API.updateOrderStatus(token(), id, 'confirmed'), function () {
        refreshMarket('Заказ подтверждён.', 'vendor-orders');
      });
    } else if (action === 'complete-order') {
      runApi(API.updateOrderStatus(token(), id, 'completed'), function () {
        refreshMarket('Заказ завершён.', 'vendor-orders');
      });
    } else if (action === 'open-chat') {
      db.activeOrderId = id;
      route = 'chat';
      save();
      loadOrderMessages(id);
    }
  });

  document.addEventListener('submit', function (event) {
    var form = event.target;
    var data = new FormData(form);
    var formType = form.dataset.form;
    var email;

    event.preventDefault();
    if (busy) {
      return;
    }

    setMessage();

    if (formType === 'login') {
      runApi(API.login(String(data.get('email')).trim(), String(data.get('password'))), function (payload) {
        acceptAuthentication(payload);
        route = 'dashboard';
        refreshMarket();
      });
    } else if (formType === 'register') {
      runApi(API.register({
        name: String(data.get('name')).trim(),
        email: String(data.get('email')).trim(),
        password: String(data.get('password')),
        role: String(data.get('role'))
      }), function (payload) {
        acceptAuthentication(payload);
        route = 'dashboard';
        refreshMarket();
      });
    } else if (formType === 'forgot') {
      email = String(data.get('email')).trim();
      runApi(API.forgotPassword(email), function (payload) {
        recovery.email = email;
        recovery.code = payload.demo_reset_code || '';
        route = 'auth-reset';
        setMessage(payload.demo_reset_code ? 'Демо-код получен и подставлен в форму.' : 'Инструкция по восстановлению создана.');
        render();
      });
    } else if (formType === 'reset') {
      runApi(API.resetPassword(String(data.get('email')).trim(), String(data.get('code')).trim(), String(data.get('password'))), function () {
        recovery = {email: '', code: ''};
        clearSession();
        route = 'auth-login';
        setMessage('Пароль изменён. Войдите с новым паролем.');
        render();
      });
    } else if (formType === 'add-product') {
      runApi(API.createProduct(token(), {
        name: String(data.get('name')).trim(),
        category: String(data.get('category')).trim(),
        price: Number(data.get('price')),
        unit: String(data.get('unit')).trim(),
        emoji: '🌿',
        description: String(data.get('description')).trim()
      }), function () {
        refreshMarket('Товар создан и отправлен на модерацию.', 'products');
      });
    } else if (formType === 'chat') {
      runApi(API.sendOrderMessage(token(), db.activeOrderId, String(data.get('text')).trim()), function () {
        loadOrderMessages(db.activeOrderId, 'Сообщение отправлено.');
      });
    }
  });

  render();

  if (db.session && db.session.token) {
    API.me(db.session.token).then(function (payload) {
      upsertUser(payload.user);
      save();
      refreshMarket();
    }, function () {
      clearSession();
      route = 'auth-login';
      setMessage('', 'Сохранённая сессия завершена. Войдите заново.');
      render();
    });
  }
}());

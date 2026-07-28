(function () {
  'use strict';

  var KEY = 'helpify_demo_v2';
  var API = window.DemoApi;
  var defaults = {
    users: [
      {id: 1, name: 'Helpify Customer', email: 'customer@example.test', role: 'customer'},
      {id: 2, name: 'Helpify Contractor', email: 'contractor@example.test', role: 'contractor'}
    ],
    session: null
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

    return result;
  }

  var db = load();
  var tasks = [];
  var messages = [];
  var route = 'dashboard';
  var notice = '';
  var error = '';
  var busy = false;
  var workflowLoaded = false;
  var selectedTaskId = null;
  var recovery = {email: '', code: ''};

  function save() {
    localStorage.setItem(KEY, JSON.stringify({users: db.users, session: db.session}));
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
    workflowLoaded = false;
    tasks = [];
    messages = [];
    save();
  }

  function clearSession() {
    db.session = null;
    workflowLoaded = false;
    tasks = [];
    messages = [];
    selectedTaskId = null;
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
    if (apiError.code === 'OFFER_ALREADY_EXISTS') {
      return 'Вы уже отправили предложение по этой задаче.';
    }
    if (apiError.code === 'TASK_NOT_OPEN') {
      return 'Задача больше не принимает предложения.';
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

  function button(label, action, className, extra) {
    return '<button class="button ' + (className || '') + '" data-action="' + action + '" ' + (extra || '') + '>' + label + '</button>';
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

  function refreshTasks(successMessage) {
    if (!db.session) {
      return;
    }

    busy = true;
    render();
    API.listTasks(db.session.token).then(function (payload) {
      tasks = payload.tasks || [];
      workflowLoaded = true;
      busy = false;
      if (successMessage) {
        setMessage(successMessage, '');
      }
      render();
    }, function (apiError) {
      busy = false;
      workflowLoaded = true;
      if (apiError.code === 'AUTH_TOKEN_INVALID' || apiError.code === 'AUTH_TOKEN_REQUIRED') {
        clearSession();
        route = 'auth-login';
      }
      setMessage('', apiErrorMessage(apiError));
      render();
    });
  }

  function accessibleChatTask(account) {
    var preferred = tasks.find(function (task) { return task.id === selectedTaskId; });
    if (preferred) {
      return preferred;
    }

    if (account.role === 'customer') {
      return tasks.find(function (task) { return task.offers && task.offers.length; }) || tasks[0] || null;
    }

    return tasks.find(function (task) {
      return (task.offers || []).some(function (offer) { return offer.contractorId === account.id; });
    }) || null;
  }

  function loadMessages(taskId) {
    if (!db.session || !taskId) {
      messages = [];
      render();
      return;
    }

    selectedTaskId = Number(taskId);
    busy = true;
    render();
    API.listMessages(db.session.token, selectedTaskId).then(function (payload) {
      messages = payload.messages || [];
      busy = false;
      render();
    }, function (apiError) {
      messages = [];
      busy = false;
      setMessage('', apiErrorMessage(apiError));
      render();
    });
  }

  function go(nextRoute) {
    var account;
    var task;

    route = nextRoute;
    setMessage();
    account = user();

    if (route === 'chat' && account) {
      task = accessibleChatTask(account);
      if (task) {
        loadMessages(task.id);
        return;
      }
    }

    render();
  }

  function auth(view) {
    var titles = {
      login: 'Вход в Helpify',
      register: 'Создать аккаунт',
      forgot: 'Восстановление пароля',
      reset: 'Введите код восстановления'
    };
    var submitLabels = {
      login: 'Войти',
      register: 'Зарегистрироваться',
      forgot: 'Получить код',
      reset: 'Изменить пароль'
    };
    var fields = '';

    if (view === 'register') {
      fields += '<div class="field"><label>Имя</label><input name="name" required></div>';
      fields += '<div class="field"><label>Роль</label><select name="role"><option value="customer">Заказчик</option><option value="contractor">Исполнитель</option></select></div>';
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
        '<div class="brand"><span class="brand-badge">H</span>Helpify</div>' +
        '<h1 class="auth-title">' + titles[view] + '</h1>' +
        '<p class="auth-sub">Авторизация, задачи, предложения и сообщения работают через восстановленный Laravel API.</p>' +
        (notice ? '<div class="notice">' + esc(notice) + '</div>' : '') +
        (error ? '<div class="error">' + esc(error) + '</div>' : '') +
        '<form class="form" data-form="' + view + '">' + fields +
          '<button class="button" type="submit" ' + (busy ? 'disabled' : '') + '>' + (busy ? 'Подождите…' : submitLabels[view]) + '</button>' +
        '</form>' +
        '<div class="auth-links">' +
          (view !== 'login' ? '<button class="link" data-action="auth-login">Войти</button>' : '<button class="link" data-action="auth-register">Регистрация</button>') +
          (view !== 'forgot' && view !== 'reset' ? '<button class="link" data-action="auth-forgot">Забыли пароль?</button>' : '') +
        '</div>' +
        '<div class="demo-box"><b>API-демо:</b><br>Заказчик: customer@example.test / demo123<br>Исполнитель: contractor@example.test / demo123</div>' +
      '</section>' +
      '<section class="auth-visual"><div><h1>Решение само найдёт вас</h1><p>Создайте задачу, получите предложения от специалистов, выберите исполнителя и продолжите общение в чате.</p></div></section>' +
    '</div>';
  }

  function nav(account) {
    var items = account.role === 'customer'
      ? [['dashboard', 'Обзор'], ['tasks', 'Мои задачи'], ['create', 'Создать задачу'], ['chat', 'Сообщения']]
      : [['dashboard', 'Обзор'], ['available', 'Доступные заказы'], ['active', 'Мои заказы'], ['chat', 'Сообщения']];

    return items.map(function (item) {
      return '<button class="' + (route === item[0] ? 'active' : '') + '" data-route="' + item[0] + '">' + item[1] + '</button>';
    }).join('');
  }

  function shell(content) {
    var account = user();
    return '<div class="shell">' +
      '<header class="topbar">' +
        '<div class="brand"><span class="brand-badge">H</span>Helpify <span class="pill">LIVE DEMO</span></div>' +
        '<div class="top-actions">' +
          '<div class="role-switch">' +
            '<button class="' + (account.role === 'customer' ? 'active' : '') + '" data-action="switch-customer" ' + (busy ? 'disabled' : '') + '>Заказчик</button>' +
            '<button class="' + (account.role === 'contractor' ? 'active' : '') + '" data-action="switch-contractor" ' + (busy ? 'disabled' : '') + '>Исполнитель</button>' +
          '</div>' +
          '<button class="button ghost" data-action="logout" ' + (busy ? 'disabled' : '') + '>Выйти</button>' +
        '</div>' +
      '</header>' +
      '<div class="layout"><aside class="sidebar"><nav class="nav">' + nav(account) + '</nav><div class="footer-note">Авторизация и сценарии: Laravel API + MySQL.<br>Данные: тестовые.<br>Web-demo 0.6.0.</div></aside><main class="main">' + content + '</main></div>' +
      '<nav class="mobile-nav">' + nav(account) + '</nav>' +
    '</div>';
  }

  function statusLabel(task) {
    if (task.status === 'completed') {
      return 'Завершена';
    }
    if (task.status === 'assigned') {
      return 'Исполнитель выбран';
    }
    return 'Открыта';
  }

  function dashboard(account) {
    var own = tasks.filter(function (task) {
      return account.role === 'customer'
        ? task.customerId === account.id
        : (task.offers || []).some(function (offer) { return offer.contractorId === account.id; });
    });
    var open = tasks.filter(function (task) { return task.status === 'open'; });

    return '<div class="page-head"><div><h1>Здравствуйте, ' + esc(account.name) + '</h1><p>' +
      (account.role === 'customer' ? 'Управляйте задачами и предложениями специалистов.' : 'Находите новые заказы и отправляйте предложения.') +
      '</p></div>' + (account.role === 'customer' ? button('Создать задачу', 'create-task') : '') + '</div>' +
      (notice ? '<div class="notice">' + esc(notice) + '</div>' : '') +
      (error ? '<div class="error">' + esc(error) + '</div>' : '') +
      '<div class="grid grid-3"><div class="card"><div class="muted">' +
      (account.role === 'customer' ? 'Ваши задачи' : 'Доступные задачи') + '</div><div class="stat">' +
      (account.role === 'customer' ? own.length : open.length) + '</div></div>' +
      '<div class="card"><div class="muted">Активные</div><div class="stat">' + own.filter(function (task) { return task.status === 'assigned'; }).length + '</div></div>' +
      '<div class="card"><div class="muted">Завершённые</div><div class="stat">' + own.filter(function (task) { return task.status === 'completed'; }).length + '</div></div></div>' +
      '<div class="card" style="margin-top:18px"><h2>Последние задачи</h2>' + taskList((account.role === 'customer' ? own : open).slice(0, 4), account) + '</div>';
  }

  function taskList(items, account) {
    if (!items.length) {
      return '<div class="empty">Пока ничего нет</div>';
    }

    return '<div class="list">' + items.map(function (task) {
      var offered = (task.offers || []).some(function (offer) { return offer.contractorId === account.id; });
      return '<div class="item"><div><span class="pill ' + (task.status !== 'open' ? 'ok' : '') + '">' + statusLabel(task) + '</span><h3>' + esc(task.title) + '</h3><p>' +
        esc(task.category) + ' · ' + esc(task.address) + ' · бюджет €' + esc(task.budget) + '</p></div><div class="item-actions">' +
        '<button class="button secondary" data-action="task" data-id="' + task.id + '">Открыть</button>' +
        (account.role === 'contractor' && task.status === 'open' && !offered
          ? '<button class="button" data-action="offer" data-id="' + task.id + '">Откликнуться</button>'
          : '') +
        (account.role === 'contractor' && offered ? '<span class="pill ok">Отклик отправлен</span>' : '') +
        '</div></div>';
    }).join('') + '</div>';
  }

  function tasksPage(account) {
    return '<div class="page-head"><div><h1>Мои задачи</h1><p>Созданные заявки и полученные предложения.</p></div>' + button('Новая задача', 'create-task') + '</div>' +
      (notice ? '<div class="notice">' + esc(notice) + '</div>' : '') +
      (error ? '<div class="error">' + esc(error) + '</div>' : '') +
      '<div class="card">' + taskList(tasks, account) + '</div>';
  }

  function createPage() {
    return '<div class="page-head"><div><h1>Новая задача</h1><p>Опишите, что необходимо сделать.</p></div></div>' +
      (error ? '<div class="error">' + esc(error) + '</div>' : '') +
      '<div class="card" style="max-width:720px"><form class="form" data-form="create-task"><div class="field"><label>Название задачи</label><input name="title" required placeholder="Например: собрать шкаф"></div><div class="field"><label>Категория</label><select name="category"><option>Сантехник</option><option>Электрик</option><option>Строитель</option><option>Уборщик</option><option>Грузчик</option><option>Плотник</option></select></div><div class="field"><label>Адрес</label><input name="address" required value="Рига, центр"></div><div class="field"><label>Описание</label><textarea name="description" rows="4" required></textarea></div><div class="field"><label>Ориентировочный бюджет, €</label><input name="budget" type="number" min="1" value="50"></div><button class="button" type="submit" ' + (busy ? 'disabled' : '') + '>Опубликовать задачу</button></form></div>';
  }

  function available(account) {
    return '<div class="page-head"><div><h1>Доступные заказы</h1><p>Новые задачи рядом с вами.</p></div></div>' +
      (notice ? '<div class="notice">' + esc(notice) + '</div>' : '') +
      (error ? '<div class="error">' + esc(error) + '</div>' : '') +
      '<div class="card">' + taskList(tasks.filter(function (task) { return task.status === 'open'; }), account) + '</div>';
  }

  function active(account) {
    var activeTasks = tasks.filter(function (task) {
      return (task.offers || []).some(function (offer) { return offer.contractorId === account.id; });
    });
    return '<div class="page-head"><div><h1>Мои заказы</h1><p>Отправленные предложения и активные работы.</p></div></div>' +
      (notice ? '<div class="notice">' + esc(notice) + '</div>' : '') +
      (error ? '<div class="error">' + esc(error) + '</div>' : '') +
      '<div class="card">' + taskList(activeTasks, account) + '</div>';
  }

  function taskPage(account, id) {
    var task = tasks.find(function (candidate) { return candidate.id === id; });
    var offers;
    var offered;

    if (!task) {
      return '<div class="empty">Задача не найдена или недоступна.</div>';
    }

    offers = task.offers || [];
    offered = offers.some(function (offer) { return offer.contractorId === account.id; });

    return '<div class="page-head"><div><h1>' + esc(task.title) + '</h1><p>' + esc(task.category) + ' · ' + esc(task.address) + '</p></div><button class="button ghost" data-route="' + (account.role === 'customer' ? 'tasks' : 'available') + '">Назад</button></div>' +
      (notice ? '<div class="notice">' + esc(notice) + '</div>' : '') +
      (error ? '<div class="error">' + esc(error) + '</div>' : '') +
      '<div class="grid grid-2"><div class="card"><h2>Описание</h2><p>' + esc(task.description) + '</p><p><b>Бюджет:</b> €' + esc(task.budget) + '</p><p><b>Статус:</b> ' + statusLabel(task) + '</p>' +
      (account.role === 'contractor' && task.status === 'open' && !offered ? '<button class="button" data-action="offer" data-id="' + task.id + '">Отправить предложение</button>' : '') +
      (offered || account.role === 'customer' ? '<button class="button secondary" data-action="chat-task" data-id="' + task.id + '">Открыть чат</button>' : '') +
      (task.status === 'assigned' ? '<button class="button ghost" data-action="complete-task" data-id="' + task.id + '">Завершить задачу</button>' : '') +
      '</div><div class="card"><h2>Предложения</h2>' +
      (offers.length
        ? '<div class="list">' + offers.map(function (offer) {
          return '<div class="item"><div><h3>' + esc(offer.name) + '</h3><p>Рейтинг ' + offer.rating + ' · ' + esc(offer.distance) + ' · €' + offer.price + '</p></div>' +
            (account.role === 'customer' && task.status === 'open' ? '<button class="button" data-action="choose-offer" data-task="' + task.id + '" data-offer="' + offer.id + '">Выбрать</button>' : '<span class="pill ' + (offer.status === 'selected' ? 'ok' : '') + '">' + (offer.status === 'selected' ? 'Выбран' : 'Отправлен') + '</span>') + '</div>';
        }).join('') + '</div>'
        : '<div class="empty">Предложений пока нет</div>') + '</div></div>';
  }

  function chatPage(account) {
    var task = accessibleChatTask(account);

    if (!task) {
      return '<div class="page-head"><div><h1>Сообщения</h1><p>Чат становится доступен после первого предложения.</p></div></div><div class="card"><div class="empty">Нет доступных диалогов</div></div>';
    }

    return '<div class="page-head"><div><h1>Сообщения</h1><p>' + esc(task.title) + '</p></div><button class="button ghost" data-action="reload-chat" data-id="' + task.id + '">Обновить</button></div>' +
      (notice ? '<div class="notice">' + esc(notice) + '</div>' : '') +
      (error ? '<div class="error">' + esc(error) + '</div>' : '') +
      '<div class="card"><div class="chat">' + (messages.length ? messages.map(function (message) {
        return '<div class="bubble ' + (message.authorId === account.id ? 'me' : '') + '">' + esc(message.text) + '<div class="muted" style="font-size:11px;margin-top:5px">' + esc(message.authorName || '') + ' · ' + esc(String(message.createdAt || '').substring(11, 16)) + '</div></div>';
      }).join('') : '<div class="empty">Сообщений пока нет</div>') + '</div><form class="chat-form" data-form="chat"><input name="text" required placeholder="Введите сообщение"><button class="button" ' + (busy ? 'disabled' : '') + '>Отправить</button></form></div>';
  }

  function app() {
    var account = user();
    var content = '';

    if (!account) {
      return auth(route.indexOf('auth-') === 0 ? route.substring(5) : 'login');
    }

    if (!workflowLoaded) {
      return shell('<div class="card"><div class="empty">Загрузка данных с сервера…</div></div>');
    }

    if (route === 'dashboard') {
      content = dashboard(account);
    } else if (route === 'tasks') {
      content = tasksPage(account);
    } else if (route === 'create') {
      content = createPage();
    } else if (route === 'available') {
      content = available(account);
    } else if (route === 'active') {
      content = active(account);
    } else if (route === 'chat') {
      content = chatPage(account);
    } else if (route.indexOf('task-') === 0) {
      content = taskPage(account, Number(route.split('-')[1]));
    } else {
      content = dashboard(account);
    }

    return shell(content);
  }

  function render() {
    document.getElementById('app').innerHTML = app();
  }

  function loginDemo(email) {
    setMessage();
    runApi(API.login(email, 'demo123'), function (payload) {
      acceptAuthentication(payload);
      route = 'dashboard';
      refreshTasks();
    });
  }

  function handleAction(element) {
    var action = element.dataset.action;
    var task;
    var account;
    var price;

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
      account = db.session;
      busy = true;
      render();
      API.logout(account.token).then(function () {
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
    } else if (action === 'switch-customer') {
      loginDemo('customer@example.test');
    } else if (action === 'switch-contractor') {
      loginDemo('contractor@example.test');
    } else if (action === 'create-task') {
      go('create');
    } else if (action === 'task') {
      go('task-' + element.dataset.id);
    } else if (action === 'offer') {
      task = tasks.find(function (candidate) { return candidate.id === Number(element.dataset.id); });
      price = task ? Math.max(20, Number(task.budget) - 5) : 40;
      runApi(API.createOffer(db.session.token, Number(element.dataset.id), {price: price, distance: '2,0 км'}), function () {
        refreshTasks('Предложение отправлено.');
      });
    } else if (action === 'choose-offer') {
      runApi(API.selectOffer(db.session.token, Number(element.dataset.task), Number(element.dataset.offer)), function () {
        refreshTasks('Исполнитель выбран.');
      });
    } else if (action === 'complete-task') {
      runApi(API.updateTaskStatus(db.session.token, Number(element.dataset.id), 'completed'), function () {
        refreshTasks('Задача завершена.');
      });
    } else if (action === 'chat-task' || action === 'reload-chat') {
      route = 'chat';
      loadMessages(Number(element.dataset.id));
    }
  }

  document.addEventListener('click', function (event) {
    var routeElement = event.target.closest('[data-route]');
    var actionElement;

    if (routeElement) {
      go(routeElement.dataset.route);
      return;
    }

    actionElement = event.target.closest('[data-action]');
    if (actionElement) {
      handleAction(actionElement);
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
        refreshTasks();
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
        refreshTasks();
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
    } else if (formType === 'create-task') {
      runApi(API.createTask(db.session.token, {
        title: String(data.get('title')).trim(),
        category: String(data.get('category')).trim(),
        address: String(data.get('address')).trim(),
        description: String(data.get('description')).trim(),
        budget: Number(data.get('budget'))
      }), function () {
        route = 'tasks';
        refreshTasks('Задача опубликована.');
      });
    } else if (formType === 'chat') {
      if (!selectedTaskId) {
        setMessage('', 'Не выбран диалог.');
        render();
        return;
      }
      runApi(API.sendMessage(db.session.token, selectedTaskId, String(data.get('text')).trim()), function () {
        setMessage('Сообщение отправлено.');
        loadMessages(selectedTaskId);
      });
    }
  });

  render();

  if (db.session && db.session.token) {
    API.me(db.session.token).then(function (payload) {
      upsertUser(payload.user);
      save();
      refreshTasks();
    }, function () {
      clearSession();
      route = 'auth-login';
      setMessage('', 'Сохранённая сессия завершена. Войдите заново.');
      render();
    });
  }
}());

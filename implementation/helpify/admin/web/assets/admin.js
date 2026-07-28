(function(){
  'use strict';

  var config=window.AdminConfig;
  var API=window.AdminApi;
  var root=document.getElementById('admin-app');
  var storageKey=config.product+'_admin_session_v1';
  var state={session:null,route:'dashboard',busy:false,notice:'',error:'',data:null};

  document.documentElement.style.setProperty('--accent',config.accent);

  function esc(value){
    return String(value==null?'':value).replace(/[&<>'"]/g,function(character){
      return ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'})[character];
    });
  }

  function save(){
    if(state.session){localStorage.setItem(storageKey,JSON.stringify(state.session));}
    else{localStorage.removeItem(storageKey);}
  }

  function loadSession(){
    try{state.session=JSON.parse(localStorage.getItem(storageKey)||'null');}
    catch(ignore){state.session=null;}
  }

  function setMessage(notice,error){state.notice=notice||'';state.error=error||'';}
  function errorText(error){
    if(error && error.payload && error.payload.code==='ADMIN_ACCESS_REQUIRED'){return 'У этой учётной записи нет прав администратора.';}
    if(error && error.payload && error.payload.code==='AUTH_CREDENTIALS_INVALID'){return 'Неверный email или пароль.';}
    if(error && error.payload && error.payload.message){return error.payload.message;}
    return 'Запрос не выполнен. Проверьте API и повторите попытку.';
  }

  function statusLabel(status){
    return ({active:'Активен',blocked:'Заблокирован',open:'Открыта',assigned:'Исполнитель выбран',completed:'Завершено',cancelled:'Отменено',moderation:'На модерации',published:'Опубликован',rejected:'Отклонён',new:'Новый',confirmed:'Подтверждён'})[status]||status;
  }

  function money(value){return Number(value||0).toFixed(2).replace('.',',');}

  function loginView(){
    root.innerHTML='<div class="login-page"><section class="login-card"><div class="logo">'+esc(config.name)+' <span>Admin</span></div><h1>Панель управления</h1><p class="muted">Демонстрационная административная консоль разработки.</p>'+
      (state.error?'<div class="error">'+esc(state.error)+'</div>':'')+
      '<form class="form" data-form="login"><div class="field"><label>Email</label><input type="email" name="email" value="admin@example.test" required autocomplete="username"></div><div class="field"><label>Пароль</label><input type="password" name="password" value="demo123" required autocomplete="current-password"></div><button class="button" '+(state.busy?'disabled':'')+'>Войти</button></form><div class="hint"><b>тестовый доступ</b><br>admin@example.test / demo123</div></section></div>';
  }

  function navigation(){
    var items=[['dashboard','Обзор'],['users','Пользователи']];
    if(config.product==='helpify'){items.push(['tasks','Задачи']);}
    else{items.push(['products','Товары'],['orders','Заказы']);}
    return items.map(function(item){return '<button class="'+(state.route===item[0]?'active':'')+'" data-route="'+item[0]+'">'+item[1]+'</button>';}).join('');
  }

  function shell(content){
    var alerts=(state.notice?'<div class="notice">'+esc(state.notice)+'</div>':'')+(state.error?'<div class="error">'+esc(state.error)+'</div>':'');
    return '<header class="topbar"><div class="logo">'+esc(config.name)+' <span>Admin</span></div><div class="topbar-actions"><span class="muted admin-email">'+esc(state.session.user.email)+'</span><button class="button secondary" data-action="logout">Выйти</button></div></header><div class="admin-layout"><aside class="sidebar"><nav class="nav">'+navigation()+'</nav><div class="sidebar-note">Laravel 8 / MySQL 8<br>Administrative demo 0.8.0</div></aside><main class="main">'+alerts+content+'</main></div><nav class="mobile-nav">'+navigation()+'</nav>';
  }

  function statCards(stats){
    var labels=config.product==='helpify'
      ? [['users_total','Пользователи'],['tasks_total','Задачи'],['tasks_open','Открытые'],['messages_total','Сообщения']]
      : [['users_total','Пользователи'],['products_total','Товары'],['products_moderation','На модерации'],['orders_active','Активные заказы']];
    return '<div class="grid stats">'+labels.map(function(item){return '<div class="card"><div class="muted">'+item[1]+'</div><div class="stat-value">'+esc(stats[item[0]]||0)+'</div></div>';}).join('')+'</div>';
  }

  function dashboardView(payload){
    var recent='';
    if(config.product==='helpify'){
      recent='<h2>Последние задачи</h2>'+taskTable(payload.recent_tasks||[],true);
    }else{
      recent='<h2>Последние товары</h2>'+productTable(payload.recent_products||[],true)+'<h2 style="margin-top:24px">Последние заказы</h2>'+orderTable(payload.recent_orders||[],true);
    }
    return '<div class="page-head"><div><h1>Обзор системы</h1><p class="muted">тестовый операционные данные из MySQL.</p></div><button class="button secondary" data-action="refresh">Обновить</button></div>'+statCards(payload.statistics||{})+'<div style="margin-top:26px">'+recent+'</div>';
  }

  function usersTable(users){
    if(!users.length){return '<div class="card empty">Пользователи не найдены.</div>';}
    return '<div class="table-wrap"><table class="table"><thead><tr><th>ID</th><th>Пользователь</th><th>Роль</th><th>Статус</th><th>Действие</th></tr></thead><tbody>'+users.map(function(user){
      var next=user.status==='active'?'blocked':'active';
      var action=user.role==='admin'?'—':'<button class="button small '+(next==='blocked'?'danger':'success')+'" data-action="user-status" data-id="'+user.id+'" data-status="'+next+'">'+(next==='blocked'?'Заблокировать':'Активировать')+'</button>';
      return '<tr><td>'+user.id+'</td><td><b>'+esc(user.name)+'</b><br><span class="muted">'+esc(user.email)+'</span></td><td>'+esc(user.role)+'</td><td><span class="pill '+esc(user.status)+'">'+esc(statusLabel(user.status))+'</span></td><td>'+action+'</td></tr>';
    }).join('')+'</tbody></table></div>';
  }

  function taskTable(tasks,compact){
    if(!tasks.length){return '<div class="card empty">Задачи не найдены.</div>';}
    return '<div class="table-wrap"><table class="table"><thead><tr><th>ID</th><th>Задача</th><th>Заказчик</th><th>Бюджет</th><th>Статус</th>'+(compact?'':'<th>Действия</th>')+'</tr></thead><tbody>'+tasks.map(function(task){
      var actions=compact?'':'<div class="actions"><button class="button small secondary" data-action="task-status" data-id="'+task.id+'" data-status="open">Открыть</button><button class="button small success" data-action="task-status" data-id="'+task.id+'" data-status="completed">Завершить</button><button class="button small danger" data-action="task-status" data-id="'+task.id+'" data-status="cancelled">Отменить</button></div>';
      return '<tr><td>'+task.id+'</td><td><b>'+esc(task.title)+'</b><br><span class="muted">'+esc(task.category)+' · '+esc(task.address)+'</span></td><td>'+esc(task.customer_name)+'</td><td>€'+money(task.budget)+'</td><td><span class="pill '+esc(task.status)+'">'+esc(statusLabel(task.status))+'</span></td>'+(compact?'':'<td>'+actions+'</td>')+'</tr>';
    }).join('')+'</tbody></table></div>';
  }

  function productTable(products,compact){
    if(!products.length){return '<div class="card empty">Товары не найдены.</div>';}
    return '<div class="table-wrap"><table class="table"><thead><tr><th>ID</th><th>Товар</th><th>Вендор</th><th>Цена</th><th>Статус</th>'+(compact?'':'<th>Модерация</th>')+'</tr></thead><tbody>'+products.map(function(product){
      var actions=compact?'':'<div class="actions"><button class="button small success" data-action="product-status" data-id="'+product.id+'" data-status="published">Опубликовать</button><button class="button small danger" data-action="product-status" data-id="'+product.id+'" data-status="rejected">Отклонить</button><button class="button small secondary" data-action="product-status" data-id="'+product.id+'" data-status="moderation">На проверку</button></div>';
      return '<tr><td>'+product.id+'</td><td><b>'+esc(product.emoji)+' '+esc(product.name)+'</b><br><span class="muted">'+esc(product.category)+'</span></td><td>'+esc(product.vendor_name)+'</td><td>€'+money(product.price)+'</td><td><span class="pill '+esc(product.status)+'">'+esc(statusLabel(product.status))+'</span></td>'+(compact?'':'<td>'+actions+'</td>')+'</tr>';
    }).join('')+'</tbody></table></div>';
  }

  function orderTable(orders,compact){
    if(!orders.length){return '<div class="card empty">Заказы не найдены.</div>';}
    return '<div class="table-wrap"><table class="table"><thead><tr><th>ID</th><th>Покупатель</th><th>Вендор</th><th>Сумма</th><th>Статус</th>'+(compact?'':'<th>Действия</th>')+'</tr></thead><tbody>'+orders.map(function(order){
      var actions=compact?'':'<div class="actions"><button class="button small secondary" data-action="order-status" data-id="'+order.id+'" data-status="confirmed">Подтвердить</button><button class="button small success" data-action="order-status" data-id="'+order.id+'" data-status="completed">Завершить</button><button class="button small danger" data-action="order-status" data-id="'+order.id+'" data-status="cancelled">Отменить</button></div>';
      return '<tr><td>'+order.id+'</td><td>'+esc(order.buyer_name)+'</td><td>'+esc(order.vendor_name)+'</td><td>€'+money(order.total)+'</td><td><span class="pill '+esc(order.status)+'">'+esc(statusLabel(order.status))+'</span></td>'+(compact?'':'<td>'+actions+'</td>')+'</tr>';
    }).join('')+'</tbody></table></div>';
  }

  function routeTitle(){
    return ({users:['Пользователи','Управление доступом тестовый аккаунтов.'],tasks:['Задачи Helpify','Контроль статусов пользовательских задач.'],products:['Товары MyDealer','Демонстрационная модерация карточек.'],orders:['Заказы MyDealer','Контроль состояния заказов.']})[state.route];
  }

  function listView(){
    var title=routeTitle();
    var table='';
    if(state.route==='users'){table=usersTable(state.data.users||[]);}
    if(state.route==='tasks'){table=taskTable(state.data.tasks||[],false);}
    if(state.route==='products'){table=productTable(state.data.products||[],false);}
    if(state.route==='orders'){table=orderTable(state.data.orders||[],false);}
    return '<div class="page-head"><div><h1>'+title[0]+'</h1><p class="muted">'+title[1]+'</p></div><button class="button secondary" data-action="refresh">Обновить</button></div>'+table;
  }

  function render(){
    if(!state.session){loginView();return;}
    if(state.busy && !state.data){root.innerHTML=shell('<div class="spinner">Загрузка данных…</div>');return;}
    root.innerHTML=shell(state.route==='dashboard'?dashboardView(state.data||{}):listView());
  }

  function loadRoute(){
    if(!state.session){render();return Promise.resolve();}
    state.busy=true;state.data=null;render();
    var token=state.session.token;
    var promise=state.route==='dashboard'?API.dashboard(token):state.route==='users'?API.users(token):state.route==='tasks'?API.tasks(token):state.route==='products'?API.products(token):API.orders(token);
    return promise.then(function(payload){state.data=payload;state.busy=false;render();}).catch(function(err){
      state.busy=false;
      if(err.status===401||err.status===403){state.session=null;save();setMessage('',errorText(err));loginView();return;}
      state.data={};setMessage('',errorText(err));render();
    });
  }

  function login(form){
    var data=new FormData(form);state.busy=true;state.error='';loginView();
    API.login(data.get('email'),data.get('password')).then(function(payload){
      if(!payload.user||payload.user.role!=='admin'){
        if(payload.token){API.logout(payload.token).catch(function(){});}
        throw {payload:{code:'ADMIN_ACCESS_REQUIRED'}};
      }
      state.session={token:payload.token,user:payload.user};save();state.busy=false;state.route='dashboard';return loadRoute();
    }).catch(function(err){state.busy=false;setMessage('',errorText(err));loginView();});
  }

  function updateAction(kind,id,status){
    var token=state.session.token;state.busy=true;setMessage('','');render();
    var call=kind==='user'?API.userStatus(token,id,status):kind==='task'?API.taskStatus(token,id,status):kind==='product'?API.productStatus(token,id,status):API.orderStatus(token,id,status);
    call.then(function(){state.busy=false;setMessage('Изменение сохранено.','');return loadRoute();}).catch(function(err){state.busy=false;setMessage('',errorText(err));render();});
  }

  root.addEventListener('submit',function(event){
    var form=event.target.closest('form');if(!form){return;}event.preventDefault();
    if(form.dataset.form==='login'){login(form);}
  });

  root.addEventListener('click',function(event){
    var target=event.target.closest('button');if(!target){return;}
    if(target.dataset.route){state.route=target.dataset.route;setMessage('','');loadRoute();return;}
    var action=target.dataset.action;
    if(action==='logout'){
      var token=state.session.token;state.session=null;save();API.logout(token).catch(function(){});setMessage('','');render();return;
    }
    if(action==='refresh'){loadRoute();return;}
    if(action==='user-status'){updateAction('user',target.dataset.id,target.dataset.status);}
    if(action==='task-status'){updateAction('task',target.dataset.id,target.dataset.status);}
    if(action==='product-status'){updateAction('product',target.dataset.id,target.dataset.status);}
    if(action==='order-status'){updateAction('order',target.dataset.id,target.dataset.status);}
  });

  loadSession();
  if(state.session&&state.session.token){
    API.me(state.session.token).then(function(payload){
      if(!payload.user||payload.user.role!=='admin'){throw {status:403,payload:{code:'ADMIN_ACCESS_REQUIRED'}};}
      state.session.user=payload.user;save();return loadRoute();
    }).catch(function(err){state.session=null;save();setMessage('',errorText(err));render();});
  }else{render();}
})();

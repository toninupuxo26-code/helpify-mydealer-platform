(function(){
  'use strict';
  function request(path,options,token){
    var settings=options||{};
    settings.headers=Object.assign({'Accept':'application/json'},settings.headers||{});
    if(settings.body && typeof settings.body!=='string'){
      settings.headers['Content-Type']='application/json';
      settings.body=JSON.stringify(settings.body);
    }
    if(token){settings.headers.Authorization='Bearer '+token;}
    return fetch('/api'+path,settings).then(function(response){
      return response.json().catch(function(){return {};}).then(function(payload){
        if(!response.ok){
          var error=new Error(payload.message||'API request failed');
          error.status=response.status;
          error.payload=payload;
          throw error;
        }
        return payload;
      });
    });
  }
  window.AdminApi={
    login:function(email,password){return request('/auth/login',{method:'POST',body:{email:email,password:password}});},
    me:function(token){return request('/auth/me',{},token);},
    logout:function(token){return request('/auth/logout',{method:'POST'},token);},
    dashboard:function(token){return request('/admin/dashboard',{},token);},
    users:function(token){return request('/admin/users',{},token);},
    userStatus:function(token,id,status){return request('/admin/users/'+id+'/status',{method:'POST',body:{status:status}},token);},
    tasks:function(token){return request('/admin/tasks',{},token);},
    taskStatus:function(token,id,status){return request('/admin/tasks/'+id+'/status',{method:'POST',body:{status:status}},token);},
    products:function(token){return request('/admin/products',{},token);},
    productStatus:function(token,id,status){return request('/admin/products/'+id+'/status',{method:'POST',body:{status:status}},token);},
    orders:function(token){return request('/admin/orders',{},token);},
    orderStatus:function(token,id,status){return request('/admin/orders/'+id+'/status',{method:'POST',body:{status:status}},token);}
  };
})();

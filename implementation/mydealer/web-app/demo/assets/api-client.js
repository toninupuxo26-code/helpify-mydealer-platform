(function (global) {
  'use strict';

  function parseResponse(response) {
    return response.text().then(function (text) {
      var payload = {};

      if (text) {
        try {
          payload = JSON.parse(text);
        } catch (error) {
          payload = {message: 'Server returned an invalid JSON response.'};
        }
      }

      if (!response.ok) {
        var apiError = new Error(payload.message || 'API request failed.');
        apiError.status = response.status;
        apiError.code = payload.code || 'API_REQUEST_FAILED';
        apiError.errors = payload.errors || null;
        apiError.payload = payload;
        throw apiError;
      }

      return payload;
    });
  }

  function request(path, options) {
    var settings = options || {};
    var headers = {
      Accept: 'application/json'
    };

    if (settings.body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }

    if (settings.token) {
      headers.Authorization = 'Bearer ' + settings.token;
    }

    return fetch('/api' + path, {
      method: settings.method || 'GET',
      headers: headers,
      body: settings.body === undefined ? undefined : JSON.stringify(settings.body),
      credentials: 'same-origin'
    }).then(parseResponse).catch(function (error) {
      if (error && error.status) {
        throw error;
      }

      var networkError = new Error('API is unavailable.');
      networkError.status = 0;
      networkError.code = 'API_NETWORK_ERROR';
      networkError.cause = error;
      throw networkError;
    });
  }

  global.DemoApi = {
    capabilities: function () {
      return request('/auth/capabilities');
    },
    login: function (email, password) {
      return request('/auth/login', {
        method: 'POST',
        body: {email: email, password: password}
      });
    },
    register: function (data) {
      return request('/auth/register', {
        method: 'POST',
        body: data
      });
    },
    me: function (token) {
      return request('/auth/me', {token: token});
    },
    logout: function (token) {
      return request('/auth/logout', {method: 'POST', token: token});
    },
    forgotPassword: function (email) {
      return request('/auth/password/forgot', {
        method: 'POST',
        body: {email: email}
      });
    },
    resetPassword: function (email, code, password) {
      return request('/auth/password/reset', {
        method: 'POST',
        body: {email: email, code: code, password: password}
      });
    },
    listProducts: function (token) {
      return request('/market/products', {token: token});
    },
    createProduct: function (token, data) {
      return request('/market/products', {method: 'POST', token: token, body: data});
    },
    publishProduct: function (token, productId) {
      return request('/market/products/' + productId + '/publish', {method: 'POST', token: token, body: {}});
    },
    getCart: function (token) {
      return request('/market/cart', {token: token});
    },
    addCartItem: function (token, productId, quantity) {
      return request('/market/cart/items', {method: 'POST', token: token, body: {product_id: productId, quantity: quantity || 1}});
    },
    removeCartItem: function (token, productId) {
      return request('/market/cart/items/' + productId, {method: 'DELETE', token: token});
    },
    checkout: function (token) {
      return request('/market/orders/checkout', {method: 'POST', token: token, body: {}});
    },
    listOrders: function (token) {
      return request('/market/orders', {token: token});
    },
    updateOrderStatus: function (token, orderId, status) {
      return request('/market/orders/' + orderId + '/status', {method: 'POST', token: token, body: {status: status}});
    },
    listOrderMessages: function (token, orderId) {
      return request('/market/orders/' + orderId + '/messages', {token: token});
    },
    sendOrderMessage: function (token, orderId, text) {
      return request('/market/orders/' + orderId + '/messages', {method: 'POST', token: token, body: {text: text}});
    }
  };
}(window));

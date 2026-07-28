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
    listTasks: function (token) {
      return request('/work/tasks', {token: token});
    },
    createTask: function (token, data) {
      return request('/work/tasks', {method: 'POST', token: token, body: data});
    },
    createOffer: function (token, taskId, data) {
      return request('/work/tasks/' + taskId + '/offers', {method: 'POST', token: token, body: data});
    },
    selectOffer: function (token, taskId, offerId) {
      return request('/work/tasks/' + taskId + '/offers/' + offerId + '/select', {method: 'POST', token: token, body: {}});
    },
    updateTaskStatus: function (token, taskId, status) {
      return request('/work/tasks/' + taskId + '/status', {method: 'POST', token: token, body: {status: status}});
    },
    listMessages: function (token, taskId) {
      return request('/work/tasks/' + taskId + '/messages', {token: token});
    },
    sendMessage: function (token, taskId, text) {
      return request('/work/tasks/' + taskId + '/messages', {method: 'POST', token: token, body: {text: text}});
    }
  };
}(window));

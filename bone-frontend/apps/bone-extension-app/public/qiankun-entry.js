(function () {
  var instance = {
    bootstrap: function () {
      return Promise.resolve();
    },
    mount: function (props) {
      if (props && props.token) {
        localStorage.setItem('token', props.token);
      }
      return import('http://localhost:3008/src/main.tsx')
        .then(function (m) {
          if (m && m.mount) {
            return m.mount(props);
          }
          return Promise.resolve();
        });
    },
    unmount: function () {
      return Promise.resolve();
    },
  };
  window['bone-extension-app'] = instance;
  return instance;
})();

(function () {
  var instance = {
    bootstrap: function () {
      return Promise.resolve();
    },
    mount: function (props) {
      if (props && props.token) {
        localStorage.setItem('token', props.token);
      }
      // 动态 import() 加载 main.tsx（Vite 会编译它为 ES module）
      return import('http://localhost:3003/src/main.tsx')
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
  window['bone-iam-app'] = instance;
  return instance;
})();

(function () {
  var loaded = null;

  var instance = {
    bootstrap: function () {
      return loaded && loaded.bootstrap ? loaded.bootstrap() : Promise.resolve();
    },
    mount: function (props) {
      if (props && props.token) {
        localStorage.setItem('token', props.token);
      }
      // 用**相对路径**动态 import：硬编码 http://localhost:3003 会让预发/联调环境
      // 一律去连本机 3003 端口，表现为「子应用白屏且无报错」（详设 §2.11）。
      return import('/src/main.tsx')
        .then(function (m) {
          loaded = m;
          return m && m.mount ? m.mount(props) : undefined;
        });
    },
    unmount: function () {
      // 之前这里恒返 resolved（假 unmount）：qiankun 认为已卸载、实际 React 根仍在，
      // 二次进入会撞「Attempted to synchronously unmount a root while React was already rendering」。
      return loaded && loaded.unmount ? loaded.unmount() : Promise.resolve();
    },
  };

  window['bone-iam-app'] = instance;
  return instance;
})();

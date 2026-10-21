/* ============================================================
   富文本编辑器（原生 contenteditable，无第三方依赖）
   用法：App.createRichEditor(toolbarEl, areaEl) → { getHtml, setHtml, clear }
   说明：提交前后端会再做白名单过滤，前端只负责生成 HTML
   单品小铺 · 迭代 2 · 邱圣洁
   ============================================================ */
(function (global) {
  'use strict';

  var COMMANDS = [
    { cmd: 'bold', label: 'B', title: '加粗', style: 'font-weight:700' },
    { cmd: 'italic', label: 'I', title: '斜体', style: 'font-style:italic' },
    { cmd: 'underline', label: 'U', title: '下划线', style: 'text-decoration:underline' },
    { cmd: 'insertUnorderedList', label: '• 列表', title: '无序列表' },
    { cmd: 'insertOrderedList', label: '1. 列表', title: '有序列表' },
    { cmd: 'formatBlock', arg: 'H3', label: '标题', title: '小标题' },
    { cmd: 'formatBlock', arg: 'P', label: '正文', title: '普通段落' },
    { cmd: 'removeFormat', label: '清除格式', title: '清除格式' }
  ];

  function createRichEditor(toolbarEl, areaEl) {
    if (!toolbarEl || !areaEl) return null;

    var html = '';
    COMMANDS.forEach(function (item, idx) {
      html += '<button type="button" class="ed-btn" data-idx="' + idx + '" title="' +
        item.title + '"' + (item.style ? ' style="' + item.style + '"' : '') + '>' +
        item.label + '</button>';
    });
    html += '<button type="button" class="ed-btn ed-clear" data-clear="1" title="清空内容">清空</button>';
    toolbarEl.innerHTML = html;
    toolbarEl.classList.add('editor-toolbar');

    areaEl.setAttribute('contenteditable', 'true');
    areaEl.classList.add('editor-area');

    toolbarEl.addEventListener('mousedown', function (e) { e.preventDefault(); }); /* 保住选区 */

    toolbarEl.addEventListener('click', function (e) {
      var btn = e.target && e.target.closest ? e.target.closest('button') : null;
      if (!btn) return;

      if (btn.getAttribute('data-clear')) {
        areaEl.innerHTML = '';
        areaEl.focus();
        return;
      }
      var item = COMMANDS[Number(btn.getAttribute('data-idx'))];
      if (!item) return;
      areaEl.focus();
      try {
        document.execCommand(item.cmd, false, item.arg || null);
      } catch (err) { /* 忽略不支持的命令 */ }
      syncActive();
    });

    function syncActive() {
      Array.prototype.forEach.call(toolbarEl.querySelectorAll('[data-idx]'), function (btn) {
        var item = COMMANDS[Number(btn.getAttribute('data-idx'))];
        var on = false;
        try { on = document.queryCommandState(item.cmd); } catch (e) { on = false; }
        btn.classList.toggle('is-active', !!on);
      });
    }

    areaEl.addEventListener('keyup', syncActive);
    areaEl.addEventListener('mouseup', syncActive);

    /* 粘贴时只保留纯文本，避免带入外站样式与脚本 */
    areaEl.addEventListener('paste', function (e) {
      e.preventDefault();
      var text = '';
      if (e.clipboardData && e.clipboardData.getData) text = e.clipboardData.getData('text/plain');
      if (!text) return;
      document.execCommand('insertText', false, text);
    });

    return {
      getHtml: function () { return areaEl.innerHTML; },
      getText: function () { return areaEl.textContent || ''; },
      setHtml: function (html) { areaEl.innerHTML = html || ''; },
      clear: function () { areaEl.innerHTML = ''; },
      focus: function () { areaEl.focus(); }
    };
  }

  global.App = global.App || {};
  global.App.createRichEditor = createRichEditor;
})(window);

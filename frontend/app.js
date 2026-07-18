// ============================================================
// 做市商报价监控面板 - 纯 JS 实现
// ============================================================

(function () {
  'use strict';

  // ---- API 配置 ----
  var MARKET_DATA_URL = '/api/market-data';
  var PRICING_URL = '/api/pricing';
  var OMS_URL = '/api/oms';

  // ---- 状态 ----
  var state = {
    refreshInterval: 5000,
    isPaused: false,
    activeTab: 'order',
    orderSide: 'BUY',
    orderType: 'MARKET',
    marketData: [],
    quotes: {},
    orders: [],
    timers: {
      market: null,
      orders: null,
    },
  };

  // ---- 工具函数 ----
  function $(id) {
    return document.getElementById(id);
  }

  function formatPrice(value, decimals) {
    if (value === null || value === undefined || isNaN(value)) return '-';
    return Number(value).toFixed(decimals || 2);
  }

  function formatTime(dateStr) {
    if (!dateStr) return '-';
    try {
      var d = new Date(dateStr);
      return d.toLocaleTimeString('zh-CN', { hour12: false });
    } catch (e) {
      return dateStr;
    }
  }

  function formatDateTime(dateStr) {
    if (!dateStr) return '-';
    try {
      var d = new Date(dateStr);
      return d.toLocaleString('zh-CN', {
        hour12: false,
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch (e) {
      return dateStr;
    }
  }

  function statusClass(status) {
    var map = {
      NEW: 'status-new',
      PENDING_RISK: 'status-pending_risk',
      ACCEPTED: 'status-accepted',
      FILLED: 'status-filled',
      REJECTED: 'status-rejected',
      CANCELLED: 'status-cancelled',
    };
    return map[status] || 'status-cancelled';
  }

  function isCancellable(status) {
    return status === 'NEW' || status === 'PENDING_RISK' || status === 'ACCEPTED';
  }

  // ---- HTTP 工具 ----
  function httpGet(url) {
    return fetch(url).then(function (res) {
      if (!res.ok) {
        throw new Error('HTTP ' + res.status + ' ' + res.statusText);
      }
      return res.json();
    });
  }

  function httpPost(url, body) {
    return fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }).then(function (res) {
      if (!res.ok) {
        return res.text().then(function (text) {
          throw new Error('HTTP ' + res.status + ' - ' + text);
        });
      }
      return res.json();
    });
  }

  function httpDelete(url) {
    return fetch(url, { method: 'DELETE' }).then(function (res) {
      if (!res.ok) {
        throw new Error('HTTP ' + res.status + ' ' + res.statusText);
      }
      return res.json();
    });
  }

  // ---- API 调用 ----
  function fetchMarketData() {
    return httpGet(MARKET_DATA_URL + '/marketdata');
  }

  function fetchQuote(symbol) {
    return httpGet(PRICING_URL + '/quotes/' + symbol);
  }

  function fetchOrders() {
    return httpGet(OMS_URL + '/orders');
  }

  function createOrder(data) {
    return httpPost(OMS_URL + '/orders', data);
  }

  function cancelOrder(orderId) {
    return httpDelete(OMS_URL + '/orders/' + orderId);
  }

  // ---- 轮询管理 ----
  function startPolling() {
    stopPolling();
    if (state.isPaused) return;

    // 行情轮询
    function tickMarket() {
      fetchMarketData()
        .then(function (data) {
          state.marketData = data || [];
          setConnected(true);
          hideError();

          // 获取每个合约的报价
          if (state.marketData.length > 0) {
            return Promise.all(
              state.marketData.map(function (md) {
                return fetchQuote(md.symbol)
                  .then(function (q) {
                    state.quotes[md.symbol] = q;
                    return q;
                  })
                  .catch(function () {
                    return null;
                  });
              })
            );
          }
        })
        .then(function () {
          renderQuoteTable();
        })
        .catch(function (err) {
          setConnected(false);
          showError('行情连接错误: ' + err.message);
        });

      state.timers.market = setTimeout(tickMarket, state.refreshInterval);
    }

    // 订单轮询
    function tickOrders() {
      fetchOrders()
        .then(function (data) {
          state.orders = data || [];
          renderOrderList();
        })
        .catch(function () {
          // 订单错误不显示全局错误
        });

      state.timers.orders = setTimeout(tickOrders, state.refreshInterval);
    }

    tickMarket();
    tickOrders();
  }

  function stopPolling() {
    if (state.timers.market) {
      clearTimeout(state.timers.market);
      state.timers.market = null;
    }
    if (state.timers.orders) {
      clearTimeout(state.timers.orders);
      state.timers.orders = null;
    }
  }

  // ---- 渲染: 行情报价表 ----
  function renderQuoteTable() {
    var loading = $('quote-loading');
    var empty = $('quote-empty');
    var container = $('quote-table-container');
    var tbody = $('quote-tbody');

    var data = state.marketData;

    if (!data || data.length === 0) {
      loading.classList.add('hidden');
      container.classList.add('hidden');
      empty.classList.remove('hidden');
      return;
    }

    loading.classList.add('hidden');
    empty.classList.add('hidden');
    container.classList.remove('hidden');

    var html = '';
    for (var i = 0; i < data.length; i++) {
      var md = data[i];
      var quote = state.quotes[md.symbol];
      var midPrice = (md.bidPrice + md.askPrice) / 2;
      var change = md.lastPrice - midPrice;
      var changePercent = midPrice !== 0 ? (change / midPrice) * 100 : 0;

      var changeColor = change > 0 ? 'text-green' : change < 0 ? 'text-red' : 'text-slate';
      var changeSign = change > 0 ? '+' : '';

      html += '<tr>'
        + '<td class="symbol-cell">' + escapeHtml(md.symbol) + '</td>'
        + '<td class="text-right text-red tabular-nums">' + formatPrice(md.bidPrice) + '</td>'
        + '<td class="text-right text-green tabular-nums">' + formatPrice(md.askPrice) + '</td>'
        + '<td class="text-right tabular-nums" style="font-weight:500">' + formatPrice(md.lastPrice) + '</td>'
        + '<td class="text-right bank-col tabular-nums">' + (quote ? formatPrice(quote.customerBidPrice) : '-') + '</td>'
        + '<td class="text-right bank-col tabular-nums">' + (quote ? formatPrice(quote.customerAskPrice) : '-') + '</td>'
        + '<td class="text-right tabular-nums">' + (quote ? formatPrice(quote.midPrice) : '-') + '</td>'
        + '<td class="text-right tabular-nums text-slate">' + (quote ? quote.spreadBps.toFixed(1) : '-') + '</td>'
        + '<td class="text-right tabular-nums ' + changeColor + '">'
        + changeSign + change.toFixed(2) + ' (' + changeSign + changePercent.toFixed(2) + '%)'
        + '</td>'
        + '<td class="text-right time-cell">' + formatTime(md.timestamp) + '</td>'
        + '</tr>';
    }

    tbody.innerHTML = html;

    // 更新合约下拉选项
    updateSymbolOptions(data);
  }

  function updateSymbolOptions(data) {
    var select = $('order-symbol');
    if (!select) return;

    var currentValue = select.value;
    var options = '';
    for (var i = 0; i < data.length; i++) {
      var symbol = data[i].symbol;
      var selected = symbol === currentValue ? 'selected' : '';
      options += '<option value="' + escapeHtml(symbol) + '" ' + selected + '>' + escapeHtml(symbol) + '</option>';
    }
    select.innerHTML = options;
  }

  function escapeHtml(text) {
    var div = document.createElement('div');
    div.appendChild(document.createTextNode(text));
    return div.innerHTML;
  }

  // ---- 渲染: 订单列表 ----
  function renderOrderList() {
    var loading = $('orders-loading');
    var empty = $('orders-empty');
    var container = $('orders-table-container');
    var tbody = $('orders-tbody');

    var orders = state.orders;

    if (!orders || orders.length === 0) {
      loading.classList.add('hidden');
      container.classList.add('hidden');
      empty.classList.remove('hidden');
      return;
    }

    loading.classList.add('hidden');
    empty.classList.add('hidden');
    container.classList.remove('hidden');

    var html = '';
    for (var i = 0; i < orders.length; i++) {
      var o = orders[i];
      var sideColor = o.side === 'BUY' ? 'text-red' : 'text-green';
      var sideText = o.side === 'BUY' ? '买' : '卖';
      var priceVal = o.avgPrice !== null ? o.avgPrice : o.price;
      var priceStr = priceVal !== null && priceVal !== undefined ? formatPrice(priceVal) : '-';
      var cancellable = isCancellable(o.status);

      html += '<tr>'
        + '<td class="order-id-cell">' + escapeHtml(o.orderId.slice(0, 12)) + '</td>'
        + '<td>' + escapeHtml(o.symbol) + '</td>'
        + '<td class="text-center"><span class="' + sideColor + '" style="font-weight:500">' + sideText + '</span></td>'
        + '<td class="text-center text-slate">' + o.type + '</td>'
        + '<td class="text-right tabular-nums">' + o.qty + '</td>'
        + '<td class="text-right tabular-nums">' + o.filledQty + '</td>'
        + '<td class="text-right tabular-nums">' + priceStr + '</td>'
        + '<td class="text-center"><span class="status-tag ' + statusClass(o.status) + '">' + o.status + '</span></td>'
        + '<td class="text-right time-cell">' + formatDateTime(o.createdAt) + '</td>'
        + '<td class="text-center">'
        + (cancellable ? '<button class="cancel-btn" data-order-id="' + escapeHtml(o.orderId) + '">撤单</button>' : '')
        + '</td>'
        + '</tr>';
    }

    tbody.innerHTML = html;

    // 绑定撤单按钮事件
    var cancelBtns = tbody.querySelectorAll('.cancel-btn');
    for (var j = 0; j < cancelBtns.length; j++) {
      cancelBtns[j].addEventListener('click', handleCancelClick);
    }
  }

  // ---- 连接状态 ----
  function setConnected(connected) {
    var dot = $('status-dot');
    var text = $('status-text');
    if (connected) {
      dot.classList.remove('status-disconnected');
      dot.classList.add('status-connected');
      text.textContent = '已连接';
    } else {
      dot.classList.remove('status-connected');
      dot.classList.add('status-disconnected');
      text.textContent = '未连接';
    }
  }

  function showError(msg) {
    var banner = $('error-banner');
    banner.textContent = msg;
    banner.classList.remove('hidden');
  }

  function hideError() {
    $('error-banner').classList.add('hidden');
  }

  // ---- 事件处理 ----
  function handleIntervalClick(e) {
    var btn = e.target.closest('.interval-btn');
    if (!btn) return;

    var interval = parseInt(btn.dataset.interval, 10);
    state.refreshInterval = interval;

    // 更新按钮状态
    var btns = document.querySelectorAll('.interval-btn');
    for (var i = 0; i < btns.length; i++) {
      btns[i].classList.remove('active');
    }
    btn.classList.add('active');

    // 重启轮询
    startPolling();
  }

  function handlePauseClick() {
    var btn = $('pause-btn');
    if (state.isPaused) {
      state.isPaused = false;
      btn.textContent = '暂停';
      btn.classList.remove('control-green');
      btn.classList.add('control-amber');
      startPolling();
    } else {
      state.isPaused = true;
      btn.textContent = '恢复';
      btn.classList.remove('control-amber');
      btn.classList.add('control-green');
      stopPolling();
    }
  }

  function handleRefreshClick() {
    stopPolling();
    startPolling();
  }

  function handleTabClick(e) {
    var btn = e.target.closest('.tab-btn');
    if (!btn) return;

    var tab = btn.dataset.tab;
    state.activeTab = tab;

    // 更新按钮状态
    var btns = document.querySelectorAll('.tab-btn');
    for (var i = 0; i < btns.length; i++) {
      btns[i].classList.remove('active');
    }
    btn.classList.add('active');

    // 切换内容
    $('tab-order').classList.toggle('hidden', tab !== 'order');
    $('tab-orders').classList.toggle('hidden', tab !== 'orders');
  }

  function handleSideClick(e) {
    var btn = e.target.closest('.side-btn');
    if (!btn) return;

    var side = btn.dataset.side;
    state.orderSide = side;

    var btns = document.querySelectorAll('.side-btn');
    for (var i = 0; i < btns.length; i++) {
      btns[i].classList.remove('active');
    }
    btn.classList.add('active');
  }

  function handleTypeClick(e) {
    var btn = e.target.closest('.type-btn');
    if (!btn) return;

    var type = btn.dataset.type;
    state.orderType = type;

    var btns = document.querySelectorAll('.type-btn');
    for (var i = 0; i < btns.length; i++) {
      btns[i].classList.remove('active');
    }
    btn.classList.add('active');

    // 显示/隐藏价格输入
    $('price-item').classList.toggle('hidden', type !== 'LIMIT');
  }

  function handleOrderSubmit(e) {
    e.preventDefault();

    var customerId = $('order-customerId').value.trim();
    var symbol = $('order-symbol').value;
    var qty = parseFloat($('order-qty').value);
    var priceInput = $('order-price');
    var submitBtn = $('submit-order-btn');
    var msgEl = $('order-message');

    msgEl.classList.add('hidden');

    if (!customerId || !symbol || isNaN(qty) || qty <= 0) {
      showOrderMessage('请填写正确的订单信息', 'error');
      return;
    }

    var req = {
      customerId: customerId,
      symbol: symbol,
      side: state.orderSide,
      type: state.orderType,
      qty: qty,
    };

    if (state.orderType === 'LIMIT') {
      var price = parseFloat(priceInput.value);
      if (isNaN(price) || price <= 0) {
        showOrderMessage('请填写有效的限价', 'error');
        return;
      }
      req.price = price;
    }

    submitBtn.disabled = true;
    submitBtn.textContent = '提交中...';

    createOrder(req)
      .then(function (order) {
        showOrderMessage(
          '下单成功: ' + order.side + ' ' + order.qty + ' ' + order.symbol,
          'success'
        );
        // 刷新订单列表
        return fetchOrders();
      })
      .then(function (data) {
        state.orders = data || [];
        renderOrderList();
      })
      .catch(function (err) {
        showOrderMessage('下单失败: ' + err.message, 'error');
      })
      .finally(function () {
        submitBtn.disabled = false;
        submitBtn.textContent = '提交下单';
      });
  }

  function showOrderMessage(text, type) {
    var msgEl = $('order-message');
    msgEl.textContent = text;
    msgEl.className = 'form-message ' + type;
    msgEl.classList.remove('hidden');
  }

  function handleCancelClick(e) {
    var btn = e.target;
    var orderId = btn.dataset.orderId;
    if (!orderId) return;

    btn.disabled = true;
    btn.textContent = '...';

    cancelOrder(orderId)
      .then(function () {
        return fetchOrders();
      })
      .then(function (data) {
        state.orders = data || [];
        renderOrderList();
      })
      .catch(function (err) {
        alert('撤单失败: ' + err.message);
        btn.disabled = false;
        btn.textContent = '撤单';
      });
  }

  // ---- 初始化 ----
  function init() {
    // 工具栏事件
    document.querySelector('.interval-buttons').addEventListener('click', handleIntervalClick);
    $('pause-btn').addEventListener('click', handlePauseClick);
    $('refresh-btn').addEventListener('click', handleRefreshClick);

    // Tab 切换
    document.querySelector('.tabs').addEventListener('click', handleTabClick);

    // 下单表单
    document.querySelector('.side-buttons').addEventListener('click', handleSideClick);
    document.querySelectorAll('.type-btn')[0].parentNode.addEventListener('click', handleTypeClick);
    $('order-form').addEventListener('submit', handleOrderSubmit);

    // 启动轮询
    startPolling();
  }

  // DOM 就绪后初始化
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

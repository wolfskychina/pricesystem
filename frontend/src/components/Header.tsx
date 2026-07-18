interface HeaderProps {
  connected: boolean;
}

export function Header({ connected }: HeaderProps) {
  return (
    <header className="bg-slate-800 text-white px-6 py-3 flex items-center justify-between shadow-md">
      <div className="flex items-center gap-3">
        <h1 className="text-xl font-bold tracking-wide">做市商报价监控面板</h1>
        <span className="text-slate-400 text-sm">Bank Trading System</span>
      </div>
      <div className="flex items-center gap-2">
        <span
          className={`inline-block w-2.5 h-2.5 rounded-full ${
            connected ? 'bg-green-400' : 'bg-red-400'
          }`}
        />
        <span className="text-sm text-slate-300">
          {connected ? '已连接' : '未连接'}
        </span>
      </div>
    </header>
  );
}

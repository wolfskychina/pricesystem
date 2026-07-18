import type { RefreshInterval } from '../types';

interface RefreshControlProps {
  interval: RefreshInterval;
  onIntervalChange: (interval: RefreshInterval) => void;
  isPaused: boolean;
  onPause: () => void;
  onResume: () => void;
  onRefresh: () => void;
}

const INTERVALS: { value: RefreshInterval; label: string }[] = [
  { value: 1000, label: '1s' },
  { value: 5000, label: '5s' },
  { value: 10000, label: '10s' },
  { value: 30000, label: '30s' },
];

export function RefreshControl({
  interval,
  onIntervalChange,
  isPaused,
  onPause,
  onResume,
  onRefresh,
}: RefreshControlProps) {
  return (
    <div className="flex items-center gap-3">
      <span className="text-sm text-slate-500">刷新频率:</span>
      <div className="flex gap-1">
        {INTERVALS.map(({ value, label }) => (
          <button
            key={value}
            onClick={() => onIntervalChange(value)}
            className={`px-2.5 py-1 text-xs rounded font-medium transition-colors ${
              interval === value
                ? 'bg-blue-600 text-white'
                : 'bg-slate-200 text-slate-600 hover:bg-slate-300'
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      <button
        onClick={isPaused ? onResume : onPause}
        className={`px-3 py-1 text-xs rounded font-medium transition-colors ${
          isPaused
            ? 'bg-green-500 text-white hover:bg-green-600'
            : 'bg-amber-500 text-white hover:bg-amber-600'
        }`}
      >
        {isPaused ? '恢复' : '暂停'}
      </button>
      <button
        onClick={onRefresh}
        className="px-3 py-1 text-xs rounded font-medium bg-slate-200 text-slate-600 hover:bg-slate-300 transition-colors"
      >
        刷新
      </button>
    </div>
  );
}

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePolling } from '../hooks/usePolling';

describe('usePolling', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should fetch data immediately on mount', async () => {
    const fetchFn = vi.fn().mockResolvedValue({ data: 'test' });

    const { result } = renderHook(() => usePolling(fetchFn, 5000));

    // Wait for the initial fetch
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(fetchFn).toHaveBeenCalledTimes(1);
    expect(result.current.data).toEqual({ data: 'test' });
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('should poll at the specified interval', async () => {
    const fetchFn = vi.fn().mockResolvedValue({ count: 0 });

    renderHook(() => usePolling(fetchFn, 5000));

    // Initial fetch
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(fetchFn).toHaveBeenCalledTimes(1);

    // After 5 seconds, another fetch
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });
    expect(fetchFn).toHaveBeenCalledTimes(2);

    // After another 5 seconds
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });
    expect(fetchFn).toHaveBeenCalledTimes(3);
  });

  it('should set error when fetch fails', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => usePolling(fetchFn, 5000));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(result.current.error).toBe('Network error');
    expect(result.current.loading).toBe(false);
  });

  it('should pause and resume polling', async () => {
    const fetchFn = vi.fn().mockResolvedValue('data');

    const { result } = renderHook(() => usePolling(fetchFn, 5000));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(fetchFn).toHaveBeenCalledTimes(1);

    // Pause
    act(() => {
      result.current.pause();
    });
    expect(result.current.isPaused).toBe(true);

    // Advance time while paused - no new fetch
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10000);
    });
    expect(fetchFn).toHaveBeenCalledTimes(1);

    // Resume
    act(() => {
      result.current.resume();
    });
    expect(result.current.isPaused).toBe(false);

    // Now polling resumes
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(fetchFn).toHaveBeenCalledTimes(2);
  });

  it('should not poll when enabled is false', async () => {
    const fetchFn = vi.fn().mockResolvedValue('data');

    renderHook(() => usePolling(fetchFn, 5000, false));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10000);
    });

    expect(fetchFn).not.toHaveBeenCalled();
  });
});

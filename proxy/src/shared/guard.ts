// OWNER: Track B. Rate limit + daily cap trong bộ nhớ isolate.
//
// GIỚI HẠN: Cloudflare có thể chạy nhiều isolate song song và khởi động lại bất cứ lúc nào,
// nên các đếm này là best-effort, KHÔNG phải quota toàn cục hay hard cap tiền. Trần tiền thật
// phải đặt ở console provider (spend limit). Quota toàn cục/atomic thuộc BACK-011.

export class UsageGuard {
  private readonly minuteWindows = new Map<string, { windowStart: number; count: number }>();
  private readonly dailyCounts = new Map<string, { day: string; count: number }>();

  /** Fixed-window 60s. Trả false nếu `key` đã dùng hết `limit` trong phút hiện tại. */
  allowRate(key: string, limit: number, nowMs: number): boolean {
    const windowStart = nowMs - (nowMs % 60_000);
    const entry = this.minuteWindows.get(key);
    if (entry === undefined || entry.windowStart !== windowStart) {
      this.minuteWindows.set(key, { windowStart, count: 1 });
      this.pruneMinuteWindows(windowStart);
      return limit >= 1;
    }
    if (entry.count >= limit) return false;
    entry.count += 1;
    return true;
  }

  /** Giữ chỗ một call upstream trong ngày UTC. Gọi ngay trước khi gọi provider, sau khi đã validate. */
  tryReserveDaily(service: string, cap: number, nowMs: number): boolean {
    const day = new Date(nowMs).toISOString().slice(0, 10);
    const entry = this.dailyCounts.get(service);
    if (entry === undefined || entry.day !== day) {
      if (cap < 1) return false;
      this.dailyCounts.set(service, { day, count: 1 });
      return true;
    }
    if (entry.count >= cap) return false;
    entry.count += 1;
    return true;
  }

  private pruneMinuteWindows(currentWindow: number): void {
    if (this.minuteWindows.size < 1000) return;
    for (const [key, value] of this.minuteWindows) {
      if (value.windowStart !== currentWindow) this.minuteWindows.delete(key);
    }
  }
}

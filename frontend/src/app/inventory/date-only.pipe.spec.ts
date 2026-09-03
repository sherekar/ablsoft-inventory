import { DateOnlyPipe } from './date-only.pipe';

describe('DateOnlyPipe', () => {
  afterEach(() => vi.unstubAllEnvs());

  it.each(['Asia/Kolkata', 'America/Los_Angeles', 'UTC'])('preserves calendar dates in %s', timezone => {
    vi.stubEnv('TZ', timezone);
    const pipe = new DateOnlyPipe();
    expect(pipe.transform('2026-08-25')).toBe('25 Aug 2026');
    expect(pipe.transform('2026-01-01')).toBe('01 Jan 2026');
    expect(pipe.transform('2024-02-29')).toBe('29 Feb 2024');
  });
});

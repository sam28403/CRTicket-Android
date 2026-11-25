# RailLog resume checkpoint

- Snapshot: `2026.04.10`
- Validated cached train records: 9,101 of 15,236
- Completed snapshot dates: 253 of 259
- Crawl endpoint: `2026.10.06`
- Archive: `20260410-cache.tar.gz` (contains `cache-2026.04.10/`)

Restore the cache and resume from the saved progress with:

```sh
mkdir -p build/research/raillog-all
tar -xzf data/RailLog/checkpoints/20260410-cache.tar.gz -C build/research/raillog-all
RAILLOG_WORKERS=16 RAILLOG_MIN_WORKERS=8 RAILLOG_MAX_WORKERS=16 RAILLOG_TRANSIENT_ERROR_THRESHOLD=3 \
  node --use-env-proxy tools/convert-raillog-all.mjs --through=2026.10.06
```

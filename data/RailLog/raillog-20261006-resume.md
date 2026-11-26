# RailLog 2026.10.06 partial crawl checkpoint

This checkpoint preserves the per-train response cache needed to resume the `2026.10.06` timetable snapshot.

- Snapshot-level progress at pause: 258 of 259 snapshots complete.
- Downloader's completed counter: 14,840 of 17,087 trains.
- Cache archive: `raillog-20261006-partial-cache.tar.gz`.
- The archive contains 14,841 unique train response JSON files. All files were revalidated for snapshot version, nonempty stops, and matching normalized train codes; the extra valid cache entry had been written before the downloader's progress counter was updated.
- The scheduled progress monitor and the active crawler were paused after this checkpoint was captured.

To resume from the repository root, extract the existing cache and run the collector:

```sh
mkdir -p build/research/raillog-all
tar -xzf data/RailLog/raillog-20261006-partial-cache.tar.gz -C build/research/raillog-all
RAILLOG_WORKERS=16 \\
RAILLOG_MIN_WORKERS=8 \\
RAILLOG_MAX_WORKERS=16 \\
RAILLOG_TRANSIENT_ERROR_THRESHOLD=3 \\
node --use-env-proxy tools/convert-raillog-all.mjs --through=2026.10.06
```

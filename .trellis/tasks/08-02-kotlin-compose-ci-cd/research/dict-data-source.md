# Research: Authorized dictionary data source

- The authorized source is the live HTML page `https://isdc.pages.dev/`; it embeds the complete dataset in `<script type="application/json" id="asp-data">` rather than loading a separate JSON endpoint.
- Fetch with a browser-like `User-Agent: Mozilla/5.0`; the analyzed snapshot was 19,040,827 bytes and decoded to 75,582,915 bytes of UTF-8 JSON.
- Decode 16 newline-separated segments using the page's printable-ASCII base85 alphabet (ASCII 33..126, excluding `\"`, `'`, `<`), map five characters to four bytes with final-group padding digit 84, Brotli-decompress each segment independently, concatenate, then parse JSON.
- Top-level keys are `g`, `d`, `p`; seven groups contain exactly 49,213 words. Supporting tables observed: `d.n` 92, `d.p` 63, `d.y` 4, `p.s` 52,683, `p.k` 26,422, `p.v` 91,188, `p.r` 32,633.
- Word fields are `w,t,p,e,ec,ay,am,rt,dv,ed,ax,oc,cl,dt`; optional coverage must be preserved. Runtime expands `dv` via `p.v`, `rt` via `p.r`, and `dt` indexes via `p.k/p.s/d.n/d.p/d.y`.
- Relative audio paths use `https://oss.ors.de5.net/` as the runtime prefix.
- Snapshot SHA-256 values: HTML `c5cab0349b5fcf3e56904619a5f15c8923c7021a1f30c2c20639e2e597459c20`; raw JSON `f83cddde1f09a8c4a15e97a6502187c935ba7dbf028e1c45812abd912cebecef`.
- User confirmed the dataset is authorized by the original author. The repository should commit a reproducible converter and validation metadata, not blindly commit the large HTML/raw JSON; the generated SQLite asset may be produced by CI or a documented data-generation command.

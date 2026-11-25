# OCR Lab experimental references

- SubLens (MIT), bnsware, pinned 5f3f3506a5e19630fac8d0c81ab9fec8e3617eee: copied isolate_bright_text into third_party/sublens_filter.py with original MIT notice in third_party/SubLens-LICENSE.txt. Windows public OCR API adapter follows the documented API workflow and is implemented locally. https://github.com/bnsware/SubLens
- MORT (MIT), kmonkeyhead: inspiration for selectable engines and latest-frame/overlay handling. No copied source. https://github.com/kmonkeyhead/MORT
- LunaTranslator (GPL-3.0): inspiration for image-change gating and text comparison. Original implementation, no copied source. https://github.com/HIllya51/LunaTranslator
- Lingolay (GPL-3.0): inspiration for alternative image filters and bounded text settling. Original implementation, no copied source. https://github.com/keremss7/lingolay
- VideoSubFinder (GPL-2.0): reference for isolating subtitles; no code or binaries copied. https://github.com/SWHL/VideoSubFinder

No proprietary OneOCR DLLs or additional external AI APIs. Windows OCR is provided by the OS; optional WinRT bindings only. Other dependencies and model licenses require separate review before production distribution.

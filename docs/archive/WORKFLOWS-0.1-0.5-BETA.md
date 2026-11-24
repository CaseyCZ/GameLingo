# Historical GameLingo publishing workflows

Archived from the audit branch at commit `4ca6d4a78204e246b1ef48ce7e91dab2ecb3c6ae`. These workflows target obsolete build IDs and versions and are not part of the current 0.5 pipeline. Some can overwrite release assets or delete older release listings. The exact files remain in Git history, with immutable links and original blob IDs below. Do not dispatch or restore them for a new release.

| Retired file | Historical source | Git blob SHA |
|---|---|---|
| `publish-gamelingo-beta.yml` | [original at 4ca6d4a](https://github.com/CaseyCZ/GameLingo/blob/4ca6d4a78204e246b1ef48ce7e91dab2ecb3c6ae/.github/workflows/publish-gamelingo-beta.yml) | `a30b4e41a9ffc60b4969e614f38faea64a2b4e56` |
| `publish-gamelingo-v02.yml` | [original at 4ca6d4a](https://github.com/CaseyCZ/GameLingo/blob/4ca6d4a78204e246b1ef48ce7e91dab2ecb3c6ae/.github/workflows/publish-gamelingo-v02.yml) | `47fa1cce9e6281525ffd3589bcda8a6e0fa4b76f` |
| `publish-gamelingo-v03.yml` | [original at 4ca6d4a](https://github.com/CaseyCZ/GameLingo/blob/4ca6d4a78204e246b1ef48ce7e91dab2ecb3c6ae/.github/workflows/publish-gamelingo-v03.yml) | `54e71ee899f0ae3340dfe90344237f9cbc7dd278` |
| `publish-gamelingo-v04.yml` | [original at 4ca6d4a](https://github.com/CaseyCZ/GameLingo/blob/4ca6d4a78204e246b1ef48ce7e91dab2ecb3c6ae/.github/workflows/publish-gamelingo-v04.yml) | `ccd2ec067c6732154ded704458ab5f0f424dcd2a` |
| `package-gamelingo-beta2.yml` | [original at 4ca6d4a](https://github.com/CaseyCZ/GameLingo/blob/4ca6d4a78204e246b1ef48ce7e91dab2ecb3c6ae/.github/workflows/package-gamelingo-beta2.yml) | `629c117faf2782055586fd42c4a700f6b2fe2d69` |
| `publish-gamelingo-current.yml` | [original at 4ca6d4a](https://github.com/CaseyCZ/GameLingo/blob/4ca6d4a78204e246b1ef48ce7e91dab2ecb3c6ae/.github/workflows/publish-gamelingo-current.yml) | `7ef71b799a49e59d131343930b32932e91977adf` |

Active audit pipeline: `audit-0.5-ci.yml` invokes `fast-gamelingo-checks.yml` and `build-gamelingo.yml`. It produces test artifacts only. Targeted historical iOS/Android QA workflows are retained pending comparison of their test coverage, but are not part of the audit CI entrypoint.

No tags, releases, assets, other branches or history were deleted. To inspect an exact original file, use `git show 4ca6d4a78204e246b1ef48ce7e91dab2ecb3c6ae:.github/workflows/<name>`. Do not restore unsafe publishing jobs without review.

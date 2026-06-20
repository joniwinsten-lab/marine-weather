# Play Console — 0.3.0 phone support (internal → open testing)

**Package:** `fi.veneappi.app`  
**versionCode:** 26  
**versionName:** 0.3.0  
**Branch:** `feature/phone-audit` (merge to `main` after sign-off)

## AAB (upload this file)

```text
app/build/outputs/bundle/release/app-release.aab
```

Build:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd /Users/Safelight/Veneappi
./gradlew :app:bundleRelease
```

Verify:

```bash
ls -lh app/build/outputs/bundle/release/app-release.aab
```

---

## 1 — Internal testing (now)

Use this for **your own phones** and a small trusted group (max 100 testers on internal track).

### Console path

1. [Google Play Console](https://play.google.com/console) → **Marine Weather**
2. **Test and release** → **Testing** → **Internal testing**
3. **Create new release** (or **Edit release** if draft exists)
4. **Upload** → select `app-release.aab`
5. **Release name:** `0.3.0 — Phone support (internal)`
6. Paste **Release notes** below (English default; add FI if you use localized notes)
7. **Next** → **Save** (draft) or **Review release** → **Start rollout to Internal testing**

### Add testers

1. Same track: **Internal testing** → **Testers** tab
2. Create or select an **email list** (Gmail accounts only)
3. Add tester emails → **Save**
4. Copy the **opt-in link** and send to testers

Testers must:

1. Open the opt-in link on the phone (signed in with the same Google account)
2. Tap **Become a tester** / **Liity testaajaksi**
3. Install or update from the Play Store link shown on that page

### Release notes — English (default)

```
Phone support: Marine Weather now runs on phones and tablets (portrait and landscape).

Free: map & weather comparison, rain radar, marine weather summaries.

Premium: route planning with weather along the route, 12-day wind outlook, AIS, offline route pack.

Not for primary navigation — planning aid only.
```

### Release notes — Finnish (optional)

```
Puhelintuki: Marine Weather toimii nyt puhelimilla ja tableteilla (pysty- ja vaakasuunta).

Ilmaiseksi: kartta ja säävertailu, sadetutka, merisää.

Premium: reittisuunnittelu, 12 päivän tuuliennuste, AIS, offline-reittipaketti.

Ei päänavigointiin — vain suunnitteluapu.
```

### What to test on real phones

- [ ] Cold start: splash → map visible without crash
- [ ] Portrait: bottom nav, all 5 tabs reachable
- [ ] Landscape (short phone): bottom nav, storm tab reachable
- [ ] Compare: map + weather scroll in portrait
- [ ] Route tab: paywall or premium route UI
- [ ] Billing: trial / subscription still works (same product IDs as 0.2.19)
- [ ] Tablet (if available): navigation rail + two-pane compare unchanged

---

## 2 — Open testing (after internal sign-off)

Open testing is **public opt-in** — anyone with the link can join (up to your country list and Play limits). Promote the **same build** after internal QA passes.

### Console path

1. **Test and release** → **Testing** → **Open testing**
2. First time: complete **Track setup** (countries, feedback email, store listing for testers if prompted)
3. **Create new release**
4. Either:
   - **Promote release** from Internal testing → select the 0.3.0 release, **or**
   - Upload the same `app-release.aab` again (same versionCode 26 — only one active build per code)
5. **Release name:** `0.3.0 — Phone support (open beta)`
6. Same or slightly expanded release notes
7. **Review release** → **Start rollout to Open testing**

### Share with testers

1. **Open testing** → **Testers** tab → copy **Opt-in URL**
2. Post link on website, email, or social — testers join without being on an email list

### Differences: Internal vs Open

| | Internal | Open |
|---|----------|------|
| Testers | Email list (max 100) | Anyone with opt-in link |
| Visibility | Hidden from public store search | May show “Early access” on store listing |
| Review | Usually fast (minutes–hours) | May take longer first time |
| Use when | Team + close friends on real devices | Wider beta before production |

---

## 3 — Production (later, not this upload)

After open beta feedback:

1. Merge `feature/phone-audit` → `main`, tag `snapshot/production-live-0.3.0`
2. **Production** → **Create new release** → promote from Open testing **or** upload new AAB if you bumped versionCode
3. **Staged rollout** (e.g. 20% → 50% → 100%)
4. Update Play **device catalog** screenshots (add phone portrait images)
5. Update store listing text: phones + tablets supported

---

## Play Console checklist (0.3.0)

- [ ] **App content** still valid (privacy URL, data safety, ads = No)
- [ ] **Device catalog** — after first phone AAB, confirm phones appear (no longer tablet-only filter)
- [ ] **Store listing** — consider adding 2+ phone screenshots before production; internal/open can use existing tablet shots
- [ ] **Billing products** unchanged — `route_premium_lifetime`, `marine_weather_premium` ([play-billing-products.md](play-billing-products.md))

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| “Version code already used” | Bump `versionCode` in `app/build.gradle`, rebuild AAB |
| Tester doesn’t see update | Same Google account as opt-in; clear Play Store cache; wait ~15 min |
| Install blocked | Tester must accept opt-in link first |
| Wrong package | Must be `fi.veneappi.app` release AAB, not `friends` debug |

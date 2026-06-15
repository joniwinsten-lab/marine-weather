# Google Play — Policy response: trial / subscription terms

**Issue:** Terms of trial offer or introductory pricing are unclear  
**App:** Marine Weather · `fi.veneappi.app`  
**Fixed in:** `0.2.19` (versionCode **21**)  
**Where to paste:** Play Console → **Policy status** (or the rejection message thread) → **Reply**

---

## Copy-paste reply (English)

```
Thank you for the review feedback.

We updated Marine Weather to version 0.2.19 (versionCode 21) to make all offer terms clear before any purchase or trial action.

There are two separate offers on the Route premium paywall:

1) Device-local 3-day preview — NOT a Google Play subscription trial
• Button label: "Try Premium free for 3 days (no subscription)"
• Terms are shown immediately below this button (not hidden at the bottom)
• Duration: 3 days from tap, once per device
• No payment method required; Google Play Billing is not started
• After 3 days, Premium locks until the user explicitly buys
• No automatic charge; nothing to cancel in Google Play Subscriptions
• To continue after preview: one-time purchase (route_premium_lifetime) or monthly subscription (marine_weather_premium) — prices shown on the buttons from Play ProductDetails

2) Paid products through Google Play Billing
• One-time: route_premium_lifetime — price on "Buy once" button
• Monthly subscription: marine_weather_premium — price on "Subscribe" button
• Subscription terms below the subscribe button: monthly price, billed through Google Play, renews automatically until cancelled in Google Play → Subscriptions
• Paywall includes "Manage subscription in Google Play" link
• Final price and any Play offers are shown in the Google Play checkout

What changed from 0.2.18:
The previous build used "Start 3-day free trial" with disclaimers in small text at the bottom of the screen. Version 0.2.19 renames the offer, lists all trial terms directly under the trial button, and adds explicit subscription terms plus a manage-subscription link.

How to verify (0.2.19):
Open app → Route tab (without premium) → review text under "Try Premium free for 3 days (no subscription)", then lifetime/subscribe buttons and subscription terms below.

We believe this meets Play's requirement to clearly disclose offer duration, post-offer pricing, and cancellation/management paths.
```

---

## Lyhyt versio (jos merkkiraja)

```
We fixed this in v0.2.19 (versionCode 21).

The "3-day" option is a device-local preview only — not a Play subscription trial. Terms now appear directly under the button: 3 days, no payment method, no auto-charge, Premium locks after 3 days, continue only by explicit purchase at prices shown on the paywall.

Monthly subscription terms (price/month, auto-renewal, cancel in Play → Subscriptions) and a "Manage subscription in Google Play" link are on the same paywall screen.

Previous build hid disclaimers in small footer text; 0.2.19 moves all terms next to each offer.
```

---

## Resubmit checklist

1. Build AAB: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
2. Production → Create release → upload **0.2.19** (versionCode **21**)
3. Release name: `0.2.19 — Trial/subscription terms (policy fix)`
4. Paste the English reply above in **Policy status**
5. Submit release for review

---

## Reviewer path in app

| Step | Screen |
|------|--------|
| 1 | Open app → **Route** tab (locked) |
| 2 | Trial button + bullet terms directly below |
| 3 | **Buy once — [price]** and **Subscribe — [price]/month** |
| 4 | Subscription terms text below subscribe button |
| 5 | **Manage subscription in Google Play** link |

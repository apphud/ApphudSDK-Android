# Changelog

## 3.7.2 — 2026-11-11

- Add `Apphud.consumePurchase(purchaseToken)`. It consumes a one-time Google Play purchase that was acknowledged but not consumed, so the product can be bought again. Returns `null` on success, or `ApphudError` with the Google Play `BillingResponseCode` in `errorCode`.
- Purchases made with `Apphud.purchase` now report whether the product was marked consumable.
- Deferred deep link attribution no longer loads a hidden WebView or sends `visitor_id`, and no longer needs an Activity. The result arrives right after registration: set the deep link handler (in `start(...)` or with `setDeeplinkHandler`) before calling `requestDeferredDeeplinkAttribution()`, because a result that arrives while no handler is set is not delivered.
- `requestDeferredDeeplinkAttribution(activity)` is deprecated; call `requestDeferredDeeplinkAttribution()` without arguments.

## 3.7.1 — 2026-10-22

- Fix crashes when Android restores Rules or Figma screens without an active SDK session. Screens from an ended session close safely, preserving the host task.
- Restored Rules and Figma screens now close after process death, even if the host has already called `Apphud.start()` in `Application.onCreate`.

## 3.7.0 — 2026-10-21

- Add opt-in Identity V2 with separate installation, anonymous and user IDs on API v4. V1 remains the default, preserving existing integrations and upgrade behavior.
- Support host-supplied identity and reinstall answers, legacy customer migration, and reinstall detection through Google Block Store.
- In V2, automatic purchase submissions preserve another customer's ownership; explicit Restore purchases transfers eligible subscriptions.
- Improve V2 purchase recovery and bound checkout attempts to 10 minutes. Completed purchases arriving after a timeout are still processed automatically.
- Keep pending reinstall reports with the original identity across retries and relaunches; do not attribute them to another user after logout or an account switch.

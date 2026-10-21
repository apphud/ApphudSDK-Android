# Changelog

## 3.7.0 — 2026-10-21

- Add opt-in Identity V2 with separate installation, anonymous and user IDs on API v4. V1 remains the default, preserving existing integrations and upgrade behavior.
- Support host-supplied identity and reinstall answers, legacy customer migration, and reinstall detection through Google Block Store.
- In V2, automatic purchase submissions preserve another customer's ownership; explicit Restore purchases transfers eligible subscriptions.
- Improve V2 purchase recovery and bound checkout attempts to 10 minutes. Completed purchases arriving after a timeout are still processed automatically.
- Keep pending reinstall reports with the original identity across retries and relaunches; do not attribute them to another user after logout or an account switch.

package com.apphud.sdk

/**
 * The identity model the SDK runs, passed to [Apphud.start]. It is read once per launch: a change
 * takes effect on the next launch.
 *
 * Switching keeps the customer:
 * - [V1] → [V2]: [V2] sends the old device ID until a registration succeeds, and the backend keeps the customer.
 * - [V2] → [V1]: [V1] sends the old device ID (or the installation ID when the install started on [V2])
 *   and the current user ID (or the anonymous ID), and the backend finds the customer by that user ID.
 *   This adds a device record to the customer; apps that disabled the search by user ID may get a new
 *   customer; purchases follow the [V1] rules again. After a logout on [V2], [V1] starts with a new
 *   device ID, as its own logout does.
 */
enum class ApphudIdentityVersion {
    /** One device ID for the install and, until your app sets a user ID, for the user. The behaviour of SDK 3.6 and earlier. The default. */
    V1,

    /**
     * The installation ID, the anonymous ID and your app's user ID as separate IDs. Purchases sent
     * automatically never move a subscription that another logged-in user owns: only a restore does.
     */
    V2,
}

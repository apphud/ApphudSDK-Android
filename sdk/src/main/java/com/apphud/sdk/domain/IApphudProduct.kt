package com.apphud.sdk.domain

enum class ApphudProductType(){
    SUBS(),
    INAPP
}

enum class RecurrenceMode (val mode: Int){
    FINITE_RECURRING(2),
    INFINITE_RECURRING(1),
    NON_RECURRING (3),
    UNDEFINED (0);

    companion object {
        fun getRecurringMode(mode: Int): RecurrenceMode {
            val result = RecurrenceMode.values().firstOrNull { it.mode == mode }
            result?.let {
                return it
            }
            return RecurrenceMode.UNDEFINED
        }
    }
}

interface IApphudProduct {
    fun type() :ApphudProductType?
    fun productId() :String?
    fun title() :String?
    fun description() :String?
    fun priceCurrencyCode(): String?
    fun priceAmountMicros(): String?

    fun oneTimePurchaseOfferDetails() :OneTimePurchaseOfferDetails?
    fun subscriptionOfferDetails() :List<SubscriptionOfferDetails>?

}

data class OneTimePurchaseOfferDetails (
    var priceAmountMicros: Long,
    var formattedPrice: String?,
    var priceCurrencyCode: String?,
    var offerIdToken: String?,
)


data class SubscriptionOfferDetails (
    var pricingPhases: PricingPhases?,
    var basePlanId: String?,
    var offerId: String?,
    var offerToken: String?,
    var offerTags: List<String>?
)

data class PricingPhases (
    var pricingPhaseList: List<PricingPhase>? = null
)


data class PricingPhase (
    var billingPeriod: String?,
    var priceCurrencyCode: String?,
    var formattedPrice: String?,
    var priceAmountMicros: Long,
    var recurrenceMode :RecurrenceMode,
    var billingCycleCount: Int
)



/*
public final class ProductDetails {
    private final String zza;
    private final JSONObject zzb;
    private final String zzc;
    private final String zzd;
    private final String zze;
    private final String zzf;
    private final String zzg;
    private final String zzh;
    private final String zzi;
    private final String zzj;
    @Nullable
    private final String zzk;
    @Nullable
    private final List zzl;
    @Nullable
    private final List zzm;

    public int hashCode() {
        return this.zza.hashCode();
    }

    @Nullable
    public OneTimePurchaseOfferDetails getOneTimePurchaseOfferDetails() {
        List var1 = this.zzm;
        return var1 != null && !var1.isEmpty() ? (OneTimePurchaseOfferDetails)this.zzm.get(0) : null;
    }

    @NonNull
    public String getDescription() {
        return this.zzg;
    }

    @NonNull
    public String getName() {
        return this.zzf;
    }

    @NonNull
    public String getProductId() {
        return this.zzc;
    }

    @NonNull
    public String getProductType() {
        return this.zzd;
    }

    @NonNull
    public String getTitle() {
        return this.zze;
    }

    @NonNull
    public String toString() {
        List var10000 = this.zzl;
        String var1 = this.zzb.toString();
        String var2 = String.valueOf(var10000);
        StringBuilder var10001 = new StringBuilder();
        var10001.append("ProductDetails{jsonString='");
        var10001.append(this.zza);
        var10001.append("', parsedJson=");
        var10001.append(var1);
        var10001.append(", productId='");
        var10001.append(this.zzc);
        var10001.append("', productType='");
        var10001.append(this.zzd);
        var10001.append("', title='");
        var10001.append(this.zze);
        var10001.append("', productDetailsToken='");
        var10001.append(this.zzh);
        var10001.append("', subscriptionOfferDetails=");
        var10001.append(var2);
        var10001.append("}");
        return var10001.toString();
    }

    @Nullable
    public List<SubscriptionOfferDetails> getSubscriptionOfferDetails() {
        return this.zzl;
    }

    ProductDetails(String var1) throws JSONException {
        this.zza = var1;
        this.zzb = new JSONObject(this.zza);
        this.zzc = this.zzb.optString("productId");
        this.zzd = this.zzb.optString("type");
        if (TextUtils.isEmpty(this.zzc)) {
            throw new IllegalArgumentException("Product id cannot be empty.");
        } else if (TextUtils.isEmpty(this.zzd)) {
            throw new IllegalArgumentException("Product type cannot be empty.");
        } else {
            this.zze = this.zzb.optString("title");
            this.zzf = this.zzb.optString("name");
            this.zzg = this.zzb.optString("description");
            this.zzi = this.zzb.optString("packageDisplayName");
            this.zzj = this.zzb.optString("iconUrl");
            this.zzh = this.zzb.optString("skuDetailsToken");
            this.zzk = this.zzb.optString("serializedDocid");
            JSONArray var2 = this.zzb.optJSONArray("subscriptionOfferDetails");
            ArrayList var3;
            int var4;
            if (var2 != null) {
                var3 = new ArrayList();

                for(var4 = 0; var4 < var2.length(); ++var4) {
                    var3.add(new SubscriptionOfferDetails(var2.getJSONObject(var4)));
                }

                this.zzl = var3;
            } else {
                ArrayList var5;
                if (!this.zzd.equals("subs") && !this.zzd.equals("play_pass_subs")) {
                    var5 = null;
                } else {
                    var5 = new ArrayList();
                }

                this.zzl = var5;
            }

            JSONObject var6 = this.zzb.optJSONObject("oneTimePurchaseOfferDetails");
            var2 = this.zzb.optJSONArray("oneTimePurchaseOfferDetailsList");
            var3 = new ArrayList();
            if (var2 == null) {
                if (var6 != null) {
                    var3.add(new OneTimePurchaseOfferDetails(var6));
                    this.zzm = var3;
                } else {
                    this.zzm = null;
                }
            } else {
                for(var4 = 0; var4 < var2.length(); ++var4) {
                    var3.add(new OneTimePurchaseOfferDetails(var2.getJSONObject(var4)));
                }

                this.zzm = var3;
            }
        }
    }

    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof ProductDetails)) {
            return false;
        } else {
            ProductDetails o1 = (ProductDetails)o;
            return TextUtils.equals(this.zza, o1.zza);
        }
    }

    @NonNull
    public final String zza() {
        return this.zzb.optString("packageName");
    }

    final String zzb() {
        return this.zzh;
    }

    @Nullable
    public String zzc() {
        return this.zzk;
    }

    public static final class SubscriptionOfferDetails {
        private final String zza;
        @Nullable
        private final String zzb;
        private final String zzc;
        private final PricingPhases zzd;
        private final List zze;
        @Nullable
        private final zzby zzf;

        @NonNull
        public PricingPhases getPricingPhases() {
            return this.zzd;
        }

        @NonNull
        public String getBasePlanId() {
            return this.zza;
        }

        @Nullable
        public String getOfferId() {
            return this.zzb;
        }

        @NonNull
        public String getOfferToken() {
            return this.zzc;
        }

        @NonNull
        public List<String> getOfferTags() {
            return this.zze;
        }

        SubscriptionOfferDetails(JSONObject var1) throws JSONException {
            this.zza = var1.optString("basePlanId");
            String var2 = var1.optString("offerId");
            if (var2.isEmpty()) {
                var2 = null;
            }

            this.zzb = var2;
            this.zzc = var1.getString("offerIdToken");
            var2 = "pricingPhases";
            this.zzd = new PricingPhases(var1.getJSONArray(var2));
            JSONObject var5 = var1.optJSONObject("installmentPlanDetails");
            zzby var6;
            if (var5 == null) {
                var6 = null;
            } else {
                var6 = new zzby(var5);
            }

            this.zzf = var6;
            ArrayList var7 = new ArrayList();
            JSONArray var3 = var1.optJSONArray("offerTags");
            if (var3 != null) {
                for(int var4 = 0; var4 < var3.length(); ++var4) {
                    var7.add(var3.getString(var4));
                }
            }

            this.zze = var7;
        }
    }

    public static final class OneTimePurchaseOfferDetails {
        private final String zza;
        private final long zzb;
        private final String zzc;
        private final String zzd;
        private final String zze;
        private final zzaf zzf;
        @Nullable
        private final Long zzg;
        @Nullable
        private final zzbz zzh;
        @Nullable
        private final zzcc zzi;
        @Nullable
        private final zzca zzj;
        @Nullable
        private final zzcb zzk;

        public long getPriceAmountMicros() {
            return this.zzb;
        }

        @NonNull
        public String getFormattedPrice() {
            return this.zza;
        }

        @NonNull
        public String getPriceCurrencyCode() {
            return this.zzc;
        }

        OneTimePurchaseOfferDetails(JSONObject var1) throws JSONException {
            this.zza = var1.optString("formattedPrice");
            this.zzb = var1.optLong("priceAmountMicros");
            this.zzc = var1.optString("priceCurrencyCode");
            this.zzd = var1.optString("offerIdToken");
            this.zze = var1.optString("offerId");
            var1.optInt("offerType");
            JSONArray var3 = var1.optJSONArray("offerTags");
            ArrayList var4 = new ArrayList();
            if (var3 != null) {
                for(int var2 = 0; var2 < var3.length(); ++var2) {
                    var4.add(var3.getString(var2));
                }
            }

            this.zzf = zzaf.zzj(var4);
            Long var6;
            if (var1.has("fullPriceMicros")) {
                var6 = var1.optLong("fullPriceMicros");
            } else {
                var6 = null;
            }

            this.zzg = var6;
            JSONObject var7 = var1.optJSONObject("discountDisplayInfo");
            zzbz var8;
            if (var7 == null) {
                var8 = null;
            } else {
                var8 = new zzbz(var7);
            }

            this.zzh = var8;
            var7 = var1.optJSONObject("validTimeWindow");
            zzcc var9;
            if (var7 == null) {
                var9 = null;
            } else {
                var9 = new zzcc(var7);
            }

            this.zzi = var9;
            var7 = var1.optJSONObject("limitedQuantityInfo");
            zzca var10;
            if (var7 == null) {
                var10 = null;
            } else {
                var10 = new zzca(var7);
            }

            this.zzj = var10;
            var1 = var1.optJSONObject("preorderDetails");
            zzcb var5;
            if (var1 == null) {
                var5 = null;
            } else {
                var5 = new zzcb(var1);
            }

            this.zzk = var5;
        }

        @NonNull
        public final String zza() {
            return this.zzd;
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface RecurrenceMode {
        int INFINITE_RECURRING = 1;
        int FINITE_RECURRING = 2;
        int NON_RECURRING = 3;
    }

    public static final class PricingPhase {
        private final String zza;
        private final long zzb;
        private final String zzc;
        private final String zzd;
        private final int zze;
        private final int zzf;

        public int getBillingCycleCount() {
            return this.zze;
        }

        public int getRecurrenceMode() {
            return this.zzf;
        }

        public long getPriceAmountMicros() {
            return this.zzb;
        }

        @NonNull
        public String getBillingPeriod() {
            return this.zzd;
        }

        @NonNull
        public String getFormattedPrice() {
            return this.zza;
        }

        @NonNull
        public String getPriceCurrencyCode() {
            return this.zzc;
        }

        PricingPhase(JSONObject var1) {
            this.zzd = var1.optString("billingPeriod");
            this.zzc = var1.optString("priceCurrencyCode");
            this.zza = var1.optString("formattedPrice");
            this.zzb = var1.optLong("priceAmountMicros");
            this.zzf = var1.optInt("recurrenceMode");
            this.zze = var1.optInt("billingCycleCount");
        }
    }

    public static class PricingPhases {
        private final List zza;

        @NonNull
        public List<PricingPhase> getPricingPhaseList() {
            return this.zza;
        }

        PricingPhases(JSONArray var1) {
            ArrayList var3 = new ArrayList();
            if (var1 != null) {
                for(int var2 = 0; var2 < var1.length(); ++var2) {
                    JSONObject var4 = var1.optJSONObject(var2);
                    if (var4 != null) {
                        var3.add(new PricingPhase(var4));
                    }
                }
            }

            this.zza = var3;
        }
    }
}

 */

/*
====================================================

 */
# UAE Parking (Android prototype)

Bilingual Arabic/English Android app. Uses Android LocationManager + Geocoder (no Google Play Services API dependency) to infer emirate, stores plate fields locally, and opens the system SMS app using ACTION_SENDTO. Does **not** send SMS or charge money itself.

## Supported SMS drafts
- Dubai: 7275, `CODE+NUMBER ZONE HOURS`. User MUST enter zone from sign.
- Sharjah: 5566, `SOURCE NUMBER HOURS` (1,2,3,5).
- Ajman: 5155, `SOURCE CATEGORY NUMBER` (one-hour SMS only).
- Abu Dhabi: 3009, `SOURCE+CODE NUMBER S/P HOURS` (standard/premium selection).
- Umm Al Quwain, Ras Al Khaimah, Fujairah: GPS emirate detection supported; SMS payment draft deliberately disabled because a verified local public-parking SMS destination/format was not established. Do not confuse vehicle plate-source prefixes accepted in *other* emirates with local SMS parking services.

**Limitations:** Geocoder needs compatible geocoding backend and may be unavailable on some Huawei phones, despite not using Google SDK. Last known GPS/network location may be stale; app requires manual confirmation before sending. Does not verify paid parking zones, tariff, parking eligibility, or SMS confirmation. Dubai format assumes standard Dubai plate; other Dubai plate types may need different formatting. Sharjah plate categories and special plates may require registration or a different format. This is a prototype, not production-certified.

## Build APK from a phone
1. Create a GitHub repository and upload the contents of this folder (not the enclosing folder).
2. In GitHub Actions run **Build Android APK** (or push a commit).
3. Download the artifact `UAE-Parking-debug-apk` from the successful run; extract ZIP to get `app-debug.apk`.
4. Install on Android phones that support APK. Confirm app installation security prompts.

Requires Java 17, Gradle 8.9+, Android SDK 35. GitHub Actions uses the preinstalled `gradle` runner command. If unavailable, update workflow to install Gradle using `gradle/actions/setup-gradle@v4` with `gradle-version: '8.9'`.

## Sources (verify again before public release)
- Dubai: https://cms.parkin.ae/en/general-parking-information
- Sharjah: https://ppd.shjmun.gov.ae/faq
- Ajman: https://www.am.gov.ae/wp-content/uploads/2021/06/SMS-PARKING-SERVICE-BROCHURE.pdf

- Abu Dhabi authority (SMS 3009 and format): https://www.wam.ae/en/article/hszrahja-mawaqif-fees-resume-abu-dhabi-from-july
- UAQ caution: https://uaeparking.com/en/guides/umm-al-quwain-parking

## Seven emirates
GPS detection covers Abu Dhabi, Dubai, Sharjah, Ajman, Umm Al Quwain, Ras Al Khaimah and Fujairah. This is not a claim that every emirate has an official SMS parking service. Payment remains blocked where unverified. GPS geocoding can fail; no guessing of payment destinations.

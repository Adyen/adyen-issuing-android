<picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/resources/adyen-issuing-android-hero-dark.svg">
    <img alt="Adyen Issuing Android SDKs — A suite of Android SDKs for the Adyen Issuing platform." src="docs/resources/adyen-issuing-android-hero-light.svg">
</picture>

[![License][shield.license.image]][shield.license.link]

# Adyen Issuing Android

This is the home page of the Adyen Issuing Android SDKs.

The repository contains the following SDKs:

- [Card Provisioning (Offline)][readme.provisioning.sdk]
- [Card Provisioning Sessions][readme.provisioning.sessions]



For integration guides, the Kotlin API reference, the migration guide, and a walkthrough of the example app, see the **[Adyen Issuing Android documentation site][docsite]**.

The repository also contains an [example app][readme.example.app] which demonstrates how to integrate the above SDKs into your banking Android app. 

## Environment Constraints

To ensure a high level of security for the sensitive operations performed by the Adyen Issuing SDKs, several runtime environment constraints are enforced. When integrating and testing the Adyen Issuing SDKs, please note that the following conditions will trigger defensive actions by the SDKs:

- **Debugging**: A debugger is attached to a release build of the client application. Debug builds tolerate an attached debugger to support integration and development.
- **Emulators & Virtual Environments**: The application is running on an emulator or within a virtual environment (a physical Android device is required for development and testing).
- **Rooted Devices**: The device has been rooted.
- **Hooking Frameworks**: A hooking or instrumentation framework is present.
- **Memory Scanning**: Tools are attempting to scan or manipulate the client application's memory.
- **Compromised Hardware**: The hardware integrity of the device is compromised.

These protections are in place to safeguard user data and prevent malicious interactions with the Adyen Issuing SDKs.

## Security Checklist

When integrating the Adyen Issuing Android SDKs into your banking application, it is essential to ensure that your client application is highly secure as it handles sensitive user data and performs critical operations. Please review the following sections in our [Security Guide](SECURITY.md) to ensure your integration meets modern security best practices:

- [1. Prevent Screenshots and Screen Recording](SECURITY.md#1-prevent-screenshots-and-screen-recording)
- [2. Prevent Tapjacking and Overlay Attacks](SECURITY.md#2-prevent-tapjacking-and-overlay-attacks)
- [3. Restrict Backup of Sensitive Data](SECURITY.md#3-restrict-backup-of-sensitive-data)
- [4. Verify Release Builds Are Not Debuggable](SECURITY.md#4-verify-release-builds-are-not-debuggable)
- [5. Protect Exported Components](SECURITY.md#5-protect-exported-components)
- [6. Secure WebViews](SECURITY.md#6-secure-webviews)
- [7. Handle Sensitive Data in Memory Carefully](SECURITY.md#7-handle-sensitive-data-in-memory-carefully)
- [8. ProGuard and R8 Configuration](SECURITY.md#8-proguard-and-r8-configuration)
- [9. Respond to Tamper Signals](SECURITY.md#9-respond-to-tamper-signals)

## License

The SDKs are available under the Apache License, Version 2.0. For more information, see the [LICENSE][shield.license.link] file.<br>
The example app is available under the MIT license. For more information see the example app [LICENSE][license.example.app] file.

[shield.license.image]: https://img.shields.io/github/license/Adyen/adyen-issuing-android
[shield.license.link]: LICENSE
[readme.provisioning.sdk]: https://adyen.github.io/adyen-issuing-android/AdyenIssuingSdks/latest/network-token-provisioning/provisioning-sdk/
[readme.provisioning.sessions]: https://adyen.github.io/adyen-issuing-android/AdyenIssuingSdks/latest/network-token-provisioning/provisioning-sessions/


[readme.example.app]: example/PUBLIC_README.md
[license.example.app]: example/LICENSE
[docsite]: https://adyen.github.io/adyen-issuing-android/AdyenIssuingSdks/latest/

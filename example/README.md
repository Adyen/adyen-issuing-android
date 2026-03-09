# Adyen Google Wallet Provisioning Example App

This example application demonstrates how to integrate the Adyen Issuing Provisioning SDK to enable push provisioning of cards to Google Wallet.

## Overview

The app showcases the following key steps:

1.  **Fetching Activation Data**: Retrieving the necessary card activation data from your backend.
2.  **Initializing the SDK**: Creating a `CardProvisioning` client using the activation data.
3.  **Checking Provisioning Status**: Determining if a card can be added to Google Wallet or if it's already added.
4.  **Provisioning the Card**: Initiating the "Add to Google Wallet" flow and handling the Opaque Payment Card (OPC) data exchange.

## Key Components

*   **`MainViewModel.kt`**: Contains the core logic for interacting with the Provisioning SDK. It handles the state management and the provisioning flow.
*   **`Backend.kt`**: Defines the interface for backend communication. In a real app, this would make network requests to your server to fetch activation data and OPC data.
*   **`MockBackend.kt`**: A mock implementation of the `Backend` interface for demonstration purposes.
*   **`MainActivity.kt`**: The entry point of the application, which sets up the UI and provides the `Activity` context to the SDK.

## Getting Started

1.  **Request the Google Tap and Pay SDK and Host it Locally**: See [Get the Google Push Provisioning API](../docs/apiDocumentation/README.md#get-the-google-push-provisioning-api) for instructions on how to request the SDK and host it using maven local.
2.  **Clone the repository**.
3.  **Open the project in Android Studio**.
4.  **Build and run the `example` module** on an Android device or emulator with Google Play Services.

## Important Notes

*   **Mock Data**: Tapping the "Add to Google Wallet" button will result in an error because the mocked Opaque Payment Card (OPC) data is not valid for actual provisioning with Google.
*   **Backend Integration**: The `MockBackend` is for demonstration only. You must implement a real backend service to securely fetch card data from Adyen's APIs.
*   **Security**: Never hardcode sensitive card data in your application. Always fetch it securely from your backend.
*   **App Architecture**: This sample app demonstrates SDK integration only. The architecture, project structure, coding conventions, and UI design are illustrative and do not represent required or preferred patterns from Adyen.

## Resources

*   [Adyen Issuing Documentation](https://docs.adyen.com/issuing)

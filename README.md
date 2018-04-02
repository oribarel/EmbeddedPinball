# EmbeddedPinball

Our project is a pinball game  based on a CC-1350 launchpad that is connected to 2 servos that function as flippers and a force sensor that is used to encounter that the ball fall into the drain. The game is controlled through a dedicated Android and TI-RTOS applications that communicate over BLE. The Android app transmit the flippers' state to the board using the TI-RTOS application and gets notifications from the TI-RTOS application with regard to the number of lives left.


## Building the Project
### Pre-requisites:
- Java 8 (or above).
- Android SDK 27.
- Android Build tools v27.0.2.
- TI's [CCS compiler](http://processors.wiki.ti.com/index.php/Compiler_Releases).

### Recommended build steps:
1. Download the latest [Android Studio](https://developer.android.com/studio/index.html) and [Code Composer Studio](http://processors.wiki.ti.com/index.php/Download_CCS).
2. Clone the project from the repository to your local computer.
3. Open the Android app project in Android Studio.
4. Connect your Android device to the computer and [enable debug mode](https://www.howtogeek.com/129728/how-to-access-the-developer-options-menu-and-enable-usb-debugging-on-android-4.2/).
5. Download the app to your android device by running the application in Android studio.
6. Open the board apps simple_peripheral_cc1350lp_stack_FlashROM and simple_peripheral_cc1350lp_app_FlashROM projects in Code Composer Studio.
7. Connect your CC1350 launchpad.
8. Run simple_peripheral_cc1350lp_stack_FlashROM (this step should be done only once).
9. Run simple_peripheral_cc1350lp_app_FlashROM and start controlling the launchpad using the Android app.

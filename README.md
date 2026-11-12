# Screeneo U5 红外遥控 / IR Remote

适用于飞利浦 Screeneo U5 国际版 `SCN550/INT` 的社区 Android 遥控器。用带内置红外发射器的 Android 手机安装 [Releases](https://github.com/x420520/screeneo-u5-ir-remote/releases) 中的 APK，打开后把发射口对准投影仪即可使用。

App 直接发送 38 kHz NEC 红外帧。日常使用无需蓝牙配对、Wi-Fi、账号或 ADB。已在小米 15 Pro 和一台 `SCN550/INT` 上实测。其他同款设备应在使用相同红外码表时兼容，其他地区型号、固件和发射器组合仍待验证。

This community remote controls the international Philips Screeneo U5 (`SCN550/INT`) using an Android phone's built-in infrared emitter. Install the APK from Releases and point the emitter at the projector. Infrared control works without pairing. Testing used one `SCN550/INT` and a Xiaomi 15 Pro.

## 功能

- 电源待机与唤醒、HDMI 信号源、方向键、确定、返回和主页。
- 音量加减和静音。按住音量键可连续调节，松手即停止发出新的重复帧。
- 大号方向圆盘和独立的应用设置。按键震动默认开启，可在应用右上角的设置中关闭，实际效果遵循手机的系统触感设置。
- 系统设置、电子对焦、Disney+、Prime Video 和 YouTube 快捷键。
- Google 助理入口。搜索界面是否打开由 U5 当前状态决定，手机语音音频需要独立的音频通道。

按键的实际 NEC 帧、主页的红外组合序列和采集结果见 [红外代码.md](红外代码.md)。

应用仅声明发射红外所需的 `android.permission.TRANSMIT_IR`。按键震动使用 Android 系统触感反馈，无需额外权限。

## Build from source

Install PowerShell 7, a JDK, Android SDK Platform 30, and Android SDK Build-Tools 30.0.3. Set `JAVA_HOME` and `ANDROID_HOME`, or pass the SDK path with `-SdkRoot`.

```powershell
pwsh -File ./build.ps1
```

The script writes a locally signed APK to `build/U5-IR-Remote-debug.apk`. Android requires the same signing certificate for an in-place update. The public Release APK and a locally signed build use different certificates.

`MainActivity.java` contains the button mappings and press behavior. `IrSignal.java` encodes the NEC waveforms. The app uses Android's [`ConsumerIrManager`](https://developer.android.com/reference/android/hardware/ConsumerIrManager) and Android 6.0 or later.

## References

- [Philips SCN550/INT official user manual, remote control on page 7](https://www.documents.philips.com/assets/20221220/653ed121fee34f76bff2af71012b5d0d.pdf#page=7)
- [Android infrared emitter API](https://developer.android.com/reference/android/hardware/ConsumerIrManager)

Source code is available under the [MIT License](LICENSE).

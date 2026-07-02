# GadgetDrive

**Turn your rooted Android device into a USB mass storage device.**

Boot ISO images directly on a PC from your phone.

</div>

---

## What is GadgetDrive?

GadgetDrive exposes your Android device to a connected PC as a standard **USB Mass Storage (UMS)** drive. You select any disk image file (`.iso`, `.img`, etc.), tap **Mount**, and your phone appears as a USB drive on the host computer — perfect for booting live Linux distributions, flashing firmware, or transferring raw images without a dedicated USB stick.

It works by configuring the kernel's **USB ConfigFS gadget** subsystem via root, using the USB Mass Storage function (`mass_storage`) that Android kernels have always included but rarely expose through the UI.

## Features

- **Material You** — follows the device's system colour scheme automatically on Android 12+
- **Dark / light mode** — respects the system theme
- **Real-time activity log** — every shell operation is shown on screen as it happens
- **Read-only toggle** — prevents the PC from writing to your image
- **Persistent selection** — remembers the last selected image across restarts
- **File size display** — shows the selected image size in MiB
- **Clean error reporting** — clear messages when root is denied or the file is missing

## Requirements

| Requirement | Details |
|---|---|
| **Root access** | The app must be granted superuser permission (Magisk, KernelSU, etc.) |
| **Android version** | 5.0 (API 21) or higher |
| **Kernel** | Must support USB ConfigFS gadget with `mass_storage` function |
| **USB cable** | The device must be connected to the PC via USB in the correct mode |

> **Tip:** If your device does not expose ConfigFS, check whether a Magisk module exists for your kernel that enables it.

## Building

Standard Gradle build. The wrapper downloads Gradle 8.1.1 automatically on first run.

```bash
git clone https://github.com/Tadakai/GadgetDrive.git
cd GadgetDrive
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

For a release build:

```bash
./gradlew assembleRelease
```

## Usage

1. Connect your Android device to your PC via USB.
2. Open **GadgetDrive** and grant root access when prompted.
3. Tap the **DISK IMAGE** card and select an `.iso` or `.img` file.
4. (Optional) Toggle **Read-only** off if you need the PC to write to the image.
5. Tap **Mount** — the activity log shows each step in real time.
6. The device should appear as a USB drive on your PC within a few seconds.
7. When done, tap **Unmount** to restore normal USB behaviour.

## How it works

GadgetDrive shells into ConfigFS — the Linux kernel's gadget configuration filesystem — and builds a USB gadget descriptor from scratch:

```
/sys/kernel/config/usb_gadget/swy/
├── idVendor, idProduct, bcdUSB
├── strings/0x409/  (manufacturer, product, serial)
├── configs/swyconfig.1/
│   └── mass_storage.0 -> ../../functions/mass_storage.0
└── functions/mass_storage.0/
    └── lun.0/
        ├── file   ← path to your disk image
        ├── ro     ← read-only flag
        └── cdrom  ← CD-ROM emulation flag
```

It then attaches the gadget to the physical USB Device Controller (UDC), making the device visible on the host PC.

## Troubleshooting

**"Couldn't get root access"** — Open your root manager (Magisk / KernelSU) and grant GadgetDrive superuser permission, then try again.

**Mount succeeds but the PC does not see the drive** — Your kernel may not support ConfigFS UMS. Check `ls /sys/kernel/config/usb_gadget/` exists. Also ensure the USB cable is in data mode (not charging-only).

**App crashes or shows errors** — Check the activity log on screen. All shell output is printed verbatim, which usually reveals the specific failing command.

## License

GadgetDrive is free software, released under the [GNU General Public License v3](LICENSE).

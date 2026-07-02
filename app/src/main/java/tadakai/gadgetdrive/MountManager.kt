package tadakai.gadgetdrive

import android.util.Log
import eu.chainfire.libsuperuser.Shell

object MountManager {

    private const val TAG = "MountManager"

    data class Result(val success: Boolean, val output: List<String>)

    fun mount(file: String, ro: String): Result {

	    val output = Shell.SU.run(arrayOf(
             "SWY_MOUNT_FILE=$file",
             "SWY_MOUNT_CDROM='n'",
             "if [ $ro == 1 ]",
             "then",
               "SWY_MOUNT_READ_ONLY='y'",
             "else",
               "SWY_MOUNT_READ_ONLY='n'",
             "fi",
             "CONFIGFS=`mount -t configfs | head -n1 | cut -d' ' -f 3`",
             "mkdir \$CONFIGFS/usb_gadget/swy # swy: create a new gadget",
             "cd    \$CONFIGFS/usb_gadget/swy # swy: enter the folder",
             "echo 0x1d6b > idVendor  # swy: set the USB manufacturer code",
             "echo 0x0104 > idProduct # swy: set the USB device code",
             "echo 0x0100 > bcdUSB    # swy: set the USB revision",
             "echo 0xEF > bDeviceClass # swy: Multi-interface Function: 0xEF",
             "echo    2 > bDeviceSubClass # swy: USB Common Sub Class 2",
             "echo    1 > bDeviceProtocol # swy: USB IAD Protocol 1",
             "mkdir strings/0x409 # swy: create a folder to store the text descriptors that will be shown to the host; fill it out",
             "echo 1337       > strings/0x409/serialnumber",
             "echo swyter     > strings/0x409/manufacturer",
             "echo [andropxe]  > strings/0x409/product",
             "mkdir configs/swyconfig.1 # swy: create an empty configuration; the name doesn't matter",
             "mkdir configs/swyconfig.1/strings/0x409",
             "echo 'first rndis, then mass_storage to work on win32' > configs/swyconfig.1/strings/0x409/configuration",
             "mkdir functions/mass_storage.0 # swy: create a gadget function of type 'mass_storage', only the part after the . is customizable",
             "echo \$SWY_MOUNT_READ_ONLY                > functions/mass_storage.0/lun.0/ro",
             "echo y                                   > functions/mass_storage.0/lun.0/removable",
             "echo \$SWY_MOUNT_CDROM                    > functions/mass_storage.0/lun.0/cdrom",
             "echo \$SWY_MOUNT_FILE                     > functions/mass_storage.0/lun.0/file # swy: make sure we assign the actual path last, or setting ro/cdrom won't work until we empty this",
             "ln -s functions/mass_storage.0 configs/swyconfig.1 # swy: add a symbolic link to put our function into a premade config folder",
             "# swy: detach the USB port from the original gadget ",
             "echo '' > ../g1/UDC",
             "# swy: enable/attach the gadget to the physical USB device controller; mark this gadget as active",
             "# swy: note: `getprop sys.usb.controller` == `ls /sys/class/udc`",
             "getprop sys.usb.controller > UDC",
             "setprop sys.usb.state mass_storage",
             "echo success"
        ))

        Log.d(TAG, "mount output: $output")

        val success = !(output?.isEmpty() ?: true)
        return Result(success, output ?: emptyList())
    }

    fun unmount(): Result {

	    val output = Shell.SU.run(arrayOf(
             "CONFIGFS=`mount -t configfs | head -n1 | cut -d' ' -f 3`",
             "cd    \$CONFIGFS/usb_gadget/swy # swy: enter the folder",
             "# swy: detach the gadget from the physical USB port",
             "echo '' > UDC",
             "setprop sys.usb.state ''",
             "svc usb resetUsbGadget",
             "svc usb resetUsbPort # swy: https://android.stackexchange.com/a/236070",
             "svc usb setFunctions ''",
             "# swy: reattach to the original gadget",
             "getprop sys.usb.controller > ../g1/UDC",
             "rm configs/swyconfig.1/mass_storage.0 #swy: remove the symbolic link to each function, times two",
             "rmdir configs/swyconfig.1/strings/0x409  #swy: deallocate the configuration strings",
             "rmdir configs/swyconfig.1/               #swy: now we can remove the empty config",
             "rmdir functions/mass_storage.0           #swy: remove the now-unlinked function",
             "rmdir strings/0x409                      #swy: deallocate the gadget strings",
             "cd .. && rmdir swy                       #swy: remove the now-empty gadget",
             "echo success"
        ))

        Log.d(TAG, "unmount output: $output")

        val success = !(output?.isEmpty() ?: true)
        return Result(success, output ?: emptyList())
    }
}
param(
    [string]$WindowTitle = 'Android Emulator - OneLineJournalTest:5580',
    [ValidateRange(0, 32767)]
    [int]$X = 20,
    [ValidateRange(0, 32767)]
    [int]$Y = 20
)
$ErrorActionPreference = 'Stop'

if (!('OneLineJournalTools.EmulatorWindowRecovery' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

namespace OneLineJournalTools
{
    public static class EmulatorWindowRecovery
    {
        private delegate bool WindowVisitor(IntPtr handle, IntPtr parameter);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool EnumWindows(WindowVisitor visitor, IntPtr parameter);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern int GetWindowText(IntPtr handle, StringBuilder title, int capacity);

        [DllImport("user32.dll")]
        private static extern uint GetWindowThreadProcessId(IntPtr handle, out uint processId);

        [DllImport("user32.dll")]
        private static extern bool ShowWindow(IntPtr handle, int command);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool SetWindowPos(IntPtr handle, IntPtr insertAfter, int x, int y,
            int width, int height, uint flags);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool GetWindowRect(IntPtr handle, out WindowBounds bounds);

        [StructLayout(LayoutKind.Sequential)]
        public struct WindowBounds
        {
            public int Left;
            public int Top;
            public int Right;
            public int Bottom;
        }

        public static IntPtr[] Find(string exactTitle)
        {
            var matches = new List<IntPtr>();
            bool enumerated = EnumWindows(delegate(IntPtr handle, IntPtr parameter)
            {
                var title = new StringBuilder(1024);
                GetWindowText(handle, title, title.Capacity);
                if (String.Equals(title.ToString(), exactTitle, StringComparison.Ordinal))
                    matches.Add(handle);
                return true;
            }, IntPtr.Zero);
            if (!enumerated)
                throw new Win32Exception(Marshal.GetLastWin32Error());
            return matches.ToArray();
        }

        public static uint Owner(IntPtr handle)
        {
            uint processId;
            GetWindowThreadProcessId(handle, out processId);
            return processId;
        }

        public static void Move(IntPtr handle, int x, int y)
        {
            ShowWindow(handle, 9);
            // Keep the device's size and aspect ratio; move only this window.
            if (!SetWindowPos(handle, IntPtr.Zero, x, y, 0, 0, 0x0001 | 0x0004 | 0x0040))
                throw new Win32Exception(Marshal.GetLastWin32Error());
        }

        public static WindowBounds Bounds(IntPtr handle)
        {
            WindowBounds bounds;
            if (!GetWindowRect(handle, out bounds))
                throw new Win32Exception(Marshal.GetLastWin32Error());
            return bounds;
        }
    }
}
'@
}

$windows = @([OneLineJournalTools.EmulatorWindowRecovery]::Find($WindowTitle))
if ($windows.Count -eq 0) {
    throw "No running window named '$WindowTitle' was found. Start the emulator first; this script does not start or reset it."
}
if ($windows.Count -ne 1) {
    throw "More than one window matches '$WindowTitle'. No windows were moved."
}
$handle = $windows[0]
$ownerId = [OneLineJournalTools.EmulatorWindowRecovery]::Owner($handle)
$owner = Get-Process -Id $ownerId
if ($owner.ProcessName -ne 'qemu-system-x86_64') {
    throw "The matching window does not belong to the Android emulator. No window was moved."
}
[OneLineJournalTools.EmulatorWindowRecovery]::Move($handle, $X, $Y)
Start-Sleep -Milliseconds 300
$bounds = [OneLineJournalTools.EmulatorWindowRecovery]::Bounds($handle)
if ([Math]::Abs($bounds.Left - $X) -gt 5 -or [Math]::Abs($bounds.Top - $Y) -gt 5) {
    throw "Windows did not retain the requested position. Current position: $($bounds.Left), $($bounds.Top)."
}
Write-Host "Moved '$WindowTitle' to $($bounds.Left),$($bounds.Top). Emulator data and other windows were not changed."

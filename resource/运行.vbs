Set shell = CreateObject("Shell.Application")
Set fso = CreateObject("Scripting.FileSystemObject")
currentDir = fso.GetParentFolderName(WScript.ScriptFullName)
batPath = currentDir & "\run_desktop_auto.bat"

shell.ShellExecute batPath, "", currentDir, "runas", 0
